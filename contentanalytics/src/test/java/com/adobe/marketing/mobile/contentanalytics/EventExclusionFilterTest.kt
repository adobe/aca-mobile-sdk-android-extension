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
import com.adobe.marketing.mobile.EventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for EventExclusionFilter - filters events based on URL and location patterns
 */
class EventExclusionFilterTest {
    
    private lateinit var stateManager: ContentAnalyticsStateManager
    private lateinit var filter: EventExclusionFilter
    
    @Before
    fun setUp() {
        stateManager = ContentAnalyticsStateManager()
        filter = EventExclusionFilter(stateManager)
        
        // Apply basic configuration
        val config = ContentAnalyticsConfiguration(trackExperiences = true)
        stateManager.updateConfiguration(config)
    }
    
    // Asset URL Exclusion Tests
    
    @Test
    fun `shouldExcludeAsset with no exclusion patterns returns false`() {
        val event = createAssetEvent(
            assetURL = "https://example.com/image.jpg",
            assetLocation = "header"
        )
        
        assertFalse("Should not exclude when no patterns configured", filter.shouldExcludeAsset(event))
    }
    
    @Test
    fun `shouldExcludeAsset with matching URL pattern returns true`() {
        val config = ContentAnalyticsConfiguration(
            excludedAssetUrlsRegexp = ".*\\.gif$"
        )
        stateManager.updateConfiguration(config)
        
        val event = createAssetEvent(
            assetURL = "https://example.com/animation.gif",
            assetLocation = "content"
        )
        
        assertTrue("Should exclude URLs matching pattern", filter.shouldExcludeAsset(event))
    }
    
    @Test
    fun `shouldExcludeAsset with non-matching URL pattern returns false`() {
        val config = ContentAnalyticsConfiguration(
            excludedAssetUrlsRegexp = ".*\\.gif$"
        )
        stateManager.updateConfiguration(config)
        
        val event = createAssetEvent(
            assetURL = "https://example.com/image.jpg",
            assetLocation = "content"
        )
        
        assertFalse("Should not exclude URLs not matching pattern", filter.shouldExcludeAsset(event))
    }
    
    // Asset Location Exclusion Tests
    
    @Test
    fun `shouldExcludeAsset with matching location pattern returns true`() {
        val config = ContentAnalyticsConfiguration(
            excludedAssetLocationsRegexp = "^footer.*"
        )
        stateManager.updateConfiguration(config)
        
        val event = createAssetEvent(
            assetURL = "https://example.com/image.jpg",
            assetLocation = "footer-banner"
        )
        
        assertTrue("Should exclude locations matching pattern", filter.shouldExcludeAsset(event))
    }
    
    @Test
    fun `shouldExcludeAsset with non-matching location pattern returns false`() {
        val config = ContentAnalyticsConfiguration(
            excludedAssetLocationsRegexp = "^footer.*"
        )
        stateManager.updateConfiguration(config)
        
        val event = createAssetEvent(
            assetURL = "https://example.com/image.jpg",
            assetLocation = "header-banner"
        )
        
        assertFalse("Should not exclude locations not matching pattern", filter.shouldExcludeAsset(event))
    }
    
    // Experience Location Exclusion Tests
    
    @Test
    fun `shouldExcludeExperience with no exclusion patterns returns false`() {
        val event = createExperienceEvent(
            experienceId = "exp-123",
            experienceLocation = "home-page"
        )
        
        assertFalse("Should not exclude when no patterns configured", filter.shouldExcludeExperience(event))
    }
    
    @Test
    fun `shouldExcludeExperience with matching location pattern returns true`() {
        val config = ContentAnalyticsConfiguration(
            excludedExperienceLocationsRegexp = "^test-.*"
        )
        stateManager.updateConfiguration(config)
        
        val event = createExperienceEvent(
            experienceId = "exp-123",
            experienceLocation = "test-environment"
        )
        
        assertTrue("Should exclude experiences matching pattern", filter.shouldExcludeExperience(event))
    }
    
    @Test
    fun `shouldExcludeExperience with non-matching location pattern returns false`() {
        val config = ContentAnalyticsConfiguration(
            excludedExperienceLocationsRegexp = "^test-.*"
        )
        stateManager.updateConfiguration(config)
        
        val event = createExperienceEvent(
            experienceId = "exp-123",
            experienceLocation = "production-page"
        )
        
        assertFalse("Should not exclude experiences not matching pattern", filter.shouldExcludeExperience(event))
    }
    
    // Edge Cases
    
    @Test
    fun `shouldExcludeAsset with null assetURL returns false`() {
        val event = createAssetEvent(
            assetURL = null,
            assetLocation = "header"
        )
        
        // Should not exclude, let validation handle missing URL
        assertFalse("Should not exclude when URL is null", filter.shouldExcludeAsset(event))
    }
    
    @Test
    fun `shouldExcludeAsset with null location returns false`() {
        val config = ContentAnalyticsConfiguration(
            excludedAssetLocationsRegexp = "^footer.*"
        )
        stateManager.updateConfiguration(config)
        
        val event = createAssetEvent(
            assetURL = "https://example.com/image.jpg",
            assetLocation = null
        )
        
        assertFalse("Should not exclude when location is null", filter.shouldExcludeAsset(event))
    }
    
    // Helper Methods
    
    private fun createAssetEvent(
        assetURL: String?,
        assetLocation: String?
    ): Event {
        val data = mutableMapOf<String, Any?>()
        
        assetURL?.let { data["assetURL"] = it }
        assetLocation?.let { data["assetLocation"] = it }
        data["action"] = ContentAnalyticsConstants.ActionType.VIEW
        
        return Event.Builder(
            "Content Analytics Asset Event",
            EventType.GENERIC_TRACK,
            EventSource.REQUEST_CONTENT
        ).setEventData(data.filterValues { it != null } as Map<String, Any>).build()
    }
    
    private fun createExperienceEvent(
        experienceId: String?,
        experienceLocation: String?
    ): Event {
        val data = mutableMapOf<String, Any?>()
        
        experienceId?.let { data["experienceId"] = it }
        experienceLocation?.let { data["experienceLocation"] = it }
        data["action"] = ContentAnalyticsConstants.ActionType.VIEW
        
        return Event.Builder(
            "Content Analytics Experience Event",
            EventType.GENERIC_TRACK,
            EventSource.REQUEST_CONTENT
        ).setEventData(data.filterValues { it != null } as Map<String, Any>).build()
    }
}
