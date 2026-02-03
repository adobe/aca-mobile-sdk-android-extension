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

internal class DefinitionRepository : DefinitionRepositoryProtocol {
    
    private val lock = ReentrantReadWriteLock()
    
    @Volatile
    private var dataQueue: DataQueue? = null
    private val diskIndex = mutableMapOf<String, String>()
    
    override fun setDataQueue(queue: DataQueue?) {
        dataQueue = queue
    }
    
    override fun save(definition: ExperienceDefinition) {
        lock.write {
            val queue = dataQueue ?: return@write
            
            // Encode definition
            val definitionMap = definition.toMap()
            val jsonObject = JSONObject(definitionMap)
            val dataString = jsonObject.toString()
            
            val timestamp = System.currentTimeMillis()
            val uniqueIdentifier = "experience_${definition.experienceId}_$timestamp"
            
            val entity = DataEntity(
                uniqueIdentifier,
                Date(),
                dataString
            )
            
            if (queue.add(entity)) {
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
    
    override fun load(experienceId: String): ExperienceDefinition? {
        return lock.read {
            val queue = dataQueue ?: return@read null
            
            // Check index first to avoid expensive scan
            if (!diskIndex.containsKey(experienceId)) {
                Log.trace(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "Definition '$experienceId' not in disk index - skipping disk scan"
                )
                return@read null
            }
            
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
                // Index is stale
                Log.warning(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "Definition '$experienceId' in index but not found on disk - removing from index"
                )
                diskIndex.remove(experienceId)
                return@read null
            }
            
            // Return latest version if duplicates exist
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
    
    override fun restoreAll(capacity: Int): List<ExperienceDefinition> {
        return lock.write {
            val queue = dataQueue ?: return@write emptyList()
            
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
            
            // Deduplicate - keep latest version of each experienceId
            val definitionsByExperienceId = mutableMapOf<String, Pair<DataEntity, ExperienceDefinition>>()
            
            for ((entity, definition) in entityPairs) {
                val existing = definitionsByExperienceId[definition.experienceId]
                if (existing != null) {
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
            
            val sortedPairs = definitionsByExperienceId.values.sortedByDescending { it.first.timestamp }
            val topN = sortedPairs.take(capacity)
            
            Log.debug(
                ContentAnalyticsConstants.LOG_TAG,
                ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                "Restoring top ${topN.size} of ${definitionsByExperienceId.size} definition(s) to cache"
            )
            
            // Rewrite deduplicated definitions
            queue.clear()
            for ((entity, _) in sortedPairs) {
                queue.add(entity)
            }
            
            // Rebuild index
            diskIndex.clear()
            for ((entity, definition) in sortedPairs) {
                diskIndex[definition.experienceId] = entity.uniqueIdentifier
            }
            
            Log.debug(
                ContentAnalyticsConstants.LOG_TAG,
                ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                "Restored definitions to disk | Total: ${sortedPairs.size} | Index size: ${diskIndex.size}"
            )
            
            return@write topN.map { it.second }
        }
    }
    
    override fun contains(experienceId: String): Boolean {
        return lock.read {
            return@read diskIndex.containsKey(experienceId)
        }
    }
    
    // MARK: - Reset
    
    override fun clearAll() {
        lock.write {
            dataQueue?.clear()
            diskIndex.clear()
            
            Log.debug(
                ContentAnalyticsConstants.LOG_TAG,
                ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                "Cleared all persisted definitions from disk"
            )
        }
    }
}
