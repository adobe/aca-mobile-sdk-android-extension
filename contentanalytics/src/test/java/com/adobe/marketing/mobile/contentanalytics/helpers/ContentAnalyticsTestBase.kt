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
import com.adobe.marketing.mobile.EventType
import com.adobe.marketing.mobile.contentanalytics.ContentAnalyticsOrchestrator
import com.adobe.marketing.mobile.contentanalytics.ContentAnalyticsStateManager
import com.adobe.marketing.mobile.contentanalytics.EventDispatcher
import com.adobe.marketing.mobile.contentanalytics.PrivacyValidator
import com.adobe.marketing.mobile.contentanalytics.XDMEventBuilder
import org.junit.Before

/**
 * Base class for Content Analytics tests providing common setup.
 */
internal abstract class ContentAnalyticsTestBase {
    
    protected lateinit var dispatchedEvents: MutableList<Event>
    protected lateinit var eventDispatcher: EventDispatcher
    protected lateinit var privacyValidator: PrivacyValidator
    protected lateinit var state: ContentAnalyticsStateManager
    protected lateinit var orchestrator: ContentAnalyticsOrchestrator
    
    @Before
    open fun setUp() {
        dispatchedEvents = mutableListOf()
        
        eventDispatcher = ContentAnalyticsMocks.createMockEventDispatcher(dispatchedEvents)
        privacyValidator = ContentAnalyticsMocks.createMockPrivacyValidator(allowDataCollection = true)
        state = ContentAnalyticsStateManager()
        
        orchestrator = ContentAnalyticsOrchestrator(
            state = state,
            eventDispatcher = eventDispatcher,
            privacyValidator = privacyValidator,
            xdmEventBuilder = XDMEventBuilder,
            batchCoordinator = null
        )
    }
    
    /**
     * Clear all dispatched events.
     */
    protected fun clearDispatchedEvents() {
        dispatchedEvents.clear()
    }
    
    /**
     * Get all Edge events that were dispatched.
     */
    protected fun getEdgeEvents(): List<Event> {
        return dispatchedEvents.filter { it.type == EventType.EDGE }
    }
    
    /**
     * Get the first Edge event that was dispatched.
     */
    protected fun getFirstEdgeEvent(): Event? {
        return getEdgeEvents().firstOrNull()
    }
    
    /**
     * Get the last Edge event that was dispatched.
     */
    protected fun getLastEdgeEvent(): Event? {
        return getEdgeEvents().lastOrNull()
    }
    
    /**
     * Assert that a specific number of Edge events were dispatched.
     */
    protected fun assertEdgeEventCount(expected: Int, message: String = "Edge event count should match") {
        val actual = getEdgeEvents().size
        org.junit.Assert.assertEquals(message, expected, actual)
    }
    
    /**
     * Wait for a condition to be met (for async operations).
     */
    protected fun waitFor(timeoutMs: Long = 1000, condition: () -> Boolean): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) {
                return true
            }
            Thread.sleep(10)
        }
        return false
    }
}

