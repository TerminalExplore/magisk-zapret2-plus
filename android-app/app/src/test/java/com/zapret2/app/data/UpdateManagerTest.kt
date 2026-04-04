package com.zapret2.app.data

import org.junit.Assert.*
import org.junit.Test

class UpdateManagerTest {

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = parseVersion(latest)
        val currentParts = parseVersion(current)

        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }

            when {
                latestPart > currentPart -> return true
                latestPart < currentPart -> return false
            }
        }

        return false
    }

    private fun parseVersion(version: String): List<Int> {
        return version
            .removePrefix("v")
            .split(".")
            .mapNotNull { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull()
            }
    }

    @Test
    fun `isNewerVersion returns true when latest is newer`() {
        assertTrue(isNewerVersion("2.0.0", "1.0.0"))
        assertTrue(isNewerVersion("1.10.0", "1.9.0"))
        assertTrue(isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `isNewerVersion returns false when current is newer`() {
        assertFalse(isNewerVersion("1.0.0", "2.0.0"))
        assertFalse(isNewerVersion("1.9.0", "1.10.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun `isNewerVersion handles equal versions`() {
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("2.2.2", "2.2.2"))
    }

    @Test
    fun `isNewerVersion handles version with v prefix`() {
        assertTrue(isNewerVersion("v2.0.0", "v1.0.0"))
        assertFalse(isNewerVersion("v1.0.0", "v2.0.0"))
    }

    @Test
    fun `isNewerVersion handles partial versions`() {
        assertTrue(isNewerVersion("2", "1"))
        assertFalse(isNewerVersion("1", "2"))
    }

    @Test
    fun `parseVersion handles standard versions`() {
        assertEquals(listOf(1, 2, 3), parseVersion("1.2.3"))
        assertEquals(listOf(10, 20, 30), parseVersion("10.20.30"))
    }

    @Test
    fun `parseVersion handles v prefix`() {
        assertEquals(listOf(1, 2, 3), parseVersion("v1.2.3"))
    }

    @Test
    fun `parseVersion handles beta versions`() {
        assertEquals(listOf(1, 0, 0), parseVersion("1.0.0-beta"))
        assertEquals(listOf(2, 0, 0), parseVersion("v2.0.0-alpha"))
    }

    @Test
    fun `parseVersion handles single number`() {
        assertEquals(listOf(5), parseVersion("5"))
        assertEquals(listOf(100), parseVersion("v100"))
    }

    @Test
    fun `parseVersion handles empty string`() {
        assertTrue(parseVersion("").isEmpty())
    }
}
