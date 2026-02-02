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
import java.util.Date
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Manages persistent storage of experience definitions
 *
 * Responsibilities:
 * - Save definitions to disk (DataQueue)
 * - Load definitions from disk
 * - Restore all persisted definitions on startup
 * - Maintain disk index for fast lookups
 * - Handle deduplication of persisted definitions
 *
 * Thread-safe: All operations use read-write locks
 */
internal class DefinitionRepository : DefinitionRepositoryProtocol {
    
    // MARK: - Private Properties
    
    private val lock = ReentrantReadWriteLock()
    
    /** Persistent storage queue (DataQueue backed by SQLite) */
    @Volatile
    private var dataQueue: DataQueue? = null
    
    /** In-memory index mapping experienceId -> DataEntity.uniqueIdentifier */
    private val diskIndex = mutableMapOf<String, String>()
    
    // MARK: - Configuration
    
    /**
     * Set the DataQueue for persistence
     * @param queue DataQueue to use for storage
     *
     * Should be called once during initialization
     */
    override fun setDataQueue(queue: DataQueue?) {
        dataQueue = queue
    }
    
    // MARK: - Persistence Operations
    
    /**
     * Save a definition to disk
     * @param definition Definition to persist
     *
     * Uses experienceId as unique identifier to enable updates
     */
    override fun save(definition: ExperienceDefinition) {
        lock.write {
            val queue = dataQueue ?: return@write
            
            // Encode definition
            val definitionMap = definition.toMap()
            val jsonObject = JSONObject(definitionMap)
            val dataString = jsonObject.toString()
            
            // Create truly unique identifier (experienceId + timestamp)
            // We use experienceId as prefix to enable deduplication during restore
            val timestamp = System.currentTimeMillis()
            val uniqueIdentifier = "experience_${definition.experienceId}_$timestamp"
            
            val entity = DataEntity(
                uniqueIdentifier,
                Date(),
                dataString
            )
            
            // DataQueue allows duplicate uniqueIdentifiers - deduplication happens during restore
            if (queue.add(entity)) {
                // Update disk index (track that this experienceId exists on disk)
                diskIndex[definition.experienceId] = uniqueIdentifier
                
                Log.trace(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "Definition persisted to disk | ID: ${definition.experienceId}"
                )
            } else {
                Log.error(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "Failed to persist definition to disk | ID: ${definition.experienceId} | " +
                    "Queue may be full or serialization failed"
                )
            }
        }
    }
    
    /**
     * Load a definition from disk by experienceId
     * @param experienceId ID of definition to load
     * @return Definition if found, null otherwise
     *
     * Uses disk index for O(1) existence check before scanning
     */
    override fun load(experienceId: String): ExperienceDefinition? {
        return lock.read {
            val queue = dataQueue ?: return@read null
            
            // Fast path: Check if this experienceId exists on disk using index
            if (!diskIndex.containsKey(experienceId)) {
                Log.trace(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "Definition '$experienceId' not in disk index - skipping disk scan"
                )
                return@read null
            }
            
            // Use scanner to find ALL matching definitions (there may be duplicates)
            val scanner = DataQueueScanner<ExperienceDefinition>(
                queue = queue,
                decoder = { entity ->
                    try {
                        val jsonString = entity.data ?: return@DataQueueScanner null
                        val jsonObject = JSONObject(jsonString)
                        val definitionMap = JSONUtils.jsonObjectToMap(jsonObject)
                        ExperienceDefinition.fromMap(definitionMap)
                    } catch (e: Exception) {
                        null
                    }
                },
                label = ContentAnalyticsConstants.LogLabels.STATE_MANAGER
            )
            
            val entityPairs = scanner.scanWithEntities { it.experienceId == experienceId }
            
            if (entityPairs.isEmpty()) {
                // Index said it exists, but we didn't find it - index may be stale
                Log.warning(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "Definition '$experienceId' in index but not found on disk - removing from index"
                )
                diskIndex.remove(experienceId)
                return@read null
            }
            
            // If multiple versions exist (before deduplication), return the latest by timestamp
            val latestPair = entityPairs.maxByOrNull { it.first.timestamp }
            val foundDefinition = latestPair?.second
            
            if (foundDefinition != null) {
                Log.debug(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "Loaded definition from disk | ID: '$experienceId' | Found ${entityPairs.size} version(s), returning latest"
                )
            }
            
            return@read foundDefinition
        }
    }
    
