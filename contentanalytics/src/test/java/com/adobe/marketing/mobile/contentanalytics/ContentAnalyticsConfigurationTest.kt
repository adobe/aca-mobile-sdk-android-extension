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

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for ContentAnalyticsConfiguration
 */
class ContentAnalyticsConfigurationTest {
    
    @Test
    fun testDefaultConfiguration_HasCorrectDefaults() {
        // Act
        val config = ContentAnalyticsConfiguration()
        
        // Assert
        assertTrue("Track experiences should be true by default", config.trackExperiences)
        assertTrue("Batching should be enabled by default", config.batchingEnabled)
        assertEquals(ContentAnalyticsConstants.Defaults.MAX_BATCH_SIZE, config.maxBatchSize)
        assertEquals(ContentAnalyticsConstants.Defaults.BATCH_FLUSH_INTERVAL, config.batchFlushInterval)
        assertNull("Excluded asset locations regexp should be null", config.excludedAssetLocationsRegexp)
        assertNull("Excluded asset URLs regexp should be null", config.excludedAssetUrlsRegexp)
        assertNull("Excluded experience locations regexp should be null", config.excludedExperienceLocationsRegexp)
    }
    
    @Test
    fun testShouldExcludeUrl_WithValidRegex_ExcludesMatching() {
        // Arrange
        val config = ContentAnalyticsConfiguration(
            excludedAssetUrlsRegexp = ".*\\.gif$"
        )
        
        // Act & Assert
        assertTrue("Should exclude .gif files", config.shouldExcludeUrl("https://example.com/image.gif"))
        assertFalse("Should not exclude .jpg files", config.shouldExcludeUrl("https://example.com/image.jpg"))
    }
    
    @Test
    fun testShouldExcludeUrl_WithNullRegex_ExcludesNothing() {
        // Arrange
        val config = ContentAnalyticsConfiguration(
            excludedAssetUrlsRegexp = null
        )
        
        // Act & Assert
        assertFalse(config.shouldExcludeUrl("https://example.com/image.gif"))
        assertFalse(config.shouldExcludeUrl("https://example.com/image.jpg"))
    }
    
    @Test
    fun testShouldExcludeUrl_WithInvalidRegex_ExcludesNothing() {
        // Arrange
        val config = ContentAnalyticsConfiguration(
            excludedAssetUrlsRegexp = "[invalid(pattern"
        )
        
        // Act & Assert - Invalid regex should be ignored gracefully
        assertFalse("Invalid regex should not exclude anything", 
            config.shouldExcludeUrl("https://example.com/image.jpg"))
    }
    
    @Test
    fun testShouldExcludeAsset_RegexMatch_Works() {
        // Arrange
        val config = ContentAnalyticsConfiguration(
            excludedAssetLocationsRegexp = ".*(test|debug).*"
        )
        
        // Act & Assert
        assertTrue("Should exclude test-page", config.shouldExcludeAsset("test-page"))
        assertTrue("Should exclude debug", config.shouldExcludeAsset("debug"))
        assertTrue("Should exclude my-test-location", config.shouldExcludeAsset("my-test-location"))
        assertFalse("Should not exclude homepage", config.shouldExcludeAsset("homepage"))
    }
    
    @Test
    fun testShouldExcludeAsset_NullLocation_ReturnsFalse() {
        // Arrange
        val config = ContentAnalyticsConfiguration(
            excludedAssetLocationsRegexp = ".*test.*"
        )
        
        // Act & Assert
        assertFalse("Null location should not be excluded", config.shouldExcludeAsset(null))
    }
    
    @Test
    fun testShouldExcludeExperience_RegexMatch_Works() {
        // Arrange
        val config = ContentAnalyticsConfiguration(
            excludedExperienceLocationsRegexp = ".*test.*"
        )
        
        // Act & Assert
        assertTrue("Should exclude test-page", config.shouldExcludeExperience("test-page"))
        assertTrue("Should exclude testing-area", config.shouldExcludeExperience("testing-area"))
        assertFalse("Should not exclude homepage", config.shouldExcludeExperience("homepage"))
    }
    
    @Test
    fun testShouldExcludeExperience_NullLocation_ReturnsFalse() {
        // Arrange
        val config = ContentAnalyticsConfiguration(
            excludedExperienceLocationsRegexp = ".*test.*"
        )
        
        // Act & Assert
        assertFalse("Null location should not be excluded", config.shouldExcludeExperience(null))
    }
    
    @Test
    fun testBatchingConfiguration_SetCorrectly() {
        // Arrange
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            maxBatchSize = 10,
            batchFlushInterval = 30000
        )
        
        // Assert
        assertFalse(config.batchingEnabled)
        assertEquals(10, config.maxBatchSize)
        assertEquals(30000, config.batchFlushInterval)
    }
}

