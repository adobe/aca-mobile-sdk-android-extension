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

import com.adobe.marketing.mobile.contentanalytics.ContentAnalyticsConfiguration
import com.adobe.marketing.mobile.contentanalytics.ContentItem
import com.adobe.marketing.mobile.contentanalytics.ExperienceDefinition

/**
 * Builder pattern for creating test data structures.
 * Provides consistent, reusable test data across test suite.
 */
internal object TestDataBuilder {
    
    /**
     * Create a list of test ContentItems.
     */
    fun buildContentItems(
        count: Int,
        prefix: String = "test",
        contentType: String = "image",
        styles: Map<String, Any> = emptyMap()
    ): List<ContentItem> {
        return (0 until count).map { index ->
            val value = when (contentType) {
                "image" -> "https://example.com/$prefix$index.jpg"
                "video" -> "https://example.com/$prefix$index.mp4"
                "text" -> "Test $prefix $index"
                "cta" -> "Click $prefix $index"
                else -> "$prefix$index"
            }
            ContentItem(value, styles)
        }
    }
    
    /**
     * Create a single ContentItem.
     */
    fun buildContentItem(
        value: String,
        styles: Map<String, Any> = emptyMap()
    ): ContentItem {
        return ContentItem(value, styles)
    }
    
    /**
     * Create a test ExperienceDefinition.
     */
    fun buildExperienceDefinition(
        experienceId: String = "test-experience-id",
        assetCount: Int = 2,
        textCount: Int = 1,
        ctaCount: Int = 1,
        sentToFeaturization: Boolean = false
    ): ExperienceDefinition {
        val assetURLs = (0 until assetCount).map { "https://example.com/asset$it.jpg" }
        val texts = buildContentItems(count = textCount, prefix = "text", contentType = "text")
        val ctas = if (ctaCount > 0) buildContentItems(count = ctaCount, prefix = "cta", contentType = "cta") else null
        
        return ExperienceDefinition(
            experienceId = experienceId,
            assets = assetURLs,
            texts = texts,
            ctas = ctas,
            sentToFeaturization = sentToFeaturization
        )
    }
    
    /**
     * Create a test ContentAnalyticsConfiguration.
     */
    fun buildConfiguration(
        trackExperiences: Boolean = true,
        batchingEnabled: Boolean = false,
        maxBatchSize: Int = 5,
        batchFlushInterval: Long = 5000,
        excludedAssetLocationsRegexp: String? = null,
        excludedAssetUrlsRegexp: String? = null,
        excludedExperienceLocationsRegexp: String? = null,
        datastreamId: String? = null,
        experienceCloudOrgId: String? = "test-org-id@AdobeOrg",
        edgeDomain: String? = "edge.adobedc.net",
        edgeEnvironment: String? = null,
        region: String? = null
    ): ContentAnalyticsConfiguration {
        return ContentAnalyticsConfiguration(
            trackExperiences = trackExperiences,
            batchingEnabled = batchingEnabled,
            maxBatchSize = maxBatchSize,
            batchFlushInterval = batchFlushInterval,
            excludedAssetLocationsRegexp = excludedAssetLocationsRegexp,
            excludedAssetUrlsRegexp = excludedAssetUrlsRegexp,
            excludedExperienceLocationsRegexp = excludedExperienceLocationsRegexp,
            datastreamId = datastreamId,
            experienceCloudOrgId = experienceCloudOrgId,
            edgeDomain = edgeDomain,
            edgeEnvironment = edgeEnvironment,
            region = region
        )
    }
    
    /**
     * Create test asset URLs.
     */
    fun buildAssetURLs(count: Int, prefix: String = "asset"): List<String> {
        return (0 until count).map { "https://example.com/$prefix$it.jpg" }
    }
    
    /**
     * Create test asset URL.
     */
    fun buildAssetURL(name: String = "test-asset"): String {
        return "https://example.com/$name.jpg"
    }
    
    /**
     * Create test experience ID.
     */
    fun buildExperienceId(name: String = "test-experience"): String {
        return "exp-$name-${System.currentTimeMillis()}"
    }
    
    /**
     * Create test extras map.
     */
    fun buildExtras(vararg pairs: Pair<String, Any>): Map<String, Any> {
        return mapOf(*pairs)
    }
    
    /**
     * Create test XDM event data.
     */
    fun buildXDMEventData(
        eventType: String = "content.contentEngagement",
        experienceContent: Map<String, Any>
    ): Map<String, Any> {
        return mapOf(
            "eventType" to eventType,
            "experienceContent" to experienceContent
        )
    }
    
    /**
     * Create test asset XDM data.
     */
    fun buildAssetXDMData(
        assetURL: String,
        assetLocation: String? = null,
        viewCount: Double = 1.0,
        clickCount: Double = 0.0,
        extras: Map<String, Any>? = null
    ): Map<String, Any> {
        val assetData = mutableMapOf<String, Any>(
            "assetID" to assetURL,
            "assetViews" to mapOf("value" to viewCount),
            "assetClicks" to mapOf("value" to clickCount)
        )
        
        assetLocation?.let { assetData["assetSource"] = it }
        extras?.let { assetData["assetExtras"] = it }
        
        return assetData
    }
    
    /**
     * Create test experience XDM data.
     */
    fun buildExperienceXDMData(
        experienceId: String,
        experienceLocation: String? = null,
        viewCount: Double = 1.0,
        clickCount: Double = 0.0,
        extras: Map<String, Any>? = null,
        assets: List<Map<String, Any>>? = null
    ): Map<String, Any> {
        val experienceData = mutableMapOf<String, Any>(
            "experienceID" to experienceId,
            "experienceChannel" to "mobile",
            "experienceSource" to (experienceLocation ?: "mobile-app"),
            "experienceViews" to mapOf("value" to viewCount),
            "experienceClicks" to mapOf("value" to clickCount)
        )
        
        extras?.let { experienceData["experienceExtras"] = it }
        
        return experienceData
    }
}

