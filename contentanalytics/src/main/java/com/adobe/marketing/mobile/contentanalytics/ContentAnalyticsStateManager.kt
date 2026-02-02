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

import com.adobe.marketing.mobile.services.DataEntity
import com.adobe.marketing.mobile.services.DataQueue
import com.adobe.marketing.mobile.services.Log
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe state manager for Content Analytics extension
 * 
 * Manages experience definitions in memory with disk persistence.
 * Coordinates between ConfigurationManager, cache, and disk operations.
 * Uses read-write locks for thread safety with minimal contention.
 */
internal class ContentAnalyticsStateManager(
    private val configManager: ConfigurationManaging = ConfigurationManager(),
    private val definitionCache: DefinitionCacheProtocol = DefinitionCache(),
    private val definitionRepository: DefinitionRepositoryProtocol = DefinitionRepository()
) {
    
    private val lock = ReentrantReadWriteLock()
    
    
    // MARK: - Configuration Management (Delegated to ConfigurationManager)
    
    /**
     * Get current configuration (thread-safe)
     */
    val configuration: ContentAnalyticsConfiguration?
        get() = configManager.getCurrentConfiguration()
    
    /**
     * Check if batching is enabled (convenience getter)
     */
    val batchingEnabled: Boolean
        get() = configManager.batchingEnabled
    
    /**
     * Update configuration (thread-safe)
     */
    fun updateConfiguration(config: ContentAnalyticsConfiguration) {
        configManager.updateConfiguration(config)
    }
    
    /**
     * Sets the persistent storage queue for experience definitions
     * Should be called once during extension initialization
     */
    fun setDefinitionsDataQueue(queue: DataQueue?) {
        // Set queue on repository
        definitionRepository.setDataQueue(queue)
        
        if (queue != null) {
            lock.write {
                // Restore persisted definitions from disk
                val cacheCapacity = 100 // LRU cache capacity
                val definitions = definitionRepository.restoreAll(cacheCapacity)
                
                // Load restored definitions into cache
                for (definition in definitions) {
                    definitionCache.store(definition)
                }
                
                Log.debug(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "Restored ${definitions.size} definitions from disk to cache"
                )
            }
        }
    }
    
    
    
    // MARK: - URL Exclusion (Delegated to ConfigurationManager)
    
    /**
     * Check if a URL should be tracked (not excluded by patterns)
     */
    fun shouldTrackUrl(url: String): Boolean {
        return configManager.shouldTrackUrl(url)
    }
    
    /**
     * Check if an asset location should be tracked (not excluded)
     */
    fun shouldTrackAssetLocation(location: String?): Boolean {
        return configManager.shouldTrackAssetLocation(location)
    }
    
    /**
     * Check if an experience location should be tracked (not excluded by patterns)
     */
    fun shouldTrackExperience(location: String?): Boolean {
        return configManager.shouldTrackExperience(location)
    }
    
    
    /**
     * Register an experience definition
     * Persists to both memory and disk for crash resilience
     */
    fun registerExperienceDefinition(definition: ExperienceDefinition) = lock.write {
        // Store in memory
        definitionCache.store(definition)
        
        // Persist to disk (delegated to repository)
        definitionRepository.save(definition)
    }
    
    
    /**
     * Get experience definition by ID
     * Checks memory cache first, falls back to disk if not found (transparent lazy load)
     */
    fun getExperienceDefinition(experienceId: String): ExperienceDefinition? {
        // Fast path: Check memory cache first (read lock only)
        lock.read {
            definitionCache.get(experienceId)?.let { return it }
        }
        
        // Slow path: Cache miss - try loading from disk (write lock to update cache)
        return lock.write {
            // Double-check in case another thread just loaded it
            definitionCache.get(experienceId)?.let { return@write it }
            
            // Load from disk (delegated to repository)
            val definition = definitionRepository.load(experienceId)
            if (definition != null) {
                // Restore to cache for future access
                definitionCache.store(definition)
            } else {
                // Not found - warn developer about possible API misuse
                Log.warning(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "⚠️ Experience definition not found for '$experienceId'. " +
                    "Make sure to call ContentAnalytics.trackExperience() with " +
                    "interactionType: DEFINITION (including assetURLs and texts) before tracking views/clicks."
                )
            }
            definition
        }
    }
    
    /**
     * Get all registered experience definitions
     */
    fun getAllExperienceDefinitions(): List<ExperienceDefinition> = lock.read {
        return definitionCache.getAllDefinitions()
    }
    
    /**
     * Clear all experience definitions
     */
    fun clearExperienceDefinitions() = lock.write {
        definitionCache.removeAll()
    }
    
    
    /**
     * Check if an experience definition has been sent to featurization service
     * Checks memory cache first, falls back to disk if not found (transparent lazy load)
     */
    fun hasExperienceDefinitionBeenSent(experienceId: String): Boolean = lock.read {
        // Check memory first
        definitionCache.get(experienceId)?.let {
            return it.sentToFeaturization
        }
        
        // Check disk (delegated to repository)
        val diskDef = definitionRepository.load(experienceId)
        return diskDef?.sentToFeaturization ?: false
    }
    
    /**
     * Mark an experience definition as sent to featurization service
     * Updates both memory and disk storage
     * If not in memory cache, loads from disk first (transparent lazy load)
     */
    fun markExperienceDefinitionAsSent(experienceId: String) = lock.write {
        // Try memory first
        var definition = definitionCache.get(experienceId)
        
        // Cache miss - load from disk (delegated to repository)
        if (definition == null) {
            definition = definitionRepository.load(experienceId)
        }
        
        // Update definition if found (either in memory or on disk)
        if (definition != null) {
            val updatedDefinition = definition.copy(sentToFeaturization = true)
            definitionCache.update(updatedDefinition)
            
            // Update persisted definition (delegated to repository)
            definitionRepository.save(updatedDefinition)
        }
    }
    
    
    /**
     * Reset all state (used for identity reset)
     * Clears both memory and disk storage
     */
    fun reset() = lock.write {
        // Clear configuration (delegated to ConfigurationManager)
        configManager.reset()
        
        // Clear in-memory definitions
        definitionCache.removeAll()
        
        // Clear persisted definitions from disk (delegated to repository)
        definitionRepository.clearAll()
    }
    
    
    /**
     * Get asset URLs associated with an experience
     * Used for asset attribution in experience events
     */
    fun getAssetsForExperience(experienceId: String): List<String> = lock.read {
        return definitionCache.get(experienceId)?.assets ?: emptyList()
    }
    
    
    /**
     * Get count of registered experience definitions
     */
    fun getExperienceDefinitionCount(): Int = lock.read {
        return definitionCache.count
    }
    
    /**
     * Get count of sent experience definitions (counts in-memory definitions only)
     */
    fun getSentExperienceDefinitionCount(): Int = lock.read {
        return definitionCache.getSentCount()
    }
}
