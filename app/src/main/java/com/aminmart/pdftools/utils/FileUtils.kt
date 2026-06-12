package com.aminmart.pdftools.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.aminmart.pdftools.data.PdfFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat

/**
 * Utility class for file operations
 */
object FileUtils {

    private const val TEMP_DIR_NAME = "pdf_temp"
    private const val DOWNLOAD_DIR_NAME = "PDF Tools"

    /**
     * Get the temporary directory for PDF operations
     */
    fun getTempDir(context: Context): File {
        val tempDir = File(context.cacheDir, TEMP_DIR_NAME)
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return tempDir
    }

    /**
     * Get the download directory for output files
     */
    fun getDownloadDir(context: Context): File {
        val downloadDir = File(context.getExternalFilesDir(null), DOWNLOAD_DIR_NAME)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        return downloadDir
    }

    /**
     * Copy URI content to a temporary file
     */
    fun copyUriToTempFile(context: Context, uri: Uri): File {
        val tempFile = File(getTempDir(context), "temp_${System.currentTimeMillis()}.pdf")
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IOException("Cannot open input stream for URI: $uri")
        
        return tempFile
    }

    /**
     * Copy file to temporary directory
     */
    fun copyToTempFile(context: Context, sourceFile: File): File {
        val tempFile = File(getTempDir(context), "temp_${System.currentTimeMillis()}_${sourceFile.name}")
        sourceFile.copyTo(tempFile, overwrite = true)
        return tempFile
    }

    /**
     * Create output file with given name
     */
    fun createOutputFile(context: Context, filename: String): File {
        val cleanFilename = if (filename.endsWith(".pdf", ignoreCase = true)) {
            filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        } else {
            "${filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.pdf"
        }
        return File(getDownloadDir(context), cleanFilename)
    }

    /**
     * Generate unique filename
     */
    fun generateFilename(prefix: String = "output"): String {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "${prefix}_$timestamp.pdf"
    }

    /**
     * Delete temporary directory and all its contents
     */
    fun cleanTempFiles(context: Context) {
        val tempDir = getTempDir(context)
        deleteRecursively(tempDir)
    }

    /**
     * Delete a specific temporary file
     */
    fun deleteTempFile(context: Context, file: File) {
        if (file.exists() && file.absolutePath.contains(TEMP_DIR_NAME)) {
            file.delete()
        }
    }

    /**
     * Recursively delete a directory and its contents
     */
    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    /**
     * Format file size to human readable string
     */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> {
                val format = DecimalFormat("#.##")
                "${format.format(size / (1024.0 * 1024.0))} MB"
            }
        }
    }

    /**
     * Copy a file into the public Downloads collection via MediaStore so the
     * user can find it in any file manager. Returns the new content Uri, or
     * null on Android 9 and below where the app's external files dir is
     * already accessible and MediaStore.Downloads does not exist.
     */
    fun saveToDownloads(context: Context, file: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(file).use { it.copyTo(outputStream) }
            } ?: throw IOException("Cannot open output stream for $uri")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return uri
    }

    /**
     * Get file from URI, copying to temp if necessary
     */
    fun getFileFromUri(context: Context, uri: Uri): File {
        return when {
            uri.scheme == "file" -> File(uri.path!!)
            uri.scheme == "content" -> copyUriToTempFile(context, uri)
            else -> throw IOException("Unsupported URI scheme: ${uri.scheme}")
        }
    }

    /**
     * Check if file is a valid PDF
     */
    fun isValidPdfFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        if (!file.name.endsWith(".pdf", ignoreCase = true)) return false
        
        // Check PDF header
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                fis.read(header)
                val pdfHeader = String(header)
                return pdfHeader == "%PDF"
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Create PdfFile from Uri
     */
    fun createPdfFileFromUri(context: Context, uri: Uri): PdfFile {
        val file = getFileFromUri(context, uri)
        return PdfFile(
            file = file,
            name = getFileNameFromUri(context, uri) ?: file.name,
            size = file.length()
        )
    }

    /**
     * Get file name from URI
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return when {
            uri.scheme == "file" -> File(uri.path!!).name
            uri.scheme == "content" -> {
                DocumentFile.fromSingleUri(context, uri)?.name
                    ?: uri.lastPathSegment
            }
            else -> null
        }
    }
}
