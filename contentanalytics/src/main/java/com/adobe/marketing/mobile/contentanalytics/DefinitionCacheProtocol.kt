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
 * Protocol for in-memory cache of experience definitions
 *
 * Enables dependency injection and testing with mock implementations
 */
internal interface DefinitionCacheProtocol {
    
    /**
     * Store a definition in cache
     * @param definition Definition to cache
     */
    fun store(definition: ExperienceDefinition)
    
    /**
     * Retrieve a definition from cache
     * @param experienceId ID of definition to retrieve
     * @return Definition if found, null otherwise
     */
    fun get(experienceId: String): ExperienceDefinition?
    
    /**
     * Check if cache contains a definition
     * @param experienceId ID to check
     * @return true if definition exists in cache
     */
    fun contains(experienceId: String): Boolean
    
    /**
     * Update an existing definition in cache
     * @param definition Updated definition
     */
    fun update(definition: ExperienceDefinition)
    
    /**
     * Get all cached definitions
     * @return List of all definitions in cache
     */
    fun getAllDefinitions(): List<ExperienceDefinition>
    
    /**
     * Get count of cached definitions
     */
    val count: Int
    
    /**
     * Mark a definition as sent to featurization
     * @param experienceId ID of definition to mark
     * @return Updated definition if found
     */
    fun markAsSent(experienceId: String): ExperienceDefinition?
    
    /**
     * Check if a definition has been sent to featurization
     * @param experienceId ID to check
     * @return true if definition has been sent
     */
    fun hasBeenSent(experienceId: String): Boolean
    
    /**
     * Get count of definitions that have been sent
     * @return Count of sent definitions
     */
    fun getSentCount(): Int
    
    /**
     * Remove all definitions from cache
     */
    fun removeAll()
}
