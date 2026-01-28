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

import com.adobe.marketing.mobile.ExtensionApi
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.ServiceProvider

/**
 * Factory for creating ContentAnalytics services and dependencies
 * 
 * Matches iOS ContentAnalyticsFactory pattern:
 * - Dependency injection
 * - Proper initialization order
 * - Component wiring
 * - Testability
 */
internal class ContentAnalyticsFactory(
    private val extensionApi: ExtensionApi,
    private val state: ContentAnalyticsStateManager
) {
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
    }
    
    private var batchCoordinator: BatchCoordinator? = null
    private var privacyValidator: ContentAnalyticsPrivacyValidator? = null
    
    
    /**
     * Create the complete orchestrator with all dependencies
     */
    fun createContentAnalyticsOrchestrator(): ContentAnalyticsOrchestrator {
        val eventDispatcher = createEventDispatcher()
        privacyValidator = createPrivacyValidator()
        val xdmEventBuilder = createXDMEventBuilder()
        batchCoordinator = createBatchCoordinator()
        val featurizationHitQueue = createFeaturizationHitQueue()
        
        val orchestrator = ContentAnalyticsOrchestrator(
            state = state,
            eventDispatcher = eventDispatcher,
            privacyValidator = privacyValidator!!,
            xdmEventBuilder = xdmEventBuilder,
            batchCoordinator = batchCoordinator,
            featurizationHitQueue = featurizationHitQueue
        )
        
        batchCoordinator?.setCallbacks(
            assetCallback = { events ->
                orchestrator.handleAssetBatchFlush(events)
            },
            experienceCallback = { events ->
                orchestrator.handleExperienceBatchFlush(events)
            }
        )
        
        Log.debug(TAG, TAG, "ContentAnalyticsOrchestrator created")
        
        return orchestrator
    }
    
    /**
     * Get the batch coordinator instance
     */
    fun getBatchCoordinator(): BatchCoordinator? {
        return batchCoordinator
    }
    
    /**
     * Get the privacy validator instance (for cache updates)
     * Returns the cached instance or creates a new one if needed (defensive)
     */
    fun getPrivacyValidator(): ContentAnalyticsPrivacyValidator {
        if (privacyValidator == null) {
            Log.warning(TAG, TAG, "Privacy validator accessed before initialization - creating new instance")
            privacyValidator = createPrivacyValidator()
        }
        return privacyValidator!!
    }
    
    
    private fun createEventDispatcher(): EventDispatcher {
        return EdgeEventDispatcher(extensionApi)
    }
    
    private fun createXDMEventBuilder(): XDMEventBuilder {
        return XDMEventBuilder
    }
    
    private fun createPrivacyValidator(): ContentAnalyticsPrivacyValidator {
        return ContentAnalyticsPrivacyValidator(state, extensionApi)
    }
    
    private fun createBatchCoordinator(): BatchCoordinator? {
        // Get DataQueues from ServiceProvider (like iOS)
        val dataQueueService = ServiceProvider.getInstance().dataQueueService
        
        val assetDataQueue = dataQueueService.getDataQueue(
            ContentAnalyticsConstants.ASSET_BATCH_QUEUE_NAME
        )
        
        if (assetDataQueue == null) {
            Log.warning(TAG, TAG, "Failed to create data queue for asset batches")
            return null
        }
        
        val experienceDataQueue = dataQueueService.getDataQueue(
            ContentAnalyticsConstants.EXPERIENCE_BATCH_QUEUE_NAME
        )
        
        if (experienceDataQueue == null) {
            Log.warning(TAG, TAG, "Failed to create data queue for experience batches")
            return null
        }
        
        Log.debug(TAG, TAG, "Creating BatchCoordinator")
        
        val batchCoordinator = BatchCoordinator(
            assetDataQueue = assetDataQueue,
            experienceDataQueue = experienceDataQueue,
            state = state
        )
        
        Log.debug(TAG, TAG, "BatchCoordinator created")
        
        return batchCoordinator
    }
    
    /**
     * Create featurization hit queue (optional, for ML service)
     * Matches iOS createFeaturizationHitQueue()
     */
    fun createFeaturizationHitQueue(): PersistentHitQueue? {
        val dataQueueService = ServiceProvider.getInstance().dataQueueService
        
        val dataQueue = dataQueueService.getDataQueue(
            ContentAnalyticsConstants.FEATURIZATION_QUEUE_NAME
        )
        
        if (dataQueue == null) {
            Log.warning(TAG, TAG, "Failed to create data queue for featurization")
            return null
        }
        
        // Get configuration
        val config = state.configuration
        if (config == null) {
            Log.warning(TAG, TAG, "No configuration available for featurization service")
            return null
        }
        
        // Get featurization base URL (JAG Gateway routing via Edge domain + region)
        val serviceUrl = config.getFeaturizationBaseUrl()
        if (serviceUrl == null) {
            Log.warning(TAG, TAG, "❌ Cannot determine featurization URL - Edge domain not configured")
            return null
        }
        
        Log.debug(TAG, TAG, "Using featurization base URL: $serviceUrl")
        
        // Create featurization service for the processor
        val featurizationService = ExperienceFeaturizationService(
            baseUrl = serviceUrl,
            networkService = ServiceProvider.getInstance().networkService
        )
        
        // Create hit processor that handles featurization requests
        val hitProcessor = FeaturizationHitProcessor(featurizationService = featurizationService)
        
        // Create persistent hit queue with processor
        val hitQueue = PersistentHitQueue(dataQueue, hitProcessor)
        hitQueue.beginProcessing()
        
        Log.debug(TAG, TAG, "✅ Featurization hit queue created via JAG Gateway")
        
        return hitQueue
    }
}

