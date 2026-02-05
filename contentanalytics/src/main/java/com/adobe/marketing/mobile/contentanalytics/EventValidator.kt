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
import com.adobe.marketing.mobile.services.Log

/**
 * Result of event validation
 */
internal data class ValidationResult(
    val isValid: Boolean,
    val error: String? = null
)

/**
 * Validates event fields (URL, action type, required keys).
 * Processing conditions are checked separately by the orchestrator.
 */
internal class EventValidator(
    private val state: ContentAnalyticsStateManager
) : EventValidating {

    companion object {
        private const val TAG = "EventValidator"
    }

    override fun validateAssetEvent(event: Event): ValidationResult {
        // Validate required fields
        val assetURL = event.assetURL
        val action = event.assetAction
        val assetKey = event.assetKey

        if (assetURL == null || action == null || assetKey == null) {
            return ValidationResult(false, "Missing required asset fields")
        }

        // Validate action is view or click
        if (!action.isViewAction() && !action.isClickAction()) {
            Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "Asset event has invalid action: $action")
            return ValidationResult(false, "Invalid action type: $action")
        }

        return ValidationResult(true)
    }

    override fun validateExperienceEvent(event: Event): ValidationResult {
        // Validate required fields
        val experienceId = event.experienceId
        val action = event.experienceAction

        if (experienceId == null || action == null) {
            return ValidationResult(false, "Missing required experience fields")
        }

        // Validate action is definition, view, or click
        if (!action.isDefinitionAction() && !action.isViewAction() && !action.isClickAction()) {
            Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "Experience event has invalid action: $action")
            return ValidationResult(false, "Invalid action type: $action")
        }

        return ValidationResult(true)
    }

    override fun validateProcessingConditions(): String? {
        // Check configuration state
        if (state.configuration == null) {
            return "Configuration not available"
        }

        return null
    }

    override fun isExperienceTrackingEnabled(): Boolean {
        return state.configuration?.trackExperiences == true
    }
}
