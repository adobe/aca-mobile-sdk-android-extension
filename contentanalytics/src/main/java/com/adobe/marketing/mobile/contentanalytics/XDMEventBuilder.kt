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

/**
 * Builds XDM-compliant event payloads for content analytics tracking.
 */
internal object XDMEventBuilder {
    
    /**
     * Build XDM payload for asset interaction events.
     */
    fun buildAssetInteractionXDM(
        assetKeys: List<String>,
        metrics: Map<String, Map<String, Any>>
    ): Map<String, Any> {
        val assetData = createAssetDataForXDM(assetKeys, metrics)
        
        val experienceContent = mapOf("assets" to assetData)
        val xdmEvent = createBaseXDMEvent(experienceContent)
        
        
        return xdmEvent
    }
    
    /**
     * Build XDM payload for experience interaction events with asset attribution.
     */
    fun buildExperienceInteractionXDM(
        experienceId: String,
        metrics: Map<String, Any>,
        assetURLs: List<String>,
        experienceLocation: String?
    ): Map<String, Any> {
        val source = experienceLocation?.takeIf { it.isNotEmpty() } ?: "mobile-app"
        
        val interactionData = mutableMapOf<String, Any>(
            "experienceID" to experienceId,
            "experienceChannel" to "mobile",
            "experienceSource" to source
        )
        
        metrics["viewCount"]?.let {
            interactionData["experienceViews"] = mapOf("value" to it)
        }
        
        metrics["clickCount"]?.let {
            interactionData["experienceClicks"] = mapOf("value" to it)
        }
        
        (metrics["experienceExtras"] as? Map<String, Any>)?.let {
            interactionData["experienceExtras"] = it
        }
        
        // Build asset attribution array (zero metrics since assets are tracked separately)
        val assetsData = assetURLs.map { assetURL ->
            val assetData = mutableMapOf<String, Any>(
                "assetID" to assetURL
            )
            
            if (!experienceLocation.isNullOrEmpty()) {
                assetData["assetSource"] = experienceLocation
            } else if (experienceId.isNotEmpty()) {
                assetData["assetSource"] = experienceId
            }
            
            assetData["assetViews"] = mapOf("value" to 0)
            assetData["assetClicks"] = mapOf("value" to 0)
            
            assetData
        }
        
        val experienceContent = mutableMapOf<String, Any>("experience" to interactionData)
        
        if (assetsData.isNotEmpty()) {
            experienceContent["assets"] = assetsData
        }
        
        return createBaseXDMEvent(experienceContent)
    }
    private fun createBaseXDMEvent(experienceContent: Map<String, Any>): Map<String, Any> {
        return mapOf(
            "eventType" to ContentAnalyticsConstants.EventType.XDM_CONTENT_ENGAGEMENT,
            "experienceContent" to experienceContent
        )
    }
    
    private fun createAssetDataForXDM(
        assetKeys: List<String>,
        metrics: Map<String, Map<String, Any>>
    ): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        
        for (assetKey in assetKeys) {
            // Get metrics for this asset (with fallback to empty metrics)
            val assetMetrics = metrics[assetKey] ?: emptyMap()
            
            // Create asset data directly from asset key and metrics
            val assetData = createAssetDataFromKeyAndMetrics(assetKey, assetMetrics)
            
            result.add(assetData)
        }
        
        return result
    }
    
    private fun createAssetDataFromKeyAndMetrics(
        assetKey: String,
        metrics: Map<String, Any>
    ): Map<String, Any> {
        val assetURL = metrics["assetURL"] as? String ?: ""
        val assetLocation = metrics["assetLocation"] as? String ?: ""
        
        val assetData = mutableMapOf<String, Any>(
            "assetID" to assetURL,
            "assetViews" to mapOf("value" to (metrics["viewCount"] ?: 0)),
            "assetClicks" to mapOf("value" to (metrics["clickCount"] ?: 0))
        )
        
        if (assetLocation.isNotEmpty()) {
            assetData["assetSource"] = assetLocation
        }
        
        (metrics["assetExtras"] as? Map<String, Any>)?.let {
            assetData["assetExtras"] = it
        }
        
        return assetData
    }
}
