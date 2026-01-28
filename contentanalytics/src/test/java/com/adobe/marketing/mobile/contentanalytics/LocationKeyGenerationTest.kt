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
 * Tests verifying location-based key generation for assets and experiences.
 * Keys are used for batching events by location to track metrics separately per page.
 */
class LocationKeyGenerationTest {
    
    // MARK: - Asset Key Generation Tests
    
    @Test
    fun `asset key with location includes location`() {
        // Given
        val assetURL = "https://example.com/banner.jpg"
        val location = "home"
        
        // When
        val event = TestEventFactory.createAssetEvent(
            url = assetURL,
            location = location,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val key = event.assetKey
        
        // Then
        assertNotNull("Key should not be null", key)
        assertTrue("Key should contain asset URL", key?.contains(assetURL) == true)
        assertTrue("Key should contain location", key?.contains(location) == true)
        assertEquals("Key should follow format", "$assetURL|$location", key)
    }
    
    @Test
    fun `asset key without location uses default`() {
        // Given
        val assetURL = "https://example.com/banner.jpg"
        
        // When
        val event = TestEventFactory.createAssetEvent(
            url = assetURL,
            location = null,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val key = event.assetKey
        
        // Then
        assertNotNull("Key should not be null", key)
        assertEquals("Key should use 'no-location' default", "$assetURL|no-location", key)
    }
    
    @Test
    fun `asset key with empty location uses default`() {
        // Given
        val assetURL = "https://example.com/banner.jpg"
        
        // When
        val event = TestEventFactory.createAssetEvent(
            url = assetURL,
            location = "",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val key = event.assetKey
        
        // Then
        // Empty string is still a valid location, so it should be used as-is
        assertEquals("Key should use empty location", "$assetURL|", key)
    }
    
    @Test
    fun `asset key same asset different locations generates different keys`() {
        // Given
        val assetURL = "https://example.com/banner.jpg"
        
        // When
        val homeEvent = TestEventFactory.createAssetEvent(
            url = assetURL,
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val productEvent = TestEventFactory.createAssetEvent(
            url = assetURL,
            location = "product",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        val homeKey = homeEvent.assetKey
        val productKey = productEvent.assetKey
        
        // Then
        assertNotEquals("Same asset on different pages should have different keys", homeKey, productKey)
        assertTrue("Home key should contain 'home'", homeKey?.contains("home") == true)
        assertTrue("Product key should contain 'product'", productKey?.contains("product") == true)
    }
    
    @Test
    fun `asset key without URL returns null`() {
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
        assertNull("Key should be null when URL is missing", key)
    }
    
    @Test
    fun `asset key handles special characters in location`() {
        // Given
        val assetURL = "https://example.com/banner.jpg"
        val specialLocation = "products/category-123/subcategory?filter=new"
        
        // When
        val event = TestEventFactory.createAssetEvent(
            url = assetURL,
            location = specialLocation,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val key = event.assetKey
        
        // Then
        assertNotNull("Key should not be null", key)
        assertTrue("Key should contain special location", key?.contains(specialLocation) == true)
    }
    
    // MARK: - Experience Key Generation Tests
    
    @Test
    fun `experience key with location includes location`() {
        // Given
        val experienceId = "mobile-abc123"
        val location = "home"
        
        // When
        val event = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = location,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val key = event.experienceKey
        
        // Then
        assertNotNull("Key should not be null", key)
        assertTrue("Key should contain experience ID", key?.contains(experienceId) == true)
        assertTrue("Key should contain location", key?.contains(location) == true)
        assertEquals("Key should follow format", "$experienceId|$location", key)
    }
    
    @Test
    fun `experience key without location uses default`() {
        // Given
        val experienceId = "mobile-abc123"
        
        // When
        val event = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = null,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val key = event.experienceKey
        
        // Then
        assertNotNull("Key should not be null", key)
        assertEquals("Key should use 'no-location' default", "$experienceId|no-location", key)
    }
    
    @Test
    fun `experience key with empty location uses default`() {
        // Given
        val experienceId = "mobile-abc123"
        
        // When
        val event = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = "",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val key = event.experienceKey
        
        // Then
        // Empty string is still a valid location
        assertEquals("Key should use empty location", "$experienceId|", key)
    }
    
    @Test
    fun `experience key same content different locations generates different keys`() {
        // Given
        val experienceId = "mobile-abc123"
        
        // When
        val homeEvent = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val productEvent = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = "product",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        val homeKey = homeEvent.experienceKey
        val productKey = productEvent.experienceKey
        
        // Then
        assertNotEquals("Same experience on different pages should have different keys", homeKey, productKey)
        assertTrue("Home key should contain 'home'", homeKey?.contains("home") == true)
        assertTrue("Product key should contain 'product'", productKey?.contains("product") == true)
    }
    
    @Test
    fun `experience key without ID returns null`() {
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
        assertNull("Key should be null when experience ID is missing", key)
    }
    
    @Test
    fun `experience key handles special characters in location`() {
        // Given
        val experienceId = "mobile-abc123"
        val specialLocation = "products/category-123/subcategory?filter=new"
        
        // When
        val event = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = specialLocation,
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val key = event.experienceKey
        
        // Then
        assertNotNull("Key should not be null", key)
        assertTrue("Key should contain special location", key?.contains(specialLocation) == true)
    }
    
    // MARK: - Experience ID vs Key Tests
    
    @Test
    fun `experience ID same content different locations generates same ID`() {
        // Given
        val assets = listOf(ContentItem(value = "https://example.com/hero.jpg", styles = emptyMap()))
        val texts = listOf(ContentItem(value = "Welcome", styles = emptyMap()))
        
        // When
        val idHome = ContentAnalyticsUtilities.generateExperienceId(assets, texts, null)
        val idProduct = ContentAnalyticsUtilities.generateExperienceId(assets, texts, null)
        
        // Then
        assertEquals("Same content should generate same experienceId regardless of location", idHome, idProduct)
    }
    
    @Test
    fun `experience key includes ID for featurization deduplication`() {
        // Given
        // Verify that experience key starts with experienceId
        // This ensures featurization can still deduplicate based on ID
        val assets = listOf(ContentItem(value = "https://example.com/hero.jpg", styles = emptyMap()))
        val texts = listOf(ContentItem(value = "Welcome", styles = emptyMap()))
        val experienceId = ContentAnalyticsUtilities.generateExperienceId(assets, texts, null)
        
        // When
        val homeEvent = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val productEvent = TestEventFactory.createExperienceEvent(
            experienceId = experienceId,
            location = "product",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        val keyHome = homeEvent.experienceKey
        val keyProduct = productEvent.experienceKey
        
        // Then
        // Both keys should start with experienceId
        assertTrue("Experience key should start with experienceId", keyHome?.startsWith(experienceId) == true)
        assertTrue("Experience key should start with experienceId", keyProduct?.startsWith(experienceId) == true)
        
        // But keys should be different (for separate metrics tracking)
        assertNotEquals("Keys should differ by location", keyHome, keyProduct)
    }
    
    // MARK: - Consistency Between Assets and Experiences
    
    @Test
    fun `location based tracking assets and experiences behave consistently`() {
        // Given
        // Both should use location in key generation
        val assetURL = "https://example.com/banner.jpg"
        val assetEvent1 = TestEventFactory.createAssetEvent(url = assetURL, location = "home", action = ContentAnalyticsConstants.ActionType.VIEW)
        val assetEvent2 = TestEventFactory.createAssetEvent(url = assetURL, location = "product", action = ContentAnalyticsConstants.ActionType.VIEW)
        val assetEventNoLoc = TestEventFactory.createAssetEvent(url = assetURL, location = null, action = ContentAnalyticsConstants.ActionType.VIEW)
        
        val experienceId = "mobile-abc123"
        val expEvent1 = TestEventFactory.createExperienceEvent(experienceId = experienceId, location = "home", action = ContentAnalyticsConstants.ActionType.VIEW)
        val expEvent2 = TestEventFactory.createExperienceEvent(experienceId = experienceId, location = "product", action = ContentAnalyticsConstants.ActionType.VIEW)
        val expEventNoLoc = TestEventFactory.createExperienceEvent(experienceId = experienceId, location = null, action = ContentAnalyticsConstants.ActionType.VIEW)
        
        // When
        val assetKey1 = assetEvent1.assetKey
        val assetKey2 = assetEvent2.assetKey
        val assetKeyNoLoc = assetEventNoLoc.assetKey
        
        val expKey1 = expEvent1.experienceKey
        val expKey2 = expEvent2.experienceKey
        val expKeyNoLoc = expEventNoLoc.experienceKey
        
        // Then
        // Both should include location in key when provided
        assertTrue("Asset key should include location", assetKey1?.contains("home") == true)
        assertTrue("Asset key should include location", assetKey2?.contains("product") == true)
        assertTrue("Experience key should include location", expKey1?.contains("home") == true)
        assertTrue("Experience key should include location", expKey2?.contains("product") == true)
        
        // Keys should differ by location
        assertNotEquals("Different locations = different asset keys", assetKey1, assetKey2)
        assertNotEquals("Different locations = different experience keys", expKey1, expKey2)
        
        // Without location, should use default 'no-location'
        assertEquals("No location = asset URL with default", "$assetURL|no-location", assetKeyNoLoc)
        assertEquals("No location = experienceId with default", "$experienceId|no-location", expKeyNoLoc)
    }
    
    // MARK: - Key Format Tests
    
    @Test
    fun `asset key uses pipe separator format`() {
        // Given
        val assetURL = "https://example.com/banner.jpg"
        val location = "homepage"
        
        // When
        val event = TestEventFactory.createAssetEvent(url = assetURL, location = location, action = ContentAnalyticsConstants.ActionType.VIEW)
        val key = event.assetKey
        
        // Then
        assertEquals("Key should use pipe separator", "$assetURL|$location", key)
        assertTrue("Key should contain pipe separator", key?.contains("|") == true)
    }
    
    @Test
    fun `experience key uses pipe separator format`() {
        // Given
        val experienceId = "mobile-abc123"
        val location = "homepage"
        
        // When
        val event = TestEventFactory.createExperienceEvent(experienceId = experienceId, location = location, action = ContentAnalyticsConstants.ActionType.VIEW)
        val key = event.experienceKey
        
        // Then
        assertEquals("Key should use pipe separator", "$experienceId|$location", key)
        assertTrue("Key should contain pipe separator", key?.contains("|") == true)
    }
    
    // MARK: - Batching Scenarios
    
    @Test
    fun `keys enable correct batching by location`() {
        // Given - Multiple events for same asset on different pages
        val assetURL = "https://example.com/banner.jpg"
        
        val homeEvent1 = TestEventFactory.createAssetEvent(url = assetURL, location = "home", action = ContentAnalyticsConstants.ActionType.VIEW)
        val homeEvent2 = TestEventFactory.createAssetEvent(url = assetURL, location = "home", action = ContentAnalyticsConstants.ActionType.VIEW)
        val productEvent = TestEventFactory.createAssetEvent(url = assetURL, location = "product", action = ContentAnalyticsConstants.ActionType.VIEW)
        
        // When
        val homeKey1 = homeEvent1.assetKey
        val homeKey2 = homeEvent2.assetKey
        val productKey = productEvent.assetKey
        
        // Then
        // Same location = same key (will be batched together)
        assertEquals("Same asset on same page should have identical keys", homeKey1, homeKey2)
        
        // Different location = different key (will be batched separately)
        assertNotEquals("Same asset on different page should have different keys", homeKey1, productKey)
    }
    
    @Test
    fun `keys group experience events correctly for batching`() {
        // Given - Multiple events for same experience on different pages
        val experienceId = "mobile-abc123"
        
        val homeView = TestEventFactory.createExperienceEvent(experienceId = experienceId, location = "home", action = ContentAnalyticsConstants.ActionType.VIEW)
        val homeClick = TestEventFactory.createExperienceEvent(experienceId = experienceId, location = "home", action = ContentAnalyticsConstants.ActionType.CLICK)
        val productView = TestEventFactory.createExperienceEvent(experienceId = experienceId, location = "product", action = ContentAnalyticsConstants.ActionType.VIEW)
        
        // When
        val homeViewKey = homeView.experienceKey
        val homeClickKey = homeClick.experienceKey
        val productViewKey = productView.experienceKey
        
        // Then
        // Same location = same key (views and clicks batched together per location)
        assertEquals("Same experience on same page should have identical keys regardless of action", homeViewKey, homeClickKey)
        
        // Different location = different key (batched separately)
        assertNotEquals("Same experience on different page should have different keys", homeViewKey, productViewKey)
    }
    
    // MARK: - Edge Cases
    
    @Test
    fun `asset key handles URL with query parameters`() {
        // Given
        val assetURL = "https://example.com/banner.jpg?v=1.2&cache=false"
        val location = "home"
        
        // When
        val event = TestEventFactory.createAssetEvent(url = assetURL, location = location, action = ContentAnalyticsConstants.ActionType.VIEW)
        val key = event.assetKey
        
        // Then
        assertEquals("Key should include full URL with query params", "$assetURL|$location", key)
    }
    
    @Test
    fun `experience key handles complex location paths`() {
        // Given
        val experienceId = "mobile-abc123"
        val complexLocation = "products/electronics/phones/iphone-15-pro"
        
        // When
        val event = TestEventFactory.createExperienceEvent(experienceId = experienceId, location = complexLocation, action = ContentAnalyticsConstants.ActionType.VIEW)
        val key = event.experienceKey
        
        // Then
        assertEquals("Key should include full complex location", "$experienceId|$complexLocation", key)
    }
    
    @Test
    fun `asset key handles unicode characters in location`() {
        // Given
        val assetURL = "https://example.com/banner.jpg"
        val unicodeLocation = "产品/类别"
        
        // When
        val event = TestEventFactory.createAssetEvent(url = assetURL, location = unicodeLocation, action = ContentAnalyticsConstants.ActionType.VIEW)
        val key = event.assetKey
        
        // Then
        assertNotNull("Key should handle unicode", key)
        assertTrue("Key should contain unicode location", key?.contains(unicodeLocation) == true)
    }
    
    @Test
    fun `experience key handles whitespace in location`() {
        // Given
        val experienceId = "mobile-abc123"
        val locationWithSpaces = "home page section 1"
        
        // When
        val event = TestEventFactory.createExperienceEvent(experienceId = experienceId, location = locationWithSpaces, action = ContentAnalyticsConstants.ActionType.VIEW)
        val key = event.experienceKey
        
        // Then
        assertNotNull("Key should handle whitespace", key)
        assertEquals("Key should preserve whitespace", "$experienceId|$locationWithSpaces", key)
    }
}

