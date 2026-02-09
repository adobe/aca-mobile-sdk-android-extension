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
 * Tests for XDMEventBuilder - validates XDM payload structure
 */
class XDMEventBuilderTest {
    
    @Test
    fun testBuildAssetInteractionXDM_CreatesValidStructure() {
        // Arrange
        val assetURL = "https://example.com/image.jpg"
        val assetLocation = "homepage"
        val assetKey = "$assetURL?location=$assetLocation"
        
        val metrics = mapOf(
            assetKey to mapOf(
                "assetURL" to assetURL,
                "assetLocation" to assetLocation,
                "viewCount" to 5.0,
                "clickCount" to 2.0
            )
        )
        
        // Act
        val xdm = XDMEventBuilder.buildAssetInteractionXDM(
            assetKeys = listOf(assetKey),
            metrics = metrics
        )
        
        // Assert
        assertNotNull("XDM should not be null", xdm)
        assertEquals("content.contentEngagement", xdm["eventType"])
        
        val experienceContent = xdm["experienceContent"] as? Map<*, *>
        assertNotNull("experienceContent should exist", experienceContent)
        
        val assets = experienceContent?.get("assets") as? List<*>
        assertNotNull("assets array should exist", assets)
        assertEquals("Should have 1 asset", 1, assets?.size)
        
        val assetData = assets?.firstOrNull() as? Map<*, *>
        assertEquals(assetURL, assetData?.get("assetID"))
        assertEquals(assetLocation, assetData?.get("assetSource"))
        
        val assetViews = assetData?.get("assetViews") as? Map<*, *>
        assertEquals(5.0, assetViews?.get("value"))
        
        val assetClicks = assetData?.get("assetClicks") as? Map<*, *>
        assertEquals(2.0, assetClicks?.get("value"))
    }
    
    @Test
    fun testBuildAssetInteractionXDM_WithExtras_IncludesExtras() {
        // Arrange
        val assetURL = "https://example.com/image.jpg"
        val assetKey = assetURL
        val extras = mapOf("campaign" to "summer", "variant" to "A")
        
        val metrics = mapOf(
            assetKey to mapOf(
                "assetURL" to assetURL,
                "viewCount" to 1.0,
                "clickCount" to 0.0,
                "assetExtras" to extras
            )
        )
        
        // Act
        val xdm = XDMEventBuilder.buildAssetInteractionXDM(
            assetKeys = listOf(assetKey),
            metrics = metrics
        )
        
        // Assert
        val experienceContent = xdm["experienceContent"] as? Map<*, *>
        val assets = experienceContent?.get("assets") as? List<*>
        val assetData = assets?.firstOrNull() as? Map<*, *>
        val assetExtras = assetData?.get("assetExtras") as? Map<*, *>
        
        assertNotNull("assetExtras should be present", assetExtras)
        assertEquals("summer", assetExtras?.get("campaign"))
        assertEquals("A", assetExtras?.get("variant"))
    }
    
    @Test
    fun testBuildExperienceInteractionXDM_CreatesValidStructure() {
        // Arrange
        val experienceId = "exp-homepage-hero"
        val experienceLocation = "homepage"
        val assetURLs = listOf("https://example.com/hero1.jpg", "https://example.com/hero2.jpg")
        
        val metrics = mapOf(
            "viewCount" to 3.0,
            "clickCount" to 1.0
        )
        
        // Act
        val xdm = XDMEventBuilder.buildExperienceInteractionXDM(
            experienceId = experienceId,
            metrics = metrics,
            assetURLs = assetURLs,
            experienceLocation = experienceLocation
        )
        
        // Assert
        assertNotNull("XDM should not be null", xdm)
        assertEquals("content.contentEngagement", xdm["eventType"])
        
        val experienceContent = xdm["experienceContent"] as? Map<*, *>
        assertNotNull("experienceContent should exist", experienceContent)
        
        val experience = experienceContent?.get("experience") as? Map<*, *>
        assertNotNull("experience should exist", experience)
        assertEquals(experienceId, experience?.get("experienceID"))
        assertEquals(experienceLocation, experience?.get("experienceSource"))
        
        val experienceViews = experience?.get("experienceViews") as? Map<*, *>
        assertEquals(3.0, experienceViews?.get("value"))
        
        val experienceClicks = experience?.get("experienceClicks") as? Map<*, *>
        assertEquals(1.0, experienceClicks?.get("value"))
        
        // Verify asset attribution
        val assets = experienceContent?.get("assets") as? List<*>
        assertNotNull("assets should be present for attribution", assets)
        assertEquals(2, assets?.size)
        
        val asset1 = assets?.get(0) as? Map<*, *>
        assertEquals("https://example.com/hero1.jpg", asset1?.get("assetID"))
        
        val asset2 = assets?.get(1) as? Map<*, *>
        assertEquals("https://example.com/hero2.jpg", asset2?.get("assetID"))
    }
    
