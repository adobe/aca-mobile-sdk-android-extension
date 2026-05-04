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

import com.adobe.marketing.mobile.services.Log
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

internal data class ContentAnalyticsConfiguration(
    val trackExperiences: Boolean = ContentAnalyticsConstants.Defaults.TRACK_EXPERIENCES,
    
    val excludedAssetLocationsRegexp: String? = null,
    val excludedAssetUrlsRegexp: String? = null,
    val excludedExperienceLocationsRegexp: String? = null,
    /** When true, do not collect assets that belong to excluded experiences (default: false) */
    val excludeAssetsFromUntrackedExperience: Boolean = false,
    
    val experienceCloudOrgId: String? = null,
    val datastreamId: String? = null,
    val edgeEnvironment: String? = null,
    val edgeDomain: String? = null,
    val region: String? = null,
    
    val featurizationMaxRetries: Int = ContentAnalyticsConstants.Defaults.FEATURIZATION_MAX_RETRIES,
    val featurizationRetryDelay: Long = ContentAnalyticsConstants.Defaults.FEATURIZATION_RETRY_DELAY,
    
    val batchingEnabled: Boolean = ContentAnalyticsConstants.Defaults.BATCHING_ENABLED,
    val maxBatchSize: Int = ContentAnalyticsConstants.Defaults.DEFAULT_BATCH_SIZE,
    val batchFlushInterval: Long = ContentAnalyticsConstants.Defaults.BATCH_FLUSH_INTERVAL,
    val maxWaitTimeMs: Long = (ContentAnalyticsConstants.Defaults.BATCH_FLUSH_INTERVAL * ContentAnalyticsConstants.Defaults.MAX_WAIT_TIME_MULTIPLIER).toLong(),
    
    val debugLogging: Boolean = false
) {
    private val compiledAssetLocationRegex: Pattern? by lazy {
        compileRegex(excludedAssetLocationsRegexp)
    }
    
    private val compiledAssetUrlRegex: Pattern? by lazy {
        compileRegex(excludedAssetUrlsRegexp)
    }
    
    private val compiledExperienceLocationRegex: Pattern? by lazy {
        compileRegex(excludedExperienceLocationsRegexp)
    }
    
    fun shouldExcludeUrl(url: String): Boolean {
        return compiledAssetUrlRegex?.matcher(url)?.find() == true
    }
    
    fun shouldExcludeAsset(location: String?): Boolean {
        location ?: return false
        // Check regex pattern
        return compiledAssetLocationRegex?.matcher(location)?.find() == true
    }
    
    fun shouldExcludeExperience(location: String?): Boolean {
        location ?: return false
        // Check regex pattern
        return compiledExperienceLocationRegex?.matcher(location)?.find() == true
    }
    
    private fun compileRegex(pattern: String?): Pattern? {
        if (pattern.isNullOrEmpty()) return null
        
        return try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
        } catch (e: PatternSyntaxException) {
            Log.warning(
                ContentAnalyticsConstants.LOG_TAG,
                ContentAnalyticsConstants.LOG_TAG,
                "Invalid regex pattern: $pattern - ${e.message}"
            )
            null
        }
    }
    
    fun getFeaturizationBaseUrl(): String? {
        if (edgeDomain.isNullOrEmpty()) {
            Log.debug(
                ContentAnalyticsConstants.LOG_TAG,
                "ContentAnalyticsConfiguration",
                "Cannot construct featurization URL - Edge domain not configured"
            )
            return null
        }
        
        val resolvedRegion = region ?: extractRegion(edgeDomain)
        
        val source = when {
            region != null -> "explicit config"
            edgeDomain.contains("edge-") || edgeDomain.contains("adobedc.net") -> "parsed from domain"
            else -> "default fallback"
        }
        
        Log.debug(
            ContentAnalyticsConstants.LOG_TAG,
            "ContentAnalyticsConfiguration",
            "Featurization URL | Domain: $edgeDomain | Region: $resolvedRegion | Source: $source"
        )
        
        // Ensure https:// prefix
        val baseUrl = if (edgeDomain.startsWith("http")) edgeDomain else "https://$edgeDomain"
        val trimmedUrl = baseUrl.trim('/')
        
        return "$trimmedUrl/aca/$resolvedRegion"
    }
    
    private fun extractRegion(domain: String): String {
        val lowercasedDomain = domain.lowercase()
        
        return when {
            lowercasedDomain.contains("edge-eu") || lowercasedDomain.contains("irl1") -> "irl1"
            lowercasedDomain.contains("edge-au") || lowercasedDomain.contains("aus3") -> "aus3"
            lowercasedDomain.contains("edge-jp") || lowercasedDomain.contains("jpn3") -> "jpn3"
            lowercasedDomain.contains("edge-in") || lowercasedDomain.contains("ind1") -> "ind1"
            lowercasedDomain.contains("edge-sg") || lowercasedDomain.contains("sgp3") -> "sgp3"
            lowercasedDomain.contains("or2") -> "or2"
            lowercasedDomain.contains("va6") -> "va6"
            else -> "va7"
        }
    }
    
    companion object {
        fun fromEventData(data: Map<String, Any?>): ContentAnalyticsConfiguration {
            return ContentAnalyticsConfiguration(
                trackExperiences = data[ContentAnalyticsConstants.ConfigurationKeys.TRACK_EXPERIENCES] as? Boolean
                    ?: ContentAnalyticsConstants.Defaults.TRACK_EXPERIENCES,
                
                excludedAssetLocationsRegexp = data[ContentAnalyticsConstants.ConfigurationKeys.EXCLUDED_ASSET_LOCATIONS_REGEXP] as? String,
                excludedAssetUrlsRegexp = data[ContentAnalyticsConstants.ConfigurationKeys.EXCLUDED_ASSET_URLS_REGEXP] as? String,
                excludedExperienceLocationsRegexp = data[ContentAnalyticsConstants.ConfigurationKeys.EXCLUDED_EXPERIENCE_LOCATIONS_REGEXP] as? String,
                excludeAssetsFromUntrackedExperience = data[ContentAnalyticsConstants.ConfigurationKeys.EXCLUDE_ASSETS_FROM_UNTRACKED_EXPERIENCE] as? Boolean
                    ?: false,
                
                experienceCloudOrgId = (data[ContentAnalyticsConstants.ConfigurationKeys.EXPERIENCE_CLOUD_ORG_ID] 
                    ?: data[ContentAnalyticsConstants.ConfigurationKeys.EXPERIENCE_CLOUD_ORG]) as? String,
                datastreamId = data[ContentAnalyticsConstants.ConfigurationKeys.DATASTREAM_ID] as? String,
                edgeEnvironment = data[ContentAnalyticsConstants.ConfigurationKeys.EDGE_ENVIRONMENT] as? String,
                edgeDomain = data[ContentAnalyticsConstants.ConfigurationKeys.EDGE_DOMAIN] as? String,
                region = data[ContentAnalyticsConstants.ConfigurationKeys.REGION] as? String,
                
                batchingEnabled = data[ContentAnalyticsConstants.ConfigurationKeys.BATCHING_ENABLED] as? Boolean
                    ?: ContentAnalyticsConstants.Defaults.BATCHING_ENABLED,
                
                maxBatchSize = (data[ContentAnalyticsConstants.ConfigurationKeys.MAX_BATCH_SIZE_KEY] as? Number)?.toInt()
                    ?: ContentAnalyticsConstants.Defaults.DEFAULT_BATCH_SIZE,
                
                batchFlushInterval = (data[ContentAnalyticsConstants.ConfigurationKeys.BATCH_FLUSH_INTERVAL] as? Number)?.toLong()
                    ?: ContentAnalyticsConstants.Defaults.BATCH_FLUSH_INTERVAL
            )
        }
    }
}

