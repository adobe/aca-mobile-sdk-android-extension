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
import com.adobe.marketing.mobile.MobileCore

/**
 * Public API for Content Analytics tracking
 */
object ContentAnalytics {
    
    /**
     * Extension version
     */
    const val EXTENSION_VERSION = ContentAnalyticsConstants.EXTENSION_VERSION
    
    /**
     * Extension class for registration with MobileCore.registerExtensions()
     * 
     * Example:
     * ```
     * MobileCore.registerExtensions(
     *     listOf(ContentAnalytics.EXTENSION),
     *     null
     * )
     * ```
     */
    @JvmField
    val EXTENSION: Class<out com.adobe.marketing.mobile.Extension> = ContentAnalyticsExtension::class.java
    
    /**
     * @param assetLocation Location context (e.g., "homepage", "pdp")
     * @param additionalData Custom metadata for this interaction
     */
    @JvmStatic
    @JvmOverloads
    fun trackAsset(
        assetURL: String,
        interactionType: InteractionType = InteractionType.VIEW,
        assetLocation: String? = null,
        additionalData: Map<String, Any>? = null
    ) {
        val eventData = mutableMapOf<String, Any>(
            ContentAnalyticsConstants.EventDataKeys.ASSET_URL to assetURL,
            ContentAnalyticsConstants.EventDataKeys.ASSET_ACTION to interactionType.stringValue
        )
        
        assetLocation?.let {
            eventData[ContentAnalyticsConstants.EventDataKeys.ASSET_LOCATION] = it
        }
        
        additionalData?.let {
            eventData[ContentAnalyticsConstants.EventDataKeys.ASSET_EXTRAS] = it
        }
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(eventData).build()
        
        MobileCore.dispatchEvent(event)
    }
    
    @JvmStatic
    @JvmOverloads
    fun trackAssetView(
        assetURL: String,
        assetLocation: String? = null,
        additionalData: Map<String, Any>? = null
    ) {
        trackAsset(assetURL, InteractionType.VIEW, assetLocation, additionalData)
    }
    
    @JvmStatic
    @JvmOverloads
    fun trackAssetClick(
        assetURL: String,
        assetLocation: String? = null,
        additionalData: Map<String, Any>? = null
    ) {
        trackAsset(assetURL, InteractionType.CLICK, assetLocation, additionalData)
    }
    
    /**
     * Registers an experience and returns a content-based ID for tracking.
     * 
     * The returned experienceId is deterministic (same content = same ID).
     * Use experienceLocation in trackExperienceView/Click for location-specific analytics.
     * 
     * @return Generated experience ID in format "mobile-{hash}"
     * 
     * Example:
     * ```kotlin
     * val expId = ContentAnalytics.registerExperience(
     *     assets = listOf(ContentItem("https://example.com/hero.jpg", mapOf())),
     *     texts = listOf(
     *         ContentItem("Product Title", mapOf("role" to "headline")),
     *         ContentItem("$999", mapOf("role" to "price"))
     *     ),
     *     ctas = listOf(ContentItem("Buy Now", mapOf("enabled" to true)))
     * )
     * // Track at specific locations
     * ContentAnalytics.trackExperienceView(expId, "products/detail")
     * ContentAnalytics.trackExperienceView(expId, "homepage")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun registerExperience(
        assets: List<ContentItem>,
        texts: List<ContentItem>,
        ctas: List<ContentItem>? = null
    ): String {
        // Generate experienceId from content hash
        val experienceId = ContentAnalyticsUtilities.generateExperienceId(
            assets = assets,
            texts = texts,
            ctas = ctas
        )
        
        // Build event data
        val eventData = mutableMapOf<String, Any>(
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ID to experienceId,
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ACTION to InteractionType.DEFINITION.stringValue
        )
        
        eventData[ContentAnalyticsConstants.EventDataKeys.ASSETS] = assets.map { it.value }
        eventData[ContentAnalyticsConstants.EventDataKeys.TEXTS] = texts.map { it.toMap() }
        
        ctas?.let {
            eventData[ContentAnalyticsConstants.EventDataKeys.CTAS] = it.map { item -> item.toMap() }
        }
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(eventData).build()
        
        MobileCore.dispatchEvent(event)
        
        return experienceId
    }
    
    @JvmStatic
    @JvmOverloads
    fun trackExperienceView(
        experienceId: String,
        experienceLocation: String? = null,
        additionalData: Map<String, Any>? = null
    ) {
        val eventData = mutableMapOf<String, Any>(
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ID to experienceId,
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ACTION to InteractionType.VIEW.stringValue
        )
        
        experienceLocation?.let {
            eventData[ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_LOCATION] = it
        }
        
        additionalData?.let {
            eventData[ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_EXTRAS] = it
        }
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(eventData).build()
        
        MobileCore.dispatchEvent(event)
    }
    
    @JvmStatic
    @JvmOverloads
    fun trackExperienceClick(
        experienceId: String,
        experienceLocation: String? = null,
        additionalData: Map<String, Any>? = null
    ) {
        val eventData = mutableMapOf<String, Any>(
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ID to experienceId,
            ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_ACTION to InteractionType.CLICK.stringValue
        )
        
        experienceLocation?.let {
            eventData[ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_LOCATION] = it
        }
        
        additionalData?.let {
            eventData[ContentAnalyticsConstants.EventDataKeys.EXPERIENCE_EXTRAS] = it
        }
        
        val event = Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(eventData).build()
        
        MobileCore.dispatchEvent(event)
    }
    
    /**
     * Tracks multiple assets with the same interaction type.
     * For different types per asset, call trackAsset individually.
     */
    @JvmStatic
    @JvmOverloads
    fun trackAssetCollection(
        assetURLs: List<String>,
        interactionType: InteractionType = InteractionType.VIEW,
        assetLocation: String? = null
    ) {
        assetURLs.forEach { assetURL ->
            trackAsset(assetURL, interactionType, assetLocation)
        }
    }
}

