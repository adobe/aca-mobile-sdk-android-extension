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
import com.adobe.marketing.mobile.contentanalytics.EventDispatcher
import com.adobe.marketing.mobile.contentanalytics.PrivacyValidator

/**
 * Mock implementations for testing.
 */
internal object ContentAnalyticsMocks {
    
    /**
     * Create a mock EventDispatcher that captures events.
     */
    fun createMockEventDispatcher(
        dispatchedEvents: MutableList<Event>
    ): EventDispatcher {
        return object : EventDispatcher {
            override fun dispatch(event: Event) {
                dispatchedEvents.add(event)
            }
        }
    }
    
    /**
     * Create a mock PrivacyValidator with configurable consent.
     */
    fun createMockPrivacyValidator(
        allowDataCollection: Boolean = true
    ): PrivacyValidator {
        return object : PrivacyValidator {
            override fun isDataCollectionAllowed(): Boolean = allowDataCollection
        }
    }
    
    /**
     * Create a mock PrivacyValidator that changes consent dynamically.
     */
    fun createDynamicPrivacyValidator(): DynamicPrivacyValidator {
        return DynamicPrivacyValidator()
    }
    
    /**
     * Dynamic privacy validator for testing consent changes.
     */
    class DynamicPrivacyValidator : PrivacyValidator {
        var allowDataCollection: Boolean = true
        
        override fun isDataCollectionAllowed(): Boolean = allowDataCollection
        
        fun optIn() {
            allowDataCollection = true
        }
        
        fun optOut() {
            allowDataCollection = false
        }
    }
}

