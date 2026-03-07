package com.aminmart.pdftools.data

import java.io.File

/**
 * Data class representing a PDF file
 */
data class PdfFile(
    val id: String = System.currentTimeMillis().toString() + Math.random().toString(),
    val file: File,
    val name: String = file.name,
    val size: Long = file.length(),
    val pageCount: Int = 0,
    val isSelected: Boolean = false
) {
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
        }
    }
}

/**
 * Compression level enum
 */
enum class CompressionLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Result of a PDF operation
 */
sealed class PdfOperationResult {
    data class Success(val file: File) : PdfOperationResult()
    data class Error(val message: String, val exception: Throwable? = null) : PdfOperationResult()
    data class Progress(val percent: Int, val message: String) : PdfOperationResult()
}
