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
import com.adobe.marketing.mobile.EventSource
import com.adobe.marketing.mobile.contentanalytics.helpers.TestEventFactory
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Event extensions: type detection, data accessors, key generation, and action helpers.
 */
class EventExtensionsTest {
    
    // MARK: - Event Type Detection Tests
    
    @Test
    fun `isAssetEvent with asset event returns true`() {
        // Given
        val event = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When/Then
        assertTrue("Should identify asset event correctly", event.isAssetEvent)
    }
    
    @Test
    fun `isAssetEvent with experience event returns false`() {
        // Given
        val event = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When/Then
        assertFalse("Should not identify experience event as asset event", event.isAssetEvent)
    }
    
    @Test
    fun `isExperienceEvent with experience event returns true`() {
        // Given
        val event = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When/Then
        assertTrue("Should identify experience event correctly", event.isExperienceEvent)
    }
    
    @Test
    fun `isExperienceEvent with asset event returns false`() {
        // Given
        val event = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When/Then
        assertFalse("Should not identify asset event as experience event", event.isExperienceEvent)
    }
    
    // MARK: - Asset Data Accessor Tests
    
    @Test
    fun `assetURL with valid event extracts correctly`() {
        // Given
        val url = "https://example.com/image.jpg"
        val event = TestEventFactory.createAssetEvent(
            url = url,
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When/Then
        assertEquals("Should extract asset URL correctly", url, event.assetURL)
    }
    
    @Test
    fun `assetURL with missing field returns null`() {
        // Given
        val event = Event.Builder(
            "Test",
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            EventSource.REQUEST_CONTENT
        ).setEventData(mapOf("other" to "data")).build()
        
        // When/Then
        assertNull("Should return null when assetURL is missing", event.assetURL)
    }
    
    @Test
    fun `assetLocation with valid event extracts correctly`() {
        // Given
        val location = "homepage"
        val event = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = location,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When/Then
        assertEquals("Should extract asset location correctly", location, event.assetLocation)
    }
    
    @Test
    fun `assetLocation with missing field returns null`() {
        // Given
        val event = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = null,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When/Then
        assertNull("Should return null when assetLocation is missing", event.assetLocation)
    }
    
    @Test
    fun `assetAction with valid event extracts correctly`() {
        // Given
        val action = ContentAnalyticsConstants.ActionType.VIEW
        val event = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "home",
            action = action
        )
        
        // When/Then
        assertEquals("Should extract asset action correctly", action, event.assetAction)
    }
    
    @Test
    fun `assetExtras with valid event extracts correctly`() {
        // Given
        val extras = mapOf("key1" to "value1", "key2" to 123)
        val event = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW,
            extras = extras
        )
        
