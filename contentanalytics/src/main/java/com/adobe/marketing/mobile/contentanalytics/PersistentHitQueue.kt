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
import com.adobe.marketing.mobile.services.DataQueuing
import com.adobe.marketing.mobile.services.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persistent queue wrapper using AEP SDK's DataQueue for crash recovery.
 */
internal class PersistentHitQueue(
    private val dataQueue: DataQueue,
    private val processor: HitProcessor? = null
) {
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
    }
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Processing state
    @Volatile
    private var isStarted = false
    
    init {
        // Set up processor for DataQueue
        processor?.let { setupProcessor(it) }
    }
    
    /**
     * Adds an entity to the persistent queue.
     */
    fun queue(entity: DataEntity): Boolean {
        val success = dataQueue.add(entity)
        
        if (success) {
            Log.trace(TAG, TAG, "Queued entity to DataQueue | ID: ${entity.uniqueIdentifier}")
        } else {
            Log.warning(TAG, TAG, "Failed to queue entity | ID: ${entity.uniqueIdentifier}")
        }
        
        return success
    }
    
    /**
     * Starts queue processing.
     */
    fun beginProcessing() {
        isStarted = true
        Log.debug(TAG, TAG, "Queue processing started")
        
        // Trigger processing of persisted entities (crash recovery)
        processPersistedEntities()
    }
    
    /**
     * Stops queue processing.
     */
    fun suspend() {
        isStarted = false
        Log.debug(TAG, TAG, "Queue processing suspended")
    }
    
    /**
     * Clears all entities from the queue.
     */
    fun clear() {
        dataQueue.clear()
        Log.debug(TAG, TAG, "Queue cleared")
    }
    
    /**
     * Returns the current queue size.
     */
    fun count(): Int {
        return dataQueue.count()
    }
    
    /**
     * Closes the queue and releases resources.
     */
    fun close() {
        isStarted = false
        dataQueue.close()
        scope.cancel()
        Log.debug(TAG, TAG, "Queue closed")
    }
    
    private fun setupProcessor(hitProcessor: HitProcessor) {
        // Processor integration handled by DataQueue
    }
    
    private fun processPersistedEntities() {
        if (!isStarted) return
        
        scope.launch {
            try {
                // Peek and process entities from DataQueue
                var entity = dataQueue.peek()
                
                while (entity != null && isStarted) {
                    val success = processor?.processHit(entity) ?: true
                    
                    if (success) {
                        dataQueue.remove()
                        Log.trace(TAG, TAG, "Entity processed and removed | ID: ${entity.uniqueIdentifier}")
                        entity = dataQueue.peek()
                    } else {
                        Log.warning(TAG, TAG, "Entity processing failed, will retry | ID: ${entity.uniqueIdentifier}")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.warning(TAG, TAG, "Error processing persisted entities: ${e.message}")
            }
        }
    }
}

/**
 * Processes hits retrieved from the persistent queue.
 */
internal interface HitProcessor {
    /**
     * Processes a hit entity.
     * @return true if processing succeeded, false to retry later
     */
    suspend fun processHit(entity: com.adobe.marketing.mobile.services.DataEntity): Boolean
}



