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
import com.adobe.marketing.mobile.ExtensionApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Tests for EdgeEventDispatcher: event dispatch, payload preservation, and delegation to ExtensionApi.
 */
class EdgeEventDispatcherTest {
    
    @Mock
    private lateinit var mockExtensionApi: ExtensionApi
    
    private lateinit var dispatcher: EdgeEventDispatcher
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        dispatcher = EdgeEventDispatcher(mockExtensionApi)
    }
    
    // MARK: - Basic Dispatch Tests
    
    @Test
    fun `dispatch single event delegates to ExtensionApi`() {
        // Given
        val event = createTestEvent("Test Event")
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi, times(1)).dispatch(event)
    }
    
    @Test
    fun `dispatch multiple events delegates all to ExtensionApi`() {
        // Given
        val event1 = createTestEvent("Event 1")
        val event2 = createTestEvent("Event 2")
        val event3 = createTestEvent("Event 3")
        
        // When
        dispatcher.dispatch(event1)
        dispatcher.dispatch(event2)
        dispatcher.dispatch(event3)
        
        // Then
        verify(mockExtensionApi, times(1)).dispatch(event1)
        verify(mockExtensionApi, times(1)).dispatch(event2)
        verify(mockExtensionApi, times(1)).dispatch(event3)
        verify(mockExtensionApi, times(3)).dispatch(any())
    }
    
    @Test
    fun `dispatch same event multiple times delegates each time`() {
        // Given
        val event = createTestEvent("Test Event")
        
        // When
        dispatcher.dispatch(event)
        dispatcher.dispatch(event)
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi, times(3)).dispatch(event)
    }
    
    // MARK: - Event Preservation Tests
    
    @Test
    fun `dispatch preserves event name`() {
        // Given
        val expectedName = "Content Analytics Asset"
        val event = createTestEvent(expectedName)
        val eventCaptor = ArgumentCaptor.forClass(Event::class.java)
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi).dispatch(eventCaptor.capture())
        assertEquals("Should preserve event name", expectedName, eventCaptor.value.name)
    }
    
    @Test
    fun `dispatch preserves event type`() {
        // Given
        val expectedType = EventType.EDGE
        val event = Event.Builder("Test", expectedType, EventSource.REQUEST_CONTENT)
            .setEventData(mapOf("test" to "data"))
            .build()
        val eventCaptor = ArgumentCaptor.forClass(Event::class.java)
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi).dispatch(eventCaptor.capture())
        assertEquals("Should preserve event type", expectedType, eventCaptor.value.type)
    }
    
    @Test
    fun `dispatch preserves event source`() {
        // Given
        val expectedSource = EventSource.REQUEST_CONTENT
        val event = Event.Builder("Test", EventType.EDGE, expectedSource)
            .setEventData(mapOf("test" to "data"))
            .build()
        val eventCaptor = ArgumentCaptor.forClass(Event::class.java)
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi).dispatch(eventCaptor.capture())
        assertEquals("Should preserve event source", expectedSource, eventCaptor.value.source)
    }
    
    @Test
    fun `dispatch preserves event data`() {
        // Given
        val expectedData = mapOf(
            "xdm" to mapOf(
                "assetID" to "asset-123",
                "assetLocation" to "homepage"
            )
        )
        val event = Event.Builder("Test", EventType.EDGE, EventSource.REQUEST_CONTENT)
            .setEventData(expectedData)
            .build()
        val eventCaptor = ArgumentCaptor.forClass(Event::class.java)
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi).dispatch(eventCaptor.capture())
        assertEquals("Should preserve event data", expectedData, eventCaptor.value.eventData)
    }
    
    @Test
    fun `dispatch preserves event ID`() {
        // Given
        val event = createTestEvent("Test Event")
        val originalId = event.uniqueIdentifier
        val eventCaptor = ArgumentCaptor.forClass(Event::class.java)
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi).dispatch(eventCaptor.capture())
        assertEquals("Should preserve event ID", originalId, eventCaptor.value.uniqueIdentifier)
    }
    
    // MARK: - Edge Event Tests
    
    @Test
    fun `dispatch edge asset event preserves XDM structure`() {
        // Given
        val xdmData = mapOf(
            "assetID" to "asset-123",
            "assetLocation" to "homepage",
            "metrics" to mapOf("views" to 1, "clicks" to 0)
        )
        val eventData = mapOf("xdm" to xdmData)
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.CONTENT_ANALYTICS_ASSET,
            EventType.EDGE,
            EventSource.REQUEST_CONTENT
        ).setEventData(eventData).build()
        
        val eventCaptor = ArgumentCaptor.forClass(Event::class.java)
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi).dispatch(eventCaptor.capture())
        val capturedXdm = eventCaptor.value.eventData?.get("xdm") as? Map<*, *>
        assertNotNull("Should preserve XDM data", capturedXdm)
        assertEquals("Should preserve asset ID", "asset-123", capturedXdm?.get("assetID"))
        assertEquals("Should preserve asset location", "homepage", capturedXdm?.get("assetLocation"))
    }
    
    @Test
    fun `dispatch edge experience event preserves XDM structure`() {
        // Given
        val xdmData = mapOf(
            "experienceID" to "exp-456",
            "experienceSource" to "products/detail",
            "metrics" to mapOf("views" to 5, "clicks" to 2)
        )
        val eventData = mapOf("xdm" to xdmData)
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.CONTENT_ANALYTICS_EXPERIENCE,
            EventType.EDGE,
            EventSource.REQUEST_CONTENT
        ).setEventData(eventData).build()
        
        val eventCaptor = ArgumentCaptor.forClass(Event::class.java)
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi).dispatch(eventCaptor.capture())
        val capturedXdm = eventCaptor.value.eventData?.get("xdm") as? Map<*, *>
        assertNotNull("Should preserve XDM data", capturedXdm)
        assertEquals("Should preserve experience ID", "exp-456", capturedXdm?.get("experienceID"))
        assertEquals("Should preserve experience location", "products/detail", capturedXdm?.get("experienceSource"))
    }
    
    // MARK: - Null and Empty Tests
    
    @Test
    fun `dispatch event with null data delegates correctly`() {
        // Given
        val event = Event.Builder("Test", EventType.EDGE, EventSource.REQUEST_CONTENT)
            .setEventData(null)
            .build()
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi, times(1)).dispatch(event)
    }
    
    @Test
    fun `dispatch event with empty data delegates correctly`() {
        // Given
        val event = Event.Builder("Test", EventType.EDGE, EventSource.REQUEST_CONTENT)
            .setEventData(emptyMap())
            .build()
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi, times(1)).dispatch(event)
    }
    
    // MARK: - Complex Data Tests
    
    @Test
    fun `dispatch preserves nested data structures`() {
        // Given
        val complexData = mapOf(
            "xdm" to mapOf(
                "level1" to mapOf(
                    "level2" to mapOf(
                        "level3" to listOf("value1", "value2", "value3")
                    )
                )
            ),
            "config" to mapOf(
                "datastreamIdOverride" to "override-123"
            )
        )
        val event = Event.Builder("Test", EventType.EDGE, EventSource.REQUEST_CONTENT)
            .setEventData(complexData)
            .build()
        val eventCaptor = ArgumentCaptor.forClass(Event::class.java)
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi).dispatch(eventCaptor.capture())
        assertEquals("Should preserve complex nested data", complexData, eventCaptor.value.eventData)
    }
    
    @Test
    fun `dispatch preserves list data in XDM`() {
        // Given
        val xdmData = mapOf(
            "assets" to listOf("https://example.com/1.jpg", "https://example.com/2.jpg"),
            "metrics" to mapOf("views" to 10)
        )
        val eventData = mapOf("xdm" to xdmData)
        val event = Event.Builder("Test", EventType.EDGE, EventSource.REQUEST_CONTENT)
            .setEventData(eventData)
            .build()
        val eventCaptor = ArgumentCaptor.forClass(Event::class.java)
        
        // When
        dispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi).dispatch(eventCaptor.capture())
        val capturedXdm = eventCaptor.value.eventData?.get("xdm") as? Map<*, *>
        val capturedAssets = capturedXdm?.get("assets") as? List<*>
        assertEquals("Should preserve list size", 2, capturedAssets?.size)
    }
    
    // MARK: - Interface Tests
    
    @Test
    fun `EdgeEventDispatcher implements EventDispatcher interface`() {
        // Given/When/Then
        assertTrue("EdgeEventDispatcher should implement EventDispatcher",
                  dispatcher is EventDispatcher)
    }
    
    @Test
    fun `dispatcher can be used through EventDispatcher interface`() {
        // Given
        val interfaceDispatcher: EventDispatcher = dispatcher
        val event = createTestEvent("Test")
        
        // When
        interfaceDispatcher.dispatch(event)
        
        // Then
        verify(mockExtensionApi, times(1)).dispatch(event)
    }
    
    // MARK: - Helper Methods
    
    private fun createTestEvent(name: String): Event {
        return Event.Builder(name, EventType.EDGE, EventSource.REQUEST_CONTENT)
            .setEventData(mapOf("test" to "data"))
            .build()
    }
}

