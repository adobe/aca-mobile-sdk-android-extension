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

import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.contentanalytics.helpers.TestDataBuilder
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Comprehensive tests for ContentAnalytics Public API.
 * 
 * Tests verify that public API methods:
 * - Execute without crashing
 * - Dispatch correct events to MobileCore
 * - Generate correct event data structures
 * - Handle edge cases (null, empty, special characters)
 * - Support all parameter combinations
 */
class ContentAnalyticsAPITest {
    
    private val capturedEvents = mutableListOf<Event>()
    
    @Before
    fun setup() {
        capturedEvents.clear()
        
        // Mock MobileCore.dispatchEvent to capture events
        mockkStatic(MobileCore::class)
        every { MobileCore.dispatchEvent(any()) } answers {
            capturedEvents.add(firstArg())
        }
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    // MARK: - trackAssetView() Tests
    
    @Test
    fun testTrackAssetView_WithAllParameters_DispatchesEvent() {
        // Arrange
        val url = "https://example.com/image.jpg"
        val location = "homepage"
        val extras = mapOf("campaign" to "summer-sale", "variant" to "A")
        
        // Act
        ContentAnalytics.trackAssetView(url, location, extras)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        
        val event = capturedEvents[0]
        assertEquals(ContentAnalyticsConstants.EventNames.TRACK_ASSET, event.name)
        assertEquals(ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS, event.type)
        assertEquals(ContentAnalyticsConstants.EventSource.REQUEST_CONTENT, event.source)
        
        val eventData = event.eventData
        assertEquals(url, eventData?.get("assetURL"))
        assertEquals(location, eventData?.get("assetLocation"))
        assertEquals(ContentAnalyticsConstants.ActionType.VIEW, eventData?.get("action"))
        assertEquals(extras, eventData?.get("assetExtras"))
    }
    
    @Test
    fun testTrackAssetView_WithMinimalParameters_DispatchesEvent() {
        // Arrange
        val url = "https://example.com/image.jpg"
        
        // Act
        ContentAnalytics.trackAssetView(url)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        
        val event = capturedEvents[0]
        val eventData = event.eventData
        assertEquals(url, eventData?.get("assetURL"))
        assertNull(eventData?.get("assetLocation"))
        assertNull(eventData?.get("assetExtras"))
    }
    
    @Test
    fun testTrackAssetView_WithSpecialCharactersInURL_DispatchesEvent() {
        // Arrange
        val url = "https://example.com/image with spaces.jpg?param=value&other=123"
        
        // Act
        ContentAnalytics.trackAssetView(url)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        val eventData = capturedEvents[0].eventData
        assertEquals(url, eventData?.get("assetURL"))
    }
    
    // MARK: - trackAssetClick() Tests
    
    @Test
    fun testTrackAssetClick_WithAllParameters_DispatchesEvent() {
        // Arrange
        val url = "https://example.com/cta.jpg"
        val location = "product-detail"
        val extras = mapOf("product_id" to "123")
        
        // Act
        ContentAnalytics.trackAssetClick(url, location, extras)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        
        val event = capturedEvents[0]
        assertEquals(ContentAnalyticsConstants.EventNames.TRACK_ASSET, event.name)
        
        val eventData = event.eventData
        assertEquals(url, eventData?.get("assetURL"))
        assertEquals(location, eventData?.get("assetLocation"))
        assertEquals(ContentAnalyticsConstants.ActionType.CLICK, eventData?.get("action"))
        assertEquals(extras, eventData?.get("assetExtras"))
    }
    
    @Test
    fun testTrackAssetClick_WithMinimalParameters_DispatchesEvent() {
        // Arrange
        val url = "https://example.com/button.jpg"
        
        // Act
        ContentAnalytics.trackAssetClick(url)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        val eventData = capturedEvents[0].eventData
        assertEquals(url, eventData?.get("assetURL"))
        assertEquals(ContentAnalyticsConstants.ActionType.CLICK, eventData?.get("action"))
    }
    
    // MARK: - registerExperience() Tests
    
    @Test
    fun testRegisterExperience_WithAllParameters_DispatchesEvent() {
        // Arrange
        val assets = listOf(
            ContentItem("https://example.com/hero1.jpg"),
            ContentItem("https://example.com/hero2.jpg")
        )
        val texts = TestDataBuilder.buildContentItems(
            count = 2,
            prefix = "headline",
            contentType = "text"
        )
        val ctas = TestDataBuilder.buildContentItems(
            count = 1,
            prefix = "button",
            contentType = "cta"
        )
        
        // Act - returns generated experienceId
        val experienceId = ContentAnalytics.registerExperience(assets, texts, ctas)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        
        // Verify experienceId format
        assertTrue(experienceId.startsWith("mobile-"))
        assertEquals(19, experienceId.length) // "mobile-" (7) + 12 hex chars
        
        val event = capturedEvents[0]
        assertEquals(ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE, event.name)
        
        val eventData = event.eventData
        assertEquals(experienceId, eventData?.get(ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ID))
        
        // Verify assets (stored as URLs, not full ContentItem objects)
        val assetURLs = eventData?.get(ContentAnalyticsConstants.EventDataKeys.ASSETS) as? List<String>
        assertNotNull(assetURLs)
        assertEquals(assets.map { it.value }, assetURLs)
    }
    
    @Test
    fun testRegisterExperience_WithMinimalParameters_DispatchesEvent() {
        // Arrange
        val assets = listOf(ContentItem("https://example.com/image.jpg"))
        val texts = listOf(TestDataBuilder.buildContentItem("Test Text"))
        
        // Act - returns generated experienceId
        val experienceId = ContentAnalytics.registerExperience(assets, texts, null)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        
        // Verify experienceId format
        assertTrue(experienceId.startsWith("mobile-"))
        assertEquals(19, experienceId.length)
        
        val eventData = capturedEvents[0].eventData
        assertEquals(experienceId, eventData?.get(ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ID))
    }
    
    
    // MARK: - trackExperienceView() Tests
    
    @Test
    fun testTrackExperienceView_WithAllParameters_DispatchesEvent() {
        // Arrange
        val experienceId = "exp-test"
        val location = "homepage-hero"
        val extras = mapOf("test_variant" to "B")
        
        // Act
        ContentAnalytics.trackExperienceView(experienceId, location, extras)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        
        val event = capturedEvents[0]
        assertEquals(ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE, event.name)
        
        val eventData = event.eventData
        assertEquals(experienceId, eventData?.get("experienceId"))
        assertEquals(location, eventData?.get("experienceLocation"))
        assertEquals(ContentAnalyticsConstants.ActionType.VIEW, eventData?.get("action"))
        assertEquals(extras, eventData?.get("experienceExtras"))
    }
    
    @Test
    fun testTrackExperienceView_WithMinimalParameters_DispatchesEvent() {
        // Arrange
        val experienceId = "exp-minimal"
        
        // Act
        ContentAnalytics.trackExperienceView(experienceId)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        val eventData = capturedEvents[0].eventData
        assertEquals(experienceId, eventData?.get("experienceId"))
        assertNull(eventData?.get("experienceLocation"))
    }
    
    // MARK: - trackExperienceClick() Tests
    
    @Test
    fun testTrackExperienceClick_WithAllParameters_DispatchesEvent() {
        // Arrange
        val experienceId = "exp-clickable"
        val location = "product-carousel"
        val extras = mapOf("position" to 2)
        
        // Act
        ContentAnalytics.trackExperienceClick(experienceId, location, extras)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        
        val event = capturedEvents[0]
        assertEquals(ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE, event.name)
        
        val eventData = event.eventData
        assertEquals(experienceId, eventData?.get("experienceId"))
        assertEquals(location, eventData?.get("experienceLocation"))
        assertEquals(ContentAnalyticsConstants.ActionType.CLICK, eventData?.get("action"))
        assertEquals(extras, eventData?.get("experienceExtras"))
    }
    
    @Test
    fun testTrackExperienceClick_WithMinimalParameters_DispatchesEvent() {
        // Arrange
        val experienceId = "exp-click-minimal"
        
        // Act
        ContentAnalytics.trackExperienceClick(experienceId)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        val eventData = capturedEvents[0].eventData
        assertEquals(experienceId, eventData?.get("experienceId"))
        assertEquals(ContentAnalyticsConstants.ActionType.CLICK, eventData?.get("action"))
    }
    
    // MARK: - Edge Cases and Special Scenarios
    
    @Test
    fun testTrackAssetView_WithComplexExtras_PreservesDataTypes() {
        // Arrange
        val url = "https://example.com/image.jpg"
        val extras = mapOf(
            "string" to "value",
            "number" to 42,
            "decimal" to 3.14,
            "boolean" to true,
            "nested" to mapOf("key" to "value")
        )
        
        // Act
        ContentAnalytics.trackAssetView(url, null, extras)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        
        val actualExtras = capturedEvents[0].eventData?.get("assetExtras") as? Map<String, Any>
        assertEquals("value", actualExtras?.get("string"))
        assertEquals(42, actualExtras?.get("number"))
        assertEquals(3.14, actualExtras?.get("decimal"))
        assertEquals(true, actualExtras?.get("boolean"))
        assertTrue(actualExtras?.get("nested") is Map<*, *>)
    }
    
    @Test
    fun testTrackAssetView_WithVeryLongURL_DispatchesEvent() {
        // Arrange - Simulate a very long URL with query parameters
        val baseURL = "https://example.com/image.jpg"
        val queryParams = (1..100).joinToString("&") { "param$it=value$it" }
        val url = "$baseURL?$queryParams"
        
        // Act
        ContentAnalytics.trackAssetView(url)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        val eventData = capturedEvents[0].eventData
        assertEquals(url, eventData?.get("assetURL"))
    }
    
    @Test
    fun testRegisterExperience_WithManyAssets_DispatchesEvent() {
        // Arrange
        val assets = (1..50).map { ContentItem("https://example.com/asset$it.jpg") }
        val texts = listOf(TestDataBuilder.buildContentItem("Text"))
        
        // Act - returns generated experienceId
        val experienceId = ContentAnalytics.registerExperience(assets, texts)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        
        // Verify experienceId format
        assertTrue(experienceId.startsWith("mobile-"))
        
        // Verify assets count
        val assetURLs = capturedEvents[0].eventData?.get(ContentAnalyticsConstants.EventDataKeys.ASSETS) as? List<String>
        assertEquals(50, assetURLs?.size)
    }
    
    @Test
    fun testTrackExperienceView_WithUnicodeCharactersInLocation_DispatchesEvent() {
        // Arrange
        val experienceId = "exp-unicode"
        val location = "首页-英雄区" // Chinese characters
        
        // Act
        ContentAnalytics.trackExperienceView(experienceId, location)
        
        // Assert
        assertEquals(1, capturedEvents.size)
        val eventData = capturedEvents[0].eventData
        assertEquals(location, eventData?.get("experienceLocation"))
    }
    
}

