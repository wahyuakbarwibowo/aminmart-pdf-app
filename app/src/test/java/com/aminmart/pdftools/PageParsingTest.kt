package com.aminmart.pdftools

import com.aminmart.pdftools.data.parsePageOrder
import com.aminmart.pdftools.utils.PdfUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageParsingTest {

    // --- PdfUtils.parsePageRange ---

    @Test
    fun `parsePageRange single page`() {
        assertEquals(listOf(3), PdfUtils.parsePageRange("3", 10))
    }

    @Test
    fun `parsePageRange comma separated list`() {
        assertEquals(listOf(1, 3, 5), PdfUtils.parsePageRange("1,3,5", 10))
    }

    @Test
    fun `parsePageRange simple range`() {
        assertEquals(listOf(2, 3, 4), PdfUtils.parsePageRange("2-4", 10))
    }

    @Test
    fun `parsePageRange mixed list and range`() {
        assertEquals(listOf(1, 3, 4, 5, 7), PdfUtils.parsePageRange("1,3-5,7", 10))
    }

    @Test
    fun `parsePageRange output is sorted and deduplicated`() {
        assertEquals(listOf(1, 2, 3), PdfUtils.parsePageRange("3,1,2,3,1-2", 10))
    }

    @Test
    fun `parsePageRange drops out of bounds pages`() {
        assertEquals(listOf(9, 10), PdfUtils.parsePageRange("9-15", 10))
        assertTrue(PdfUtils.parsePageRange("0", 10).isEmpty())
        assertTrue(PdfUtils.parsePageRange("11", 10).isEmpty())
    }

    @Test
    fun `parsePageRange ignores invalid tokens`() {
        assertEquals(listOf(2), PdfUtils.parsePageRange("abc,2,x-y", 10))
        assertTrue(PdfUtils.parsePageRange("", 10).isEmpty())
        assertTrue(PdfUtils.parsePageRange(" , ,", 10).isEmpty())
    }

    @Test
    fun `parsePageRange reversed range yields nothing`() {
        // "5-3" is an empty ascending range by design; reverse is only
        // supported by parsePageOrder
        assertTrue(PdfUtils.parsePageRange("5-3", 10).isEmpty())
    }

    @Test
    fun `parsePageRange handles whitespace`() {
        assertEquals(listOf(1, 2, 3, 4), PdfUtils.parsePageRange(" 1 , 2 - 4 ", 10))
    }

    // --- parsePageOrder ---

    @Test
    fun `parsePageOrder preserves input order`() {
        assertEquals(listOf(3, 1, 2), parsePageOrder("3,1,2", 3))
    }

    @Test
    fun `parsePageOrder supports reverse ranges`() {
        assertEquals(listOf(5, 4, 3, 2, 1), parsePageOrder("5-1", 5))
    }

    @Test
    fun `parsePageOrder mixed list and ranges`() {
        assertEquals(listOf(1, 3, 4, 5, 7), parsePageOrder("1,3-5,7", 10))
    }

    @Test
    fun `parsePageOrder keeps first occurrence of duplicates`() {
        assertEquals(listOf(2, 1, 3), parsePageOrder("2,1,2,3,1", 3))
    }

    @Test
    fun `parsePageOrder drops out of bounds pages`() {
        assertEquals(listOf(1, 2), parsePageOrder("1,2,99", 2))
    }

    @Test
    fun `parsePageOrder ignores invalid tokens`() {
        assertEquals(listOf(2), parsePageOrder("abc,2,x-y", 10))
        assertTrue(parsePageOrder("", 10).isEmpty())
    }
}
