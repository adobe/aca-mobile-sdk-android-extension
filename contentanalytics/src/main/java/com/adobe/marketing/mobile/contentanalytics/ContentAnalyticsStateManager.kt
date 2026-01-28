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
 * Thread-safe state manager for Content Analytics extension
 * 
 * Manages configuration and experience definitions in memory.
 * Uses read-write locks for thread safety with minimal contention.
 */
internal class ContentAnalyticsStateManager {
    
    private val lock = ReentrantReadWriteLock()
    
    // Current configuration
    @Volatile
    private var _configuration: ContentAnalyticsConfiguration? = null
    
    // Registered experience definitions (for asset attribution and featurization tracking)
    private val experienceDefinitions = mutableMapOf<String, ExperienceDefinition>()
    
    // Experience definitions sent to featurization service (to avoid duplicates)
    private val sentExperienceDefinitions = mutableSetOf<String>()
    
    
    /**
     * Get current configuration (thread-safe)
     */
    val configuration: ContentAnalyticsConfiguration?
        get() = _configuration
    
    /**
     * Check if batching is enabled (convenience getter)
     */
    val batchingEnabled: Boolean
        get() = _configuration?.batchingEnabled ?: ContentAnalyticsConstants.Defaults.BATCHING_ENABLED
    
    /**
     * Update configuration (thread-safe)
     */
    fun updateConfiguration(config: ContentAnalyticsConfiguration) {
        _configuration = config
    }
    
    
    /**
     * Check if a URL should be tracked (not excluded by patterns)
     */
    fun shouldTrackUrl(url: String): Boolean = lock.read {
        val config = _configuration ?: return true
        return !config.shouldExcludeUrl(url)
    }
    
    /**
     * Check if an asset location should be tracked (not excluded)
     */
    fun shouldTrackAssetLocation(location: String?): Boolean = lock.read {
        val config = _configuration ?: return true
        return !config.shouldExcludeAsset(location)
    }
    
    /**
     * Check if an experience location should be tracked (not excluded by patterns)
     */
    fun shouldTrackExperience(location: String?): Boolean = lock.read {
        val config = _configuration ?: return true
        return !config.shouldExcludeExperience(location)
    }
    
    
    /**
     * Register an experience definition
     */
    fun registerExperienceDefinition(definition: ExperienceDefinition) = lock.write {
        experienceDefinitions[definition.experienceId] = definition
    }
    
    /**
     * Get experience definition by ID
     */
    fun getExperienceDefinition(experienceId: String): ExperienceDefinition? = lock.read {
        return experienceDefinitions[experienceId]
    }
    
    /**
     * Get all registered experience definitions
     */
    fun getAllExperienceDefinitions(): List<ExperienceDefinition> = lock.read {
        return experienceDefinitions.values.toList()
    }
    
    /**
     * Clear all experience definitions
     */
    fun clearExperienceDefinitions() = lock.write {
        experienceDefinitions.clear()
        sentExperienceDefinitions.clear()
    }
    
    
    /**
     * Check if an experience definition has been sent to featurization service
     */
    fun hasExperienceDefinitionBeenSent(experienceId: String): Boolean = lock.read {
        return experienceId in sentExperienceDefinitions
    }
    
    /**
     * Mark an experience definition as sent to featurization service
     */
    fun markExperienceDefinitionAsSent(experienceId: String) = lock.write {
        sentExperienceDefinitions.add(experienceId)
        
        // Update the definition's sentToFeaturization flag
        experienceDefinitions[experienceId]?.let { definition ->
            experienceDefinitions[experienceId] = definition.copy(sentToFeaturization = true)
        }
    }
    
    
    /**
     * Reset all state (used for identity reset)
     */
    fun reset() = lock.write {
        _configuration = null
        experienceDefinitions.clear()
        sentExperienceDefinitions.clear()
    }
    
    
    /**
     * Get asset URLs associated with an experience
     * Used for asset attribution in experience events
     */
    fun getAssetsForExperience(experienceId: String): List<String> = lock.read {
        return experienceDefinitions[experienceId]?.assets ?: emptyList()
    }
    
    
    /**
     * Get count of registered experience definitions
     */
    fun getExperienceDefinitionCount(): Int = lock.read {
        return experienceDefinitions.size
    }
    
    /**
     * Get count of sent experience definitions
     */
    fun getSentExperienceDefinitionCount(): Int = lock.read {
        return sentExperienceDefinitions.size
    }
}