        // When/Then
        assertEquals("Should extract asset extras correctly", extras, event.assetExtras)
    }
    
    @Test
    fun `assetExtras with missing field returns null`() {
        // Given
        val event = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW,
            extras = null
        )
        
        // When/Then
        assertNull("Should return null when assetExtras is missing", event.assetExtras)
    }
    
    // MARK: - Experience Data Accessor Tests
    
    @Test
    fun `experienceId with valid event extracts correctly`() {
        // Given
        val experienceId = "exp-123"
        val event = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When/Then
        assertEquals("Should extract experience ID correctly", experienceId, event.experienceId)
    }
    
    @Test
    fun `experienceLocation with valid event extracts correctly`() {
        // Given
        val location = "homepage"
        val event = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = location,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When/Then
        assertEquals("Should extract experience location correctly", location, event.experienceLocation)
    }
    
    @Test
    fun `experienceAction with valid event extracts correctly`() {
        // Given
        val action = ContentAnalyticsConstants.ActionType.CLICK
        val event = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = "home",
            action = action
        )
        
        // When/Then
        assertEquals("Should extract experience action correctly", action, event.experienceAction)
    }
    
    @Test
    fun `experienceExtras with valid event extracts correctly`() {
        // Given
        val extras = mapOf("key1" to "value1", "key2" to 456)
        val event = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW,
            extras = extras
        )
        
        // When/Then
        assertEquals("Should extract experience extras correctly", extras, event.experienceExtras)
    }
    
    // MARK: - Key Generation Tests
    
    @Test
    fun `assetKey generates correct key with location`() {
        // Given
        val url = "https://example.com/image.jpg"
        val location = "homepage"
        val event = TestEventFactory.createAssetEvent(
            url = url,
            location = location,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When
        val key = event.assetKey
        
        // Then
        val expectedKey = "$url|$location"
        assertEquals("Should generate correct asset key", expectedKey, key)
    }
    
    @Test
    fun `assetKey generates correct key without location`() {
        // Given
        val url = "https://example.com/image.jpg"
        val event = TestEventFactory.createAssetEvent(
            url = url,
            location = null,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When
        val key = event.assetKey
        
        // Then
        val expectedKey = "$url|no-location"
        assertEquals("Should generate asset key with 'no-location' when location is missing", expectedKey, key)
    }
    
    @Test
    fun `assetKey returns null when URL is missing`() {
        // Given
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            EventSource.REQUEST_CONTENT
        ).setEventData(mapOf(
            ContentAnalyticsConstants.EventDataKeys.ASSET_LOCATION to "home"
        )).build()
        
        // When
        val key = event.assetKey
        
        // Then
        assertNull("Should return null when asset URL is missing", key)
    }
    
    @Test
    fun `experienceKey generates correct key with location`() {
        // Given
        val experienceId = "exp-123"
        val location = "homepage"
        val event = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = location,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When
        val key = event.experienceKey
        
        // Then
        val expectedKey = "$experienceId|$location"
        assertEquals("Should generate correct experience key", expectedKey, key)
    }
    
    @Test
    fun `experienceKey generates correct key without location`() {
        // Given
        val experienceId = "exp-123"
        val event = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = null,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When
        val key = event.experienceKey
        
        // Then
        val expectedKey = "$experienceId|no-location"
        assertEquals("Should generate experience key with 'no-location' when location is missing", expectedKey, key)
    }
    
    @Test
    fun `experienceKey returns null when ID is missing`() {
        // Given
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            EventSource.REQUEST_CONTENT
        ).setEventData(mapOf(
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_LOCATION to "home"
        )).build()
        
        // When
        val key = event.experienceKey
        
        // Then
        assertNull("Should return null when experience ID is missing", key)
    }
    
    // MARK: - Action Helper Tests
    
    @Test
    fun `isViewAction returns true for view action`() {
        // Given
        val action = ContentAnalyticsConstants.ActionType.VIEW
        
        // When/Then
        assertTrue("Should identify view action correctly", action.isViewAction())
    }
    
    @Test
    fun `isViewAction returns false for click action`() {
        // Given
        val action = ContentAnalyticsConstants.ActionType.CLICK
        
        // When/Then
        assertFalse("Should not identify click action as view action", action.isViewAction())
    }
    
    // Note: Android only supports VIEW and CLICK actions (no DEFINITION action like iOS)
    
    @Test
    fun `isClickAction returns true for click action`() {
        // Given
        val action = ContentAnalyticsConstants.ActionType.CLICK
        
        // When/Then
        assertTrue("Should identify click action correctly", action.isClickAction())
    }
    
    @Test
    fun `isClickAction returns false for view action`() {
        // Given
        val action = ContentAnalyticsConstants.ActionType.VIEW
        
        // When/Then
        assertFalse("Should not identify view action as click action", action.isClickAction())
    }
    
    // MARK: - Edge Cases
    
    @Test
    fun `asset key is consistent for same URL and location`() {
        // Given
        val event1 = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val event2 = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.CLICK
        )
        
        // When
        val key1 = event1.assetKey
        val key2 = event2.assetKey
        
        // Then
        assertEquals("Asset keys should be identical for same URL and location", key1, key2)
    }
    
    @Test
    fun `asset key differs for different locations`() {
        // Given
        val event1 = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val event2 = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "products",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When
        val key1 = event1.assetKey
        val key2 = event2.assetKey
        
        // Then
        assertNotEquals("Asset keys should differ for different locations", key1, key2)
    }
    
    @Test
    fun `experience key is consistent for same ID and location`() {
        // Given
        val event1 = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val event2 = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.CLICK
        )
        
        // When
        val key1 = event1.experienceKey
        val key2 = event2.experienceKey
        
        // Then
        assertEquals("Experience keys should be identical for same ID and location", key1, key2)
    }
    
    @Test
    fun `experience key differs for different locations`() {
        // Given
        val event1 = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val event2 = TestEventFactory.createExperienceEvent(
            experienceId = "exp-123",
            location = "products",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When
        val key1 = event1.experienceKey
        val key2 = event2.experienceKey
        
        // Then
        assertNotEquals("Experience keys should differ for different locations", key1, key2)
    }
}

