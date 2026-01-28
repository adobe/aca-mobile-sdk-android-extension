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
 * Extension functions and helpers for Event parsing
 */


/**
 * Check if event is an asset tracking event
 */
val Event.isAssetEvent: Boolean
    get() = name.contains("Asset", ignoreCase = true)

/**
 * Check if event is an experience tracking event
 */
val Event.isExperienceEvent: Boolean
    get() = name.contains("Experience", ignoreCase = true)


/**
 * Get asset URL from event data
 */
val Event.assetURL: String?
    get() = eventData?.get(ContentAnalyticsConstants.EventDataKeys.ASSET_URL) as? String

/**
 * Get asset location from event data
 */
val Event.assetLocation: String?
    get() = eventData?.get(ContentAnalyticsConstants.EventDataKeys.ASSET_LOCATION) as? String

/**
 * Get asset action from event data
 */
val Event.assetAction: String?
    get() = eventData?.get(ContentAnalyticsConstants.EventDataKeys.ASSET_ACTION) as? String

/**
 * Get asset extras from event data
 */
val Event.assetExtras: Map<String, Any>?
    get() = eventData?.get(ContentAnalyticsConstants.EventDataKeys.ASSET_EXTRAS) as? Map<String, Any>


/**
 * Get experience ID from event data
 */
val Event.experienceId: String?
    get() = eventData?.get(ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ID) as? String

/**
 * Get experience location from event data
 */
val Event.experienceLocation: String?
    get() = eventData?.get(ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_LOCATION) as? String

/**
 * Get experience action from event data
 */
val Event.experienceAction: String?
    get() = eventData?.get(ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ACTION) as? String

/**
 * Get experience definition from event data
 */
val Event.experienceDefinition: ExperienceDefinition?
    get() {
        val defMap = eventData?.get(ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_DEFINITION) as? Map<*, *>
        return defMap?.let { ExperienceDefinition.fromMap(it as Map<String, Any?>) }
    }

/**
 * Get experience extras from event data
 */
val Event.experienceExtras: Map<String, Any>?
    get() = eventData?.get(ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_EXTRAS) as? Map<String, Any>


/**
 * Generate a unique key for asset events (for batching by location)
 */
val Event.assetKey: String?
    get() {
        val url = assetURL ?: return null
        val location = assetLocation ?: "no-location"
        return "$url|$location"
    }

/**
 * Generate a unique key for experience events (for batching by location)
 */
val Event.experienceKey: String?
    get() {
        val id = experienceId ?: return null
        val location = experienceLocation ?: "no-location"
        return "$id|$location"
    }


/**
 * Check if action is a definition (experience registration)
 */
fun String.isDefinitionAction(): Boolean {
    return this == InteractionType.DEFINITION.stringValue
}

/**
 * Check if action is a view
 */
fun String.isViewAction(): Boolean {
    return this == ContentAnalyticsConstants.ActionType.VIEW
}

/**
 * Check if action is a click
 */
fun String.isClickAction(): Boolean {
    return this == ContentAnalyticsConstants.ActionType.CLICK
}

