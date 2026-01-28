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
import com.adobe.marketing.mobile.EventType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-End tests for Content Analytics extension
 * 
 * Tests the complete flow from public API to Edge dispatch
 */
class ContentAnalyticsEndToEndTest {
    
    private lateinit var dispatchedEvents: MutableList<Event>
    private lateinit var eventDispatcher: EventDispatcher
    private lateinit var privacyValidator: PrivacyValidator
    private lateinit var state: ContentAnalyticsStateManager
    private lateinit var orchestrator: ContentAnalyticsOrchestrator
    
    @Before
    fun setup() {
        dispatchedEvents = mutableListOf()
        
        // Mock event dispatcher that captures events
        eventDispatcher = object : EventDispatcher {
            override fun dispatch(event: Event) {
                dispatchedEvents.add(event)
            }
        }
        
        // Mock privacy validator (allow by default)
        privacyValidator = object : PrivacyValidator {
            override fun isDataCollectionAllowed() = true
        }
        
        state = ContentAnalyticsStateManager()
        
        // Create orchestrator WITHOUT BatchCoordinator (immediate mode)
        orchestrator = ContentAnalyticsOrchestrator(
            state = state,
            eventDispatcher = eventDispatcher,
            privacyValidator = privacyValidator,
            xdmEventBuilder = XDMEventBuilder,
            batchCoordinator = null  // No batching for E2E tests
        )
        
        // Configure with batching OFF
        val config = ContentAnalyticsConfiguration(batchingEnabled = false)
        state.updateConfiguration(config)
    }
    
    @Test
    fun `test trackAssetView sends Edge event`() {
        // Given
        val assetURL = "https://example.com/hero.jpg"
        val location = "homepage"
        
        // When
        val event = createAssetEvent(assetURL, location, "view")
        orchestrator.processAssetEvent(event)
        
        // Then
        assertEquals(1, dispatchedEvents.size)
        
        val edgeEvent = dispatchedEvents.first()
        assertEquals(EventType.EDGE, edgeEvent.type)
        
        val xdm = edgeEvent.eventData?.get("xdm") as? Map<*, *>
        assertNotNull(xdm)
        assertEquals("content.contentEngagement", xdm?.get("eventType"))
        
        val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
        assertNotNull("experienceContent should exist", experienceContent)
        
        val assets = experienceContent?.get("assets") as? List<*>
        assertNotNull("assets array should exist", assets)
        assertEquals(1, assets?.size)
        
        val asset = assets?.firstOrNull() as? Map<*, *>
        assertEquals(assetURL, asset?.get("assetID"))
        
        if (location != null) {
            assertEquals(location, asset?.get("assetSource"))
        }
        
        val assetViews = asset?.get("assetViews") as? Map<*, *>
        assertEquals(1.0, assetViews?.get("value"))
    }
    
    @Test
    fun `test trackAssetClick sends Edge event with click metric`() {
        // Given
        val assetURL = "https://example.com/banner.jpg"
        
        // When
        val event = createAssetEvent(assetURL, null, "click")
        orchestrator.processAssetEvent(event)
        
        // Then
        assertEquals(1, dispatchedEvents.size)
        
        val edgeEvent = dispatchedEvents.first()
        val xdm = edgeEvent.eventData?.get("xdm") as? Map<*, *>
        val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
        val assets = experienceContent?.get("assets") as? List<*>
        val asset = assets?.firstOrNull() as? Map<*, *>
        
        val assetClicks = asset?.get("assetClicks") as? Map<*, *>
        assertEquals(1.0, assetClicks?.get("value"))
    }
    
