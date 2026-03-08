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

/**
 * Parse page order string to list of page numbers
 * Supports formats like: "1,3,5" or "5-1" (reverse) or "1,3-5,7"
 */
fun parsePageOrder(pageOrder: String, totalPages: Int): List<Int> {
    val pages = mutableListOf<Int>()
    val addedPages = mutableSetOf<Int>()

    pageOrder.split(",").forEach { part ->
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val range = trimmed.split("-")
            if (range.size == 2) {
                try {
                    val start = range[0].trim().toInt()
                    val end = range[1].trim().toInt()
                    val step = if (start <= end) 1 else -1
                    for (i in start..end step step) {
                        if (i in 1..totalPages && i !in addedPages) {
                            pages.add(i)
                            addedPages.add(i)
                        }
                    }
                } catch (e: NumberFormatException) {
                    // Ignore invalid ranges
                }
            }
        } else {
            try {
                val pageNum = trimmed.toInt()
                if (pageNum in 1..totalPages && pageNum !in addedPages) {
                    pages.add(pageNum)
                    addedPages.add(pageNum)
                }
            } catch (e: NumberFormatException) {
                // Ignore invalid page numbers
            }
        }
    }

    return pages
}
