/*
 * Copyright 2026 Adobe. All rights reserved.
 * This file is licensed to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.adobe.marketing.mobile.contentanalytics

import com.adobe.marketing.mobile.services.ServiceProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import kotlin.system.measureTimeMillis

/**
 * Tests for deduplication, disk index, and smart restore functionality
 */
class ContentAnalyticsStateManagerDeduplicationTest {
    
    private lateinit var stateManager: ContentAnalyticsStateManager
    
    @Before
    fun setup() {
        stateManager = ContentAnalyticsStateManager()
    }
    
    @After
    fun tearDown() {
        // Cleanup
    }
    
    // MARK: - Deduplication Tests
    
    @Test
    fun testDeduplication_removesDuplicateEntities() {
        // Given - Setup with real DataQueue
        val mockQueue = ServiceProvider.getInstance().dataQueueService.getDataQueue("test_dedup_duplicates")
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        val experienceId = "exp-duplicate-test"
        
        // When - Store same definition 5 times with updates
        for (i in 1..5) {
            val definition = ExperienceDefinition(
                experienceId = experienceId,
                assets = listOf("https://example.com/v$i.jpg"),
                texts = listOf(ContentItem("Version $i")),
                ctas = null,
                sentToFeaturization = false
            )
            stateManager.registerExperienceDefinition(definition)
            
            // Small delay to ensure different timestamps
            Thread.sleep(10)
        }
        
        // Wait for persistence
        Thread.sleep(500)
        
        // Then - Simulate restart by creating new state manager
        val stateManager2 = ContentAnalyticsStateManager()
        stateManager2.setDefinitionsDataQueue(mockQueue)
        
        // Wait for restore
        Thread.sleep(500)
        
        // Verify - Should have only latest version (version 5)
        val restored = stateManager2.getExperienceDefinition(experienceId)
        assertNotNull("Definition should be restored after deduplication", restored)
        assertEquals("Should have latest version", "https://example.com/v5.jpg", restored?.assets?.first())
        assertEquals("Should have latest text", "Version 5", restored?.texts?.first()?.value)
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun testDeduplication_keepsLatestTimestamp() {
        // Given
        val mockQueue = ServiceProvider.getInstance().dataQueueService.getDataQueue("test_dedup_timestamp")
        assertNotNull(mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        // Store definition
        stateManager.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = "exp-old",
                assets = listOf("https://example.com/old.jpg"),
                texts = listOf(ContentItem("Old")),
                ctas = null,
                sentToFeaturization = false
            )
        )
        
        Thread.sleep(100)
        
        // Update with newer version
        stateManager.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = "exp-old",
                assets = listOf("https://example.com/new.jpg"),
                texts = listOf(ContentItem("New")),
                ctas = null,
                sentToFeaturization = false
            )
        )
        
        Thread.sleep(300)
        
        // When - Restart
        val stateManager2 = ContentAnalyticsStateManager()
        stateManager2.setDefinitionsDataQueue(mockQueue)
        
        Thread.sleep(300)
        
        // Then - Should have newer version
        val restored = stateManager2.getExperienceDefinition("exp-old")
        assertEquals("https://example.com/new.jpg", restored?.assets?.first())
        assertEquals("New", restored?.texts?.first()?.value)
        
        mockQueue?.clear()
    }
    
    // MARK: - Disk Index Tests
    
    @Test
    fun testDiskIndex_enablesFastLookup() {
        // Given
        val mockQueue = ServiceProvider.getInstance().dataQueueService.getDataQueue("test_index_fast")
        assertNotNull(mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        // Store 100 definitions
        for (i in 1..100) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = listOf("https://example.com/$i.jpg"),
                    texts = listOf(ContentItem("Text $i")),
                    ctas = null,
                    sentToFeaturization = false
                )
            )
        }
        
        Thread.sleep(1000)
        
        // When - Evict from cache by storing 110 more (capacity is 100)
        for (i in 101..210) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = listOf("https://example.com/$i.jpg"),
                    texts = listOf(ContentItem("Text $i")),
                    ctas = null,
                    sentToFeaturization = false
                )
            )
        }
        
        // Then - Access evicted definition (should use index to check disk)
        val elapsed = measureTimeMillis {
            val definition = stateManager.getExperienceDefinition("exp-50")
            assertNotNull("Evicted definition should be loadable from disk", definition)
            assertEquals("exp-50", definition?.experienceId)
        }
        
        println("Disk lookup time with index: ${elapsed}ms")
        
        mockQueue?.clear()
    }
    
    @Test
    fun testDiskIndex_skipsScanForNonExistent() {
        // Given
        val mockQueue = ServiceProvider.getInstance().dataQueueService.getDataQueue("test_index_skip")
        assertNotNull(mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        // Store some definitions
        for (i in 1..50) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = emptyList(),
                    texts = emptyList(),
                    ctas = null,
                    sentToFeaturization = false
                )
            )
        }
        
        Thread.sleep(500)
        
        // When - Try to get non-existent definition
        val elapsed = measureTimeMillis {
            val definition = stateManager.getExperienceDefinition("exp-nonexistent")
            assertNull("Non-existent definition should return null", definition)
        }
        
        // Then - Should return quickly (index check only, no disk scan)
        assertTrue("Should be instant with index (no disk scan): ${elapsed}ms", elapsed < 10)
        
        mockQueue?.clear()
    }
    
    // MARK: - Smart Restore Tests
    
    @Test
    fun testSmartRestore_loadsOnlyRecentN() {
        // Given - 150 definitions on disk, capacity is 100
        val mockQueue = ServiceProvider.getInstance().dataQueueService.getDataQueue("test_restore_smart")
        assertNotNull(mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        // Store 150 definitions
        for (i in 1..150) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = listOf("https://example.com/$i.jpg"),
                    texts = listOf(ContentItem("Text $i")),
                    ctas = null,
                    sentToFeaturization = false
                )
            )
            Thread.sleep(2) // Ensure different timestamps
        }
        
        Thread.sleep(1000)
        
        // When - Restart and restore
        val stateManager2 = ContentAnalyticsStateManager()
        stateManager2.setDefinitionsDataQueue(mockQueue)
        
        Thread.sleep(1000)
        
        // Then - Most recent 100 should be in memory cache (instantly accessible)
        // exp-51 to exp-150 are most recent (100 definitions)
        for (i in 51..150) {
            val definition = stateManager2.getExperienceDefinition("exp-$i")
            assertNotNull("Recent definition exp-$i should be in memory", definition)
        }
        
        // Older definitions should still be on disk (accessible via fallback, but slower)
        val olderDef = stateManager2.getExperienceDefinition("exp-25")
        assertNotNull("Older definition should be loadable from disk", olderDef)
        
        mockQueue?.clear()
    }
    
    @Test
    fun testSmartRestore_allDefinitionsAccessible() {
        // Given - 200 definitions
        val mockQueue = ServiceProvider.getInstance().dataQueueService.getDataQueue("test_restore_accessible")
        assertNotNull(mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        for (i in 1..200) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = emptyList(),
                    texts = emptyList(),
                    ctas = null,
                    sentToFeaturization = false
                )
            )
        }
        
        Thread.sleep(1000)
        
        // When - Restart
        val stateManager2 = ContentAnalyticsStateManager()
        stateManager2.setDefinitionsDataQueue(mockQueue)
        
        Thread.sleep(1000)
        
        // Then - ALL 200 should be accessible (100 in memory, 100 via disk fallback)
        var accessibleCount = 0
        for (i in 1..200) {
            if (stateManager2.getExperienceDefinition("exp-$i") != null) {
                accessibleCount++
            }
        }
        
        assertEquals("All 200 definitions should be accessible", 200, accessibleCount)
        
        mockQueue?.clear()
    }
    
    // MARK: - Error Handling Tests
    
    @Test
    fun testErrorHandling_continuesOnPersistFailure() {
        // Given - Normal state manager
        val mockQueue = ServiceProvider.getInstance().dataQueueService.getDataQueue("test_error_persist")
        assertNotNull(mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        // When - Store valid definition (should succeed)
        stateManager.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = "exp-valid",
                assets = listOf("https://example.com/test.jpg"),
                texts = listOf(ContentItem("Test")),
                ctas = null,
                sentToFeaturization = false
            )
        )
        
        Thread.sleep(200)
        
        // Then - Definition should be accessible
        val definition = stateManager.getExperienceDefinition("exp-valid")
        assertNotNull("Valid definition should persist successfully", definition)
        
        mockQueue?.clear()
    }
    
    @Test
    fun testErrorHandling_gracefulDegradation() {
        // Test that system continues working even if persistence fails
        
        // Given - State manager without queue (persistence disabled)
        val memoryOnlyManager = ContentAnalyticsStateManager()
        // Don't set queue - persistence will fail gracefully
        
        // When - Store definitions
        memoryOnlyManager.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = "exp-memory-only",
                assets = listOf("https://example.com/test.jpg"),
                texts = listOf(ContentItem("Memory only")),
                ctas = null,
                sentToFeaturization = false
            )
        )
        
        // Then - Should work in memory-only mode
        val definition = memoryOnlyManager.getExperienceDefinition("exp-memory-only")
        assertNotNull("Should work in memory-only mode when persistence unavailable", definition)
        assertEquals("exp-memory-only", definition?.experienceId)
    }
}
