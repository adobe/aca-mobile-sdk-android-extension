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

/**
 * Scans DataQueue for items matching predicates
 * 
 * DataQueue is FIFO with no random access, so we read everything then restore the queue.
 */
internal class DataQueueScanner<T>(
    private val queue: DataQueue,
    private val decoder: (DataEntity) -> T?,
    private val label: String = "DataQueueScanner"
) {
    
    data class ScanResult<T>(
        val items: List<T>,
        val entities: List<DataEntity>,
        val entitiesScanned: Int,
        val failedDecodeCount: Int
    )
    
    fun scanAll(predicate: (T) -> Boolean = { true }): ScanResult<T> {
        val entities = mutableListOf<DataEntity>()
        val items = mutableListOf<T>()
        var iterationCount = 0
        var failedDecodeCount = 0
        
        // Read all entities from queue
        while (true) {
            iterationCount++
            
            val entity = queue.peek() ?: break
            
            // Remove from queue to advance (remove 1 entity from front)
            if (!queue.remove(1)) {
                Log.error(
                    ContentAnalyticsConstants.LOG_TAG,
                    label,
                    "Failed to remove entity during scan at iteration $iterationCount - stopping. " +
                    "Possible database corruption or concurrent access."
                )
                break
            }
            
            entities.add(entity)
            
            // Try to decode this entity
            val item = decoder(entity)
            if (item != null) {
                // Apply predicate filter
                if (predicate(item)) {
                    items.add(item)
                }
            } else {
                failedDecodeCount++
            }
        }
        
        // Restore queue state - re-add all entities
        for (entity in entities) {
            queue.add(entity)
        }
        
        return ScanResult(
            items = items,
            entities = entities,
            entitiesScanned = iterationCount,
            failedDecodeCount = failedDecodeCount
        )
    }
    
    fun findFirst(predicate: (T) -> Boolean): T? {
        val entities = mutableListOf<DataEntity>()
        var foundItem: T? = null
        var iterationCount = 0
        
        // Read all entities until we find a match
        while (true) {
            iterationCount++
            
            val entity = queue.peek() ?: break
            
            // Remove from queue to advance (remove 1 entity from front)
            if (!queue.remove(1)) {
                Log.error(
                    ContentAnalyticsConstants.LOG_TAG,
                    label,
                    "Failed to remove entity during scan at iteration $iterationCount - stopping"
                )
                break
            }
            
            entities.add(entity)
            
            // Try to decode and check predicate
            if (foundItem == null) {
                val item = decoder(entity)
                if (item != null && predicate(item)) {
                    foundItem = item
                    // Don't break - must read remaining entities to restore queue
                }
            }
        }
        
        // Restore queue state - re-add all entities
        for (entity in entities) {
            queue.add(entity)
        }
        
        return foundItem
    }
    
    fun scanWithEntities(predicate: (T) -> Boolean = { true }): List<Pair<DataEntity, T>> {
        val entities = mutableListOf<DataEntity>()
        val results = mutableListOf<Pair<DataEntity, T>>()
        var iterationCount = 0
        
        // Read all entities from queue
        while (true) {
            iterationCount++
            
            val entity = queue.peek() ?: break
            
            // Remove from queue to advance (remove 1 entity from front)
            if (!queue.remove(1)) {
                Log.error(
                    ContentAnalyticsConstants.LOG_TAG,
                    label,
                    "Failed to remove entity during scan at iteration $iterationCount - stopping"
                )
                break
            }
            
            entities.add(entity)
            
            // Try to decode this entity
            val item = decoder(entity)
            if (item != null) {
                // Apply predicate filter
                if (predicate(item)) {
                    results.add(Pair(entity, item))
                }
            }
        }
        
        // Restore queue state - re-add all entities
        for (entity in entities) {
            queue.add(entity)
        }
        
        return results
    }
}
