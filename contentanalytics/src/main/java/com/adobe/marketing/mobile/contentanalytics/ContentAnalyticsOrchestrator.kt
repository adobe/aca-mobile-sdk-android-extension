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
import com.adobe.marketing.mobile.services.Log

/**
 * Orchestrates Content Analytics event processing by coordinating between specialized components.
 * This class acts as a thin coordinator, delegating validation, filtering, metrics building,
 * and event processing to dedicated components.
 */
internal class ContentAnalyticsOrchestrator(
    private val state: ContentAnalyticsStateManager,
    private val eventValidator: EventValidating,
    private val eventExclusionFilter: EventExclusionFiltering,
    private val assetEventProcessor: AssetEventProcessing,
    private val experienceEventProcessor: ExperienceEventProcessing,
    private val featurizationCoordinator: FeaturizationCoordinator,
    private val batchCoordinator: BatchCoordinator?
) : ContentAnalyticsOrchestrating {
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
    }
    
    init {
        Log.debug(TAG, TAG, "Orchestrator initialized")
    }
    
    // Featurization Queue Management
    
    override fun hasFeaturizationQueue(): Boolean = featurizationCoordinator.hasQueue
    
    override fun initializeFeaturizationQueueIfNeeded(queue: PersistentHitQueue?) {
        featurizationCoordinator.initializeQueue(queue)
    }
    
    // Event Processing
    
    override fun processAssetEvent(event: Event) {
        // Validate using EventValidator
        val validationResult = eventValidator.validateAssetEvent(event)
        if (!validationResult.isValid) {
            Log.warning(TAG, TAG, "Asset event validation failed: ${validationResult.error}")
            return
        }
        
        // Check processing conditions
        eventValidator.validateProcessingConditions()?.let { error ->
            Log.warning(TAG, TAG, "Processing conditions not met: $error")
            return
        }
        
        // Process the validated event
        processValidatedAssetEvent(event)
    }
    
    override fun processExperienceEvent(event: Event) {
        // Check if experience tracking is enabled
        if (!eventValidator.isExperienceTrackingEnabled()) {
            Log.trace(TAG, TAG, "Experience tracking disabled")
            return
        }
        
        // Validate using EventValidator
        val validationResult = eventValidator.validateExperienceEvent(event)
        if (!validationResult.isValid) {
            Log.warning(TAG, TAG, "Experience event validation failed: ${validationResult.error}")
            return
        }
        
        // Check processing conditions
        eventValidator.validateProcessingConditions()?.let { error ->
            Log.warning(TAG, TAG, "Processing conditions not met: $error")
            return
        }
        
        // Process the validated event
        processValidatedExperienceEvent(event)
    }
    
    // Batch Management
    
    override fun flush() {
        Log.debug(TAG, TAG, "Flushing pending events")
        batchCoordinator?.flush()
    }
    
    override fun clearPendingBatch() {
        Log.debug(TAG, TAG, "Clearing pending batch")
        batchCoordinator?.clearPendingBatch()
    }
    
    // Configuration
    
    override fun updateConfiguration(config: ContentAnalyticsConfiguration) {
        Log.debug(TAG, TAG, "Updating orchestrator configuration")
        
        // Check if batching is being disabled
        val wasBatchingEnabled = state.batchingEnabled
        val isNowDisabled = !config.batchingEnabled
        
        if (wasBatchingEnabled && isNowDisabled) {
            Log.debug(TAG, TAG, "Batching disabled - flushing pending events before configuration update")
            batchCoordinator?.flush()
        }
        
        // Update batch coordinator with new configuration
        batchCoordinator?.updateConfiguration(config)
    }
    
    // Batch Flush Handlers
    
    override fun handleAssetBatchFlush(events: List<Event>) {
        if (events.isEmpty()) return
        
        Log.debug(TAG, TAG, "handleAssetBatchFlush | Events: ${events.size}")
        assetEventProcessor.processAssetEvents(events)
    }
    
    override fun handleExperienceBatchFlush(events: List<Event>) {
        if (events.isEmpty()) return
        
        Log.debug(TAG, TAG, "handleExperienceBatchFlush | Events: ${events.size}")
        experienceEventProcessor.processExperienceEvents(events)
    }
    
    // Private Helpers
    
    private fun processValidatedAssetEvent(event: Event) {
        if (event.assetURL == null) return
        
        // Check exclusion using EventExclusionFilter
        if (eventExclusionFilter.shouldExcludeAsset(event)) {
            Log.debug(TAG, TAG, "Asset excluded by pattern")
            return
        }
        
        // Route to batch or immediate processing
        if (state.batchingEnabled && batchCoordinator != null) {
            batchCoordinator.addAssetEvent(event)
            Log.trace(TAG, TAG, "Added asset event to batch")
        } else {
            assetEventProcessor.sendAssetEventImmediately(event)
        }
        
        Log.trace(TAG, TAG, "Processed asset event")
    }
    
    private fun processValidatedExperienceEvent(event: Event) {
        if (event.experienceId == null) return
        
        // Check exclusion using EventExclusionFilter
        if (eventExclusionFilter.shouldExcludeExperience(event)) {
            Log.debug(TAG, TAG, "Experience excluded by pattern")
            return
        }
        
        // Pre-process: Store experience definition if this is a definition event
        preprocessExperienceDefinition(event)
        
        // Route to batch or immediate processing
        if (state.batchingEnabled && batchCoordinator != null) {
            // Only add interaction events to batch, skip definition events
            if (event.experienceAction?.isDefinitionAction() != true) {
                batchCoordinator.addExperienceEvent(event)
                Log.trace(TAG, TAG, "Added experience event to batch")
            }
        } else {
            experienceEventProcessor.sendExperienceEventImmediately(event)
        }
        
        Log.trace(TAG, TAG, "Processed experience event")
    }
    
    /**
     * Store experience definition for asset attribution if this is a registration event
     */
    private fun preprocessExperienceDefinition(event: Event) {
        event.experienceDefinition?.let { definition ->
            state.registerExperienceDefinition(definition)
            Log.debug(TAG, TAG, "Stored experience definition: ${definition.experienceId} with ${definition.assets.size} assets")
        }
    }
}
