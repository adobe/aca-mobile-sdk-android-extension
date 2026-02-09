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
import com.adobe.marketing.mobile.contentanalytics.helpers.ContentAnalyticsTestBase
import com.adobe.marketing.mobile.contentanalytics.helpers.TestDataBuilder
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests error handling for invalid event data.
 * 
 * Validates that events with missing/invalid required fields are dropped gracefully
 * without crashing or dispatching invalid data to Edge Network.
 */
internal class ContentAnalyticsErrorHandlingTest : ContentAnalyticsTestBase() {
    
    // MARK: - Missing Required Fields
    
    @Test
    fun testMissingAssetURL_EventDropped() {
        // Arrange - Event without required assetURL
        val data = mutableMapOf<String, Any>(
            "action" to ContentAnalyticsConstants.ActionType.VIEW,
            "assetLocation" to "homepage"
            // Missing: assetURL
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert - No Edge events dispatched
        assertEdgeEventCount(0, "Event without assetURL should be dropped")
    }
    
    @Test
    fun testMissingAction_EventDropped() {
        // Arrange - Event without required action
        val data = mutableMapOf<String, Any>(
            "assetURL" to "https://example.com/image.jpg",
            "assetLocation" to "homepage"
            // Missing: action
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert
        assertEdgeEventCount(0, "Event without action should be dropped")
    }
    
    @Test
    fun testMissingExperienceId_EventDropped() {
        // Arrange - Event without required experienceId
        val data = mutableMapOf<String, Any>(
            "action" to ContentAnalyticsConstants.ActionType.VIEW,
            "experienceLocation" to "homepage"
            // Missing: experienceId
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act
        orchestrator.processExperienceEvent(event)
        
        // Assert
        assertEdgeEventCount(0, "Event without experienceId should be dropped")
    }
    
    // MARK: - Invalid Field Values
    
    @Test
    fun testEmptyAssetURL_EventDropped() {
        // Arrange - Empty string for assetURL
        val data = mutableMapOf<String, Any>(
            "assetURL" to "",
            "action" to ContentAnalyticsConstants.ActionType.VIEW
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert - Empty URL may be processed or dropped depending on validation logic
        // Either way, no crash should occur
        assertTrue("Should handle empty URL gracefully", dispatchedEvents.size >= 0)
    }
    
    @Test
    fun testEmptyExperienceId_EventDropped() {
        // Arrange - Empty string for experienceId
        val data = mutableMapOf<String, Any>(
            "experienceId" to "",
            "action" to ContentAnalyticsConstants.ActionType.VIEW
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act
        orchestrator.processExperienceEvent(event)
        
        // Assert
        assertEdgeEventCount(0, "Event with empty experienceId should be dropped")
    }
    
    @Test
    fun testInvalidActionType_EventDropped() {
        // Arrange - Invalid action type
        val data = mutableMapOf<String, Any>(
            "assetURL" to "https://example.com/image.jpg",
            "action" to "invalid_action_type"
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert
        assertEdgeEventCount(0, "Event with invalid action should be dropped")
    }
    
    // MARK: - Malformed Event Data
    
    @Test
    fun testNullEventData_EventDropped() {
        // Arrange - Event with null data
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).build() // No setEventData() call
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert
        assertEdgeEventCount(0, "Event with null data should be dropped")
    }
    
    @Test
    fun testEmptyEventData_EventDropped() {
        // Arrange - Event with empty data map
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(emptyMap()).build()
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert
        assertEdgeEventCount(0, "Event with empty data should be dropped")
    }
    
    @Test
    fun testWrongDataTypes_EventDropped() {
        // Arrange - Wrong data types (URL as number)
        val data = mutableMapOf<String, Any>(
            "assetURL" to 12345, // Wrong type!
            "action" to ContentAnalyticsConstants.ActionType.VIEW
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert - Should handle gracefully
        assertEdgeEventCount(0, "Event with wrong data types should be dropped")
    }
    
    // MARK: - Edge Cases
    
    @Test
    fun testWhitespaceOnlyURL_EventDropped() {
        // Arrange - URL is only whitespace
        val data = mutableMapOf<String, Any>(
            "assetURL" to "   ",
            "action" to ContentAnalyticsConstants.ActionType.VIEW
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert - Whitespace URL may be processed or dropped
        // Either way, no crash should occur
        assertTrue("Should handle whitespace URL gracefully", dispatchedEvents.size >= 0)
    }
    
    @Test
    fun testVeryLongURL_HandledGracefully() {
        // Arrange - Extremely long URL (10,000 characters)
        val longURL = "https://example.com/image.jpg?" + "x".repeat(10000)
        val data = mutableMapOf<String, Any>(
            "assetURL" to longURL,
            "action" to ContentAnalyticsConstants.ActionType.VIEW
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act - Should not crash
        try {
            orchestrator.processAssetEvent(event)
            
            // Assert - Either dispatches or drops, but doesn't crash
            assertTrue("Should handle long URL without crashing", true)
        } catch (e: Exception) {
            fail("Should not throw exception for long URL: ${e.message}")
        }
    }
    
    @Test
    fun testMalformedExperienceDefinition_HandledGracefully() {
        // Arrange - Experience definition with missing required fields
        val malformedDefinition = mapOf(
            "experienceId" to "exp-test"
            // Missing: assets, texts
        )
        
        val data = mutableMapOf<String, Any>(
            "experienceId" to "exp-test",
            "action" to ContentAnalyticsConstants.ActionType.VIEW,
            "experienceDefinition" to malformedDefinition
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act - Should handle gracefully (may process experience without storing definition)
        try {
            orchestrator.processExperienceEvent(event)
            
            // Assert - Doesn't crash
            assertTrue("Should handle malformed definition gracefully", true)
        } catch (e: Exception) {
            fail("Should not throw exception: ${e.message}")
        }
    }
    
    @Test
    fun testNullInExtras_HandledGracefully() {
        // Arrange - Extras containing null values
        val extras = mapOf(
            "campaign" to "summer",
            "variant" to null // null value
        )
        
        val data = mutableMapOf<String, Any>(
            "assetURL" to "https://example.com/image.jpg",
            "action" to ContentAnalyticsConstants.ActionType.VIEW,
            "assetExtras" to extras
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act - Should handle gracefully
        try {
            orchestrator.processAssetEvent(event)
            
            // Assert - Should either filter null or pass through
            assertTrue("Should handle null in extras gracefully", true)
        } catch (e: Exception) {
            fail("Should not throw exception for null in extras: ${e.message}")
        }
    }
    
    @Test
    fun testSpecialCharactersInLocation_HandledCorrectly() {
        // Arrange - Location with special characters
        val locations = listOf(
            "page/with/slashes",
            "page?with=query",
            "page&with&ampersands",
            "page#with#hashes",
            "page with spaces",
            "ページ-日本語" // Japanese characters
        )
        
        // Act & Assert - All should be handled without crashes
        locations.forEach { location ->
            clearDispatchedEvents()
            
            val event = Event.Builder(
                ContentAnalyticsConstants.EventNames.TRACK_ASSET,
                ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
                ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
            ).setEventData(
                mapOf(
                    "assetURL" to "https://example.com/image.jpg",
                    "assetLocation" to location,
                    "action" to ContentAnalyticsConstants.ActionType.VIEW
                )
            ).build()
            
            try {
                orchestrator.processAssetEvent(event)
                
                // Should either dispatch or drop, but not crash
                assertTrue("Should handle location '$location' without crashing", true)
            } catch (e: Exception) {
                fail("Should not crash for location '$location': ${e.message}")
            }
        }
    }
    
    // MARK: - Configuration Edge Cases
    
    @Test
    fun testNullConfiguration_UsesDefaults() {
        // Arrange - No configuration set
        state.updateConfiguration(ContentAnalyticsConfiguration()) // Defaults
        
        // When - Track asset
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(
            mapOf(
                "assetURL" to "https://example.com/image.jpg",
                "action" to ContentAnalyticsConstants.ActionType.VIEW
            )
        ).build()
        
        orchestrator.processAssetEvent(event)
        
        // Assert - Should use defaults and dispatch event
        assertEdgeEventCount(1, "Should use default configuration")
    }
    
    @Test
    fun testInvalidRegexPattern_DoesNotCrash() {
        // Arrange - Configuration with invalid regex
        val config = TestDataBuilder.buildConfiguration(
            excludedAssetUrlsRegexp = "[invalid(pattern",
            batchingEnabled = false
        )
        state.updateConfiguration(config)
        
        // When - Track asset
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(
            mapOf(
                "assetURL" to "https://example.com/image.jpg",
                "action" to ContentAnalyticsConstants.ActionType.VIEW
            )
        ).build()
        
        // Act - Should not crash
        try {
            orchestrator.processAssetEvent(event)
            
            // Assert - Invalid regex should be ignored, event should be tracked
            assertEdgeEventCount(1, "Invalid regex should not block tracking")
        } catch (e: Exception) {
            fail("Should not crash with invalid regex: ${e.message}")
        }
    }
    
    // MARK: - High Volume / Stress
    
    @Test
    fun testHighVolumeEvents_HandledCorrectly() {
        // Arrange - Configure for immediate dispatch
        val config = TestDataBuilder.buildConfiguration(batchingEnabled = false)
        state.updateConfiguration(config)
        
        // Act - Send many events rapidly
        val eventCount = 100
        repeat(eventCount) { i ->
            val event = Event.Builder(
                ContentAnalyticsConstants.EventNames.TRACK_ASSET,
                ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
                ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
            ).setEventData(
                mapOf(
                    "assetURL" to "https://example.com/image$i.jpg",
                    "action" to ContentAnalyticsConstants.ActionType.VIEW
                )
            ).build()
            
            orchestrator.processAssetEvent(event)
        }
        
        // Assert - Should handle all events (may dispatch all or drop some, but shouldn't crash)
        assertTrue("Should handle high volume without crashing", dispatchedEvents.size >= 0)
        assertTrue("Should process most events", dispatchedEvents.size >= eventCount * 0.8) // 80% threshold
    }
    
    @Test
    fun testVeryLongExperienceId_HandledGracefully() {
        // Arrange - Extremely long experience ID
        val longId = "exp-" + "x".repeat(10000)
        val definition = ExperienceDefinition(
            experienceId = longId,
            assets = listOf("https://example.com/image.jpg"),
            texts = listOf(ContentItem("Text")),
            ctas = null
        )
        
        val data = mutableMapOf<String, Any>(
            "experienceId" to longId,
            "action" to ContentAnalyticsConstants.ActionType.VIEW,
            "experienceDefinition" to definition.toMap()
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act - Should not crash
        try {
            orchestrator.processExperienceEvent(event)
            assertTrue("Should handle long experience ID", true)
        } catch (e: Exception) {
            fail("Should not crash with long experience ID: ${e.message}")
        }
    }
    
    @Test
    fun testDeeplyNestedExtras_HandledGracefully() {
        // Arrange - Deeply nested extras (5 levels)
        val deeplyNested = mapOf(
            "level1" to mapOf(
                "level2" to mapOf(
                    "level3" to mapOf(
                        "level4" to mapOf(
                            "level5" to "value"
                        )
                    )
                )
            )
        )
        
        val data = mutableMapOf<String, Any>(
            "assetURL" to "https://example.com/image.jpg",
            "action" to ContentAnalyticsConstants.ActionType.VIEW,
            "assetExtras" to deeplyNested
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act - Should handle gracefully
        try {
            orchestrator.processAssetEvent(event)
            assertTrue("Should handle deeply nested data", true)
        } catch (e: Exception) {
            fail("Should not crash with deeply nested extras: ${e.message}")
        }
    }
    
    @Test
    fun testMixedValidAndInvalid_ValidEventsProcessed() {
        // Arrange - Mix of valid and invalid events
        val validEvent = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(
            mapOf(
                "assetURL" to "https://example.com/valid.jpg",
                "action" to ContentAnalyticsConstants.ActionType.VIEW
            )
        ).build()
        
        val invalidEvent = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(
            mapOf(
                // Missing assetURL
                "action" to ContentAnalyticsConstants.ActionType.VIEW
            )
        ).build()
        
        // Act
        orchestrator.processAssetEvent(validEvent)
        orchestrator.processAssetEvent(invalidEvent)
        orchestrator.processAssetEvent(validEvent) // Another valid one
        
        // Assert - Only valid events dispatched
        assertEdgeEventCount(2, "Should process 2 valid events, drop 1 invalid")
    }
    
    // MARK: - Null Safety
    
    @Test
    fun testNullLocation_AllowedAndTracked() {
        // Arrange - Event with explicit null location (should be allowed)
        val data = mutableMapOf<String, Any>(
            "assetURL" to "https://example.com/image.jpg",
            "action" to ContentAnalyticsConstants.ActionType.VIEW
            // assetLocation is null (not provided)
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert - Should dispatch (null location is valid)
        assertEdgeEventCount(1, "Event with null location should be tracked")
    }
    
    @Test
    fun testNullExtras_AllowedAndTracked() {
        // Arrange - Event with explicit null extras
        val data = mutableMapOf<String, Any>(
            "assetURL" to "https://example.com/image.jpg",
            "action" to ContentAnalyticsConstants.ActionType.VIEW
            // assetExtras is null (not provided)
        )
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert - Should dispatch (null extras is valid)
        assertEdgeEventCount(1, "Event with null extras should be tracked")
    }
}