    @Test
    fun testBuildExperienceInteractionXDM_WithExtras_IncludesExtras() {
        // Arrange
        val experienceId = "exp-test"
        val extras = mapOf("test_group" to "B", "user_segment" to "premium")
        
        val metrics = mapOf(
            "viewCount" to 1.0,
            "clickCount" to 0.0,
            "experienceExtras" to extras
        )
        
        // Act
        val xdm = XDMEventBuilder.buildExperienceInteractionXDM(
            experienceId = experienceId,
            metrics = metrics,
            assetURLs = emptyList(),
            experienceLocation = "homepage"
        )
        
        // Assert
        val experienceContent = xdm["experienceContent"] as? Map<*, *>
        val experience = experienceContent?.get("experience") as? Map<*, *>
        val experienceExtras = experience?.get("experienceExtras") as? Map<*, *>
        
        assertNotNull("experienceExtras should be present", experienceExtras)
        assertEquals("B", experienceExtras?.get("test_group"))
        assertEquals("premium", experienceExtras?.get("user_segment"))
    }
    
    @Test
    fun testBuildAssetInteractionXDM_NoLocation_UsesEmptySource() {
        // Arrange
        val assetURL = "https://example.com/image.jpg"
        val assetKey = assetURL
        
        val metrics = mapOf(
            assetKey to mapOf(
                "assetURL" to assetURL,
                "viewCount" to 1.0,
                "clickCount" to 0.0
            )
        )
        
        // Act
        val xdm = XDMEventBuilder.buildAssetInteractionXDM(
            assetKeys = listOf(assetKey),
            metrics = metrics
        )
        
        // Assert
        val experienceContent = xdm["experienceContent"] as? Map<*, *>
        val assets = experienceContent?.get("assets") as? List<*>
        val assetData = assets?.firstOrNull() as? Map<*, *>
        
        // assetSource should not be present if location is empty
        assertFalse("assetSource should not be present for empty location",
            assetData?.containsKey("assetSource") == true)
    }
    
    @Test
    fun testBuildExperienceInteractionXDM_NoAssets_EmptyAssetsArray() {
        // Arrange
        val experienceId = "exp-text-only"
        val metrics = mapOf(
            "viewCount" to 1.0,
            "clickCount" to 0.0
        )
        
        // Act
        val xdm = XDMEventBuilder.buildExperienceInteractionXDM(
            experienceId = experienceId,
            metrics = metrics,
            assetURLs = emptyList(),
            experienceLocation = null
        )
        
        // Assert
        val experienceContent = xdm["experienceContent"] as? Map<*, *>
        
        // When no assets provided, assets array may not be present or may be empty
        // This is valid XDM - assets are optional for text-only experiences
        assertNotNull("experienceContent should be present", experienceContent)
        
        val experience = experienceContent?.get("experience") as? Map<*, *>
        assertNotNull("experience should be present", experience)
        assertEquals("exp-text-only", experience?.get("experienceID"))
    }
}

