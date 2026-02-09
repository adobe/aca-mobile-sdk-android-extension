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
 * Protocol for configuration management and validation
 *
 * Enables dependency injection and testing with mock implementations
 */
internal interface ConfigurationManaging {
    
    /**
     * Update configuration
     * @param config New configuration to apply
     */
    fun updateConfiguration(config: ContentAnalyticsConfiguration)
    
    /**
     * Get current configuration
     * @return Current configuration, or null if not set
     */
    fun getCurrentConfiguration(): ContentAnalyticsConfiguration?
    
    /**
     * Check if batching is enabled
     */
    val batchingEnabled: Boolean
    
    /**
     * Check if a URL should be tracked
     * @param url URL to validate
     * @return true if URL should be tracked
     */
    fun shouldTrackUrl(url: String): Boolean
    
    /**
     * Check if an experience should be tracked
     * @param location Experience location to validate
     * @return true if experience should be tracked
     */
    fun shouldTrackExperience(location: String?): Boolean
    
    /**
     * Check if an asset location should be tracked
     * @param location Asset location to validate
     * @return true if asset location should be tracked
     */
    fun shouldTrackAssetLocation(location: String?): Boolean
    
    /**
     * Reset configuration to initial state
     */
    fun reset()
}
