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

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContentAnalyticsStateManagerTest {
    
    private lateinit var stateManager: ContentAnalyticsStateManager
    
    @Before
    fun setup() {
        stateManager = ContentAnalyticsStateManager()
    }
    
    @Test
    fun `test updateConfiguration and retrieve`() {
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            maxBatchSize = 20
        )
        
        stateManager.updateConfiguration(config)
        
        assertEquals(false, stateManager.batchingEnabled)
        assertEquals(config, stateManager.configuration)
    }
    
    @Test
    fun `test shouldTrackUrl with exclusion pattern`() {
        val config = ContentAnalyticsConfiguration(
            excludedAssetUrlsRegexp = ".*\\.gif$"
        )
        stateManager.updateConfiguration(config)
        
        assertFalse(stateManager.shouldTrackUrl("https://example.com/image.gif"))
        assertTrue(stateManager.shouldTrackUrl("https://example.com/image.jpg"))
    }
    
    @Test
    fun `test registerExperienceDefinition and retrieve`() {
        val definition = ExperienceDefinition(
            experienceId = "test-exp",
            assets = listOf("https://example.com/asset1.jpg"),
            texts = listOf(ContentItem("Hello")),
            ctas = null
        )
        
        stateManager.registerExperienceDefinition(definition)
        
        val retrieved = stateManager.getExperienceDefinition("test-exp")
        assertEquals(definition, retrieved)
    }
    
    @Test
    fun `test markExperienceDefinitionAsSent`() {
        val definition = ExperienceDefinition(
            experienceId = "test-exp",
            assets = listOf("https://example.com/asset1.jpg"),
            texts = emptyList(),
            ctas = null
        )
        
        stateManager.registerExperienceDefinition(definition)
        
        assertFalse(stateManager.hasExperienceDefinitionBeenSent("test-exp"))
        
        stateManager.markExperienceDefinitionAsSent("test-exp")
        
        assertTrue(stateManager.hasExperienceDefinitionBeenSent("test-exp"))
    }
    
    @Test
    fun `test reset clears all state`() {
        val config = ContentAnalyticsConfiguration()
        val definition = ExperienceDefinition(
            experienceId = "test-exp",
            assets = emptyList(),
            texts = emptyList(),
            ctas = null
        )
        
        stateManager.updateConfiguration(config)
        stateManager.registerExperienceDefinition(definition)
        stateManager.markExperienceDefinitionAsSent("test-exp")
        
        assertEquals(1, stateManager.getExperienceDefinitionCount())
        
        stateManager.reset()
        
        assertNull(stateManager.configuration)
        assertEquals(0, stateManager.getExperienceDefinitionCount())
        assertEquals(0, stateManager.getSentExperienceDefinitionCount())
    }
    
    @Test
    fun `test getAssetsForExperience`() {
        val assets = listOf("https://example.com/asset1.jpg", "https://example.com/asset2.jpg")
        val definition = ExperienceDefinition(
            experienceId = "test-exp",
            assets = assets,
            texts = emptyList(),
            ctas = null
        )
        
        stateManager.registerExperienceDefinition(definition)
        
        val retrievedAssets = stateManager.getAssetsForExperience("test-exp")
        assertEquals(assets, retrievedAssets)
    }
    
    @Test
    fun `test getAllExperienceDefinitions`() {
        val def1 = ExperienceDefinition("exp1", emptyList(), emptyList(), null)
        val def2 = ExperienceDefinition("exp2", emptyList(), emptyList(), null)
        
        stateManager.registerExperienceDefinition(def1)
        stateManager.registerExperienceDefinition(def2)
        
        val all = stateManager.getAllExperienceDefinitions()
        assertEquals(2, all.size)
        assertTrue(all.contains(def1))
        assertTrue(all.contains(def2))
    }
    
    // MARK: - Persistence Tests
    
    @Test
    fun `test persistence - store definition persists to disk`() {
        // Given - Setup with real DataQueue
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.definitions.persist")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        val experienceId = "exp-persist-test"
        val definition = ExperienceDefinition(
            experienceId = experienceId,
            assets = listOf("https://example.com/image.jpg"),
            texts = listOf(ContentItem("Test Text")),
            ctas = null
        )
        
        // When - Store definition
        stateManager.registerExperienceDefinition(definition)
        
        // Wait for async persistence
        Thread.sleep(200)
        
        // Then - Verify persisted to disk
        val entity = mockQueue?.peek()
        assertNotNull("Should have persisted definition to disk", entity)
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun `test persistence - restore from disk loads definitions`() {
        // Given - Setup with real DataQueue and persist a definition
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.definitions.restore")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        val experienceId = "exp-restore-test"
        val definition = ExperienceDefinition(
            experienceId = experienceId,
            assets = listOf("https://example.com/restored-image.jpg"),
            texts = listOf(ContentItem("Restored Text")),
            ctas = null
        )
        
        // Store definition with first state manager
        val stateManager1 = ContentAnalyticsStateManager()
        stateManager1.setDefinitionsDataQueue(mockQueue)
        stateManager1.registerExperienceDefinition(definition)
        
        // Wait for persistence
        Thread.sleep(200)
        
        // When - Create NEW state manager (simulating app restart)
        val stateManager2 = ContentAnalyticsStateManager()
        stateManager2.setDefinitionsDataQueue(mockQueue)
        
        // Wait for restoration
        Thread.sleep(200)
        
        // Then - Verify definition was restored
        val restored = stateManager2.getExperienceDefinition(experienceId)
        assertNotNull("Definition should be restored from disk", restored)
        assertEquals(experienceId, restored?.experienceId)
        assertEquals(definition.assets, restored?.assets)
        assertEquals("Restored Text", restored?.texts?.firstOrNull()?.value)
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun `test persistence - asset attribution after restart works correctly`() {
        // Given - Persist definition and simulate restart
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.definitions.attribution")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        val experienceId = "exp-attribution-test"
        val assets = listOf("https://example.com/banner.jpg", "https://example.com/cta.jpg")
        
        // First session: register experience
        val stateManager1 = ContentAnalyticsStateManager()
        stateManager1.setDefinitionsDataQueue(mockQueue)
        stateManager1.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = experienceId,
                assets = assets,
                texts = listOf(ContentItem("Test")),
                ctas = null
            )
        )
        
        // Wait for persistence
        Thread.sleep(200)
        
        // Simulate app restart: new state manager
        val stateManager2 = ContentAnalyticsStateManager()
        stateManager2.setDefinitionsDataQueue(mockQueue)
        
        // Wait for restoration
        Thread.sleep(200)
        
        // When - Retrieve definition for tracking (simulating trackExperience call)
        val definition = stateManager2.getExperienceDefinition(experienceId)
        
        // Then - Assets should be available for attribution
        assertNotNull("Definition should be available after restart", definition)
        assertEquals(2, definition?.assets?.size)
        assertEquals(assets, definition?.assets)
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun `test persistence - mark as sent updates disk`() {
        // Given - Persist definition
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.definitions.marksent")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        val experienceId = "exp-sent-update-test"
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        stateManager.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = experienceId,
                assets = listOf("https://example.com/test.jpg"),
                texts = listOf(ContentItem("Test")),
                ctas = null
            )
        )
        
        // Wait for persistence
        Thread.sleep(200)
        
        // When - Mark as sent
        stateManager.markExperienceDefinitionAsSent(experienceId)
        
        // Wait for disk update
        Thread.sleep(200)
        
        // Then - Verify updated on disk by restoring in new state manager
        val stateManager2 = ContentAnalyticsStateManager()
        stateManager2.setDefinitionsDataQueue(mockQueue)
        
        // Wait for restoration
        Thread.sleep(200)
        
        assertTrue(
            "sentToFeaturization flag should persist to disk",
            stateManager2.hasExperienceDefinitionBeenSent(experienceId)
        )
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun `test persistence - reset clears disk`() {
        // Given - Persist definitions
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.definitions.reset")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        stateManager.registerExperienceDefinition(
            ExperienceDefinition("exp-1", listOf("https://example.com/1.jpg"), 
                listOf(ContentItem("Test 1")), null)
        )
        
        stateManager.registerExperienceDefinition(
            ExperienceDefinition("exp-2", listOf("https://example.com/2.jpg"), 
                listOf(ContentItem("Test 2")), null)
        )
        
        // Wait for persistence
        Thread.sleep(200)
        
        // Verify persisted
        val entityBefore = mockQueue?.peek()
        assertNotNull("Definitions should be on disk before reset", entityBefore)
        
        // When - Reset
        stateManager.reset()
        
        // Wait for reset
        Thread.sleep(200)
        
        // Then - Verify disk cleared
        val entityAfter = mockQueue?.peek()
        assertNull("Disk storage should be cleared after reset", entityAfter)
        
        // Verify memory also cleared
        assertNull(stateManager.getExperienceDefinition("exp-1"))
        assertNull(stateManager.getExperienceDefinition("exp-2"))
    }
    
    @Test
    fun `test persistence - multiple definitions all restored`() {
        // Given - Persist multiple definitions
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.definitions.multiple")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        val stateManager1 = ContentAnalyticsStateManager()
        stateManager1.setDefinitionsDataQueue(mockQueue)
        
        val definitionCount = 10
        for (i in 0 until definitionCount) {
            stateManager1.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = listOf("https://example.com/asset-$i.jpg"),
                    texts = listOf(ContentItem("Text $i")),
                    ctas = null
                )
            )
        }
        
        // Wait for persistence
        Thread.sleep(500)
        
        // When - Simulate restart with new state manager
        val stateManager2 = ContentAnalyticsStateManager()
        stateManager2.setDefinitionsDataQueue(mockQueue)
        
        // Wait for restoration
        Thread.sleep(500)
        
        // Then - Verify all definitions restored
        for (i in 0 until definitionCount) {
            val definition = stateManager2.getExperienceDefinition("exp-$i")
            assertNotNull("Definition $i should be restored", definition)
            assertEquals("https://example.com/asset-$i.jpg", definition?.assets?.firstOrNull())
        }
        
        // Cleanup
        mockQueue?.clear()
    }
    
    // LRU Cache with Disk Fallback Tests
    
    @Test
    fun `test LRU cache enforces capacity limit`() {
        // Given - Setup with real DataQueue
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.lru.capacity")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        // When - Register 110 definitions (capacity is 100)
        for (i in 1..110) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = listOf("https://example.com/asset-$i.jpg"),
                    texts = listOf(ContentItem("Text $i")),
                    ctas = null
                )
            )
        }
        
        // Wait for persistence
        Thread.sleep(500)
        
        // Then - Recent definitions (101-110) should be accessible
        for (i in 101..110) {
            val definition = stateManager.getExperienceDefinition("exp-$i")
            assertNotNull("Recent definition exp-$i should be accessible", definition)
            assertEquals("exp-$i", definition?.experienceId)
        }
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun `test LRU cache disk fallback loads evicted definition`() {
        // Given - Setup with real DataQueue
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.lru.fallback")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        // Register first definition
        stateManager.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = "exp-first",
                assets = listOf("https://example.com/first.jpg"),
                texts = listOf(ContentItem("First")),
                ctas = null
            )
        )
        
        Thread.sleep(200)
        
        // When - Register 110 more definitions to evict "exp-first" from cache
        for (i in 1..110) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = listOf("https://example.com/asset-$i.jpg"),
                    texts = listOf(ContentItem("Text $i")),
                    ctas = null
                )
            )
        }
        
        Thread.sleep(500)
        
        // Then - "exp-first" should still be retrievable (loaded from disk)
        val definition = stateManager.getExperienceDefinition("exp-first")
        assertNotNull("Evicted definition should be loaded from disk", definition)
        assertEquals("exp-first", definition?.experienceId)
        assertEquals("https://example.com/first.jpg", definition?.assets?.first())
        assertEquals("First", definition?.texts?.first()?.content)
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun `test LRU cache disk fallback restores to cache`() {
        // Given - Setup with real DataQueue
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.lru.restore")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        // Register first definition
        stateManager.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = "exp-restored",
                assets = listOf("https://example.com/restored.jpg"),
                texts = listOf(ContentItem("Restored")),
                ctas = null
            )
        )
        
        Thread.sleep(200)
        
        // Evict from cache by registering 110 more
        for (i in 1..110) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = emptyList(),
                    texts = emptyList(),
                    ctas = null
                )
            )
        }
        
        Thread.sleep(500)
        
        // When - Access evicted definition twice
        val firstAccess = stateManager.getExperienceDefinition("exp-restored")
        val secondAccess = stateManager.getExperienceDefinition("exp-restored")
        
        // Then - Both accesses should succeed (first loads from disk, second from cache)
        assertNotNull("First access should load from disk", firstAccess)
        assertNotNull("Second access should hit cache", secondAccess)
        assertEquals(firstAccess?.experienceId, secondAccess?.experienceId)
        assertEquals("exp-restored", secondAccess?.experienceId)
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun `test LRU cache disk fallback mark as sent after eviction`() {
        // Given - Setup with real DataQueue
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.lru.marksent")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        val experienceId = "exp-mark-sent-evicted"
        
        // Register definition
        stateManager.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = experienceId,
                assets = listOf("https://example.com/test.jpg"),
                texts = listOf(ContentItem("Test")),
                ctas = null
            )
        )
        
        Thread.sleep(200)
        
        // Verify not sent initially
        assertFalse(stateManager.hasExperienceDefinitionBeenSent(experienceId))
        
        // Evict from cache
        for (i in 1..110) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = emptyList(),
                    texts = emptyList(),
                    ctas = null
                )
            )
        }
        
        Thread.sleep(500)
        
        // When - Mark as sent (should load from disk)
        stateManager.markExperienceDefinitionAsSent(experienceId)
        
        Thread.sleep(200)
        
        // Then - Should be marked as sent
        assertTrue(
            "Definition should be marked as sent (loaded from disk)",
            stateManager.hasExperienceDefinitionBeenSent(experienceId)
        )
        
        // Verify the definition itself has the flag set
        val definition = stateManager.getExperienceDefinition(experienceId)
        assertNotNull("Definition should be loadable", definition)
        assertTrue("sentToFeaturization flag should be true", definition?.sentToFeaturization ?: false)
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun `test LRU cache disk fallback persistence across restart`() {
        // Given - Setup with real DataQueue
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.lru.restart")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        val experienceId = "exp-restart-test"
        
        // Create first state manager and register definitions
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        stateManager.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = experienceId,
                assets = listOf("https://example.com/restart.jpg"),
                texts = listOf(ContentItem("Restart Test")),
                ctas = null
            )
        )
        
        // Evict from cache
        for (i in 1..110) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-$i",
                    assets = emptyList(),
                    texts = emptyList(),
                    ctas = null
                )
            )
        }
        
        Thread.sleep(500)
        
        // When - Simulate app restart with new state manager
        val stateManager2 = ContentAnalyticsStateManager()
        stateManager2.setDefinitionsDataQueue(mockQueue)
        
        Thread.sleep(500)
        
        // Then - Original definition should be accessible (restored from disk on boot)
        val definition = stateManager2.getExperienceDefinition(experienceId)
        assertNotNull("Definition should survive restart", definition)
        assertEquals(experienceId, definition?.experienceId)
        assertEquals("https://example.com/restart.jpg", definition?.assets?.first())
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun `test LRU cache disk fallback multiple evicted definitions`() {
        // Given - Setup with real DataQueue
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.lru.multiple")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        // Register 10 definitions that will be evicted
        val evictedIds = (1..10).map { "exp-evicted-$it" }
        for (id in evictedIds) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = id,
                    assets = listOf("https://example.com/$id.jpg"),
                    texts = listOf(ContentItem(id)),
                    ctas = null
                )
            )
        }
        
        Thread.sleep(200)
        
        // When - Evict all by registering 110 more
        for (i in 1..110) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-new-$i",
                    assets = emptyList(),
                    texts = emptyList(),
                    ctas = null
                )
            )
        }
        
        Thread.sleep(500)
        
        // Then - All evicted definitions should still be retrievable from disk
        for (id in evictedIds) {
            val definition = stateManager.getExperienceDefinition(id)
            assertNotNull("Evicted definition $id should be loadable from disk", definition)
            assertEquals(id, definition?.experienceId)
            assertEquals("https://example.com/$id.jpg", definition?.assets?.first())
        }
        
        // Cleanup
        mockQueue?.clear()
    }
    
    @Test
    fun `test LRU cache disk fallback performance`() {
        // Given - Setup with real DataQueue
        val mockQueue = com.adobe.marketing.mobile.services.ServiceProvider.getInstance()
            .dataQueueService.getDataQueue("test.lru.performance")
        
        assertNotNull("Failed to create test data queue", mockQueue)
        mockQueue?.clear()
        
        stateManager.setDefinitionsDataQueue(mockQueue)
        
        val targetId = "exp-performance-test"
        
        // Register target definition first
        stateManager.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = targetId,
                assets = listOf("https://example.com/perf.jpg"),
                texts = listOf(ContentItem("Performance Test")),
                ctas = null
            )
        )
        
        // Register 200 more to evict and create a larger disk queue
        for (i in 1..200) {
            stateManager.registerExperienceDefinition(
                ExperienceDefinition(
                    experienceId = "exp-perf-$i",
                    assets = emptyList(),
                    texts = emptyList(),
                    ctas = null
                )
            )
        }
        
        Thread.sleep(1000)
        
        // When - Access evicted definition and measure time
        val start = System.currentTimeMillis()
        val definition = stateManager.getExperienceDefinition(targetId)
        val diskLoadTime = System.currentTimeMillis() - start
        
        // First access from disk
        assertNotNull("Should load from disk", definition)
        println("Disk load time for 200 entities: ${diskLoadTime}ms")
        
        // Second access from cache (should be much faster)
        val start2 = System.currentTimeMillis()
        val definition2 = stateManager.getExperienceDefinition(targetId)
        val cacheHitTime = System.currentTimeMillis() - start2
        
        assertNotNull("Should hit cache", definition2)
        println("Cache hit time: ${cacheHitTime}ms")
        
        // Then - Cache hit should be significantly faster than disk load
        assertTrue(
            "Cache hit should be faster than disk load (cache: ${cacheHitTime}ms, disk: ${diskLoadTime}ms)",
            cacheHitTime < diskLoadTime * 0.5  // At least 50% faster
        )
        
        // Cleanup
        mockQueue?.clear()
    }
}

