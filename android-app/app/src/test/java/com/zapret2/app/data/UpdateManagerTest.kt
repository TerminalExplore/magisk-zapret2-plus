package com.zapret2.app.data

import org.junit.Assert.*
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun `isNewerVersion returns true when latest is newer`() {
        val updateManager = TestableUpdateManager()
        
        assertTrue(updateManager.isNewerVersion("2.0.0", "1.0.0"))
        assertTrue(updateManager.isNewerVersion("1.10.0", "1.9.0"))
        assertTrue(updateManager.isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `isNewerVersion returns false when current is newer`() {
        val updateManager = TestableUpdateManager()
        
        assertFalse(updateManager.isNewerVersion("1.0.0", "2.0.0"))
        assertFalse(updateManager.isNewerVersion("1.9.0", "1.10.0"))
        assertFalse(updateManager.isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun `isNewerVersion handles equal versions`() {
        val updateManager = TestableUpdateManager()
        
        assertFalse(updateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(updateManager.isNewerVersion("2.2.2", "2.2.2"))
    }

    @Test
    fun `isNewerVersion handles version with v prefix`() {
        val updateManager = TestableUpdateManager()
        
        assertTrue(updateManager.isNewerVersion("v2.0.0", "v1.0.0"))
        assertFalse(updateManager.isNewerVersion("v1.0.0", "v2.0.0"))
    }

    @Test
    fun `isNewerVersion handles partial versions`() {
        val updateManager = TestableUpdateManager()
        
        assertTrue(updateManager.isNewerVersion("2", "1"))
        assertFalse(updateManager.isNewerVersion("1", "2"))
    }

    @Test
    fun `parseVersion handles standard versions`() {
        val updateManager = TestableUpdateManager()
        
        assertEquals(listOf(1, 2, 3), updateManager.parseVersion("1.2.3"))
        assertEquals(listOf(10, 20, 30), updateManager.parseVersion("10.20.30"))
    }

    @Test
    fun `parseVersion handles v prefix`() {
        val updateManager = TestableUpdateManager()
        
        assertEquals(listOf(1, 2, 3), updateManager.parseVersion("v1.2.3"))
    }

    @Test
    fun `parseVersion handles beta versions`() {
        val updateManager = TestableUpdateManager()
        
        assertEquals(listOf(1, 0, 0), updateManager.parseVersion("1.0.0-beta"))
        assertEquals(listOf(2, 0, 0), updateManager.parseVersion("v2.0.0-alpha"))
    }

    @Test
    fun `parseVersion handles single number`() {
        val updateManager = TestableUpdateManager()
        
        assertEquals(listOf(5), updateManager.parseVersion("5"))
        assertEquals(listOf(100), updateManager.parseVersion("v100"))
    }

    @Test
    fun `parseVersion handles empty string`() {
        val updateManager = TestableUpdateManager()
        
        assertTrue(updateManager.parseVersion("").isEmpty())
    }

    /**
     * Testable wrapper for UpdateManager to expose protected methods
     */
    private class TestableUpdateManager : UpdateManager(null) {
        fun isNewerVersion(latest: String, current: String): Boolean {
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

        fun parseVersion(version: String): List<Int> {
            return version
                .removePrefix("v")
                .split(".")
                .mapNotNull { part ->
                    part.takeWhile { it.isDigit() }.toIntOrNull()
                }
        }
    }
}
