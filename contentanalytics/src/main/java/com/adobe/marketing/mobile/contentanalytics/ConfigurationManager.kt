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

internal class ConfigurationManager : ConfigurationManaging {
    
    // MARK: - Private Properties
    
    private val lock = ReentrantReadWriteLock()
    @Volatile
    private var _configuration: ContentAnalyticsConfiguration? = null
    
    // MARK: - Configuration Management
    
    override fun updateConfiguration(config: ContentAnalyticsConfiguration) {
        lock.write {
            _configuration = config
        }
    }
    
    override fun getCurrentConfiguration(): ContentAnalyticsConfiguration? {
        return lock.read {
            _configuration
        }
    }
    
    override val batchingEnabled: Boolean
        get() = lock.read {
            _configuration?.batchingEnabled ?: false
        }
    
    // MARK: - Tracking Validation
    
    // Generic helper to reduce duplication
    private fun <T> shouldTrack(
        value: T?,
        validator: (ContentAnalyticsConfiguration, T) -> Boolean
    ): Boolean = lock.read {
        val config = _configuration ?: return@read true
        val val_ = value ?: return@read true
        return@read validator(config, val_)
    }
    
    override fun shouldTrackUrl(url: String): Boolean {
        return shouldTrack(url) { config, u -> !config.shouldExcludeUrl(u) }
    }
    
    override fun shouldTrackAssetLocation(location: String?): Boolean {
        return shouldTrack(location) { config, loc -> !config.shouldExcludeAsset(loc) }
    }
    
    override fun shouldTrackExperience(location: String?): Boolean {
        return shouldTrack(location) { config, loc -> !config.shouldExcludeExperience(loc) }
    }
    
    // MARK: - Reset
    
    override fun reset() {
        lock.write {
            _configuration = null
        }
    }
}
