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

import com.adobe.marketing.mobile.contentanalytics.helpers.ContentAnalyticsTestBase
import com.adobe.marketing.mobile.contentanalytics.helpers.TestDataBuilder
import com.adobe.marketing.mobile.contentanalytics.helpers.TestEventFactory
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for URL and location exclusion/filtering patterns.
 * 
 * Tests three types of exclusion:
 * 1. Asset URL regex matching (`excludedAssetUrlsRegexp`)
 * 2. Asset location regex matching (`excludedAssetLocationsRegexp`)
 * 3. Experience location regex matching (`excludedExperienceLocationsRegexp`)
 * 
 * This is critical for allowing users to filter out unwanted tracking (e.g., test assets, internal tools).
 */
internal class ContentAnalyticsExclusionTest : ContentAnalyticsTestBase() {
    
    // MARK: - Asset URL Regex Exclusion Tests
    
    @Test
    fun testExcludeUrl_ExactMatch_Excludes() {
        // Arrange - Exact URL pattern
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = "https://example\\.com/test\\.jpg"
        )
        
        // Act
        val shouldExclude = config.shouldExcludeUrl("https://example.com/test.jpg")
        
        // Assert
        assertTrue("Exact URL match should be excluded", shouldExclude)
    }
    
    @Test
    fun testExcludeUrl_ExactMatch_DoesNotExcludeOther() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = "https://example\\.com/test\\.jpg"
        )
        
        // Act
        val shouldExclude = config.shouldExcludeUrl("https://example.com/other.jpg")
        
        // Assert
        assertFalse("Different URL should not be excluded", shouldExclude)
    }
    
    @Test
    fun testExcludeUrl_WildcardPattern_ExcludesMatching() {
        // Arrange - Pattern matches any URL containing "test"
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = ".*test.*"
        )
        
        // Act & Assert - All test URLs should be excluded
        val testUrls = listOf(
            "https://images.test.example.com/hero.jpg",
            "https://cdn.test.com/banner.png",
            "https://test.example.com/image.jpg",
            "https://example.com/test/image.jpg"
        )
        
        testUrls.forEach { url ->
            assertTrue("$url should be excluded", config.shouldExcludeUrl(url))
        }
    }
    
    @Test
    fun testExcludeUrl_WildcardPattern_DoesNotExcludeNonMatching() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = ".*test.*"
        )
        
        // Act & Assert - Production URLs should NOT be excluded
        val productionUrls = listOf(
            "https://example.com/hero.jpg",
            "https://cdn.example.com/banner.png",
            "https://images.example.com/product.jpg"
        )
        
        productionUrls.forEach { url ->
            assertFalse("$url should NOT be excluded", config.shouldExcludeUrl(url))
        }
    }
    
    @Test
    fun testExcludeUrl_ComplexPattern_HandlesCorrectly() {
        // Arrange - Pattern: starts with specific domain OR ends with .test.jpg
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = "(https://test\\.example\\.com/.*|\\.test\\.jpg$)"
        )
        
        // Act & Assert
        assertTrue("Should exclude matching domain", config.shouldExcludeUrl("https://test.example.com/image.jpg"))
        assertTrue("Should exclude matching suffix", config.shouldExcludeUrl("https://cdn.example.com/hero.test.jpg"))
        assertFalse("Should not exclude non-matching", config.shouldExcludeUrl("https://example.com/image.jpg"))
    }
    
    @Test
    fun testExcludeUrl_EmptyPattern_ExcludesNothing() {
        // Arrange - Empty pattern
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = ""
        )
        
        // Act & Assert - Nothing should be excluded
        assertFalse(config.shouldExcludeUrl("https://example.com/image.jpg"))
        assertFalse(config.shouldExcludeUrl("https://test.example.com/image.jpg"))
    }
    
    @Test
    fun testExcludeUrl_NullPattern_ExcludesNothing() {
        // Arrange - No pattern configured
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = null
        )
        
        // Act & Assert - Nothing should be excluded
        assertFalse(config.shouldExcludeUrl("https://example.com/image.jpg"))
        assertFalse(config.shouldExcludeUrl("https://test.example.com/image.jpg"))
    }
    
    @Test
    fun testExcludeUrl_InvalidPattern_ExcludesNothing() {
        // Arrange - Invalid regex pattern (unclosed bracket)
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = "[invalid(pattern"
        )
        
        // Act & Assert - Invalid pattern should be ignored, nothing excluded
        assertFalse("Invalid pattern should not exclude anything", config.shouldExcludeUrl("https://example.com/image.jpg"))
    }
    
    @Test
    fun testExcludeUrl_CaseInsensitive_MatchesCorrectly() {
        // Arrange - All patterns are case-insensitive by default
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = ".*test.*"
        )
        
        // Act & Assert - Should match all cases
        assertTrue("Should match uppercase TEST", config.shouldExcludeUrl("https://example.com/TEST/image.jpg"))
        assertTrue("Should match lowercase test", config.shouldExcludeUrl("https://example.com/test/image.jpg"))
        assertTrue("Should match mixed case Test", config.shouldExcludeUrl("https://example.com/Test/image.jpg"))
    }
    
    @Test
    fun testExcludeUrl_PatternMatching_WorksCorrectly() {
        // Arrange - Pattern with wildcards
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = ".*\\.gif$"
        )
        
        // Act & Assert
        assertTrue("Should match .gif files", config.shouldExcludeUrl("https://example.com/image.gif"))
        assertTrue("Should match .GIF files (case-insensitive)", config.shouldExcludeUrl("https://example.com/image.GIF"))
        assertFalse("Should NOT match .jpg files", config.shouldExcludeUrl("https://example.com/image.jpg"))
    }
    
    // MARK: - Asset Location Exact Match Exclusion Tests
    
    @Test
    fun testExcludeAssetLocation_RegexMatch_Excludes() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetLocationsRegexp = "^(test-page|internal-tool)$"
        )
        
        // Act & Assert
        assertTrue("test-page should be excluded", config.shouldExcludeAsset("test-page"))
        assertTrue("internal-tool should be excluded", config.shouldExcludeAsset("internal-tool"))
    }
    
    @Test
    fun testExcludeAssetLocation_RegexMatch_DoesNotExcludeOther() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetLocationsRegexp = "^test-page$"
        )
        
        // Act & Assert
        assertFalse("homepage should NOT be excluded", config.shouldExcludeAsset("homepage"))
        assertFalse("product-page should NOT be excluded", config.shouldExcludeAsset("product-page"))
    }
    
    @Test
    fun testExcludeAssetLocation_NullLocation_DoesNotExclude() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetLocationsRegexp = "^test-page$"
        )
        
        // Act & Assert
        assertFalse("null location should NOT be excluded", config.shouldExcludeAsset(null))
    }
    
    @Test
    fun testExcludeAssetLocation_NullRegexp_ExcludesNothing() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetLocationsRegexp = null
        )
        
        // Act & Assert
        assertFalse(config.shouldExcludeAsset("test-page"))
        assertFalse(config.shouldExcludeAsset("homepage"))
    }
    
    @Test
    fun testExcludeAssetLocation_CaseInsensitiveRegex() {
        // Arrange - Regex is case-insensitive by default (Pattern.CASE_INSENSITIVE is used in compileRegex)
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetLocationsRegexp = "^Test-Page$"
        )
        
        // Act & Assert - All cases should match due to case-insensitive regex
        assertTrue("Exact case should be excluded", config.shouldExcludeAsset("Test-Page"))
        assertTrue("Lowercase should be excluded", config.shouldExcludeAsset("test-page"))
        assertTrue("Uppercase should be excluded", config.shouldExcludeAsset("TEST-PAGE"))
    }
    
    @Test
    fun testExcludeAssetLocation_SpecialCharacters() {
        // Arrange - Locations with special characters (properly escaped in regex)
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetLocationsRegexp = "^(test/page|internal-tool_v2|page#123)$"
        )
        
        // Act & Assert
        assertTrue(config.shouldExcludeAsset("test/page"))
        assertTrue(config.shouldExcludeAsset("internal-tool_v2"))
        assertTrue(config.shouldExcludeAsset("page#123"))
    }
    
    // MARK: - Experience Location Regex Exclusion Tests
    
    @Test
    fun testExcludeExperienceLocation_RegexMatch_Excludes() {
        // Arrange - Regex pattern
        val config = TestDataBuilder.buildConfiguration(
            excludedExperienceLocationsRegexp = ".*test.*"
        )
        
        // Act & Assert
        assertTrue(config.shouldExcludeExperience("test-page"))
        assertTrue(config.shouldExcludeExperience("homepage-test"))
        assertTrue(config.shouldExcludeExperience("testing-area"))
    }
    
    @Test
    fun testExcludeExperienceLocation_RegexMatch_DoesNotExcludeNonMatching() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedExperienceLocationsRegexp = ".*test.*"
        )
        
        // Act & Assert
        assertFalse(config.shouldExcludeExperience("homepage"))
        assertFalse(config.shouldExcludeExperience("product-page"))
    }
    
    @Test
    fun testExcludeExperienceLocation_NullLocation_DoesNotExclude() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedExperienceLocationsRegexp = ".*test.*"
        )
        
        // Act & Assert
        assertFalse(config.shouldExcludeExperience(null))
    }
    
    @Test
    fun testExcludeExperienceLocation_EmptyPattern_ExcludesNothing() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedExperienceLocationsRegexp = ""
        )
        
        // Act & Assert
        assertFalse(config.shouldExcludeExperience("test-page"))
        assertFalse(config.shouldExcludeExperience("homepage"))
    }
    
    @Test
    fun testExcludeExperienceLocation_InvalidPattern_ExcludesNothing() {
        // Arrange - Invalid regex
        val config = TestDataBuilder.buildConfiguration(
            excludedExperienceLocationsRegexp = "[invalid(pattern"
        )
        
        // Act & Assert - Invalid pattern should not exclude
        assertFalse(config.shouldExcludeExperience("test-page"))
    }
    
    // MARK: - Integration Tests with Orchestrator
    
    @Test
    fun testOrchestrator_AssetUrlExcluded_EventNotProcessed() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = ".*test.*",
            batchingEnabled = false
        )
        state.updateConfiguration(config)
        
        val event = TestEventFactory.createAssetEvent(
            url = "https://test.example.com/image.jpg",
            location = "homepage"
        )
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert - No Edge events should be dispatched
        assertEdgeEventCount(0, "Excluded URL should not dispatch events")
    }
    
    @Test
    fun testOrchestrator_AssetLocationExcluded_EventNotProcessed() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetLocationsRegexp = "^test-page$",
            batchingEnabled = false
        )
        state.updateConfiguration(config)
        
        val event = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "test-page"
        )
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert
        assertEdgeEventCount(0, "Excluded location should not dispatch events")
    }
    
    @Test
    fun testOrchestrator_AssetNotExcluded_EventProcessed() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = ".*test.*",
            excludedAssetLocationsRegexp = "^test-page$",
            batchingEnabled = false
        )
        state.updateConfiguration(config)
        
        val event = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "homepage"
        )
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert - Should dispatch Edge event
        assertEdgeEventCount(1, "Non-excluded asset should dispatch event")
    }
    
    @Test
    fun testOrchestrator_ExperienceLocationExcluded_EventNotProcessed() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedExperienceLocationsRegexp = ".*test.*",
            batchingEnabled = false
        )
        state.updateConfiguration(config)
        
        val event = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = "test-page"
        )
        
        // Act
        orchestrator.processExperienceEvent(event)
        
        // Assert
        assertEdgeEventCount(0, "Excluded experience location should not dispatch events")
    }
    
    @Test
    fun testOrchestrator_ExperienceNotExcluded_EventProcessed() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(
            excludedExperienceLocationsRegexp = ".*test.*",
            batchingEnabled = false
        )
        state.updateConfiguration(config)
        
        val event = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = "homepage"
        )
        
        // Act
        orchestrator.processExperienceEvent(event)
        
        // Assert
        assertEdgeEventCount(1, "Non-excluded experience should dispatch event")
    }
    
    @Test
    fun testOrchestrator_MultipleExclusions_AppliesCorrectly() {
        // Arrange - Configure multiple exclusions
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = ".*test.*",
            excludedAssetLocationsRegexp = "^internal$",
            excludedExperienceLocationsRegexp = ".*staging.*",
            batchingEnabled = false
        )
        state.updateConfiguration(config)
        
        // Act & Assert - Test URL excluded
        val event1 = TestEventFactory.createAssetEvent("https://test.example.com/image.jpg", "homepage")
        orchestrator.processAssetEvent(event1)
        assertEdgeEventCount(0)
        
        clearDispatchedEvents()
        
        // Act & Assert - Internal location excluded
        val event2 = TestEventFactory.createAssetEvent("https://example.com/image.jpg", "internal")
        orchestrator.processAssetEvent(event2)
        assertEdgeEventCount(0)
        
        clearDispatchedEvents()
        
        // Act & Assert - Staging experience excluded
        val event3 = TestEventFactory.createExperienceEvent("exp-123", "staging-area")
        orchestrator.processExperienceEvent(event3)
        assertEdgeEventCount(0)
        
        clearDispatchedEvents()
        
        // Act & Assert - Production asset allowed
        val event4 = TestEventFactory.createAssetEvent("https://example.com/image.jpg", "homepage")
        orchestrator.processAssetEvent(event4)
        assertEdgeEventCount(1, "Production asset should be tracked")
    }
}

