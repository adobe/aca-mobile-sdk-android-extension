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
    private lateinit var eventDispatcher: EventDispatcher
    private lateinit var privacyValidator: PrivacyValidator
    private lateinit var xdmEventBuilder: XDMEventBuilder
    private lateinit var batchCoordinator: BatchCoordinator
    private lateinit var orchestrator: ContentAnalyticsOrchestrator
    
    @Before
    fun setup() {
        state = ContentAnalyticsStateManager()
        eventDispatcher = mock()
        privacyValidator = mock()
        xdmEventBuilder = XDMEventBuilder
        batchCoordinator = mock()
        
        orchestrator = ContentAnalyticsOrchestrator(
            state, eventDispatcher, privacyValidator, 
            xdmEventBuilder, batchCoordinator
        )
        
        // Default: allow data collection
        whenever(privacyValidator.isDataCollectionAllowed()).thenReturn(true)
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
    
    // Note: Privacy consent is checked by Edge extension, not by Content Analytics
    // This matches iOS behavior and standard Adobe SDK architecture
    
    @Test
    fun `test processAssetEvent filtered by URL pattern`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            excludedAssetUrlsRegexp = ".*\\.gif$"
        )
        state.updateConfiguration(config)
        
        val event = createAssetEvent(
            "https://example.com/image.gif",
            "homepage",
            ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // When
        orchestrator.processAssetEvent(event)
        
        // Then
        verify(batchCoordinator, never()).addAssetEvent(any())
    }
    
    @Test
    fun `test processAssetEvent filtered by location`() {
        // Given
        val config = ContentAnalyticsConfiguration(
            excludedAssetLocationsRegexp = "^(debug|test)$"
        )
        state.updateConfiguration(config)
        
        val event = createAssetEvent(
            "https://example.com/image.jpg",
            "debug",
            ContentAnalyticsConstants.ActionType.VIEW
        )
        
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
        assertEquals(definition, state.getExperienceDefinition("test-exp"))
    }
    
    @Test
    fun `test processExperienceEvent disabled in config`() {
        // Given
        val config = ContentAnalyticsConfiguration(trackExperiences = false)
        state.updateConfiguration(config)
        
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
    
    @Test
    fun `test flushPendingEvents delegates to BatchCoordinator`() {
        // When
        orchestrator.flushPendingEvents()
        
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

