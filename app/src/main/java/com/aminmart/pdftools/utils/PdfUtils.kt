package com.aminmart.pdftools.utils

import com.aminmart.pdftools.data.CompressionLevel
import com.aminmart.pdftools.data.PdfOperationResult
import com.lowagie.text.Document
import com.lowagie.text.pdf.PdfCopy
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.PdfStamper
import com.lowagie.text.pdf.PdfWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream

/**
 * Utility class for PDF operations using OpenPDF library.
 * All operations are cold flows that run on Dispatchers.IO and emit
 * Progress updates followed by a terminal Success or Error.
 */
object PdfUtils {

    /**
     * Get the number of pages in a PDF file, or -1 if the file is not a readable PDF
     */
    fun getPageCount(file: File): Int {
        return try {
            val reader = PdfReader(file.absolutePath)
            val pageCount = reader.numberOfPages
            reader.close()
            pageCount
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Compress a PDF file by removing unused objects and rewriting all
     * streams with the requested deflate level and a compressed xref (PDF 1.5).
     */
    fun compressPdf(
        inputFile: File,
        outputFile: File,
        compressionLevel: CompressionLevel
    ): Flow<PdfOperationResult> = flow {
        try {
            emit(PdfOperationResult.Progress(10, "Reading PDF file..."))

            val reader = PdfReader(inputFile.absolutePath)

            emit(PdfOperationResult.Progress(30, "Removing unused objects..."))
            reader.removeUnusedObjects()

            emit(PdfOperationResult.Progress(50, "Rewriting with compression..."))

            val stamper = PdfStamper(reader, FileOutputStream(outputFile), PdfWriter.VERSION_1_5)
            stamper.setFullCompression()
            stamper.writer.setCompressionLevel(
                when (compressionLevel) {
                    CompressionLevel.LOW -> 3
                    CompressionLevel.MEDIUM -> 6
                    CompressionLevel.HIGH -> 9
                }
            )

            emit(PdfOperationResult.Progress(80, "Finalizing document..."))

            stamper.close()
            reader.close()

            val originalSize = inputFile.length()
            val compressedSize = outputFile.length()
            val reduction = ((originalSize - compressedSize).toFloat() / originalSize * 100).toInt()

            val message = if (reduction > 0) {
                "Compression complete! Reduced by $reduction%"
            } else {
                "Compression complete! File was already optimized"
            }
            emit(PdfOperationResult.Progress(100, message))

            emit(PdfOperationResult.Success(outputFile))

        } catch (e: Exception) {
            outputFile.delete()
            emit(PdfOperationResult.Error("Failed to compress PDF: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Merge multiple PDF files into one
     */
    fun mergePdfs(
        inputFiles: List<File>,
        outputFile: File
    ): Flow<PdfOperationResult> = flow {
        try {
            if (inputFiles.isEmpty()) {
                emit(PdfOperationResult.Error("No files to merge"))
                return@flow
            }

            emit(PdfOperationResult.Progress(5, "Starting merge process..."))

            val firstReader = PdfReader(inputFiles[0].absolutePath)
            val document = Document(firstReader.getPageSizeWithRotation(1))
            firstReader.close()

            val copy = PdfCopy(document, FileOutputStream(outputFile))
            document.open()

            val totalPages = inputFiles.sumOf { getPageCount(it).coerceAtLeast(0) }
            var currentPage = 0

            inputFiles.forEach { file ->
                val reader = PdfReader(file.absolutePath)

                for (i in 1..reader.numberOfPages) {
                    currentPage++
                    val percent = 5 + (currentPage * 90 / totalPages.coerceAtLeast(1))
                    emit(PdfOperationResult.Progress(percent, "Copying page $currentPage of $totalPages..."))

                    copy.addPage(copy.getImportedPage(reader, i))
                }

                reader.close()
            }

            emit(PdfOperationResult.Progress(95, "Finalizing merged document..."))

            document.close()
            copy.close()

            emit(PdfOperationResult.Progress(100, "Merge complete!"))

            emit(PdfOperationResult.Success(outputFile))

        } catch (e: Exception) {
            outputFile.delete()
            emit(PdfOperationResult.Error("Failed to merge PDFs: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Delete specific pages from a PDF file
     */
    fun deletePages(
        inputFile: File,
        outputFile: File,
        pagesToDelete: List<Int>
    ): Flow<PdfOperationResult> = flow {
        try {
            emit(PdfOperationResult.Progress(10, "Reading PDF file..."))

            val reader = PdfReader(inputFile.absolutePath)
            val totalPages = reader.numberOfPages

            // Validate pages to delete
            val validPagesToDelete = pagesToDelete
                .filter { it in 1..totalPages }
                .distinct()
                .sorted()

            val pagesToKeep = (1..totalPages).filter { it !in validPagesToDelete }

            if (pagesToKeep.isEmpty()) {
                reader.close()
                emit(PdfOperationResult.Error("Cannot delete all pages"))
                return@flow
            }

            emit(PdfOperationResult.Progress(30, "Creating new document..."))

            val document = Document()
            val copy = PdfCopy(document, FileOutputStream(outputFile))

            document.open()

            emit(PdfOperationResult.Progress(40, "Copying ${pagesToKeep.size} of $totalPages pages..."))

            pagesToKeep.forEachIndexed { index, pageNum ->
                val percent = 40 + ((index.toFloat() / pagesToKeep.size) * 50).toInt()
                emit(PdfOperationResult.Progress(percent, "Copying page ${index + 1} of ${pagesToKeep.size}..."))

                copy.addPage(copy.getImportedPage(reader, pageNum))
            }

            emit(PdfOperationResult.Progress(90, "Finalizing document..."))

            document.close()
            copy.close()
            reader.close()

            val deletedCount = totalPages - pagesToKeep.size
            emit(PdfOperationResult.Progress(100, "Successfully deleted $deletedCount page(s)"))

            emit(PdfOperationResult.Success(outputFile))

        } catch (e: Exception) {
            outputFile.delete()
            emit(PdfOperationResult.Error("Failed to delete pages: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Reorder pages in a PDF file based on the specified page order
     */
    fun reorderPages(
        inputFile: File,
        outputFile: File,
        pageOrder: List<Int>
    ): Flow<PdfOperationResult> = flow {
        try {
            emit(PdfOperationResult.Progress(10, "Reading PDF file..."))

            val reader = PdfReader(inputFile.absolutePath)
            val totalPages = reader.numberOfPages

            if (pageOrder.isEmpty()) {
                reader.close()
                emit(PdfOperationResult.Error("Page order cannot be empty"))
                return@flow
            }

            val invalidPages = pageOrder.filter { it !in 1..totalPages }
            if (invalidPages.isNotEmpty()) {
                reader.close()
                emit(PdfOperationResult.Error("Invalid page numbers: ${invalidPages.joinToString(", ")}"))
                return@flow
            }

            emit(PdfOperationResult.Progress(30, "Creating new document..."))

            val document = Document()
            val copy = PdfCopy(document, FileOutputStream(outputFile))

            document.open()

            emit(PdfOperationResult.Progress(40, "Reordering ${pageOrder.size} pages..."))

            pageOrder.forEachIndexed { index, pageNum ->
                val percent = 40 + ((index.toFloat() / pageOrder.size) * 50).toInt()
                emit(PdfOperationResult.Progress(percent, "Copying page ${index + 1} of ${pageOrder.size}..."))

                copy.addPage(copy.getImportedPage(reader, pageNum))
            }

            emit(PdfOperationResult.Progress(90, "Finalizing document..."))

            document.close()
            copy.close()
            reader.close()

            emit(PdfOperationResult.Progress(100, "Successfully reordered ${pageOrder.size} page(s)"))

            emit(PdfOperationResult.Success(outputFile))

        } catch (e: Exception) {
            outputFile.delete()
            emit(PdfOperationResult.Error("Failed to reorder pages: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Parse page range string to list of page numbers
     * Supports formats like: "1,3,5" or "2-4" or "1,3-5,7"
     */
    fun parsePageRange(pageRange: String, totalPages: Int): List<Int> {
        val pages = mutableSetOf<Int>()

        pageRange.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val range = trimmed.split("-")
                if (range.size == 2) {
                    try {
                        val start = range[0].trim().toInt()
                        val end = range[1].trim().toInt()
                        for (i in start..end) {
                            if (i in 1..totalPages) {
                                pages.add(i)
                            }
                        }
                    } catch (e: NumberFormatException) {
                        // Ignore invalid ranges
                    }
                }
            } else {
                try {
                    val pageNum = trimmed.toInt()
                    if (pageNum in 1..totalPages) {
                        pages.add(pageNum)
                    }
                } catch (e: NumberFormatException) {
                    // Ignore invalid page numbers
                }
            }
        }

        return pages.sorted()
    }
}
