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

import com.adobe.marketing.mobile.services.DataEntity
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.util.DataReader
import java.util.*

internal class FeaturizationCoordinator(
    private val state: ContentAnalyticsStateManager,
    private val privacyValidator: PrivacyValidator
) {
    
    @Volatile
    private var hitQueue: PersistentHitQueue? = null
    private val queueInitLock = Any()
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
    }
    
    // MARK: - Public Interface
    
    /** Returns true if the hit queue has been initialized */
    val hasQueue: Boolean
        get() = hitQueue != null
    
    /** Sets up the hit queue for featurization requests */
    fun initializeQueue(queue: PersistentHitQueue?) {
        synchronized(queueInitLock) {
            if (hitQueue != null) {
                Log.trace(TAG, TAG, "Featurization queue already initialized - skipping")
                return
            }
            
            hitQueue = queue
            
            if (hitQueue != null) {
                Log.debug(TAG, TAG, "✅ Featurization queue initialized successfully")
            } else {
                Log.debug(TAG, TAG, "Featurization queue not yet available (waiting for valid configuration)")
            }
        }
    }
    
    /**
     * Queue an experience for featurization
     * @return true if successfully queued, false otherwise
     */
    fun queueExperience(experienceId: String): Boolean {
        // Validate prerequisites
        val prerequisites = validatePrerequisites(experienceId) ?: return false
        
        // Build content payload
        val content = buildContent(
            definition = prerequisites.definition,
            config = prerequisites.config,
            imsOrg = prerequisites.imsOrg,
            experienceId = experienceId
        ) ?: return false
        
        return queueHit(
            experienceId = experienceId,
            imsOrg = prerequisites.imsOrg,
            content = content
        )
    }
    
    // MARK: - Private Helpers
    
    /** Checks consent, config, and experience definition before featurization */
    private fun validatePrerequisites(experienceId: String): FeaturizationPrerequisites? {
        if (!privacyValidator.isDataCollectionAllowed()) {
            Log.debug(TAG, TAG, "❌ Skipping featurization - consent denied")
            return null
        }
        
        Log.debug(TAG, TAG, "✅ Privacy check passed - proceeding with featurization")
        
        val config = state.configuration
        if (config == null) {
            Log.debug(TAG, TAG, "❌ Skipping featurization - No configuration available")
            return null
        }
        
        val serviceUrl = config.getFeaturizationBaseUrl()
        if (serviceUrl.isNullOrEmpty()) {
            Log.debug(TAG, TAG, "❌ Skipping featurization - Cannot determine URL | edgeDomain: ${config.edgeDomain}, region: ${config.region}")
            return null
        }
        
        val imsOrg = config.experienceCloudOrgId
        if (imsOrg.isNullOrEmpty()) {
            Log.debug(TAG, TAG, "❌ Skipping featurization - IMS Org not configured | experienceCloud.org: ${config.experienceCloudOrgId}")
            return null
        }
        
        Log.debug(TAG, TAG, "✅ Configuration valid | URL: $serviceUrl | Org: $imsOrg")
        
        val definition = state.getExperienceDefinition(experienceId)
        if (definition == null) {
            Log.warning(TAG, TAG, "No definition found for experience: $experienceId - registerExperience() must be called first")
            return null
        }
        
        Log.trace(TAG, TAG, "✅ Definition found | ID: $experienceId | Assets: ${definition.assets.size}")
        
        return FeaturizationPrerequisites(config, imsOrg, definition)
    }
    
    /** Builds the content payload from an experience definition */
    private fun buildContent(
        definition: ExperienceDefinition,
        config: ContentAnalyticsConfiguration,
        imsOrg: String,
        experienceId: String
    ): ExperienceContent? {
        // Only include "value" for images, no empty style objects
        val imagesData = definition.assets.map { assetURL ->
            mapOf("value" to assetURL)
        }
        
        val textsData = definition.texts.map { it.toMap() }
        val ctasData = if (definition.ctas != null && definition.ctas.isNotEmpty()) {
            definition.ctas.map { it.toMap() }
        } else null
        
        val contentData = ContentData(
            images = imagesData,
            texts = textsData,
            ctas = ctasData
        )
        
        val datastreamId = config.datastreamId
        if (datastreamId.isNullOrEmpty()) {
            Log.error(TAG, TAG, "❌ Cannot send to featurization - datastreamId not configured")
            return null
        }
        
        return ExperienceContent(
            content = contentData,
            orgId = imsOrg,
            datastreamId = datastreamId,
            experienceId = experienceId
        )
    }
    
    /**
     * Encodes and queues featurization hit
     */
    private fun queueHit(
        experienceId: String,
        imsOrg: String,
        content: ExperienceContent
    ): Boolean {
        val hit = FeaturizationHit(
            experienceId = experienceId,
            imsOrg = imsOrg,
            content = content,
            timestamp = Date().time,
            attemptCount = 0
        )
        
        // Serialize hit to JSON
        val hitJson = try {
            hit.toJson()
        } catch (e: Exception) {
            Log.error(TAG, TAG, "❌ Failed to encode featurization hit | ExperienceID: $experienceId")
            return false
        }
        
        Log.debug(TAG, TAG, "✅ Hit encoded | Size: ${hitJson.length} bytes")
        
        val dataEntity = DataEntity(hitJson)
        
        val queue = hitQueue
        if (queue == null) {
            Log.error(TAG, TAG, "❌ Featurization queue is nil - cannot queue hit | ID: $experienceId")
            return false
        }
        
        return if (queue.queue(dataEntity)) {
            Log.debug(TAG, TAG, "✅ Experience queued for featurization | ID: $experienceId")
            true
        } else {
            Log.error(TAG, TAG, "❌ Failed to queue experience | ID: $experienceId")
            false
        }
    }
}

/**
 * Container for validated featurization prerequisites
 */
private data class FeaturizationPrerequisites(
    val config: ContentAnalyticsConfiguration,
    val imsOrg: String,
    val definition: ExperienceDefinition
)
