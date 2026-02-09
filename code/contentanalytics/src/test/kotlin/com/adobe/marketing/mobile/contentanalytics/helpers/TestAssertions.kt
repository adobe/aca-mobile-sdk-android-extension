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

package com.adobe.marketing.mobile.contentanalytics.helpers

import com.adobe.marketing.mobile.Event
import org.junit.Assert.*

/**
 * Custom assertions for Content Analytics tests.
 */
object TestAssertions {
    
    /**
     * Assert that an event contains specific XDM data.
     */
    fun assertEventHasXDM(event: Event, expectedEventType: String) {
        val xdm = event.eventData?.get("xdm") as? Map<*, *>
        assertNotNull("Event should have XDM data", xdm)
        
        val eventType = xdm?.get("eventType") as? String
        assertEquals("Event type should match", expectedEventType, eventType)
    }
    
    /**
     * Assert that an event has asset XDM data with specific properties.
     */
    fun assertEventHasAssetXDM(
        event: Event,
        expectedAssetURL: String,
        expectedLocation: String? = null,
        expectedViewCount: Double? = null,
        expectedClickCount: Double? = null
    ) {
        val xdm = event.eventData?.get("xdm") as? Map<*, *>
        assertNotNull("Event should have XDM data", xdm)
        
        val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
        assertNotNull("XDM should have experienceContent", experienceContent)
        
        val assets = experienceContent?.get("assets") as? List<Map<String, Any>>
        assertNotNull("experienceContent should have assets array", assets)
        assertTrue("assets array should not be empty", assets?.isNotEmpty() == true)
        
        val asset = assets?.firstOrNull()
        assertNotNull("Should have at least one asset", asset)
        
        assertEquals("assetID should match", expectedAssetURL, asset?.get("assetID"))
        
        expectedLocation?.let {
            assertEquals("assetSource should match", it, asset?.get("assetSource"))
        }
        
        expectedViewCount?.let {
            val views = asset?.get("assetViews") as? Map<*, *>
            val value = (views?.get("value") as? Number)?.toDouble()
            assertEquals("assetViews value should match", it, value ?: 0.0, 0.001)
        }
        
        expectedClickCount?.let {
            val clicks = asset?.get("assetClicks") as? Map<*, *>
            val value = (clicks?.get("value") as? Number)?.toDouble()
            assertEquals("assetClicks value should match", it, value ?: 0.0, 0.001)
        }
    }
    
    /**
     * Assert that an event has experience XDM data with specific properties.
     */
    fun assertEventHasExperienceXDM(
        event: Event,
        expectedExperienceId: String,
        expectedLocation: String? = null,
        expectedViewCount: Double? = null,
        expectedClickCount: Double? = null
    ) {
        val xdm = event.eventData?.get("xdm") as? Map<*, *>
        assertNotNull("Event should have XDM data", xdm)
        
        val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
        assertNotNull("XDM should have experienceContent", experienceContent)
        
        val experience = experienceContent?.get("experience") as? Map<*, *>
        assertNotNull("experienceContent should have experience object", experience)
        
        assertEquals("experienceID should match", expectedExperienceId, experience?.get("experienceID"))
        
        expectedLocation?.let {
            assertEquals("experienceSource should match", it, experience?.get("experienceSource"))
        }
        
        expectedViewCount?.let {
            val views = experience?.get("experienceViews") as? Map<*, *>
            val value = (views?.get("value") as? Number)?.toDouble()
            assertEquals("experienceViews value should match", it, value ?: 0.0, 0.001)
        }
        
        expectedClickCount?.let {
            val clicks = experience?.get("experienceClicks") as? Map<*, *>
            val value = (clicks?.get("value") as? Number)?.toDouble()
            assertEquals("experienceClicks value should match", it, value ?: 0.0, 0.001)
        }
    }
    
    /**
     * Assert that an event has extras data.
     */
    fun assertEventHasExtras(
        event: Event,
        expectedExtras: Map<String, Any>,
        extrasPath: String = "assetExtras"
    ) {
        val xdm = event.eventData?.get("xdm") as? Map<*, *>
        assertNotNull("Event should have XDM data", xdm)
        
        val experienceContent = xdm?.get("experienceContent") as? Map<*, *>
        assertNotNull("XDM should have experienceContent", experienceContent)
        
        val container = if (extrasPath == "assetExtras") {
            (experienceContent?.get("assets") as? List<Map<String, Any>>)?.firstOrNull()
        } else {
            experienceContent?.get("experience") as? Map<*, *>
        }
        
        assertNotNull("Should have container for extras", container)
        
        val extras = container?.get(extrasPath) as? Map<String, Any>
        assertNotNull("Should have $extrasPath", extras)
        
        expectedExtras.forEach { (key, expectedValue) ->
            assertTrue("Extras should contain key: $key", extras?.containsKey(key) == true)
            assertEquals("Extras value for $key should match", expectedValue, extras?.get(key))
        }
    }
    
    /**
     * Assert that an event has datastream override configuration.
     */
    fun assertEventHasDatastreamOverride(event: Event, expectedDatastreamId: String) {
        val config = event.eventData?.get("config") as? Map<*, *>
        assertNotNull("Event should have config", config)
        
        val datastreamIdOverride = config?.get("datastreamIdOverride") as? String
        assertEquals("datastreamIdOverride should match", expectedDatastreamId, datastreamIdOverride)
    }
    
    /**
     * Assert that a list contains an event matching a predicate.
     */
    fun assertEventListContains(
        events: List<Event>,
        predicate: (Event) -> Boolean,
        message: String = "Event list should contain matching event"
    ) {
        assertTrue(message, events.any(predicate))
    }
    
    /**
     * Assert that a list does not contain an event matching a predicate.
     */
    fun assertEventListDoesNotContain(
        events: List<Event>,
        predicate: (Event) -> Boolean,
        message: String = "Event list should not contain matching event"
    ) {
        assertFalse(message, events.any(predicate))
    }
}

