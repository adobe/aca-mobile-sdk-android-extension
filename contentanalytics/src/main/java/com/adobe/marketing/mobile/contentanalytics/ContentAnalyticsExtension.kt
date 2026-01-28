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

import com.adobe.marketing.mobile.Extension
import com.adobe.marketing.mobile.ExtensionApi
import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.EventSource
import com.adobe.marketing.mobile.EventType
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.services.Log

/**
 * Content Analytics extension for tracking assets and experiences.
 *
 * Captures asset views/clicks and experience interactions, batches events for efficiency,
 * and sends data to Adobe Experience Platform Edge Network. Respects user consent via
 * the Consent extension.
 */
internal class ContentAnalyticsExtension(extensionApi: ExtensionApi) : Extension(extensionApi) {
    
    companion object {
        private const val TAG = "ContentAnalyticsExtension"
        const val EXTENSION_VERSION = ContentAnalyticsConstants.EXTENSION_VERSION
        
        /**
         * Register the extension with Mobile Core
         */
        @JvmStatic
        fun registerExtension() {
            MobileCore.registerExtensions(
                listOf(ContentAnalyticsExtension::class.java),
                null
            )
        }
    }
    
    private val stateManager = ContentAnalyticsStateManager()
    private val factory = ContentAnalyticsFactory(api, stateManager)
    private val orchestrator = factory.createContentAnalyticsOrchestrator()
    private val batchCoordinator = factory.getBatchCoordinator()
    private val privacyValidator = factory.getPrivacyValidator()
    
    override fun getName(): String {
        return ContentAnalyticsConstants.EXTENSION_NAME
    }
    
    override fun getFriendlyName(): String {
        return ContentAnalyticsConstants.FRIENDLY_NAME
    }
    
    override fun getVersion(): String {
        return EXTENSION_VERSION
    }
    
    override fun onRegistered() {
        super.onRegistered()
        
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "ContentAnalytics extension registered")
        
        val defaultConfig = ContentAnalyticsConfiguration()
        stateManager.updateConfiguration(defaultConfig)
        orchestrator.updateConfiguration(defaultConfig)
        
        // Register event listeners
        registerListeners()
        
        val configurationSharedState = api.getSharedState(
            ContentAnalyticsConstants.ExternalExtensions.CONFIGURATION,
            null,
            false,
            com.adobe.marketing.mobile.SharedStateResolution.ANY
        )
        
