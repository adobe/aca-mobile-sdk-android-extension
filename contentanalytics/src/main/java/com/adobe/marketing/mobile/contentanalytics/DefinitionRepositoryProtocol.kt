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

import com.adobe.marketing.mobile.services.DataQueue

/**
 * Protocol for persistent storage of experience definitions
 *
 * Enables dependency injection and testing with mock implementations
 */
internal interface DefinitionRepositoryProtocol {
    
    /**
     * Set the DataQueue for persistence
     * @param queue DataQueue to use for storage
     */
    fun setDataQueue(queue: DataQueue?)
    
    /**
     * Save a definition to persistent storage
     * @param definition Definition to persist
     */
    fun save(definition: ExperienceDefinition)
    
    /**
     * Load a definition from persistent storage
     * @param experienceId ID of definition to load
     * @return Definition if found, null otherwise
     */
    fun load(experienceId: String): ExperienceDefinition?
    
    /**
     * Restore all persisted definitions
     * @param capacity Maximum number of definitions to return
     * @return List of definitions, sorted by most recent first
     */
    fun restoreAll(capacity: Int): List<ExperienceDefinition>
    
    /**
     * Check if a definition exists in persistent storage
     * @param experienceId ID to check
     * @return true if definition exists on disk
     */
    fun contains(experienceId: String): Boolean
    
    /**
     * Clear all persisted definitions
     */
    fun clearAll()
}
