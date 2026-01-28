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
    
    val experienceCloudOrgId: String? = null,
    val datastreamId: String? = null,
    val edgeEnvironment: String? = null,
    val edgeDomain: String? = null,
    val region: String? = null,  // Org's home region (e.g., "va7", "irl1", "aus5", "jpn4") - for custom domains
    
    val featurizationMaxRetries: Int = ContentAnalyticsConstants.Defaults.FEATURIZATION_MAX_RETRIES,
    val featurizationRetryDelay: Long = ContentAnalyticsConstants.Defaults.FEATURIZATION_RETRY_DELAY,
    
    val batchingEnabled: Boolean = ContentAnalyticsConstants.Defaults.BATCHING_ENABLED,
    val maxBatchSize: Int = ContentAnalyticsConstants.Defaults.MAX_BATCH_SIZE,
    val batchFlushInterval: Long = ContentAnalyticsConstants.Defaults.BATCH_FLUSH_INTERVAL,
    val maxWaitTime: Double = (ContentAnalyticsConstants.Defaults.BATCH_FLUSH_INTERVAL * 2.5) / 1000.0,
    
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
            android.util.Log.w(
                ContentAnalyticsConstants.LOG_TAG,
                "Invalid regex pattern: $pattern - ${e.message}"
            )
            null
        }
    }
    
    /**
     * Get the effective base URL for featurization service with JAG Gateway routing
     * Returns the base URL to use for featurization requests, including region.
     * 
     * JAG Gateway URL format: https://{edgeDomain}/aca/{region}
     * 
     * Region priority:
     * 1. Explicit contentanalytics.region configuration (for custom domains)
     * 2. Parse from edge.domain (for standard Adobe domains)
     * 3. Default to "va7" (US Virginia)
     * 
     * @return The base URL string, or null if not configured
     */
    fun getFeaturizationBaseUrl(): String? {
        // Use Edge domain with /aca/{region} path (JAG Gateway routing)
        if (edgeDomain.isNullOrEmpty()) {
            Log.debug(
                ContentAnalyticsConstants.LOG_TAG,
                "ContentAnalyticsConfiguration",
                "Cannot construct featurization URL - Edge domain not configured"
            )
            return null
        }
        
        // Priority 1: Explicit region configuration (for custom domains)
        // Priority 2: Parse from edge.domain (for standard domains)
        // Priority 3: Default to US
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
    
    /**
     * Extract region from Edge domain
     * 
     * @param domain The Edge Network domain (e.g., "edge.adobedc.net", "edge-eu.adobedc.net")
     * @return The region code (e.g., "va7" for US, "irl1" for EU, "aus5" for Australia)
     * 
     * Region mapping:
     * - Default (no region in domain) → "va7" (US Virginia)
     * - "edge-eu.adobedc.net" → "irl1" (EU Ireland)
     * - "edge-au.adobedc.net" → "aus5" (Australia)
     * - "edge-jp.adobedc.net" → "jpn4" (Japan)
     */
    /**
     * Extract region from Edge domain (using Adobe Edge Network region codes)
     * Reference: https://experienceleague.adobe.com/en/docs/experience-platform/landing/edge-and-hub-comparison
     */
    private fun extractRegion(domain: String): String {
        val lowercasedDomain = domain.lowercase()
        
        return when {
            lowercasedDomain.contains("edge-eu") || lowercasedDomain.contains("irl1") -> "irl1"  // Ireland (Europe)
            lowercasedDomain.contains("edge-au") || lowercasedDomain.contains("aus3") -> "aus3"  // Australia
            lowercasedDomain.contains("edge-jp") || lowercasedDomain.contains("jpn3") -> "jpn3"  // Japan
            lowercasedDomain.contains("edge-in") || lowercasedDomain.contains("ind1") -> "ind1"  // India
            lowercasedDomain.contains("edge-sg") || lowercasedDomain.contains("sgp3") -> "sgp3"  // Singapore
            lowercasedDomain.contains("or2") -> "or2"   // Oregon (US West)
            lowercasedDomain.contains("va6") -> "va6"   // Virginia (US East, Edge)
            else -> "va7"  // Default: Virginia (US East, Platform Hub)
        }
    }
    
    companion object {
        /**
         * Parse configuration from event data
         */
        fun fromEventData(data: Map<String, Any?>): ContentAnalyticsConfiguration {
            return ContentAnalyticsConfiguration(
                trackExperiences = data[ContentAnalyticsConstants.ConfigurationKeys.TRACK_EXPERIENCES] as? Boolean
                    ?: ContentAnalyticsConstants.Defaults.TRACK_EXPERIENCES,
                
                excludedAssetLocationsRegexp = data[ContentAnalyticsConstants.ConfigurationKeys.EXCLUDED_ASSET_LOCATIONS_REGEXP] as? String,
                excludedAssetUrlsRegexp = data[ContentAnalyticsConstants.ConfigurationKeys.EXCLUDED_ASSET_URLS_REGEXP] as? String,
                excludedExperienceLocationsRegexp = data[ContentAnalyticsConstants.ConfigurationKeys.EXCLUDED_EXPERIENCE_LOCATIONS_REGEXP] as? String,
                
                // Read from both contentanalytics-prefixed keys and standard keys (for compatibility)
                // Try contentanalytics.* first, fallback to standard keys from Configuration shared state
                experienceCloudOrgId = (data[ContentAnalyticsConstants.ConfigurationKeys.EXPERIENCE_CLOUD_ORG_ID] 
                    ?: data[ContentAnalyticsConstants.ConfigurationKeys.EXPERIENCE_CLOUD_ORG]) as? String,
                // Use contentanalytics.configId for the datastream (aligns with edge.configId naming)
                datastreamId = data[ContentAnalyticsConstants.ConfigurationKeys.DATASTREAM_ID] as? String,
                edgeEnvironment = data[ContentAnalyticsConstants.ConfigurationKeys.EDGE_ENVIRONMENT] as? String,
                edgeDomain = data[ContentAnalyticsConstants.ConfigurationKeys.EDGE_DOMAIN] as? String,
                region = data[ContentAnalyticsConstants.ConfigurationKeys.REGION] as? String,
                
                batchingEnabled = data[ContentAnalyticsConstants.ConfigurationKeys.BATCHING_ENABLED] as? Boolean
                    ?: ContentAnalyticsConstants.Defaults.BATCHING_ENABLED,
                
                maxBatchSize = (data[ContentAnalyticsConstants.ConfigurationKeys.MAX_BATCH_SIZE] as? Number)?.toInt()
                    ?: ContentAnalyticsConstants.Defaults.MAX_BATCH_SIZE,
                
                batchFlushInterval = (data[ContentAnalyticsConstants.ConfigurationKeys.BATCH_FLUSH_INTERVAL] as? Number)?.toLong()
                    ?: ContentAnalyticsConstants.Defaults.BATCH_FLUSH_INTERVAL
            )
        }
    }
}

