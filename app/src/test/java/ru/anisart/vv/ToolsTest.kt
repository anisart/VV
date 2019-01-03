package ru.anisart.vv

import org.junit.Test

import org.junit.Assert.*

class ToolsTest {

    @Test
    fun testBBox() {
        val bbox = BBox(11.1, 22.2, 33.3, 44.4)

        assertEquals(bbox.north, 11.1, 0.0)
        assertEquals(bbox.south, 22.2, 0.0)
        assertEquals(bbox.west, 33.3, 0.0)
        assertEquals(bbox.east, 44.4, 0.0)
    }
}