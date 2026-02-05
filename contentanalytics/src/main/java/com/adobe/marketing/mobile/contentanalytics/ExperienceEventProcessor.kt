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
import com.adobe.marketing.mobile.EventSource
import com.adobe.marketing.mobile.EventType
import com.adobe.marketing.mobile.services.Log

/**
 * Processes experience events and dispatches them to Edge Network
 */
internal class ExperienceEventProcessor(
    private val state: ContentAnalyticsStateManager,
    private val eventDispatcher: EventDispatcher,
    private val xdmEventBuilder: XDMEventBuilder,
    private val metricsBuilder: MetricsBuilding,
    private val featurizationCoordinator: FeaturizationCoordinator
) : ExperienceEventProcessing {

    companion object {
        private const val TAG = "ExperienceEventProcessor"
    }

    override fun processExperienceEvents(events: List<Event>) {
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Processing experience events | EventCount: ${events.size}")

        // Group by experienceId to handle definitions and interactions separately
        val eventsByExperienceId = events.groupBy { it.experienceId ?: "" }

        for ((experienceId, eventsForExperience) in eventsByExperienceId) {
            if (experienceId.isEmpty()) continue

            // Send definition to featurization service if not already sent
            if (!state.hasExperienceDefinitionBeenSent(experienceId)) {
                sendExperienceDefinitionEvent(experienceId)
                state.markExperienceDefinitionAsSent(experienceId)
                Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Sent experience definition | ID: $experienceId")
            } else {
                Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Skipping featurization - already sent | ID: $experienceId")
            }

            // Only send view/click interactions to Edge (filter out definition events)
            val interactionEvents = eventsForExperience.filter { 
                it.experienceAction?.isDefinitionAction() != true 
            }

            if (interactionEvents.isNotEmpty()) {
                // Build typed metrics collection
                val (metricsCollection, interactionType) = metricsBuilder.buildExperienceMetrics(interactionEvents)

                if (metricsCollection.isEmpty) {
                    Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "No metrics found for experience: $experienceId")
                    continue
                }

                // Send one Edge event per experience key (enables CJA filtering by experienceID and location)
                for (experienceKey in metricsCollection.experienceKeys) {
                    val metrics = metricsCollection.metricsFor(experienceKey) ?: continue

                    sendExperienceInteractionEvent(
                        experienceId = experienceId,
                        metrics = metrics
                    )
                }
            } else {
                Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Skipping Edge event for $experienceId - only definition, no interactions")
            }
        }

        Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, "Experience batch sent")
    }

    override fun sendExperienceEventImmediately(event: Event) {
        if (event.experienceKey == null) {
            Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "Cannot send experience event - missing required fields")
            return
        }

        // Process as a single event
        processExperienceEvents(listOf(event))
        Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, "Sent experience event immediately")
    }

    // MARK: - Private Helpers

    private fun sendExperienceDefinitionEvent(experienceId: String) {
        featurizationCoordinator.queueExperience(experienceId)
    }

    private fun sendExperienceInteractionEvent(
        experienceId: String,
        metrics: ExperienceMetrics
    ) {
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Sending interaction event for experience: $experienceId")

        val experienceLocation = metrics.experienceSource.takeIf { it.isNotEmpty() }

        if (experienceLocation == null) {
            Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, "No experienceLocation for: $experienceId (optional)")
        }

        Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, "Using aggregated metrics | Views: ${metrics.viewCount} | Clicks: ${metrics.clickCount}")

        // Get attributed assets directly from metrics
        val assetURLs = metrics.attributedAssets
        Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, "Including ${assetURLs.size} attributed assets in experience XDM")

        // Convert metrics to event data for XDM builder
        val aggregatedMetrics = metrics.toEventData()

        val xdmData = xdmEventBuilder.buildExperienceInteractionXDM(
            experienceId = experienceId,
            metrics = aggregatedMetrics,
            assetURLs = assetURLs,
            experienceLocation = experienceLocation
        )

        sendToEdge(
            xdm = xdmData,
            eventName = ContentAnalyticsConstants.EventNames.CONTENT_ANALYTICS_EXPERIENCE,
            eventType = "Experience"
        )

        val viewCount = (aggregatedMetrics["viewCount"] as? Number)?.toInt() ?: 0
        val clickCount = (aggregatedMetrics["clickCount"] as? Number)?.toInt() ?: 0
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Experience interaction sent (views=$viewCount, clicks=$clickCount)")
    }

    private fun sendToEdge(xdm: Map<String, Any>, eventName: String, eventType: String) {
        val eventData = mutableMapOf<String, Any>("xdm" to xdm)

        // Add datastream override if configured
        buildEdgeConfigOverride()?.let { configOverride ->
            eventData["config"] = configOverride
        }

        val edgeEvent = Event.Builder(
            eventName,
            EventType.EDGE,
            EventSource.REQUEST_CONTENT
        ).setEventData(eventData).build()

        eventDispatcher.dispatch(edgeEvent)

        Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, "Dispatched $eventType event to Edge Network")
    }

    private fun buildEdgeConfigOverride(): Map<String, Any>? {
        val config = state.configuration ?: return null
        val datastreamId = config.datastreamId ?: return null

        val configOverride = mapOf(
            "datastreamIdOverride" to datastreamId
        )

        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Using datastream override: $datastreamId")

        return configOverride
    }
}
