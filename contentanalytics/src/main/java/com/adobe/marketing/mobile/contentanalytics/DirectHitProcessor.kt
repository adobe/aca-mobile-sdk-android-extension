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

import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.services.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Accumulates events in memory and dispatches on flush.
 * Used by BatchCoordinator for batching. Disk persistence handled separately.
 */
internal class DirectHitProcessor(
    private val type: BatchHitType
) : HitProcessor {
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
    }
    
    private val accumulatedEvents = mutableListOf<Event>()
    private val accumulatedEventIds = mutableSetOf<String>()
    private var processingCallback: ((List<Event>) -> Unit)? = null
    private val mutex = Mutex()
    
    fun setCallback(callback: (List<Event>) -> Unit) {
        processingCallback = callback
    }
    
    suspend fun accumulateEvent(event: Event) {
        mutex.withLock {
            accumulatedEvents.add(event)
            accumulatedEventIds.add(event.uniqueIdentifier)
            Log.trace(TAG, TAG, "Accumulated $type event | ID: ${event.uniqueIdentifier} | Total: ${accumulatedEvents.size}")
        }
    }
    
    /**
     * Process hit from disk during crash recovery.
     * Accumulates event in memory if not already present.
     * Returns true to remove from disk (we'll clear disk after dispatch).
     */
    override suspend fun processHit(entity: com.adobe.marketing.mobile.services.DataEntity): Boolean {
        return mutex.withLock {
            val event = DataEntityHelper.toEvent(entity)
            
            if (event == null) {
                Log.warning(TAG, TAG, "Failed to decode event | Type: $type | ID: ${entity.uniqueIdentifier}")
                return@withLock true  // Remove corrupted data
            }
            
            val eventId = event.uniqueIdentifier
            
            // Accumulate for crash recovery if not already in memory
            if (!accumulatedEventIds.contains(eventId)) {
                accumulatedEvents.add(event)
                accumulatedEventIds.add(eventId)
                Log.trace(TAG, TAG, "Recovered event from disk | Type: $type | ID: $eventId")
            }
            
            // Return true to clear from disk - we've accumulated it in memory
            // Disk will be cleared after successful dispatch to Edge
            return@withLock true
        }
    }
    
    suspend fun processAccumulatedEvents(): List<Event> {
        val eventsToProcess = mutex.withLock {
            if (accumulatedEvents.isEmpty()) {
                return@withLock emptyList<Event>()
            }
            
            Log.debug(TAG, TAG, "Processing ${accumulatedEvents.size} accumulated $type events")
            
            val events = accumulatedEvents.toList()
            accumulatedEvents.clear()
            accumulatedEventIds.clear()
            events
        }
        
        if (eventsToProcess.isNotEmpty()) {
            processingCallback?.invoke(eventsToProcess)
        }
        
        return eventsToProcess
    }
    
    suspend fun clear() {
        mutex.withLock {
            accumulatedEvents.clear()
            accumulatedEventIds.clear()
        }
    }
    
    suspend fun getAccumulatedCount(): Int {
        return mutex.withLock {
            accumulatedEvents.size
        }
    }
}

/**
 * Type of batch hit (asset or experience)
 */
internal enum class BatchHitType {
    ASSET,
    EXPERIENCE
}
