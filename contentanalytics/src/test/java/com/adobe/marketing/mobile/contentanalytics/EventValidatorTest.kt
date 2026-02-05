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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for EventValidator - validates incoming asset and experience events
 */
class EventValidatorTest {
    
    private lateinit var stateManager: ContentAnalyticsStateManager
    private lateinit var validator: EventValidator
    
    @Before
    fun setUp() {
        stateManager = ContentAnalyticsStateManager()
        validator = EventValidator(stateManager)
        
        // Apply configuration
        val config = ContentAnalyticsConfiguration(trackExperiences = true)
        stateManager.updateConfiguration(config)
    }
    
    // Asset Validation Tests
    
    @Test
    fun `validateAssetEvent with valid event returns success`() {
        val event = createAssetEvent(
            assetURL = "https://example.com/image.jpg",
            assetKey = "asset-key-1",
            action = InteractionType.VIEW.stringValue
        )
        
        val result = validator.validateAssetEvent(event)
        
        assertTrue("Validation should succeed", result.isValid)
        assertNull("Error should be null", result.error)
    }
    
    @Test
    fun `validateAssetEvent with missing assetURL returns failure`() {
        val event = createAssetEvent(
            assetURL = null,
            assetKey = "asset-key-1",
            action = InteractionType.VIEW.stringValue
        )
        
        val result = validator.validateAssetEvent(event)
        
        assertFalse("Validation should fail", result.isValid)
        assertNotNull("Error should not be null", result.error)
    }
    
    @Test
    fun `validateAssetEvent with missing action returns failure`() {
        val event = createAssetEvent(
            assetURL = "https://example.com/image.jpg",
            assetKey = "asset-key-1",
            action = null
        )
        
        val result = validator.validateAssetEvent(event)
        
        assertFalse("Validation should fail for missing action", result.isValid)
    }
    
    @Test
    fun `validateAssetEvent with invalid action returns failure`() {
        val event = createAssetEvent(
            assetURL = "https://example.com/image.jpg",
            assetKey = "asset-key-1",
            action = InteractionType.DEFINITION.stringValue // Invalid for asset events
        )
        
        val result = validator.validateAssetEvent(event)
        
        assertFalse("Validation should fail for invalid action", result.isValid)
    }
    
    // Experience Validation Tests
    
    @Test
    fun `validateExperienceEvent with valid event returns success`() {
        val event = createExperienceEvent(
            experienceId = "exp-123",
            experienceKey = "exp-key-1",
            action = InteractionType.VIEW.stringValue
        )
        
        val result = validator.validateExperienceEvent(event)
        
        assertTrue("Validation should succeed", result.isValid)
        assertNull("Error should be null", result.error)
    }
    
    @Test
    fun `validateExperienceEvent with definition action returns success`() {
        val event = createExperienceEvent(
            experienceId = "exp-123",
            experienceKey = "exp-key-1",
            action = InteractionType.DEFINITION.stringValue
        )
        
        val result = validator.validateExperienceEvent(event)
        
        assertTrue("Validation should succeed - definition is valid for experience events", result.isValid)
    }
    
    @Test
    fun `validateExperienceEvent with missing experienceId returns failure`() {
        val event = createExperienceEvent(
            experienceId = null,
            experienceKey = "exp-key-1",
            action = InteractionType.VIEW.stringValue
        )
        
        val result = validator.validateExperienceEvent(event)
        
        assertFalse("Validation should fail for missing experienceId", result.isValid)
    }
    
    // Processing Conditions Tests
    
    @Test
    fun `validateProcessingConditions with valid configuration returns null`() {
        val error = validator.validateProcessingConditions()
        assertNull("Should return null when configuration is valid", error)
    }
    
    @Test
    fun `validateProcessingConditions without configuration returns error`() {
        val emptyStateManager = ContentAnalyticsStateManager()
        val validatorWithoutConfig = EventValidator(emptyStateManager)
        
        val error = validatorWithoutConfig.validateProcessingConditions()
        assertNotNull("Should return error when configuration is missing", error)
    }
    
    // Experience Tracking Enabled Tests
    
    @Test
    fun `isExperienceTrackingEnabled with trackExperiences true returns true`() {
        val config = ContentAnalyticsConfiguration(trackExperiences = true)
        stateManager.updateConfiguration(config)
        
        assertTrue(validator.isExperienceTrackingEnabled())
    }
    
    @Test
    fun `isExperienceTrackingEnabled with trackExperiences false returns false`() {
        val config = ContentAnalyticsConfiguration(trackExperiences = false)
        stateManager.updateConfiguration(config)
        
        assertFalse(validator.isExperienceTrackingEnabled())
    }
    
    // Helper Methods
    
    private fun createAssetEvent(
        assetURL: String?,
        assetKey: String?,
        action: String?
    ): Event {
        val data = mutableMapOf<String, Any?>()
        
        assetURL?.let { data["assetURL"] = it }
        action?.let { data["action"] = it }
        assetKey?.let { data["assetKey"] = it }
        
        return Event.Builder(
            "Content Analytics Asset Event",
            EventType.GENERIC_TRACK,
            EventSource.REQUEST_CONTENT
        ).setEventData(data.filterValues { it != null } as Map<String, Any>).build()
    }
    
    private fun createExperienceEvent(
        experienceId: String?,
        experienceKey: String?,
        action: String?
    ): Event {
        val data = mutableMapOf<String, Any?>()
        
        experienceId?.let { data["experienceId"] = it }
        action?.let { data["action"] = it }
        experienceKey?.let { data["experienceKey"] = it }
        
        return Event.Builder(
            "Content Analytics Experience Event",
            EventType.GENERIC_TRACK,
            EventSource.REQUEST_CONTENT
        ).setEventData(data.filterValues { it != null } as Map<String, Any>).build()
    }
}
