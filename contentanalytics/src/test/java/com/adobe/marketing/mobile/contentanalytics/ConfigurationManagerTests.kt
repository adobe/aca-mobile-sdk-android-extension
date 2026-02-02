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

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ConfigurationManagerTests {
    
    private lateinit var configManager: ConfigurationManager
    
    @Before
    fun setUp() {
        configManager = ConfigurationManager()
    }
    
    @After
    fun tearDown() {
        // Clean up
    }
    
    // MARK: - Configuration Update Tests
    
    @Test
    fun `updateConfiguration stores configuration successfully`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = true,
            maxBatchSize = 50,
            flushInterval = 10.0,
            maxWaitTime = 20.0,
            excludedUrlPatterns = emptyList(),
            excludedLocations = emptyList()
        )
        
        // When
        configManager.updateConfiguration(config)
        
        // Then
        val retrieved = configManager.getCurrentConfiguration()
        assertNotNull(retrieved)
        assertTrue(retrieved?.batchingEnabled == true)
        assertEquals(50, retrieved?.maxBatchSize)
    }
    
    @Test
    fun `batchingEnabled returns true when configuration set`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = true,
            maxBatchSize = 10,
            flushInterval = 2.0,
            maxWaitTime = 5.0,
            excludedUrlPatterns = emptyList(),
            excludedLocations = emptyList()
        )
        
        // When
        configManager.updateConfiguration(config)
        
        // Then
        assertTrue(configManager.batchingEnabled)
    }
    
    @Test
    fun `batchingEnabled returns false when no configuration`() {
        // Given - no configuration set
        
        // Then
        assertFalse(configManager.batchingEnabled)
    }
    
    // MARK: - URL Tracking Tests
    
    @Test
    fun `shouldTrackUrl returns true when no configuration`() {
        // Given
        val url = "https://example.com"
        
        // When/Then
        assertTrue(configManager.shouldTrackUrl(url))
    }
    
    @Test
    fun `shouldTrackUrl returns false for excluded pattern`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = true,
            maxBatchSize = 10,
            flushInterval = 2.0,
            maxWaitTime = 5.0,
            excludedUrlPatterns = listOf(".*internal.*"),
            excludedLocations = emptyList()
        )
        configManager.updateConfiguration(config)
        
        val url = "https://example.com/internal/page"
        
        // When/Then
        assertFalse(configManager.shouldTrackUrl(url))
    }
    
    @Test
    fun `shouldTrackUrl returns true for non-matching pattern`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = true,
            maxBatchSize = 10,
            flushInterval = 2.0,
            maxWaitTime = 5.0,
            excludedUrlPatterns = listOf(".*internal.*"),
            excludedLocations = emptyList()
        )
        configManager.updateConfiguration(config)
        
        val url = "https://example.com/public/page"
        
        // When/Then
        assertTrue(configManager.shouldTrackUrl(url))
    }
    
    // MARK: - Experience Tracking Tests
    
    @Test
    fun `shouldTrackExperience returns true when no configuration`() {
        // When/Then
        assertTrue(configManager.shouldTrackExperience("home"))
    }
    
    @Test
    fun `shouldTrackExperience returns true for null location`() {
        // When/Then
        assertTrue(configManager.shouldTrackExperience(null))
    }
    
    @Test
    fun `shouldTrackExperience returns false for excluded location`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = true,
            maxBatchSize = 10,
            flushInterval = 2.0,
            maxWaitTime = 5.0,
            excludedUrlPatterns = emptyList(),
            excludedLocations = listOf("admin.*")
        )
        configManager.updateConfiguration(config)
        
        // When/Then
        assertFalse(configManager.shouldTrackExperience("admin.settings"))
    }
    
    @Test
    fun `shouldTrackExperience returns true for non-matching location`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = true,
            maxBatchSize = 10,
            flushInterval = 2.0,
            maxWaitTime = 5.0,
            excludedUrlPatterns = emptyList(),
            excludedLocations = listOf("admin.*")
        )
        configManager.updateConfiguration(config)
        
        // When/Then
        assertTrue(configManager.shouldTrackExperience("home"))
    }
    
    // MARK: - Asset Location Tracking Tests
    
    @Test
    fun `shouldTrackAssetLocation returns true when no configuration`() {
        // When/Then
        assertTrue(configManager.shouldTrackAssetLocation("https://cdn.example.com/image.jpg"))
    }
    
    @Test
    fun `shouldTrackAssetLocation returns true for null location`() {
        // When/Then
        assertTrue(configManager.shouldTrackAssetLocation(null))
    }
    
    @Test
    fun `shouldTrackAssetLocation returns false for excluded pattern`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = true,
            maxBatchSize = 10,
            flushInterval = 2.0,
            maxWaitTime = 5.0,
            excludedUrlPatterns = listOf(".*internal.*"),
            excludedLocations = emptyList()
        )
        configManager.updateConfiguration(config)
        
        // When/Then
        assertFalse(configManager.shouldTrackAssetLocation("https://cdn.example.com/internal/image.jpg"))
    }
    
    // MARK: - Reset Tests
    
    @Test
    fun `reset clears configuration`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = true,
            maxBatchSize = 50,
            flushInterval = 10.0,
            maxWaitTime = 20.0,
            excludedUrlPatterns = emptyList(),
            excludedLocations = emptyList()
        )
        configManager.updateConfiguration(config)
        assertNotNull(configManager.getCurrentConfiguration())
        
        // When
        configManager.reset()
        
        // Then
        assertNull(configManager.getCurrentConfiguration())
        assertFalse(configManager.batchingEnabled)
    }
    
    // MARK: - Thread Safety Tests
    
    @Test
    fun `concurrent access is thread safe`() {
        // Given
        val latch = CountDownLatch(100)
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = true,
            maxBatchSize = 10,
            flushInterval = 2.0,
            maxWaitTime = 5.0,
            excludedUrlPatterns = emptyList(),
            excludedLocations = emptyList()
        )
        
        // When - concurrent reads and writes
        repeat(100) { index ->
            Thread {
                if (index % 2 == 0) {
                    configManager.updateConfiguration(config)
                } else {
                    configManager.getCurrentConfiguration()
                    configManager.batchingEnabled
                }
                latch.countDown()
            }.start()
        }
        
        // Then - all operations complete without crashes
        assertTrue(latch.await(5, TimeUnit.SECONDS))
    }
}