        if (configurationSharedState?.status == com.adobe.marketing.mobile.SharedStateStatus.SET) {
            configurationSharedState.value?.let { configData ->
                parseConfiguration(configData)
            }
        }
    }
    
    override fun onUnregistered() {
        super.onUnregistered()
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Content Analytics extension unregistered")
    }
    
    override fun readyForEvent(event: Event): Boolean {
        // Always allow configuration events through
        if (event.type == EventType.CONFIGURATION) {
            return true
        }
        
        // For other events, check if configuration shared state is available
        val configSharedState = api.getSharedState(
            ContentAnalyticsConstants.ExternalExtensions.CONFIGURATION,
            event,
            false,
            com.adobe.marketing.mobile.SharedStateResolution.ANY
        )
        
        return configSharedState?.status == com.adobe.marketing.mobile.SharedStateStatus.SET
    }
    
    private fun registerListeners() {
        // Configuration events
        api.registerEventListener(
            EventType.CONFIGURATION,
            EventSource.RESPONSE_CONTENT
        ) { event ->
            handleConfigurationResponse(event)
        }
        
        // Content Analytics events
        api.registerEventListener(
            ContentAnalyticsConstants.EventType.CONTENT_ANALYTICS,
            ContentAnalyticsConstants.EventSource.REQUEST_CONTENT
        ) { event ->
            handleContentAnalyticsEvent(event)
        }
        
        // Consent events
        api.registerEventListener(
            "com.adobe.eventType.edgeConsent",
            EventSource.RESPONSE_CONTENT
        ) { event ->
            handleConsentChange(event)
        }
        
        // Identity reset events
        api.registerEventListener(
            EventType.GENERIC_IDENTITY,
            EventSource.REQUEST_RESET
        ) { event ->
            handleIdentityReset(event)
        }
        
        // Lifecycle events (for background flush)
        api.registerEventListener(
            EventType.GENERIC_LIFECYCLE,
            EventSource.APPLICATION_CLOSE
        ) { event ->
            handleApplicationClose(event)
        }
        
        // Hub shared state changes (for privacy validator cache)
        api.registerEventListener(
            EventType.HUB,
            EventSource.SHARED_STATE
        ) { event ->
            handleSharedStateChange(event)
        }
    }
    
    private fun handleConfigurationResponse(event: Event) {
        val configData = event.eventData ?: run {
            Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "Configuration event has no data")
            return
        }
        
        if (!parseConfiguration(configData)) {
            Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "Failed to parse configuration")
        }
    }
    
    private fun handleContentAnalyticsEvent(event: Event) {
        Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, "Received event | Name: ${event.name} | Type: ${event.type}")
        
        // Route to appropriate handler (consent checked by Edge extension)
        when {
            event.isAssetEvent -> handleAssetTrackingEvent(event)
            event.isExperienceEvent -> handleExperienceTrackingEvent(event)
            else -> Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "Unknown event: ${event.name}")
        }
    }
    
    private fun handleAssetTrackingEvent(event: Event) {
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Asset event received: ${event.name}")
        orchestrator.processAssetEvent(event)
    }
    
    private fun handleExperienceTrackingEvent(event: Event) {
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Experience event received: ${event.name}")
        orchestrator.processExperienceEvent(event)
    }
    
    private fun handleSharedStateChange(event: Event) {
        // Check if this is a Hub or Consent shared state change
        val stateOwner = event.eventData?.get("stateowner") as? String ?: return
        
        // Update privacy validator cache when Hub or Consent shared states change
        if (stateOwner == ContentAnalyticsConstants.ExternalExtensions.EVENT_HUB ||
            stateOwner == ContentAnalyticsConstants.ExternalExtensions.CONSENT) {
            Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, 
                     "Shared state changed for $stateOwner - updating privacy validator cache")
            privacyValidator.updateSharedStateCache()
        }
    }
    
    private fun handleConsentChange(event: Event) {
        val consentData = event.eventData ?: return
        
        // Update privacy validator cache when consent changes
        privacyValidator.updateSharedStateCache()
        
        // Check for collect consent preference
        val consents = consentData["consents"] as? Map<*, *>
        val collect = consents?.get("collect") as? Map<*, *>
        val value = collect?.get("val") as? String
        
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Consent collect preference: $value")
        
        // Clear pending events if user opts out
        if (value == "n" || value == "no") {
            Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Collect consent denied - clearing pending batch")
            orchestrator.clearPendingBatch()
        }
    }
    
    private fun handleIdentityReset(event: Event) {
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Identity reset - clearing state and pending events")
        
        stateManager.reset()
        orchestrator.clearPendingBatch()
    }
    
    private fun handleApplicationClose(event: Event) {
        val batchingEnabled = stateManager.batchingEnabled
        
        if (!batchingEnabled) {
            Log.trace(ContentAnalyticsConstants.LOG_TAG, TAG, "App backgrounded but batching disabled - no flush needed")
            return
        }
        
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "App backgrounded - flushing pending batch")
        orchestrator.flushPendingEvents()
        Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Background flush complete")
    }
    
    private fun parseConfiguration(configData: Map<String, Any?>): Boolean {
        return try {
            // Strip "contentanalytics." prefix from prefixed keys, keep non-prefixed keys (like iOS)
            // This allows us to read edge.domain, edge.configId, experienceCloud.org, etc.
            val strippedConfig = configData.mapKeys { (key, _) ->
                if (key.startsWith("contentanalytics.")) {
                    key.removePrefix("contentanalytics.")
                } else {
                    key  // Keep non-prefixed keys (edge.*, experienceCloud.*, etc.)
                }
            }
            
            Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Parsing configuration | Keys found: ${strippedConfig.keys}")
            
            // Parse and update configuration (will use defaults if empty)
            val config = ContentAnalyticsConfiguration.fromEventData(strippedConfig)
            stateManager.updateConfiguration(config)
            
            // Update orchestrator (which handles business logic and delegates to batch coordinator)
            orchestrator.updateConfiguration(config)
            
            // Initialize featurization queue if not yet created (lazy initialization on first valid config)
            if (!orchestrator.hasFeaturizationQueue()) {
                orchestrator.initializeFeaturizationQueueIfNeeded(factory.createFeaturizationHitQueue())
            }
            
            Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Configuration applied | Batching: ${config.batchingEnabled} | TrackExperiences: ${config.trackExperiences} | BatchSize: ${config.maxBatchSize} | FlushInterval: ${config.batchFlushInterval}ms")
            
            true
        } catch (e: Exception) {
            Log.warning(ContentAnalyticsConstants.LOG_TAG, TAG, "Failed to parse configuration: ${e.message}")
            false
        }
    }
}

