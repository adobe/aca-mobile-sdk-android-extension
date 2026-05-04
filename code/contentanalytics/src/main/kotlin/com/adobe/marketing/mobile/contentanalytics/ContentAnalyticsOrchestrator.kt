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

internal class ContentAnalyticsOrchestrator(
    private val state: ContentAnalyticsStateManager,
    private val eventValidator: EventValidating,
    private val eventExclusionFilter: EventExclusionFiltering,
    private val assetEventProcessor: AssetEventProcessing,
    private val experienceEventProcessor: ExperienceEventProcessing,
    private val featurizationCoordinator: FeaturizationCoordinator,
    private val batchCoordinator: BatchCoordinator?
) {

    companion object {
        private const val LOG_TAG = ContentAnalyticsConstants.LOG_TAG
        private const val TAG = "ContentAnalyticsOrchestrator"
    }

    fun hasFeaturizationQueue(): Boolean = featurizationCoordinator.hasQueue

    fun initializeFeaturizationQueueIfNeeded(queue: PersistentHitQueue?) {
        featurizationCoordinator.initializeQueue(queue)
    }

    fun processAssetEvent(event: Event) {
        val validationResult = eventValidator.validateAssetEvent(event)
        if (!validationResult.isValid) {
            Log.warning(LOG_TAG, TAG, "Asset event validation failed: ${validationResult.error}")
            return
        }

        if (eventExclusionFilter.shouldExcludeAsset(event)) {
            Log.debug(LOG_TAG, TAG, "Asset excluded by pattern")
            return
        }

        processValidatedEvent(
            event = event,
            entityType = "asset",
            identifier = { it.assetKey },
            addToBatch = { batchCoordinator?.addAssetEvent(it) },
            sendImmediately = { assetEventProcessor.sendAssetEventImmediately(it) }
        )
    }

    fun processExperienceEvent(event: Event) {
        if (state.configuration?.trackExperiences != true) {
            Log.trace(LOG_TAG, TAG, "Experience tracking disabled")
            return
        }

        val validationResult = eventValidator.validateExperienceEvent(event)
        if (!validationResult.isValid) {
            Log.warning(LOG_TAG, TAG, "Experience event validation failed: ${validationResult.error}")
            return
        }

        // Store definition payload (assets/texts/CTAs) so we can attribute assets to experiences later.
        // Must happen before exclusion so definitions for excluded experiences are still cached.
        preprocessExperienceDefinition(event)

        // Update the last-seen location for this experience from view/click events.
        // Location is NOT part of definition registration — the same experience can be viewed at
        // different locations. Must happen before exclusion so the location is captured even when
        // the view event itself is filtered out.
        captureExperienceLocation(event)

        if (eventExclusionFilter.shouldExcludeExperience(event)) {
            Log.debug(LOG_TAG, TAG, "Experience excluded by pattern")
            return
        }

        if (event.experienceAction?.isDefinitionAction() == true) {
            return
        }

        processValidatedEvent(
            event = event,
            entityType = "experience",
            identifier = { it.experienceKey },
            addToBatch = { batchCoordinator?.addExperienceEvent(it) },
            sendImmediately = { experienceEventProcessor.sendExperienceEventImmediately(it) }
        )
    }

    private fun processValidatedEvent(
        event: Event,
        entityType: String,
        identifier: (Event) -> String?,
        addToBatch: (Event) -> Unit,
        sendImmediately: (Event) -> Unit
    ) {
        val id = identifier(event) ?: return
        Log.trace(LOG_TAG, TAG, "Processing validated $entityType event: $id")

        if (state.batchingEnabled && batchCoordinator != null) {
            addToBatch(event)
            Log.trace(LOG_TAG, TAG, "Added $entityType event to batch")
        } else {
            Log.debug(LOG_TAG, TAG, "Batching disabled - sending $entityType event immediately")
            sendImmediately(event)
        }

        Log.trace(LOG_TAG, TAG, "Processed $entityType event")
    }

    /** Stores the experience definition payload (assets/texts/CTAs) for asset attribution.
     *  Location is handled separately via [captureExperienceLocation]. */
    private fun preprocessExperienceDefinition(event: Event) {
        event.experienceDefinition?.let { definition ->
            state.registerExperienceDefinition(definition)
            Log.debug(LOG_TAG, TAG, "Stored experience definition: ${definition.experienceId} with ${definition.assets.size} assets")
        }
    }

    /** Updates the last-seen location for an experience from any event that carries one.
     *  Called before exclusion filtering so the location is captured even for excluded events. */
    private fun captureExperienceLocation(event: Event) {
        val experienceId = event.experienceId ?: return
        val location = event.experienceLocation ?: return
        state.updateExperienceLocation(experienceId, location)
    }

    fun flush() {
        Log.debug(LOG_TAG, TAG, "Flushing pending events")
        batchCoordinator?.flush()
    }

    fun updateConfiguration(config: ContentAnalyticsConfiguration) {
        Log.debug(LOG_TAG, TAG, "Updating orchestrator configuration")

        val wasBatchingEnabled = state.batchingEnabled
        val isNowDisabled = !config.batchingEnabled

        if (wasBatchingEnabled && isNowDisabled) {
            Log.debug(LOG_TAG, TAG, "Batching disabled - flushing pending events before configuration update")
            batchCoordinator?.flush()
        }

        batchCoordinator?.updateConfiguration(config)
    }

    fun clearPendingBatch() {
        Log.debug(LOG_TAG, TAG, "Clearing pending batch")
        batchCoordinator?.clearPendingBatch()
    }

    fun handleAssetBatchFlush(events: List<Event>) {
        if (events.isEmpty()) return

        Log.debug(LOG_TAG, TAG, "Processing asset batch flush | Events: ${events.size}")
        assetEventProcessor.processAssetEvents(events)
    }

    fun handleExperienceBatchFlush(events: List<Event>) {
        if (events.isEmpty()) return

        Log.debug(LOG_TAG, TAG, "Processing experience batch flush | Events: ${events.size}")
        experienceEventProcessor.processExperienceEvents(events)
    }
}
