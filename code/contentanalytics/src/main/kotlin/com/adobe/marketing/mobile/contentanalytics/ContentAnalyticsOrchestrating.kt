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

/**
 * Interface defining the orchestration responsibilities for Content Analytics event processing.
 * This interface enables dependency injection and testability by abstracting the orchestrator interface.
 */
internal interface ContentAnalyticsOrchestrating {
    
    // Event Processing
    
    /**
     * Processes an asset tracking event.
     * @param event The asset event to process
     */
    fun processAssetEvent(event: Event)
    
    /**
     * Processes an experience tracking event.
     * @param event The experience event to process
     */
    fun processExperienceEvent(event: Event)
    
    // Featurization Queue Management
    
    /**
     * Returns whether the featurization queue has been initialized.
     */
    fun hasFeaturizationQueue(): Boolean
    
    /**
     * Initializes the featurization queue if not already initialized.
     * @param queue The persistent hit queue to use for featurization
     */
    fun initializeFeaturizationQueueIfNeeded(queue: PersistentHitQueue?)
    
    // Batch Management
    
    /**
     * Forces sending of any pending batched events.
     */
    fun flush()
    
    /**
     * Clears pending batched events without sending them.
     */
    fun clearPendingBatch()
    
    // Configuration
    
    /**
     * Updates the orchestrator configuration.
     * @param config The new configuration to apply
     */
    fun updateConfiguration(config: ContentAnalyticsConfiguration)
    
    // Batch Flush Handlers
    
    /**
     * Handles batch flush callback for asset events.
     * @param events List of asset events to process
     */
    fun handleAssetBatchFlush(events: List<Event>)
    
    /**
     * Handles batch flush callback for experience events.
     * @param events List of experience events to process
     */
    fun handleExperienceBatchFlush(events: List<Event>)
}
