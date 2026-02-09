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

/**
 * Interface for validating Content Analytics events before processing
 */
internal interface EventValidating {
    /**
     * Validates an asset tracking event
     * @param event The asset event to validate
     * @return ValidationResult indicating success or failure with error details
     */
    fun validateAssetEvent(event: Event): ValidationResult

    /**
     * Validates an experience tracking event
     * @param event The experience event to validate
     * @return ValidationResult indicating success or failure with error details
     */
    fun validateExperienceEvent(event: Event): ValidationResult

    /**
     * Validates common processing conditions (configuration state)
     * @return An error message if conditions are not met, null otherwise
     */
    fun validateProcessingConditions(): String?

    /**
     * Returns true if experience tracking is enabled in configuration
     */
    fun isExperienceTrackingEnabled(): Boolean
}
