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
 * Builds aggregated metrics collections from events
 */
internal class MetricsBuilder(
    private val state: ContentAnalyticsStateManager
) : MetricsBuilding {

    companion object {
        private const val TAG = "MetricsBuilder"
    }

    override fun buildAssetMetrics(events: List<Event>): Pair<AssetMetricsCollection, InteractionType> {
        val groupedEvents = events.groupBy { it.assetKey ?: "" }
        val metricsMap = mutableMapOf<String, AssetMetrics>()

        for ((key, eventsForKey) in groupedEvents) {
            if (key.isEmpty()) continue

            val firstEvent = eventsForKey.firstOrNull() ?: continue
            val context = extractAssetContext(firstEvent) ?: continue

            val assetURL = context["assetURL"] as? String ?: continue
            val assetLocation = context["assetLocation"] as? String ?: ""

            val viewCount = eventsForKey.count { it.assetAction == ContentAnalyticsConstants.ActionType.VIEW }.toDouble()
            val clickCount = eventsForKey.count { it.assetAction == ContentAnalyticsConstants.ActionType.CLICK }.toDouble()

            // Process extras
            val allExtras = eventsForKey.mapNotNull { it.assetExtras }
            val processedExtras = if (allExtras.isNotEmpty()) {
                ContentAnalyticsUtilities.processExtras(allExtras)
            } else null

            val metrics = AssetMetrics(
                assetURL = assetURL,
                assetLocation = assetLocation,
                viewCount = viewCount,
                clickCount = clickCount,
                assetExtras = processedExtras
            )

            metricsMap[key] = metrics
        }

        val interactionType = events.triggeringInteractionType
        return Pair(AssetMetricsCollection(metricsMap), interactionType)
    }

    override fun buildExperienceMetrics(events: List<Event>): Pair<ExperienceMetricsCollection, InteractionType> {
        val groupedEvents = events.groupBy { it.experienceKey ?: "" }
        val metricsMap = mutableMapOf<String, ExperienceMetrics>()

        for ((key, eventsForKey) in groupedEvents) {
            if (key.isEmpty()) continue

            val firstEvent = eventsForKey.firstOrNull() ?: continue
            val context = extractExperienceContext(firstEvent) ?: continue

            val experienceID = context["experienceID"] as? String ?: continue
            val experienceSource = context["experienceSource"] as? String ?: ""

            val viewCount = eventsForKey.count { it.experienceAction == ContentAnalyticsConstants.ActionType.VIEW }.toDouble()
            val clickCount = eventsForKey.count { it.experienceAction == ContentAnalyticsConstants.ActionType.CLICK }.toDouble()

            // Process extras
            val allExtras = eventsForKey.mapNotNull { it.experienceExtras }
            val processedExtras = if (allExtras.isNotEmpty()) {
                ContentAnalyticsUtilities.processExtras(allExtras)
            } else null

            // Get attributed assets from stored definition
            val assetURLs = state.getExperienceDefinition(experienceID)?.assets ?: run {
                Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "No definition found for experience: $experienceID - may not be registered")
                emptyList()
            }

            val metrics = ExperienceMetrics(
                experienceID = experienceID,
                experienceSource = experienceSource,
                viewCount = viewCount,
                clickCount = clickCount,
                experienceExtras = processedExtras,
                attributedAssets = assetURLs
            )

            metricsMap[key] = metrics
        }

        val interactionType = events.triggeringInteractionType
        return Pair(ExperienceMetricsCollection(metricsMap), interactionType)
    }

    // MARK: - Private Helpers

    private fun extractAssetContext(event: Event): Map<String, Any>? {
        val assetURL = event.assetURL ?: return null

        val context = mutableMapOf<String, Any>("assetURL" to assetURL)

        event.assetLocation?.let { location ->
            context["assetLocation"] = location
        }

        return context
    }

    private fun extractExperienceContext(event: Event): Map<String, Any>? {
        val experienceID = event.experienceId ?: return null

        val context = mutableMapOf<String, Any>(
            "experienceID" to experienceID
        )

        event.experienceLocation?.let { location ->
            context["experienceSource"] = location
        }

        return context
    }
}

/**
 * Extension property to determine the triggering interaction type from a list of events.
 * Returns the interaction type of the first event, defaulting to CLICK if the list is empty.
 */
internal val List<Event>.triggeringInteractionType: InteractionType
    get() {
        val firstEvent = firstOrNull() ?: return InteractionType.CLICK
        
        // Check asset action first, then experience action
        val action = firstEvent.assetAction ?: firstEvent.experienceAction
        
        return when {
            action?.isViewAction() == true -> InteractionType.VIEW
            else -> InteractionType.CLICK
        }
    }
