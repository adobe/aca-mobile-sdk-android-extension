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
import java.util.Date

/**
 * Orchestrates content analytics event processing, batching, and delivery.
 * 
 * Coordinates between event validation, filtering, batching, and Edge Network dispatch.
 */
internal class ContentAnalyticsOrchestrator(
    private val state: ContentAnalyticsStateManager,
    private val eventDispatcher: EventDispatcher,
    private val privacyValidator: PrivacyValidator,
    private val xdmEventBuilder: XDMEventBuilder,
    private val batchCoordinator: BatchCoordinator?,
    private var featurizationHitQueue: PersistentHitQueue? = null
) {
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
    }
    
    /**
     * Check if featurization queue is already initialized.
     * Used by extension to avoid recreating the queue on every config change.
     */
    fun hasFeaturizationQueue(): Boolean = featurizationHitQueue != null
    
    /**
     * Initializes the featurization hit queue if not already created (lazy initialization).
     * Only called once when valid configuration first becomes available.
     */
    fun initializeFeaturizationQueueIfNeeded(queue: PersistentHitQueue?) {
        // Only set queue if it doesn't exist yet
        if (featurizationHitQueue != null) {
            Log.trace(TAG, TAG, "Featurization queue already initialized - skipping")
            return
        }
        
        featurizationHitQueue = queue
        
        if (featurizationHitQueue != null) {
            Log.debug(TAG, TAG, "✅ Featurization queue initialized successfully")
        } else {
            Log.debug(TAG, TAG, "Featurization queue not yet available (waiting for valid configuration)")
        }
    }
    
    /**
     * Process an asset tracking event.
     */
    fun processAssetEvent(event: Event) {
        // Validate required fields
        val assetURL = event.assetURL
        val action = event.assetAction
        
        if (assetURL == null || action == null) {
            Log.warning(TAG, TAG, "Asset event missing required fields")
            return
        }
        
        // Validate action is view or click
        if (!action.isViewAction() && !action.isClickAction()) {
            Log.warning(TAG, TAG, "Asset event has invalid action: $action")
            return
        }
        
        // Process the validated event
        processValidatedAssetEvent(event)
    }
    
    fun processExperienceEvent(event: Event) {
        // Check if experience tracking is enabled
        if (state.configuration?.trackExperiences != true) {
            Log.trace(TAG, TAG, "Experience tracking disabled")
            return
        }
        
        // Validate required fields
        val experienceId = event.experienceId
        val action = event.experienceAction
        
        if (experienceId == null || action == null) {
            Log.warning(TAG, TAG, "Experience event missing required fields")
            return
        }
        
        // Validate action is definition, view, or click
        if (!action.isDefinitionAction() && !action.isViewAction() && !action.isClickAction()) {
            Log.warning(TAG, TAG, "Experience event has invalid action: $action")
            return
        }
        
        // Process the validated event
        processValidatedExperienceEvent(event)
    }
    
    private fun processValidatedAssetEvent(event: Event) {
        processValidatedEvent(
            event = event,
            entityType = "asset",
            identifier = { it.assetURL },
            shouldExclude = { shouldExcludeAssetEvent(it) },
            addToBatch = { batchCoordinator?.addAssetEvent(it) },
            sendImmediately = { sendAssetEventImmediately(it) }
        )
    }
    
    private fun processValidatedExperienceEvent(event: Event) {
        processValidatedEvent(
            event = event,
            entityType = "experience",
            identifier = { it.experienceId },
            shouldExclude = { shouldExcludeExperienceEvent(it) },
            preProcessing = { preprocessExperienceDefinition(it) },
            addToBatch = { batchCoordinator?.addExperienceEvent(it) },
            sendImmediately = { sendExperienceEventImmediately(it) }
        )
    }
    
    private fun processValidatedEvent(
        event: Event,
        entityType: String,
        identifier: (Event) -> String?,
        shouldExclude: (Event) -> Boolean,
        preProcessing: ((Event) -> Unit)? = null,
        addToBatch: (Event) -> Unit,
        sendImmediately: (Event) -> Unit
    ) {
        val id = identifier(event) ?: return
        Log.trace(TAG, TAG, "Processing validated $entityType event: $id")
        
        // Check if entity should be excluded
        if (shouldExclude(event)) {
            Log.debug(TAG, TAG, "${entityType.replaceFirstChar { it.uppercase() }} excluded by pattern")
            return
        }
        
        // Execute any pre-processing (e.g., store experience definition)
        preProcessing?.invoke(event)
        
        // Check if batching is enabled (and batch coordinator exists)
        if (state.batchingEnabled && batchCoordinator != null) {
            addToBatch(event)
            Log.trace(TAG, TAG, "Added $entityType event to batch")
        } else {
            Log.debug(TAG, TAG, "Batching disabled - sending $entityType event immediately")
            sendImmediately(event)
        }
        
        Log.trace(TAG, TAG, "Successfully processed $entityType event")
    }
    
    private fun shouldExcludeAssetEvent(event: Event): Boolean {
        // Check URL pattern exclusion
        event.assetURL?.let { url ->
            if (!state.shouldTrackUrl(url)) {
                return true
            }
        }
        
        // Check location exclusion
        if (!state.shouldTrackAssetLocation(event.assetLocation)) {
            return true
        }
        
        return false
    }
    
    private fun shouldExcludeExperienceEvent(event: Event): Boolean {
        return !state.shouldTrackExperience(event.experienceLocation)
    }
    
    private fun preprocessExperienceDefinition(event: Event) {
        event.experienceDefinition?.let { definition ->
            state.registerExperienceDefinition(definition)
            Log.debug(TAG, TAG, "Stored experience definition: ${definition.experienceId} with ${definition.assets.size} assets")
        }
    }
    
    /**
     * Flush all pending batched events (delegates to BatchCoordinator)
     */
    fun flushPendingEvents() {
        Log.debug(TAG, TAG, "Flushing pending events")
        batchCoordinator?.flush()
    }
    
    /**
     * Update configuration with business logic for handling configuration changes
     */
    fun updateConfiguration(config: ContentAnalyticsConfiguration) {
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
    
    /**
     * Clear all pending batched events (e.g., on consent denial)
     */
    fun clearPendingBatch() {
        Log.debug(TAG, TAG, "Clearing pending batch")
        batchCoordinator?.clearPendingBatch()
    }
    
    /**
     * Handle batch flush callback for asset events.
     */
    fun handleAssetBatchFlush(events: List<Event>) {
        if (events.isEmpty()) return
        
        Log.debug(TAG, TAG, "Processing asset batch flush | Events: ${events.size}")
        
        val eventsByKey = events.groupBy { it.assetKey ?: "" }
        
        for ((key, eventsForKey) in eventsByKey) {
            if (key.isEmpty()) continue
            processAssetBatch(eventsForKey)
        }
    }
    
    /**
     * Handle batch flush callback for experience events.
     */
    fun handleExperienceBatchFlush(events: List<Event>) {
        if (events.isEmpty()) return
        
        Log.debug(TAG, TAG, "Processing experience batch flush | Events: ${events.size}")
        
        val eventsByKey = events.groupBy { it.experienceKey ?: "" }
        
        for ((key, eventsForKey) in eventsByKey) {
            if (key.isEmpty()) continue
            processExperienceBatch(eventsForKey)
        }
    }
    
    private fun processAssetBatch(events: List<Event>) {
        if (events.isEmpty()) return
        
        Log.debug(TAG, TAG, "Processing asset events | EventCount: ${events.size}")
        
        // Build typed metrics collection
        val metricsCollection = buildAssetMetricsCollection(events)
        
        if (metricsCollection.isEmpty) {
            Log.warning(TAG, TAG, "No valid metrics to send - skipping")
            return
        }
        
        Log.trace(TAG, TAG, "Built aggregated metrics | AssetCount: ${metricsCollection.count}")
        
        // Send one Edge event per asset key (enables CJA filtering by assetID and location)
        for (assetKey in metricsCollection.assetKeys) {
            val metrics = metricsCollection.metricsFor(assetKey) ?: continue
            sendAssetInteractionEvent(
                assetKeys = listOf(assetKey),
                aggregatedMetrics = mapOf(assetKey to metrics.toEventData())
            )
        }
    }
    
    private fun processExperienceBatch(events: List<Event>) {
        if (events.isEmpty()) return
        
        val experienceId = events.first().experienceId ?: return
        
        Log.debug(TAG, TAG, "Processing experience events | EventCount: ${events.size}")
        
        // Send definition to featurization service if not already sent
        if (!state.hasExperienceDefinitionBeenSent(experienceId)) {
            sendExperienceDefinitionToFeaturization(experienceId, events)
            state.markExperienceDefinitionAsSent(experienceId)
        }
        
        // Build typed metrics collection
        val metricsCollection = buildExperienceMetricsCollection(events)
        
        if (metricsCollection.isEmpty) {
            Log.warning(TAG, TAG, "No metrics found for experience: $experienceId")
            return
        }
        
        Log.trace(TAG, TAG, "Built aggregated metrics | ExperienceCount: ${metricsCollection.count}")
        
        // Send one Edge event per experience key (enables CJA filtering)
        for (experienceKey in metricsCollection.experienceKeys) {
            val metrics = metricsCollection.metricsFor(experienceKey) ?: continue
            sendExperienceInteractionEvent(
                experienceId = experienceId,
                metrics = metrics.toEventData(),
                assetURLs = metrics.attributedAssets,
                experienceLocation = metrics.experienceSource
            )
        }
    }
    
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
    
    /**
     * Builds typed asset metrics collection from events
     */
    private fun buildAssetMetricsCollection(events: List<Event>): AssetMetricsCollection {
        val groupedEvents = events.groupBy { it.assetKey ?: "" }
        val metricsMap = mutableMapOf<String, AssetMetrics>()
        
        for ((key, groupedEvents) in groupedEvents) {
            if (key.isEmpty()) continue
            
            val firstEvent = groupedEvents.firstOrNull() ?: continue
            val context = extractAssetContext(firstEvent) ?: continue
            
            val assetURL = context["assetURL"] as? String ?: continue
            val assetLocation = context["assetLocation"] as? String ?: ""  // Optional, default to empty string
            
            val viewCount = groupedEvents.count { it.assetAction == ContentAnalyticsConstants.ActionType.VIEW }.toDouble()
            val clickCount = groupedEvents.count { it.assetAction == ContentAnalyticsConstants.ActionType.CLICK }.toDouble()
            
            // Process extras
            val allExtras = groupedEvents.mapNotNull { it.assetExtras }
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
        
        return AssetMetricsCollection(metricsMap)
    }
    
    /**
     * Builds typed experience metrics collection from events
     */
    private fun buildExperienceMetricsCollection(events: List<Event>): ExperienceMetricsCollection {
        val groupedEvents = events.groupBy { it.experienceKey ?: "" }
        val metricsMap = mutableMapOf<String, ExperienceMetrics>()
        
        for ((key, groupedEvents) in groupedEvents) {
            if (key.isEmpty()) continue
            
            val firstEvent = groupedEvents.firstOrNull() ?: continue
            val context = extractExperienceContext(firstEvent) ?: continue
            
            val experienceID = context["experienceID"] as? String ?: continue
            val experienceSource = context["experienceSource"] as? String ?: ""  // Optional, default to empty string
            
            val viewCount = groupedEvents.count { it.experienceAction == ContentAnalyticsConstants.ActionType.VIEW }.toDouble()
            val clickCount = groupedEvents.count { it.experienceAction == ContentAnalyticsConstants.ActionType.CLICK }.toDouble()
            
            // Process extras
            val allExtras = groupedEvents.mapNotNull { it.experienceExtras }
            val processedExtras = if (allExtras.isNotEmpty()) {
                ContentAnalyticsUtilities.processExtras(allExtras)
            } else null
            
            // Get attributed assets from stored definition
            val assetURLs = state.getExperienceDefinition(experienceID)?.assets ?: run {
                Log.warning(TAG, TAG, "No assets found for experience: $experienceID")
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
        
        return ExperienceMetricsCollection(metricsMap)
    }
    
    private fun sendAssetInteractionEvent(
        assetKeys: List<String>,
        aggregatedMetrics: Map<String, Map<String, Any>>
    ) {
        Log.debug(TAG, TAG, "Sending interaction event for ${assetKeys.size} assets")
        
        val xdmData = xdmEventBuilder.buildAssetInteractionXDM(
            assetKeys = assetKeys,
            metrics = aggregatedMetrics
        )
        
        sendToEdge(
            xdm = xdmData,
            eventName = ContentAnalyticsConstants.EventNames.CONTENT_ANALYTICS_ASSET,
            eventType = "Asset"
        )
        
        Log.debug(TAG, TAG, "Successfully sent asset batch via Edge")
    }
    
    private fun sendExperienceInteractionEvent(
        experienceId: String,
        metrics: Map<String, Any>,
        assetURLs: List<String>,
        experienceLocation: String?
    ) {
        Log.debug(TAG, TAG, "Sending experience interaction | ID: $experienceId")
        
        val xdmData = xdmEventBuilder.buildExperienceInteractionXDM(
            experienceId = experienceId,
            metrics = metrics,
            assetURLs = assetURLs,
            experienceLocation = experienceLocation
        )
        
        sendToEdge(
            xdm = xdmData,
            eventName = ContentAnalyticsConstants.EventNames.CONTENT_ANALYTICS_EXPERIENCE,
            eventType = "Experience"
        )
        
        val viewCount = (metrics["viewCount"] as? Number)?.toInt() ?: 0
        val clickCount = (metrics["clickCount"] as? Number)?.toInt() ?: 0
        Log.debug(TAG, TAG, "Sent experience batch | ID: $experienceId | Views: $viewCount | Clicks: $clickCount")
    }
    
    private fun sendEventImmediately(
        event: Event,
        entityType: String,
        keyExtractor: (Event) -> String?,
        processEvents: (List<Event>) -> Unit
    ) {
        Log.trace(TAG, TAG, "Sending $entityType event immediately (batching disabled)")
        
        if (keyExtractor(event) == null) {
            Log.warning(TAG, TAG, "Cannot send $entityType event - missing required fields")
            return
        }
        
        // Process as a single event (metrics will be calculated from events)
        processEvents(listOf(event))
    }
    
    private fun sendAssetEventImmediately(event: Event) {
        sendEventImmediately(
            event = event,
            entityType = "asset",
            keyExtractor = { it.assetKey },
            processEvents = { events ->
                events.groupBy { it.assetKey ?: "" }
                    .filter { (key, _) -> key.isNotEmpty() }
                    .forEach { (_, eventsForKey) -> processAssetBatch(eventsForKey) }
            }
        )
    }
    
    private fun sendExperienceEventImmediately(event: Event) {
        sendEventImmediately(
            event = event,
            entityType = "experience",
            keyExtractor = { it.experienceKey },
            processEvents = { events ->
                events.groupBy { it.experienceKey ?: "" }
                    .filter { (key, _) -> key.isNotEmpty() }
                    .forEach { (_, eventsForKey) -> processExperienceBatch(eventsForKey) }
            }
        )
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
        
        Log.trace(TAG, TAG, "Dispatched $eventType event to Edge Network")
    }
    
    private fun buildEdgeConfigOverride(): Map<String, Any>? {
        val config = state.configuration ?: return null
        
        val datastreamId = config.datastreamId ?: return null
        
        val configOverride = mapOf(
            "datastreamIdOverride" to datastreamId
        )
        
        Log.debug(TAG, TAG, "Using datastream override: $datastreamId")
        
        return configOverride
    }
    
    
    private fun sendExperienceDefinitionToFeaturization(experienceId: String, events: List<Event>) {
        // Check consent for direct HTTP calls (Edge Network events are validated by Edge extension, but featurization bypasses Edge)
        if (!privacyValidator.isDataCollectionAllowed()) {
            Log.debug(TAG, TAG, "❌ Skipping featurization - consent denied (check privacy validator logs above for details)")
            return
        }
        
        Log.debug(TAG, TAG, "✅ Privacy check passed - proceeding with featurization")
        
        val config = state.configuration
        if (config == null) {
            Log.debug(TAG, TAG, "❌ Skipping featurization - No configuration available")
            return
        }
        
        val serviceUrl = config.getFeaturizationBaseUrl()
        if (serviceUrl.isNullOrEmpty()) {
            Log.debug(TAG, TAG, "❌ Skipping featurization - Cannot determine featurization URL | edge.domain: ${config.edgeDomain} | region: ${config.region}")
            return
        }
        
        val imsOrg = config.experienceCloudOrgId
        if (imsOrg.isNullOrEmpty()) {
            Log.debug(TAG, TAG, "❌ Skipping featurization - IMS Org not configured | experienceCloud.org: ${config.experienceCloudOrgId}")
            return
        }
        
        Log.debug(TAG, TAG, "✅ Configuration valid | URL: $serviceUrl | Org: $imsOrg")
        
        // Get definition from state (registerExperience() must be called first)
        val definition = state.getExperienceDefinition(experienceId)
        if (definition == null) {
            Log.warning(TAG, TAG, "No definition found for experience: $experienceId - registerExperience() must be called first")
            return
        }
        
        val assetURLs = definition.assets
        val textContent = definition.texts
        val buttonContent = definition.ctas
        
        Log.trace(TAG, TAG, "Using stored definition for featurization: $experienceId")
        
        // Convert to {value} format for featurization service (no empty style objects)
        val imagesData = assetURLs.map { assetURL ->
            mapOf("value" to assetURL)
        }
        
        val textsData = textContent.map { it.toMap() }
        val ctasData = if (buttonContent != null && buttonContent.isNotEmpty()) {
            buttonContent.map { it.toMap() }
        } else null
        
        val contentData = ContentData(
            images = imagesData,
            texts = textsData,
            ctas = ctasData
        )
        
        // datastreamId is required - ensure it's present
        val datastreamId = config.datastreamId
        if (datastreamId.isNullOrEmpty()) {
            Log.error(
                ContentAnalyticsConstants.LOG_TAG,
                TAG,
                "Cannot send experience to featurization - datastreamId not configured"
            )
            return
        }
        
        val content = ExperienceContent(
            content = contentData,
            orgId = imsOrg,
            datastreamId = datastreamId,
            experienceId = experienceId
        )
        
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
            Log.warning(TAG, TAG, "Failed to encode featurization hit | ExperienceID: $experienceId")
            return
        }
        
        val dataEntity = com.adobe.marketing.mobile.services.DataEntity(
            hitJson
        )
        
        // Queue hit (persisted to disk and retried automatically)
        // Use local reference to avoid smart cast issues with mutable property
        val queue = featurizationHitQueue
        if (queue != null && queue.queue(dataEntity)) {
            Log.debug(TAG, TAG, "Experience queued for featurization | ID: $experienceId")
        } else {
            Log.warning(TAG, TAG, "Failed to queue experience for featurization | ID: $experienceId")
        }
    }
    
}

