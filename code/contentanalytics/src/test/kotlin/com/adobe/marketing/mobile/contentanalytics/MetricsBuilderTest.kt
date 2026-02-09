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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for MetricsBuilder - aggregates metrics from batched events
 */
class MetricsBuilderTest {
    
    private lateinit var stateManager: ContentAnalyticsStateManager
    private lateinit var metricsBuilder: MetricsBuilder
    
    @Before
    fun setUp() {
        stateManager = ContentAnalyticsStateManager()
        metricsBuilder = MetricsBuilder(stateManager)
        
        // Apply configuration
        val config = ContentAnalyticsConfiguration(trackExperiences = true)
        stateManager.updateConfiguration(config)
    }
    
    // Asset Metrics Tests
    
    @Test
    fun `buildAssetMetrics with single view event returns correct counts`() {
        val events = listOf(
            createAssetEvent(
                assetURL = "https://example.com/image.jpg",
                assetLocation = "header",
                action = InteractionType.VIEW.stringValue
            )
        )
        
        val (collection, interactionType) = metricsBuilder.buildAssetMetrics(events)
        
        assertFalse("Collection should not be empty", collection.isEmpty)
        assertEquals("Triggering interaction should be view", InteractionType.VIEW, interactionType)
        
        // Get metrics for the asset key
        for (assetKey in collection.assetKeys) {
            val metrics = collection.metricsFor(assetKey)
            assertNotNull("Should have metrics for asset key", metrics)
            
            assertEquals("View count should be 1", 1.0, metrics!!.viewCount, 0.001)
            assertEquals("Click count should be 0", 0.0, metrics.clickCount, 0.001)
            assertEquals("https://example.com/image.jpg", metrics.assetURL)
        }
    }
    
    @Test
    fun `buildAssetMetrics with multiple events aggregates counts`() {
        val events = listOf(
            createAssetEvent(
                assetURL = "https://example.com/image.jpg",
                assetLocation = "header",
                action = InteractionType.VIEW.stringValue
            ),
            createAssetEvent(
                assetURL = "https://example.com/image.jpg",
                assetLocation = "header",
                action = InteractionType.VIEW.stringValue
            ),
            createAssetEvent(
                assetURL = "https://example.com/image.jpg",
                assetLocation = "header",
                action = InteractionType.CLICK.stringValue
            )
        )
        
        val (collection, interactionType) = metricsBuilder.buildAssetMetrics(events)
        
        assertEquals("Should have 1 unique asset", 1, collection.count)
        assertEquals("Triggering interaction should be view (first action)", InteractionType.VIEW, interactionType)
        
        for (assetKey in collection.assetKeys) {
            val metrics = collection.metricsFor(assetKey)
            assertNotNull("Should have metrics for asset key", metrics)
            
            assertEquals("View count should be 2", 2.0, metrics!!.viewCount, 0.001)
            assertEquals("Click count should be 1", 1.0, metrics.clickCount, 0.001)
        }
    }
    
    @Test
    fun `buildAssetMetrics with multiple assets creates multiple entries`() {
        val events = listOf(
            createAssetEvent(
                assetURL = "https://example.com/image1.jpg",
                assetLocation = "header",
                action = InteractionType.VIEW.stringValue
            ),
            createAssetEvent(
                assetURL = "https://example.com/image2.jpg",
                assetLocation = "footer",
                action = InteractionType.VIEW.stringValue
            )
        )
        
        val (collection, _) = metricsBuilder.buildAssetMetrics(events)
        
        assertEquals("Should have 2 unique assets", 2, collection.count)
    }
    
    @Test
    fun `buildAssetMetrics with empty events returns empty collection`() {
        val events = emptyList<Event>()
        
        val (collection, _) = metricsBuilder.buildAssetMetrics(events)
        
        assertTrue("Collection should be empty", collection.isEmpty)
    }
    
    // Experience Metrics Tests
    
    @Test
    fun `buildExperienceMetrics with single view event returns correct counts`() {
        val events = listOf(
            createExperienceEvent(
                experienceId = "exp-123",
                experienceLocation = "home-page",
                action = InteractionType.VIEW.stringValue
            )
        )
        
        val (collection, interactionType) = metricsBuilder.buildExperienceMetrics(events)
        
        assertFalse("Collection should not be empty", collection.isEmpty)
        assertEquals("Triggering interaction should be view", InteractionType.VIEW, interactionType)
        
        for (experienceKey in collection.experienceKeys) {
            val metrics = collection.metricsFor(experienceKey)
            assertNotNull("Should have metrics for experience key", metrics)
            
            assertEquals("View count should be 1", 1.0, metrics!!.viewCount, 0.001)
            assertEquals("Click count should be 0", 0.0, metrics.clickCount, 0.001)
            assertEquals("exp-123", metrics.experienceID)
        }
    }
    
    @Test
    fun `buildExperienceMetrics with multiple events aggregates counts`() {
        val events = listOf(
            createExperienceEvent(
                experienceId = "exp-123",
                experienceLocation = "home-page",
                action = InteractionType.VIEW.stringValue
            ),
            createExperienceEvent(
                experienceId = "exp-123",
                experienceLocation = "home-page",
                action = InteractionType.VIEW.stringValue
            ),
            createExperienceEvent(
                experienceId = "exp-123",
                experienceLocation = "home-page",
                action = InteractionType.CLICK.stringValue
            )
        )
        
        val (collection, _) = metricsBuilder.buildExperienceMetrics(events)
        
        assertEquals("Should have 1 unique experience", 1, collection.count)
        
        for (experienceKey in collection.experienceKeys) {
            val metrics = collection.metricsFor(experienceKey)
            assertNotNull("Should have metrics for experience key", metrics)
            
            assertEquals("View count should be 2", 2.0, metrics!!.viewCount, 0.001)
            assertEquals("Click count should be 1", 1.0, metrics.clickCount, 0.001)
        }
    }
    
    @Test
    fun `buildExperienceMetrics with registered definition includes attributed assets`() {
        // Register an experience definition
        val definition = ExperienceDefinition(
            experienceId = "exp-123",
            assets = listOf("https://example.com/asset1.jpg", "https://example.com/asset2.jpg"),
            texts = emptyList(),
            ctas = emptyList()
        )
        stateManager.registerExperienceDefinition(definition)
        
        val events = listOf(
            createExperienceEvent(
                experienceId = "exp-123",
                experienceLocation = "home-page",
                action = InteractionType.VIEW.stringValue
            )
        )
        
        val (collection, _) = metricsBuilder.buildExperienceMetrics(events)
        
        for (experienceKey in collection.experienceKeys) {
            val metrics = collection.metricsFor(experienceKey)
            assertNotNull("Should have metrics for experience key", metrics)
            
            assertEquals("Should include attributed assets from definition", 2, metrics!!.attributedAssets.size)
        }
    }
    
    // Helper Methods
    
    private fun createAssetEvent(
        assetURL: String,
        assetLocation: String,
        action: String
    ): Event {
        val data = mapOf(
            "assetURL" to assetURL,
            "assetLocation" to assetLocation,
            "action" to action
        )
        
        return Event.Builder(
            "Content Analytics Asset Event",
            EventType.GENERIC_TRACK,
            EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
    }
    
    private fun createExperienceEvent(
        experienceId: String,
        experienceLocation: String,
        action: String
    ): Event {
        val data = mapOf(
            "experienceId" to experienceId,
            "experienceLocation" to experienceLocation,
            "action" to action
        )
        
        return Event.Builder(
            "Content Analytics Experience Event",
            EventType.GENERIC_TRACK,
            EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
    }
}
