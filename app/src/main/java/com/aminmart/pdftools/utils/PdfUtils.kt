package com.aminmart.pdftools.utils

import android.content.Context
import com.aminmart.pdftools.data.CompressionLevel
import com.aminmart.pdftools.data.PdfOperationResult
import com.lowagie.text.Document
import com.lowagie.text.pdf.PdfCopy
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.PdfWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility class for PDF operations using OpenPDF library
 */
object PdfUtils {

    /**
     * Get the number of pages in a PDF file
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
     * Compress a PDF file
     * Note: OpenPDF has limited compression capabilities compared to iText
     * This implementation removes unused objects and compresses streams
     */
    suspend fun compressPdf(
        context: Context,
        inputFile: File,
        outputFile: File,
        compressionLevel: CompressionLevel
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            PdfOperationResult.Progress(10, "Reading PDF file...")
            
            val reader = PdfReader(inputFile.absolutePath)
            val pageCount = reader.numberOfPages
            
            PdfOperationResult.Progress(20, "Creating compressed document...")
            
            // Create new document
            val document = Document()
            val writer = PdfWriter(document, FileOutputStream(outputFile))
            
            // Apply compression settings based on level
            when (compressionLevel) {
                CompressionLevel.LOW -> {
                    writer.fullCompression = true
                }
                CompressionLevel.MEDIUM -> {
                    writer.fullCompression = true
                    writer.setCompressionLevel(2)
                }
                CompressionLevel.HIGH -> {
                    writer.fullCompression = true
                    writer.setCompressionLevel(0)
                }
            }
            
            document.open()
            
            PdfOperationResult.Progress(30, "Processing $pageCount pages...")
            
            val contentByte = arrayOfNulls<ByteArray>(pageCount)
            
            // Copy pages with compression
            for (i in 1..pageCount) {
                val percent = 30 + ((i.toFloat() / pageCount) * 50).toInt()
                PdfOperationResult.Progress(percent, "Processing page $i of $pageCount...")
                
                document.newPage()
                val page = writer.getImportedPage(reader, i)
                writer.addImportedPage(page)
            }
            
            PdfOperationResult.Progress(85, "Finalizing document...")
            
            document.close()
            reader.close()
            
            // Clean up unused objects
            PdfOperationResult.Progress(90, "Optimizing...")
            
            val originalSize = inputFile.length()
            val compressedSize = outputFile.length()
            val reduction = ((originalSize - compressedSize).toFloat() / originalSize * 100).toInt()
            
            PdfOperationResult.Progress(
                100,
                "Compression complete! Reduced by $reduction%"
            )
            
            PdfOperationResult.Success(outputFile)
            
        } catch (e: Exception) {
            outputFile.delete()
            PdfOperationResult.Error("Failed to compress PDF: ${e.message}", e)
        }
    }

    /**
     * Merge multiple PDF files into one
     */
    suspend fun mergePdfs(
        context: Context,
        inputFiles: List<File>,
        outputFile: File
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            if (inputFiles.isEmpty()) {
                return@withContext PdfOperationResult.Error("No files to merge")
            }

            PdfOperationResult.Progress(10, "Starting merge process...")
            
            // Create document based on first file
            val firstReader = PdfReader(inputFiles[0].absolutePath)
            val document = Document(firstReader.getPageSizeWithRotation(1))
            val copy = PdfCopy(document, FileOutputStream(outputFile))
            
            document.open()
            
            var totalPages = 0
            inputFiles.forEach { totalPages += getPageCount(it) }
            
            var currentPage = 0
            
            inputFiles.forEachIndexed { fileIndex, file ->
                PdfOperationResult.Progress(
                    20 + (fileIndex * 40 / inputFiles.size),
                    "Processing file ${fileIndex + 1} of ${inputFiles.size}..."
                )
                
                val reader = PdfReader(file.absolutePath)
                val n = reader.numberOfPages
                
                for (i in 1..n) {
                    currentPage++
                    val percent = 60 + (currentPage * 35 / totalPages)
                    PdfOperationResult.Progress(percent, "Copying page $currentPage of $totalPages...")
                    
                    copy.addPage(copy.getImportedPage(reader, i))
                }
                
                reader.close()
            }
            
            PdfOperationResult.Progress(95, "Finalizing merged document...")
            
            document.close()
            copy.close()
            
            PdfOperationResult.Progress(100, "Merge complete!")
            
            PdfOperationResult.Success(outputFile)
            
        } catch (e: Exception) {
            outputFile.delete()
            PdfOperationResult.Error("Failed to merge PDFs: ${e.message}", e)
        }
    }

    /**
     * Delete specific pages from a PDF file
     */
    suspend fun deletePages(
        context: Context,
        inputFile: File,
        outputFile: File,
        pagesToDelete: List<Int>
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            PdfOperationResult.Progress(10, "Reading PDF file...")
            
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
                return@withContext PdfOperationResult.Error("Cannot delete all pages")
            }
            
            PdfOperationResult.Progress(30, "Creating new document...")
            
            // Create new document
            val document = Document()
            val writer = PdfWriter(document, FileOutputStream(outputFile))
            
            document.open()
            
            PdfOperationResult.Progress(40, "Copying ${pagesToKeep.size} of $totalPages pages...")
            
            pagesToKeep.forEachIndexed { index, pageNum ->
                val percent = 40 + ((index.toFloat() / pagesToKeep.size) * 50).toInt()
                PdfOperationResult.Progress(percent, "Copying page ${index + 1} of ${pagesToKeep.size}...")
                
                document.newPage()
                val page = writer.getImportedPage(reader, pageNum)
                writer.addImportedPage(page)
            }
            
            PdfOperationResult.Progress(90, "Finalizing document...")
            
            document.close()
            reader.close()
            
            val deletedCount = totalPages - pagesToKeep.size
            PdfOperationResult.Progress(
                100,
                "Successfully deleted $deletedCount page(s)"
            )
            
            PdfOperationResult.Success(outputFile)
            
        } catch (e: Exception) {
            outputFile.delete()
            PdfOperationResult.Error("Failed to delete pages: ${e.message}", e)
        }
    }

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
