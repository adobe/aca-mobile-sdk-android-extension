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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ContentAnalyticsOrchestratorTest {
    
    private lateinit var state: ContentAnalyticsStateManager
    private lateinit var eventValidator: EventValidating
    private lateinit var eventExclusionFilter: EventExclusionFiltering
    private lateinit var assetEventProcessor: AssetEventProcessing
    private lateinit var experienceEventProcessor: ExperienceEventProcessing
    private lateinit var featurizationCoordinator: FeaturizationCoordinator
    private lateinit var batchCoordinator: BatchCoordinator
    private lateinit var orchestrator: ContentAnalyticsOrchestrator
    
    @Before
    fun setup() {
        state = ContentAnalyticsStateManager()
        eventValidator = mock()
        eventExclusionFilter = mock()
        assetEventProcessor = mock()
        experienceEventProcessor = mock()
        batchCoordinator = mock()
        
        val privacyValidator = mock<PrivacyValidator>()
        whenever(privacyValidator.isDataCollectionAllowed()).thenReturn(true)
        featurizationCoordinator = FeaturizationCoordinator(state, privacyValidator)
        
        // Default: validation passes
        whenever(eventValidator.validateAssetEvent(any())).thenReturn(ValidationResult(true))
        whenever(eventValidator.validateExperienceEvent(any())).thenReturn(ValidationResult(true))
        whenever(eventValidator.validateProcessingConditions()).thenReturn(null)
        whenever(eventValidator.isExperienceTrackingEnabled()).thenReturn(true)
        
        // Default: no exclusions
        whenever(eventExclusionFilter.shouldExcludeAsset(any())).thenReturn(false)
        whenever(eventExclusionFilter.shouldExcludeExperience(any())).thenReturn(false)
        
        orchestrator = ContentAnalyticsOrchestrator(
            state = state,
            eventValidator = eventValidator,
            eventExclusionFilter = eventExclusionFilter,
            assetEventProcessor = assetEventProcessor,
            experienceEventProcessor = experienceEventProcessor,
            featurizationCoordinator = featurizationCoordinator,
            batchCoordinator = batchCoordinator
        )
    }
    
    @Test
    fun `test processAssetEvent with valid data`() {
        // Given
        val config = ContentAnalyticsConfiguration(batchingEnabled = true)
        state.updateConfiguration(config)
        
        val event = createAssetEvent(
            "https://example.com/image.jpg",
            "homepage",
            ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When
        orchestrator.processAssetEvent(event)
        
        // Then
        verify(batchCoordinator).addAssetEvent(event)
    }
    
    // Privacy consent is checked by Edge extension, not by this extension
    
    @Test
    fun `test processAssetEvent filtered by URL pattern`() {
        // Given
        val event = createAssetEvent(
            "https://example.com/image.gif",
            "homepage",
            ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // Configure filter to exclude this event
        whenever(eventExclusionFilter.shouldExcludeAsset(event)).thenReturn(true)
        
        // When
        orchestrator.processAssetEvent(event)
        
        // Then
        verify(batchCoordinator, never()).addAssetEvent(any())
    }
    
    @Test
    fun `test processAssetEvent filtered by location`() {
        // Given
        val event = createAssetEvent(
            "https://example.com/image.jpg",
            "debug",
            ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // Configure filter to exclude this event
        whenever(eventExclusionFilter.shouldExcludeAsset(event)).thenReturn(true)
        
        // When
        orchestrator.processAssetEvent(event)
        
        // Then
        verify(batchCoordinator, never()).addAssetEvent(any())
    }
    
    @Test
    fun `test processExperienceEvent with definition`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = true,
            trackExperiences = true
        )
        state.updateConfiguration(config)
        
        val definition = ExperienceDefinition(
            experienceId = "test-exp",
            assets = listOf("https://example.com/asset.jpg"),
            texts = listOf(ContentItem("Welcome")),
            ctas = null
        )
        
        val event = createExperienceEvent(
            "test-exp",
            "homepage",
            ContentAnalyticsConstants.ActionType.VIEW,
            definition
        )
        
        // When
        orchestrator.processExperienceEvent(event)
        
        // Then
        verify(batchCoordinator).addExperienceEvent(event)
        // Definition content is stored correctly, and the last-seen location is set by
        // captureExperienceLocation (from the VIEW event), not at definition registration time.
        assertEquals(definition.copy(experienceLocation = "homepage"), state.getExperienceDefinition("test-exp"))
    }
    
    @Test
    fun `test processExperienceEvent disabled in config`() {
        // Given - experience tracking disabled
        whenever(eventValidator.isExperienceTrackingEnabled()).thenReturn(false)
        
        val event = createExperienceEvent(
            "test-exp",
            "homepage",
            ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When
        orchestrator.processExperienceEvent(event)
        
        // Then
        verify(batchCoordinator, never()).addExperienceEvent(any())
    }
    
    // Experience Location Capture (captureExperienceLocation)
    // Location is NOT stored at definition registration time. The orchestrator captures it from
    // VIEW events BEFORE the exclusion filter, so excluded events still write the location.

    @Test
    fun `captureExperienceLocation - view event updates stored definition location`() {
        // Given - definition registered without a location
        val config = ContentAnalyticsConfiguration(batchingEnabled = true, trackExperiences = true)
        state.updateConfiguration(config)

        state.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = "hero",
                assets = listOf("https://example.com/hero.jpg"),
                texts = emptyList(),
                ctas = null
            )
        )
        assertNull(state.getExperienceDefinition("hero")?.experienceLocation)

        val viewEvent = createExperienceEvent("hero", "homepage", ContentAnalyticsConstants.ActionType.VIEW)

        // When
        orchestrator.processExperienceEvent(viewEvent)

        // Then
        assertEquals("homepage", state.getExperienceDefinition("hero")?.experienceLocation)
    }

    @Test
    fun `captureExperienceLocation - excluded view event still captures location`() {
        // Given - exclusion filter returns true (experience is excluded)
        val config = ContentAnalyticsConfiguration(batchingEnabled = true, trackExperiences = true)
        state.updateConfiguration(config)

        state.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = "banner",
                assets = listOf("https://example.com/banner.jpg"),
                texts = emptyList(),
                ctas = null
            )
        )

        val excludedViewEvent = createExperienceEvent("banner", "test-admin-panel", ContentAnalyticsConstants.ActionType.VIEW)
        whenever(eventExclusionFilter.shouldExcludeExperience(excludedViewEvent)).thenReturn(true)

        // When
        orchestrator.processExperienceEvent(excludedViewEvent)

        // Then - event was excluded but location was still captured before the filter ran
        verify(batchCoordinator, never()).addExperienceEvent(any())
        // Location must be captured even when the experience event itself is excluded
        assertEquals("test-admin-panel", state.getExperienceDefinition("banner")?.experienceLocation)
    }

    @Test
    fun `captureExperienceLocation - second view at different location overwrites first`() {
        // Given - same experience viewed at two different locations
        val config = ContentAnalyticsConfiguration(batchingEnabled = true, trackExperiences = true)
        state.updateConfiguration(config)

        state.registerExperienceDefinition(
            ExperienceDefinition(
                experienceId = "card",
                assets = listOf("https://example.com/card.jpg"),
                texts = emptyList(),
                ctas = null
            )
        )

        val firstView = createExperienceEvent("card", "page-a", ContentAnalyticsConstants.ActionType.VIEW)
        val secondView = createExperienceEvent("card", "page-b", ContentAnalyticsConstants.ActionType.VIEW)

        // When
        orchestrator.processExperienceEvent(firstView)
        orchestrator.processExperienceEvent(secondView)

        // Then - most recently seen location wins
        assertEquals("page-b", state.getExperienceDefinition("card")?.experienceLocation)
    }

    @Test
    fun `test flush delegates to BatchCoordinator`() {
        // When
        orchestrator.flush()
        
        // Then
        verify(batchCoordinator).flush()
    }
    
    @Test
    fun `test clearPendingBatch delegates to BatchCoordinator`() {
        // When
        orchestrator.clearPendingBatch()
        
        // Then
        verify(batchCoordinator).clearPendingBatch()
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
            EventSource.REQUEST_CONTENT
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
            EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
    }
}

