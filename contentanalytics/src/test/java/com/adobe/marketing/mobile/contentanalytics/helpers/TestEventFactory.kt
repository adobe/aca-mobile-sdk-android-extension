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

import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.contentanalytics.ContentAnalyticsConstants

/**
 * Factory for creating test events with consistent structure.
 * Reduces code duplication and ensures test data consistency.
 */
object TestEventFactory {
    
    /**
     * Create an asset tracking event.
     */
    fun createAssetEvent(
        url: String,
        location: String? = null,
        action: String = ContentAnalyticsConstants.ActionType.VIEW,
        extras: Map<String, Any>? = null
    ): Event {
        val data = mutableMapOf<String, Any>(
            "assetURL" to url,
            "action" to action
        )
        
        location?.let { data["assetLocation"] = it }
        extras?.let { data["assetExtras"] = it }
        
        return Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_ASSET,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
    }
    
    /**
     * Create an experience tracking event.
     */
    fun createExperienceEvent(
        experienceId: String,
        location: String? = null,
        action: String = ContentAnalyticsConstants.ActionType.VIEW,
        extras: Map<String, Any>? = null
    ): Event {
        val data = mutableMapOf<String, Any>(
            "experienceId" to experienceId,
            "action" to action
        )
        
        location?.let { data["experienceLocation"] = it }
        extras?.let { data["experienceExtras"] = it }
        
        return Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
    }
    
    /**
     * Create an experience registration event.
     */
    fun createExperienceRegistrationEvent(
        experienceId: String,
        assets: List<String> = emptyList(),
        texts: List<Map<String, Any>> = emptyList(),
        ctas: List<Map<String, Any>> = emptyList()
    ): Event {
        val experienceDefinition = mutableMapOf<String, Any>(
            "experienceId" to experienceId,
            "assets" to assets
        )
        
        if (texts.isNotEmpty()) {
            experienceDefinition["texts"] = texts
        }
        
        if (ctas.isNotEmpty()) {
            experienceDefinition["ctas"] = ctas
        }
        
        val data = mapOf(
            "experienceId" to experienceId,
            "experienceDefinition" to experienceDefinition
        )
        
        return Event.Builder(
            ContentAnalyticsConstants.EventNames.TRACK_EXPERIENCE,
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ).setEventData(data).build()
    }
    
    /**
     * Create a configuration event.
     */
    fun createConfigurationEvent(
        trackExperiences: Boolean = true,
        batchingEnabled: Boolean = false,
        maxBatchSize: Int = ContentAnalyticsConstants.Defaults.MAX_BATCH_SIZE,
        batchFlushInterval: Long = ContentAnalyticsConstants.Defaults.BATCH_FLUSH_INTERVAL,
        excludedAssetLocationsRegexp: String? = null,
        excludedAssetUrlsRegexp: String? = null,
        excludedExperienceLocationsRegexp: String? = null,
        datastreamId: String? = null,
        experienceCloudOrgId: String? = null
    ): Map<String, Any> {
        val config = mutableMapOf<String, Any>(
            "contentanalytics.trackExperiences" to trackExperiences,
            "contentanalytics.batchingEnabled" to batchingEnabled,
            "contentanalytics.maxBatchSize" to maxBatchSize,
            "contentanalytics.batchFlushInterval" to batchFlushInterval
        )
        
        excludedAssetLocationsRegexp?.let {
            config["contentanalytics.excludedAssetLocationsRegexp"] = it
        }
        
        excludedAssetUrlsRegexp?.let {
            config["contentanalytics.excludedAssetUrlsRegexp"] = it
        }
        
        excludedExperienceLocationsRegexp?.let {
            config["contentanalytics.excludedExperienceLocationsRegexp"] = it
        }
        
        datastreamId?.let {
            config["contentanalytics.configId"] = it
        }
        
        
        experienceCloudOrgId?.let {
            config["experienceCloud.org"] = it
        }
        
        return config
    }
    
    /**
     * Create a consent update event.
     */
    fun createConsentEvent(optedIn: Boolean = true): Event {
        val data = mapOf(
            "consents" to mapOf(
                "collect" to mapOf(
                    "val" to if (optedIn) "y" else "n"
                )
            )
        )
        
        return Event.Builder(
            "Consent Update",
            "com.adobe.eventType.edgeConsent",
            "com.adobe.eventSource.updateConsent"
        ).setEventData(data).build()
    }
}

