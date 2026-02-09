/*
 Copyright 2026 Adobe. All rights reserved.
 This file is licensed to you under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under
 the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 OF ANY KIND, either express or implied. See the License for the specific language
 governing permissions and limitations under the License.
 */

package com.adobe.marketing.mobile.contentanalytics

/**
 * Represents aggregated metrics for a single experience
 *
 * @property experienceID The unique identifier for the experience
 * @property experienceSource The location or source where the experience appears (dimension)
 * @property viewCount Number of view interactions
 * @property clickCount Number of click interactions
 * @property experienceExtras Optional additional metadata for the experience
 * @property attributedAssets List of asset URLs attributed to this experience
 */
internal data class ExperienceMetrics(
    val experienceID: String,
    val experienceSource: String,
    val viewCount: Double,
    val clickCount: Double,
    val experienceExtras: Map<String, Any>? = null,
    val attributedAssets: List<String>
) {
    /**
     * Converts this typed model to an event data map
     * @return A map representation suitable for XDM or event dispatch
     */
    fun toEventData(): Map<String, Any> {
        val data = mutableMapOf<String, Any>(
            "experienceID" to experienceID,
            "experienceSource" to experienceSource,
            "viewCount" to viewCount,
            "clickCount" to clickCount,
            "attributedAssets" to attributedAssets
        )
        
        experienceExtras?.let {
            data[ExperienceTrackingEventPayload.OptionalFields.EXPERIENCE_EXTRAS] = it
        }
        
        return data
    }
}

/**
 * Represents aggregated metrics for a collection of experiences
 *
 * @property metrics Map of experience key to metrics
 */
internal data class ExperienceMetricsCollection(
    private val metrics: Map<String, ExperienceMetrics>
) {
    /**
     * All experience keys in this collection
     */
    val experienceKeys: List<String>
        get() = metrics.keys.toList()
    
    /**
     * Get metrics for a specific experience key
     */
    fun metricsFor(key: String): ExperienceMetrics? = metrics[key]
    
    /**
     * Converts the entire collection to event data format
     * @return A map of experience keys to their metrics
     */
    fun toEventData(): Map<String, Map<String, Any>> {
        return metrics.mapValues { it.value.toEventData() }
    }
    
    /**
     * Number of experiences in this collection
     */
    val count: Int
        get() = metrics.size
    
    /**
     * Check if collection is empty
     */
    val isEmpty: Boolean
        get() = metrics.isEmpty()
}