    /**
     * Restore all persisted definitions from disk
     * @param capacity Maximum number of definitions to return (for LRU cache)
     * @return List of definitions to load into cache, sorted by most recent first
     *
     * Performs deduplication: keeps only latest version of each experienceId
     * Returns top N most recent definitions for cache, but rebuilds full disk index
     */
    override fun restoreAll(capacity: Int): List<ExperienceDefinition> {
        return lock.write {
            val queue = dataQueue ?: return@write emptyList()
            
            // Use scanner to read all entities with their definitions
            val scanner = DataQueueScanner<ExperienceDefinition>(
                queue = queue,
                decoder = { entity ->
                    try {
                        val jsonString = entity.data ?: return@DataQueueScanner null
                        val jsonObject = JSONObject(jsonString)
                        val definitionMap = JSONUtils.jsonObjectToMap(jsonObject)
                        ExperienceDefinition.fromMap(definitionMap)
                    } catch (e: Exception) {
                        Log.warning(
                            ContentAnalyticsConstants.LOG_TAG,
                            ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                            "Failed to parse entity during scan: ${e.message}"
                        )
                        null
                    }
                },
                label = ContentAnalyticsConstants.LogLabels.STATE_MANAGER
            )
            
            val entityPairs = scanner.scanWithEntities()
            
            Log.debug(
                ContentAnalyticsConstants.LOG_TAG,
                ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                "Scanned ${entityPairs.size} entities from disk"
            )
            
            // Deduplicate: Group by experienceId and keep only latest (by timestamp)
            val definitionsByExperienceId = mutableMapOf<String, Pair<DataEntity, ExperienceDefinition>>()
            
            for ((entity, definition) in entityPairs) {
                // Check if we already have this experienceId
                val existing = definitionsByExperienceId[definition.experienceId]
                if (existing != null) {
                    // Keep the one with latest timestamp (>= to handle collisions)
                    if (entity.timestamp >= existing.first.timestamp) {
                        definitionsByExperienceId[definition.experienceId] = Pair(entity, definition)
                    }
                } else {
                    definitionsByExperienceId[definition.experienceId] = Pair(entity, definition)
                }
            }
            
            val duplicatesRemoved = entityPairs.size - definitionsByExperienceId.size
            if (duplicatesRemoved > 0) {
                Log.debug(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "Removed $duplicatesRemoved duplicate definition(s) during restore"
                )
            }
            
            // Performance warning: Large definition counts may impact performance
            if (definitionsByExperienceId.size > 500) {
                Log.warning(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "⚠️ Large definition count (${definitionsByExperienceId.size}) detected. " +
                    "This may impact performance due to O(n) disk scans on cache misses. " +
                    "Consider upgrading to a future version with optimized storage, or reducing " +
                    "the number of unique experience definitions."
                )
            }
            
            // Sort by timestamp (most recent first) and take top N for cache
            val sortedPairs = definitionsByExperienceId.values.sortedByDescending { it.first.timestamp }
            val topN = sortedPairs.take(capacity)
            
            Log.debug(
                ContentAnalyticsConstants.LOG_TAG,
                ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                "Restoring top ${topN.size} of ${definitionsByExperienceId.size} definition(s) to cache"
            )
            
            // Clear and re-add deduplicated definitions to disk
            queue.clear()
            
            for ((entity, _) in sortedPairs) {
                queue.add(entity)
            }
            
            // Rebuild disk index with ALL definitions (not just top N)
            diskIndex.clear()
            for ((entity, definition) in sortedPairs) {
                diskIndex[definition.experienceId] = entity.uniqueIdentifier
            }
            
            Log.debug(
                ContentAnalyticsConstants.LOG_TAG,
                ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                "Restored definitions to disk | Total: ${sortedPairs.size} | Index size: ${diskIndex.size}"
            )
            
            // Return top N definitions for cache
            return@write topN.map { it.second }
        }
    }
    
    /**
     * Check if a definition exists on disk
     * @param experienceId ID to check
     * @return true if exists on disk, false otherwise
     */
    override fun contains(experienceId: String): Boolean {
        return lock.read {
            return@read diskIndex.containsKey(experienceId)
        }
    }
    
    // MARK: - Reset
    
    /**
     * Clear all persisted definitions and disk index
     */
    override fun clearAll() {
        lock.write {
            // Clear disk storage
            dataQueue?.clear()
            
            // Clear disk index
            diskIndex.clear()
            
            Log.debug(
                ContentAnalyticsConstants.LOG_TAG,
                ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                "Cleared all persisted definitions from disk"
            )
        }
    }
}