    @Test
    fun `test trackExperienceView with definition sends Edge event`() {
        // Given
        val experienceId = "homepage-hero"
        val definition = ExperienceDefinition(
            experienceId = experienceId,
            assets = listOf("https://example.com/hero.jpg"),
            texts = listOf(ContentItem("Welcome!")),
            ctas = listOf(ContentItem("Shop Now"))
        )
        
        // When
        val event = createExperienceEvent(experienceId, "homepage", "view", definition)
        orchestrator.processExperienceEvent(event)
        
        // Then
        assertEquals(1, dispatchedEvents.size)
        
        // Verify definition was stored
        val storedDef = state.getExperienceDefinition(experienceId)
        assertNotNull(storedDef)
        assertEquals(experienceId, storedDef?.experienceId)
        assertEquals(definition.assets, storedDef?.assets)
        assertEquals(definition.texts, storedDef?.texts)
        assertEquals(definition.ctas, storedDef?.ctas)
        
        // Verify definition was marked as sent to featurization
        assertTrue(state.hasExperienceDefinitionBeenSent(experienceId))
        
        // Verify Edge event
        val edgeEvent = dispatchedEvents.first()
        val xdm = edgeEvent.eventData?.get("xdm") as? Map<*, *>
        val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
        
        val experience = experienceContent?.get("experience") as? Map<*, *>
        assertEquals(experienceId, experience?.get("experienceID"))
        assertEquals("homepage", experience?.get("experienceSource"))
        
        // Verify asset attribution
        val assets = experienceContent?.get("assets") as? List<*>
        assertEquals(1, assets?.size)
        val asset = assets?.firstOrNull() as? Map<*, *>
        assertEquals("https://example.com/hero.jpg", asset?.get("assetID"))
    }
    
