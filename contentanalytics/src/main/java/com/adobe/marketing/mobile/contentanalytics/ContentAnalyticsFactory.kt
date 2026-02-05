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
 * Factory for creating ContentAnalytics services and dependencies.
 * Handles dependency injection, initialization order, and component wiring.
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
        // Create helper components
        val eventDispatcher = createEventDispatcher()
        privacyValidator = createPrivacyValidator()
        val xdmEventBuilder = createXDMEventBuilder()
        batchCoordinator = createBatchCoordinator()
        val featurizationCoordinator = createFeaturizationCoordinator()
        
        // Create processing components
        val eventValidator = createEventValidator()
        val eventExclusionFilter = createEventExclusionFilter()
        val metricsBuilder = createMetricsBuilder()
        
        val assetEventProcessor = AssetEventProcessor(
            state = state,
            eventDispatcher = eventDispatcher,
            xdmEventBuilder = xdmEventBuilder,
            metricsBuilder = metricsBuilder
        )
        
        val experienceEventProcessor = ExperienceEventProcessor(
            state = state,
            eventDispatcher = eventDispatcher,
            xdmEventBuilder = xdmEventBuilder,
            metricsBuilder = metricsBuilder,
            featurizationCoordinator = featurizationCoordinator
        )
        
        // Create orchestrator with all dependencies
        val orchestrator = ContentAnalyticsOrchestrator(
            state = state,
            eventValidator = eventValidator,
            eventExclusionFilter = eventExclusionFilter,
            assetEventProcessor = assetEventProcessor,
            experienceEventProcessor = experienceEventProcessor,
            featurizationCoordinator = featurizationCoordinator,
            batchCoordinator = batchCoordinator
        )
        
        batchCoordinator?.setCallbacks(
            assetCallback = { events ->
                orchestrator.handleAssetBatchFlush(events)
            },
            experienceCallback = { events ->
                orchestrator.handleExperienceBatchFlush(events)
            }
        )
        
        Log.debug(TAG, TAG, "Orchestrator ready")
        
        return orchestrator
    }
    
    private fun createFeaturizationCoordinator(): FeaturizationCoordinator {
        return FeaturizationCoordinator(state, privacyValidator!!)
    }
    
    /**
     * Get the batch coordinator instance
     */
    fun getBatchCoordinator(): BatchCoordinator? {
        return batchCoordinator
    }
    
    fun getPrivacyValidator(): ContentAnalyticsPrivacyValidator {
        if (privacyValidator == null) {
            Log.warning(TAG, TAG, "Privacy validator accessed before initialization - creating new instance")
            privacyValidator = createPrivacyValidator()
        }
        return privacyValidator!!
    }
    
    
    // Processing Component Creation
    
    /**
     * Creates an EventValidator for validating incoming events.
     */
    fun createEventValidator(): EventValidating {
        return EventValidator(state)
    }
    
    /**
     * Creates an EventExclusionFilter for filtering events based on configuration.
     */
    fun createEventExclusionFilter(): EventExclusionFiltering {
        return EventExclusionFilter(state)
    }
    
    /**
     * Creates a MetricsBuilder for aggregating event metrics.
     */
    fun createMetricsBuilder(): MetricsBuilding {
        return MetricsBuilder(state)
    }
    
    /**
     * Creates an AssetEventProcessor for processing asset events.
     */
    fun createAssetEventProcessor(
        eventDispatcher: EventDispatcher,
        xdmEventBuilder: XDMEventBuilder,
        metricsBuilder: MetricsBuilding
    ): AssetEventProcessing {
        return AssetEventProcessor(
            state = state,
            eventDispatcher = eventDispatcher,
            xdmEventBuilder = xdmEventBuilder,
            metricsBuilder = metricsBuilder
        )
    }
    
    /**
     * Creates an ExperienceEventProcessor for processing experience events.
     */
    fun createExperienceEventProcessor(
        eventDispatcher: EventDispatcher,
        xdmEventBuilder: XDMEventBuilder,
        metricsBuilder: MetricsBuilding,
        featurizationCoordinator: FeaturizationCoordinator
    ): ExperienceEventProcessing {
        return ExperienceEventProcessor(
            state = state,
            eventDispatcher = eventDispatcher,
            xdmEventBuilder = xdmEventBuilder,
            metricsBuilder = metricsBuilder,
            featurizationCoordinator = featurizationCoordinator
        )
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
     * Create featurization hit queue for ML service requests.
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
        
        val config = state.configuration
        if (config == null) {
            Log.warning(TAG, TAG, "No configuration available for featurization service")
            return null
        }
        
        val serviceUrl = config.getFeaturizationBaseUrl()
        if (serviceUrl == null) {
            Log.warning(TAG, TAG, "Cannot determine featurization URL - Edge domain not configured")
            return null
        }
        
        Log.debug(TAG, TAG, "Featurization URL: $serviceUrl")
        
        val featurizationService = ExperienceFeaturizationService(
            baseUrl = serviceUrl,
            networkService = ServiceProvider.getInstance().networkService
        )
        
        val hitProcessor = FeaturizationHitProcessor(featurizationService = featurizationService)
        val hitQueue = PersistentHitQueue(dataQueue, hitProcessor)
        hitQueue.beginProcessing()
        
        Log.debug(TAG, TAG, "Featurization hit queue created")
        
        return hitQueue
    }
}

