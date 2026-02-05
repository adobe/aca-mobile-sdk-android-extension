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
 * Processes asset events and dispatches them to Edge Network
 */
internal class AssetEventProcessor(
    private val state: ContentAnalyticsStateManager,
    private val eventDispatcher: EventDispatcher,
    private val xdmEventBuilder: XDMEventBuilder,
    private val metricsBuilder: MetricsBuilding
) : AssetEventProcessing {

    companion object {
        private const val TAG = "AssetEventProcessor"
    }

    override fun processAssetEvents(events: List<Event>) {
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Processing asset events | EventCount: ${events.size}")

        // Build typed metrics collection
        val (metricsCollection, interactionType) = metricsBuilder.buildAssetMetrics(events)

        if (metricsCollection.isEmpty) {
            Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "No valid metrics to send - skipping")
            return
        }

        Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, "Built aggregated metrics | AssetCount: ${metricsCollection.count}")

        // Send one Edge event per asset key (enables CJA filtering by assetID and location)
        for (assetKey in metricsCollection.assetKeys) {
            val metrics = metricsCollection.metricsFor(assetKey) ?: continue

            sendAssetInteractionEvent(
                assetKeys = listOf(assetKey),
                aggregatedMetrics = mapOf(assetKey to metrics.toEventData())
            )
        }
    }

    override fun sendAssetEventImmediately(event: Event) {
        if (event.assetKey == null) {
            Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "Cannot send asset event - missing required fields")
            return
        }

        // Process as a single event
        processAssetEvents(listOf(event))
        Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, "Sent asset event immediately")
    }

    // MARK: - Private Helpers

    private fun sendAssetInteractionEvent(
        assetKeys: List<String>,
        aggregatedMetrics: Map<String, Map<String, Any>>
    ) {
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Sending interaction event for ${assetKeys.size} assets")

        val xdmData = xdmEventBuilder.buildAssetInteractionXDM(
            assetKeys = assetKeys,
            metrics = aggregatedMetrics
        )

        sendToEdge(
            xdm = xdmData,
            eventName = ContentAnalyticsConstants.EventNames.CONTENT_ANALYTICS_ASSET,
            eventType = "Asset"
        )

        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Asset batch sent")
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
