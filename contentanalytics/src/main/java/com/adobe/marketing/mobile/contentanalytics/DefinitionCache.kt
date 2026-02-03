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
 * LRU cache for experience definitions
 */
internal class DefinitionCache(
    capacity: Int = 100
) : DefinitionCacheProtocol {
    
    // MARK: - Private Properties
    
    private val cache = LRUCache<String, ExperienceDefinition>(capacity)
    
    // MARK: - Definition Management
    
    override fun store(definition: ExperienceDefinition) {
        cache.set(definition.experienceId, definition)
    }
    
    override fun get(experienceId: String): ExperienceDefinition? {
        return cache.get(experienceId)
    }
    
    override fun contains(experienceId: String): Boolean {
        return cache.get(experienceId) != null
    }
    
    override fun update(definition: ExperienceDefinition) {
        cache.set(definition.experienceId, definition)
    }
    
    override fun getAllDefinitions(): List<ExperienceDefinition> {
        return cache.values()
    }
    
    override val count: Int
        get() = cache.count()
    
    // MARK: - Featurization Tracking
    
    override fun markAsSent(experienceId: String): ExperienceDefinition? {
        val definition = cache.get(experienceId) ?: return null
        
        val updated = definition.copy(sentToFeaturization = true)
        cache.set(experienceId, updated)
        return updated
    }
    
    override fun hasBeenSent(experienceId: String): Boolean {
        val definition = cache.get(experienceId) ?: return false
        return definition.sentToFeaturization
    }
    
    override fun getSentCount(): Int {
        return cache.values().count { it.sentToFeaturization }
    }
    
    // MARK: - Reset
    
    override fun removeAll() {
        cache.removeAll()
    }
}
