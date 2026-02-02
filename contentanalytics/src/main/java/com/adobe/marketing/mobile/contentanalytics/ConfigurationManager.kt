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

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Manages ContentAnalytics configuration and validation rules
 *
 * Responsibilities:
 * - Store and update configuration
 * - Validate tracking permissions (URL/location exclusions)
 * - Provide batching configuration
 *
 * Thread-safe: All operations use read-write locks
 */
internal class ConfigurationManager : ConfigurationManaging {
    
    // MARK: - Private Properties
    
    private val lock = ReentrantReadWriteLock()
    @Volatile
    private var _configuration: ContentAnalyticsConfiguration? = null
    
    // MARK: - Configuration Management
    
    /**
     * Update configuration (thread-safe)
     * @param config New configuration to apply
     */
    override fun updateConfiguration(config: ContentAnalyticsConfiguration) {
        lock.write {
            _configuration = config
        }
    }
    
    /**
     * Get current configuration (thread-safe)
     * @return Current configuration, or null if not yet configured
     */
    override fun getCurrentConfiguration(): ContentAnalyticsConfiguration? {
        return lock.read {
            _configuration
        }
    }
    
    /**
     * Check if batching is enabled
     * @return true if batching is enabled, false if disabled or no config
     */
    override val batchingEnabled: Boolean
        get() = lock.read {
            _configuration?.batchingEnabled ?: false
        }
    
    // MARK: - Tracking Validation
    
    /**
     * Generic tracking validation - reduces duplication across shouldTrack methods
     * @param value Value to validate (URL, location string, etc.)
     * @param validator Validation function that checks the value against config
     * @return true if tracking is allowed, false if excluded
     *
     * Returns true if:
     * - No configuration exists (default allow)
     * - Value is null (default allow)
     * - Validator returns true (not excluded)
     */
    private fun <T> shouldTrack(
        value: T?,
        validator: (ContentAnalyticsConfiguration, T) -> Boolean
    ): Boolean = lock.read {
        val config = _configuration ?: return@read true
        val val_ = value ?: return@read true
        return@read validator(config, val_)
    }
    
    /**
     * Check if a URL should be tracked (not excluded by patterns)
     * @param url URL to validate
     * @return true if tracking is allowed, false if excluded
     */
    override fun shouldTrackUrl(url: String): Boolean {
        return shouldTrack(url) { config, u -> !config.shouldExcludeUrl(u) }
    }
    
    /**
     * Check if an asset location should be tracked (not excluded)
     * @param location Location to validate
     * @return true if tracking is allowed, false if excluded
     */
    override fun shouldTrackAssetLocation(location: String?): Boolean {
        return shouldTrack(location) { config, loc -> !config.shouldExcludeAsset(loc) }
    }
    
    /**
     * Check if an experience location should be tracked (not excluded by patterns)
     * @param location Location to validate
     * @return true if tracking is allowed, false if excluded or location is null
     */
    override fun shouldTrackExperience(location: String?): Boolean {
        return shouldTrack(location) { config, loc -> !config.shouldExcludeExperience(loc) }
    }
    
    // MARK: - Reset
    
    /**
     * Clear all configuration (for testing or identity reset)
     */
    override fun reset() {
        lock.write {
            _configuration = null
        }
    }
}