    @Test
    fun `test excludedAssetUrlsRegexp filters events`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            excludedAssetUrlsRegexp = ".*\\.gif$"
        )
        state.updateConfiguration(config)
        
        // When - Track GIF (should be filtered)
        val event1 = createAssetEvent("https://example.com/image.gif", "homepage", "view")
        orchestrator.processAssetEvent(event1)
        
        // When - Track JPG (should NOT be filtered)
        val event2 = createAssetEvent("https://example.com/image.jpg", "homepage", "view")
        orchestrator.processAssetEvent(event2)
        
        // Then - Only JPG event dispatched
        assertEquals(1, dispatchedEvents.size)
        
        val xdm = dispatchedEvents.first().eventData?.get("xdm") as? Map<*, *>
        val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
        val assets = experienceContent?.get("assets") as? List<*>
        val asset = assets?.firstOrNull() as? Map<*, *>
        
        assertEquals("https://example.com/image.jpg", asset?.get("assetID"))
    }
    
    @Test
    fun `test excludedAssetLocationsRegexp filters events`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            excludedAssetLocationsRegexp = "^(debug|test)$"
        )
        state.updateConfiguration(config)
        
        // When - Track with excluded location
        val event1 = createAssetEvent("https://example.com/image.jpg", "debug", "view")
        orchestrator.processAssetEvent(event1)
        
        // When - Track with allowed location
        val event2 = createAssetEvent("https://example.com/image.jpg", "homepage", "view")
        orchestrator.processAssetEvent(event2)
        
        // Then - Only homepage event dispatched
        assertEquals(1, dispatchedEvents.size)
    }
    
    @Test
    fun `test datastreamOverride applied to Edge events`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            datastreamId = "ca-datastream-123"
        )
        state.updateConfiguration(config)
        
        // When
        val event = createAssetEvent("https://example.com/image.jpg", "homepage", "view")
        orchestrator.processAssetEvent(event)
        
        // Then
        assertEquals(1, dispatchedEvents.size)
        
        val edgeEvent = dispatchedEvents.first()
        val configOverride = edgeEvent.eventData?.get("config") as? Map<*, *>
        
        assertNotNull(configOverride)
        assertEquals("ca-datastream-123", configOverride?.get("datastreamIdOverride"))
    }
    
    // MARK: - Location-Based Grouping Tests (CJA Breakdowns)
    
    @Test
    fun `test assetLocation different locations send separate Edge events`() {
        // Given
        val assetURL = "https://example.com/image.jpg"
        
        // When - Track same asset in different locations
        val event1 = createAssetEvent(assetURL, "homepage", "view")
        val event2 = createAssetEvent(assetURL, "product-page", "view")
        
        orchestrator.processAssetEvent(event1)
        orchestrator.processAssetEvent(event2)
        
        // Then - Should have 2 separate Edge events (enables CJA breakdown by location)
        assertEquals(2, dispatchedEvents.size)
        
        val locations = dispatchedEvents.mapNotNull { event ->
            val xdm = event.eventData?.get("xdm") as? Map<*, *>
            val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
            val assets = experienceContent?.get("assets") as? List<Map<*, *>>
            assets?.firstOrNull()?.get("assetSource") as? String
        }
        
        assertTrue("Should have homepage location", locations.contains("homepage"))
        assertTrue("Should have product-page location", locations.contains("product-page"))
    }
    
    @Test
    fun `test experienceLocation different locations send separate Edge events`() {
        // Given
        val experienceId = "exp-hero"
        val definition = ExperienceDefinition(
            experienceId = experienceId,
            assets = listOf("https://example.com/hero.jpg"),
            texts = listOf(ContentItem("Welcome")),
            ctas = null
        )
        
        // When - Track same experience in different locations
        val event1 = createExperienceEvent(experienceId, "homepage", "view", definition)
        val event2 = createExperienceEvent(experienceId, "landing-page", "view", null)
        
        orchestrator.processExperienceEvent(event1)
        orchestrator.processExperienceEvent(event2)
        
        // Then - Should have 2 separate Edge events
        assertEquals(2, dispatchedEvents.size)
        
        val locations = dispatchedEvents.mapNotNull { event ->
            val xdm = event.eventData?.get("xdm") as? Map<*, *>
            val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
            val experience = experienceContent?.get("experience") as? Map<*, *>
            experience?.get("experienceSource") as? String
        }
        
        assertTrue("Should have homepage location", locations.contains("homepage"))
        assertTrue("Should have landing-page location", locations.contains("landing-page"))
    }
    
    // MARK: - Exclusion Tests
    
    @Test
    fun `test excludedAssetLocation not dispatched`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            excludedAssetLocationsRegexp = "^(test-page|debug)$"
        )
        state.updateConfiguration(config)
        
        // When - Track with excluded location
        val event = createAssetEvent("https://example.com/image.jpg", "test-page", "view")
        orchestrator.processAssetEvent(event)
        
        // Then - No events dispatched
        assertEquals(0, dispatchedEvents.size)
    }
    
    @Test
    fun `test nonExcludedAssetLocation dispatched`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            excludedAssetLocationsRegexp = "^test-page$"
        )
        state.updateConfiguration(config)
        
        // When - Track with allowed location
        val event = createAssetEvent("https://example.com/image.jpg", "homepage", "view")
        orchestrator.processAssetEvent(event)
        
        // Then - Event dispatched
        assertEquals(1, dispatchedEvents.size)
    }
    
    @Test
    fun `test assetNoLocation with excludedLocations still dispatched`() {
        // Given - Excluded locations configured
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            excludedAssetLocationsRegexp = "^test-page$"
        )
        state.updateConfiguration(config)
        
        // When - Track asset with NO location
        val event = createAssetEvent("https://example.com/image.jpg", null, "view")
        orchestrator.processAssetEvent(event)
        
        // Then - Event should still be dispatched (null location not in exclusion set)
        assertEquals(1, dispatchedEvents.size)
    }
    
    @Test
    fun `test excludedExperienceLocationsRegexp filters experiences`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            excludedExperienceLocationsRegexp = ".*test.*"
        )
        state.updateConfiguration(config)
        
        val definition = ExperienceDefinition(
            experienceId = "exp-test",
            assets = listOf("https://example.com/image.jpg"),
            texts = listOf(ContentItem("Text")),
            ctas = null
        )
        
        // When - Track with excluded location
        val event1 = createExperienceEvent("exp-test", "test-page", "view", definition)
        orchestrator.processExperienceEvent(event1)
        
        // Then - No events
        assertEquals(0, dispatchedEvents.size)
        
        // When - Track with allowed location
        val event2 = createExperienceEvent("exp-test", "homepage", "view", null)
        orchestrator.processExperienceEvent(event2)
        
        // Then - Event dispatched
        assertEquals(1, dispatchedEvents.size)
    }
    
    // MARK: - Datastream Override Tests
    
    @Test
    fun `test noDatastreamOverride uses default`() {
        // Given - No datastream override
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            datastreamId = null  // No override
        )
        state.updateConfiguration(config)
        
        // When
        val event = createAssetEvent("https://example.com/image.jpg", "homepage", "view")
        orchestrator.processAssetEvent(event)
        
        // Then - No config override in event
        val edgeEvent = dispatchedEvents.first()
        val configOverride = edgeEvent.eventData?.get("config")
        
        assertNull("Should NOT have config override", configOverride)
    }
    
    @Test
    fun `test datastreamOverride applied to experience events`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            datastreamId = "ca-datastream-456"
        )
        state.updateConfiguration(config)
        
        val definition = ExperienceDefinition(
            experienceId = "exp-hero",
            assets = listOf("https://example.com/hero.jpg"),
            texts = listOf(ContentItem("Text")),
            ctas = null
        )
        
        // When
        val event = createExperienceEvent("exp-hero", "homepage", "view", definition)
        orchestrator.processExperienceEvent(event)
        
        // Then - Override should be applied
        val edgeEvent = dispatchedEvents.first()
        val configOverride = edgeEvent.eventData?.get("config") as? Map<*, *>
        assertEquals("ca-datastream-456", configOverride?.get("datastreamIdOverride"))
    }
    
    // MARK: - Missing Required Fields Tests
    
    @Test
    fun `test missingAssetURL not dispatched`() {
        // Given - Event with missing asset URL
        val data = mutableMapOf<String, Any>(
            ContentAnalyticsConstants.EventDataKeys.ASSET_ACTION to "view"
            // Missing: ASSET_URL
        )
        
        val event = Event.Builder(
            "Track Asset",
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // When
        orchestrator.processAssetEvent(event)
        
        // Then - No events dispatched
        assertEquals(0, dispatchedEvents.size)
    }
    
    @Test
    fun `test missingExperienceId not dispatched`() {
        // Given - Event with missing experience ID
        val data = mutableMapOf<String, Any>(
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ACTION to "view"
            // Missing: EXPERIENCE_ID
        )
        
        val event = Event.Builder(
            "Track Experience",
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // When
        orchestrator.processExperienceEvent(event)
        
        // Then - No events dispatched
        assertEquals(0, dispatchedEvents.size)
    }
    
    // MARK: - Extras Tests
    
    @Test
    fun `test assetExtras included in XDM`() {
        // Given
        val extras = mapOf("campaign" to "summer", "variant" to "A")
        val data = mutableMapOf<String, Any>(
            ContentAnalyticsConstants.EventDataKeys.ASSET_URL to "https://example.com/image.jpg",
            ContentAnalyticsConstants.EventDataKeys.ASSET_LOCATION to "homepage",
            ContentAnalyticsConstants.EventDataKeys.ASSET_ACTION to "view",
            ContentAnalyticsConstants.EventDataKeys.ASSET_EXTRAS to extras
        )
        
        val event = Event.Builder(
            "Track Asset",
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // When
        orchestrator.processAssetEvent(event)
        
        // Then
        val xdm = dispatchedEvents.first().eventData?.get("xdm") as? Map<*, *>
        val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
        val assets = experienceContent?.get("assets") as? List<Map<*, *>>
        val assetExtras = assets?.firstOrNull()?.get("assetExtras") as? Map<*, *>
        
        assertNotNull("Should have assetExtras", assetExtras)
        assertEquals("summer", assetExtras?.get("campaign"))
        assertEquals("A", assetExtras?.get("variant"))
    }
    
    @Test
    fun `test experienceExtras included in XDM`() {
        // Given
        val extras = mapOf("test_group" to "B", "user_segment" to "premium")
        val definition = ExperienceDefinition(
            experienceId = "exp-test",
            assets = listOf("https://example.com/image.jpg"),
            texts = listOf(ContentItem("Text")),
            ctas = null
        )
        
        val data = mutableMapOf<String, Any>(
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ID to "exp-test",
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_LOCATION to "homepage",
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ACTION to "view",
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_DEFINITION to definition.toMap(),
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_EXTRAS to extras
        )
        
        val event = Event.Builder(
            "Track Experience",
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
        
        // When
        orchestrator.processExperienceEvent(event)
        
        // Then
        val xdm = dispatchedEvents.first().eventData?.get("xdm") as? Map<*, *>
        val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
        val experience = experienceContent?.get("experience") as? Map<*, *>
        val experienceExtras = experience?.get("experienceExtras") as? Map<*, *>
        
        assertNotNull("Should have experienceExtras", experienceExtras)
        assertEquals("B", experienceExtras?.get("test_group"))
        assertEquals("premium", experienceExtras?.get("user_segment"))
    }
    
    // MARK: - Privacy/Consent Tests
    
    // Note: Privacy consent is checked by Edge extension, not by Content Analytics
    // Content Analytics will dispatch events, and Edge will drop them if consent is denied
    // This matches iOS behavior and standard Adobe SDK architecture
    
    // MARK: - Experience Asset Attribution
    
    @Test
    fun `test experienceWithAssets includes assets in XDM`() {
        // Given
        val experienceId = "exp-hero"
        val assetURLs = listOf(
            "https://example.com/hero1.jpg",
            "https://example.com/hero2.jpg",
            "https://example.com/hero3.jpg"
        )
        
        val definition = ExperienceDefinition(
            experienceId = experienceId,
            assets = assetURLs,
            texts = listOf(ContentItem("Welcome")),
            ctas = listOf(ContentItem("Shop Now"))
        )
        
        // When
        val event = createExperienceEvent(experienceId, "homepage", "view", definition)
        orchestrator.processExperienceEvent(event)
        
        // Then - Should include all 3 assets for attribution
        val xdm = dispatchedEvents.first().eventData?.get("xdm") as? Map<*, *>
        val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
        val assets = experienceContent?.get("assets") as? List<Map<*, *>>
        
        assertNotNull("Should have assets array", assets)
        assertEquals("Should have 3 assets", 3, assets?.size)
        
        val assetIDs = assets?.mapNotNull { it["assetID"] as? String }
        assertTrue("Should contain hero1.jpg", assetIDs?.contains("https://example.com/hero1.jpg") == true)
        assertTrue("Should contain hero2.jpg", assetIDs?.contains("https://example.com/hero2.jpg") == true)
        assertTrue("Should contain hero3.jpg", assetIDs?.contains("https://example.com/hero3.jpg") == true)
    }
    
    // MARK: - Track Experiences Configuration
    
    @Test
    fun `test trackExperiencesDisabled blocks experience events`() {
        // Given - Experience tracking disabled
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            trackExperiences = false
        )
        state.updateConfiguration(config)
        
        val definition = ExperienceDefinition(
            experienceId = "exp-test",
            assets = listOf("https://example.com/image.jpg"),
            texts = listOf(ContentItem("Text")),
            ctas = null
        )
        
        // When - Try to track experience
        val event = createExperienceEvent("exp-test", "homepage", "view", definition)
        orchestrator.processExperienceEvent(event)
        
        // Then - No events dispatched
        assertEquals(0, dispatchedEvents.size)
    }
    
    // MARK: - Helper Methods
    
    private fun createAssetEvent(url: String, location: String?, action: String): Event {
        val data = mutableMapOf<String, Any>(
            ContentAnalyticsConstants.EventDataKeys.ASSET_URL to url,
            ContentAnalyticsConstants.EventDataKeys.ASSET_ACTION to action
        )
        location?.let { data[ContentAnalyticsConstants.EventDataKeys.ASSET_LOCATION] = it }
        
        return Event.Builder(
            "Track Asset",
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
    }
    
    private fun createExperienceEvent(
        experienceId: String,
        location: String?,
        action: String,
        definition: ExperienceDefinition? = null
    ): Event {
        val data = mutableMapOf<String, Any>(
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ID to experienceId,
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ACTION to action
        )
        location?.let { data[ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_LOCATION] = it }
        definition?.let { data[ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_DEFINITION] = it.toMap() }
        
        return Event.Builder(
            "Track Experience",
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
    }
}

