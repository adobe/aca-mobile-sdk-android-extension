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
 * Represents aggregated metrics for a single asset
 *
 * @property assetURL The URL of the asset (identifier)
 * @property assetLocation The location where the asset appears (dimension)
 * @property viewCount Number of view interactions
 * @property clickCount Number of click interactions
 * @property assetExtras Optional additional metadata for the asset
 */
internal data class AssetMetrics(
    val assetURL: String,
    val assetLocation: String,
    val viewCount: Double,
    val clickCount: Double,
    val assetExtras: Map<String, Any>? = null
) {
    /**
     * Converts this typed model to an event data map
     * @return A map representation suitable for XDM or event dispatch
     */
    fun toEventData(): Map<String, Any> {
        val data = mutableMapOf<String, Any>(
            "assetURL" to assetURL,
            "assetLocation" to assetLocation,
            "viewCount" to viewCount,
            "clickCount" to clickCount
        )
        
        assetExtras?.let {
            data[AssetTrackingEventPayload.OptionalFields.ASSET_EXTRAS] = it
        }
        
        return data
    }
}

/**
 * Represents aggregated metrics for a collection of assets
 *
 * @property metrics Map of asset key to metrics
 */
internal data class AssetMetricsCollection(
    private val metrics: Map<String, AssetMetrics>
) {
    /**
     * All asset keys in this collection
     */
    val assetKeys: List<String>
        get() = metrics.keys.toList()
    
    /**
     * Get metrics for a specific asset key
     */
    fun metricsFor(key: String): AssetMetrics? = metrics[key]
    
    /**
     * Converts the entire collection to event data format
     * @return A map of asset keys to their metrics
     */
    fun toEventData(): Map<String, Map<String, Any>> {
        return metrics.mapValues { it.value.toEventData() }
    }
    
    /**
     * Number of assets in this collection
     */
    val count: Int
        get() = metrics.size
    
    /**
     * Check if collection is empty
     */
    val isEmpty: Boolean
        get() = metrics.isEmpty()
}

