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
 * In-memory cache for experience definitions with LRU eviction
 *
 * Responsibilities:
 * - Store experience definitions in memory with size limit
 * - Provide fast O(1) access to definitions
 * - Evict least recently used definitions when capacity is reached
 * - Track which definitions have been sent to featurization
 *
 * Thread-safe: All operations are synchronized internally by LRUCache
 */
internal class DefinitionCache(
    capacity: Int = 100
) : DefinitionCacheProtocol {
    
    // MARK: - Private Properties
    
    private val cache = LRUCache<String, ExperienceDefinition>(capacity)
    
    // MARK: - Definition Management
    
    /**
     * Store a definition in cache
     * @param definition Definition to store
     *
     * If cache is at capacity, least recently used definition will be evicted
     */
    override fun store(definition: ExperienceDefinition) {
        cache.set(definition.experienceId, definition)
    }
    
    /**
     * Retrieve a definition from cache
     * @param experienceId ID of definition to retrieve
     * @return Definition if found in cache, null otherwise
     */
    override fun get(experienceId: String): ExperienceDefinition? {
        return cache.get(experienceId)
    }
    
    /**
     * Check if a definition exists in cache
     * @param experienceId ID to check
     * @return true if definition is in cache, false otherwise
     */
    override fun contains(experienceId: String): Boolean {
        return cache.get(experienceId) != null
    }
    
    /**
     * Update an existing definition in cache
     * @param definition Updated definition
     *
     * If definition doesn't exist in cache, it will be added
     */
    override fun update(definition: ExperienceDefinition) {
        cache.set(definition.experienceId, definition)
    }
    
    /**
     * Get all definitions currently in cache
     * @return List of all cached definitions
     *
     * Note: This requires scanning the cache, so use sparingly
     */
    override fun getAllDefinitions(): List<ExperienceDefinition> {
        return cache.values()
    }
    
    /**
     * Get count of definitions in cache
     * @return Number of definitions currently cached
     */
    override val count: Int
        get() = cache.count()
    
    // MARK: - Featurization Tracking
    
    /**
     * Mark a definition as sent to featurization
     * @param experienceId ID of definition to mark
     * @return Updated definition if found in cache, null otherwise
     */
    override fun markAsSent(experienceId: String): ExperienceDefinition? {
        val definition = cache.get(experienceId) ?: return null
        
        val updated = definition.copy(sentToFeaturization = true)
        cache.set(experienceId, updated)
        return updated
    }
    
    /**
     * Check if a definition has been sent to featurization
     * @param experienceId ID to check
     * @return true if sent, false if not sent or not in cache
     */
    override fun hasBeenSent(experienceId: String): Boolean {
        val definition = cache.get(experienceId) ?: return false
        return definition.sentToFeaturization
    }
    
    /**
     * Get count of definitions sent to featurization
     * @return Number of definitions marked as sent
     *
     * Note: This requires scanning the cache, so use sparingly
     */
    override fun getSentCount(): Int {
        return cache.values().count { it.sentToFeaturization }
    }
    
    // MARK: - Reset
    
    /**
     * Clear all definitions from cache
     */
    override fun removeAll() {
        cache.removeAll()
    }
}
