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

/**
 * Constants for Content Analytics extension
 */
internal object ContentAnalyticsConstants {
    
    // Extension Information
    const val EXTENSION_NAME = "com.adobe.contentanalytics"
    const val FRIENDLY_NAME = "Content Analytics"
    const val EXTENSION_VERSION = "3.0.0-beta.1"
    const val LOG_TAG = FRIENDLY_NAME
    const val DATASTORE_NAME = EXTENSION_NAME
    
    // Event Types
    object EventType {
        const val CONTENT_ANALYTICS = "com.adobe.eventType.contentAnalytics"
        const val XDM_CONTENT_ENGAGEMENT = "content.contentEngagement"
    }
    
    // Event Sources
    object EventSource {
        const val REQUEST_CONTENT = "com.adobe.eventSource.requestContent"
    }
    
    // Event Names
    object EventNames {
        // Public API events (dispatched for internal routing)
        const val TRACK_ASSET = "Track Asset"
        const val TRACK_EXPERIENCE = "Track Experience"
        
        // Edge Network events (dispatched to Adobe Experience Platform)
        const val CONTENT_ANALYTICS_ASSET = "Content Analytics Asset"
        const val CONTENT_ANALYTICS_EXPERIENCE = "Content Analytics Experience"
    }
    
    // Event Data Keys
    object EventDataKeys {
        // Asset tracking
        const val ASSET_URL = "assetURL"
        const val ASSET_LOCATION = "assetLocation"
        const val ASSET_ACTION = "action"
        const val ASSET_EXTRAS = "assetExtras"
        
        // Experience tracking
        const val EXPERIENCE_ID = "experienceId"
        const val EXPERIENCE_LOCATION = "experienceLocation"
        const val EXPERIENCE_ACTION = "action"
        const val EXPERIENCE_DEFINITION = "experienceDefinition"
        const val EXPERIENCE_EXTRAS = "experienceExtras"
        
        // Experience definition
        const val ASSETS = "assets"
        const val TEXTS = "texts"
        const val CTAS = "ctas"
        
        // ContentItem
        const val VALUE = "value"
        const val TYPE = "type"
    }
    
    // Action Types
    object ActionType {
        const val VIEW = "view"
        const val CLICK = "click"
    }
    
    // Content Types
    object ContentType {
        const val IMAGE = "image"
        const val VIDEO = "video"
        const val TEXT = "text"
        const val CTA = "cta"
    }
    
    // Configuration Keys
    object ConfigurationKeys {
        // Content Analytics specific keys
        const val BATCHING_ENABLED = "contentanalytics.batchingEnabled"
        const val MAX_BATCH_SIZE = "contentanalytics.maxBatchSize"
        const val BATCH_FLUSH_INTERVAL = "contentanalytics.batchFlushInterval"
        const val TRACK_EXPERIENCES = "contentanalytics.trackExperiences"
        const val EXCLUDED_ASSET_LOCATIONS_REGEXP = "contentanalytics.excludedAssetLocationsRegexp"
        const val EXCLUDED_ASSET_URLS_REGEXP = "contentanalytics.excludedAssetUrlsRegexp"
        const val EXCLUDED_EXPERIENCE_LOCATIONS_REGEXP = "contentanalytics.excludedExperienceLocationsRegexp"
        const val EXPERIENCE_CLOUD_ORG_ID = "contentanalytics.experienceCloudOrgId"
        const val DATASTREAM_ID = "contentanalytics.configId"  // Datastream ID (aligns with edge.configId naming)
        const val REGION = "contentanalytics.region"  // Org's home region (e.g., "va7", "irl1", "aus5", "jpn4")
        
        // Edge Network keys (published by Edge extension to Configuration shared state)
        const val EDGE_DOMAIN = "edge.domain"
        const val EDGE_CONFIG_ID = "edge.configId"
        const val EDGE_ENVIRONMENT = "edge.environment"
        
        // Experience Cloud keys (published to Configuration shared state)
        const val EXPERIENCE_CLOUD_ORG = "experienceCloud.org"
    }
    
    // Queue Names (matching iOS convention)
    const val ASSET_BATCH_QUEUE_NAME = "com.adobe.module.contentanalytics.assetbatch"
    const val EXPERIENCE_BATCH_QUEUE_NAME = "com.adobe.module.contentanalytics.experiencebatch"
    const val FEATURIZATION_QUEUE_NAME = "com.adobe.module.contentanalytics.featurization"
    
    // Log Labels
    object LogLabels {
        const val EXTENSION = "ContentAnalytics"
        const val ORCHESTRATOR = "ContentAnalytics.Orchestrator"
        const val STATE_MANAGER = "ContentAnalytics.StateManager"
        const val XDM_BUILDER = "ContentAnalytics.XDMBuilder"
        const val BATCH_PROCESSOR = "ContentAnalytics.BatchCoordinator"
        const val PRIVACY_VALIDATOR = "ContentAnalytics.PrivacyValidator"
        const val CONFIG = "ContentAnalytics.Config"
    }
    
    // External extension names used by ContentAnalytics
    object ExternalExtensions {
        const val CONFIGURATION = "com.adobe.module.configuration"
        const val CONSENT = "com.adobe.edge.consent"
        const val EVENT_HUB = "com.adobe.module.eventhub"
    }
    
    // Hub shared state keys
    object HubSharedState {
        const val EXTENSIONS_KEY = "extensions"
    }
    
    // Defaults
    object Defaults {
        const val MAX_BATCH_SIZE = 10
        const val BATCH_FLUSH_INTERVAL = 2000L // milliseconds
        const val TRACK_EXPERIENCES = true
        const val BATCHING_ENABLED = true
        const val FEATURIZATION_MAX_RETRIES = 3
        const val FEATURIZATION_RETRY_DELAY = 500L // milliseconds
    }
    
    // Featurization Service
    object Featurization {
        const val CHANNEL_MOBILE = "mobile"
    }
    
    // Entity Types
    object EntityType {
        const val ASSET = "asset"
        const val EXPERIENCE = "experience"
    }
}

