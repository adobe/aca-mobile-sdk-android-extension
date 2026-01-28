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
 * Reads persisted events from disk, accumulates in memory, and dispatches on flush.
 * Used by BatchCoordinator to integrate with PersistentHitQueue for crash recovery.
 */
internal class DirectHitProcessor(
    private val type: BatchHitType
) : HitProcessor {
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
    }
    
    private val accumulatedEvents = mutableListOf<Event>()
    private val dispatchedEventIds = mutableSetOf<String>()
    private var processingCallback: ((List<Event>) -> Unit)? = null
    private val mutex = Mutex()
    
    /**
     * Set callback for dispatching accumulated events.
     */
    fun setCallback(callback: (List<Event>) -> Unit) {
        processingCallback = callback
    }
    
    /**
     * Accumulate event in memory for batching.
     */
    suspend fun accumulateEvent(event: Event) {
        mutex.withLock {
            accumulatedEvents.add(event)
            Log.trace(TAG, TAG, "Accumulated $type event in memory | ID: ${event.uniqueIdentifier} | Total: ${accumulatedEvents.size}")
        }
    }
    
    /**
     * Process hit from persistent queue during crash recovery.
     * - Normal operation: Event already in memory, keep on disk until dispatched
     * - Crash recovery: Event not in memory, accumulate from disk
     * - After dispatch: Event ID tracked, remove from disk
     */
    override suspend fun processHit(entity: com.adobe.marketing.mobile.services.DataEntity): Boolean {
        return mutex.withLock {
            val event = DataEntityHelper.toEvent(entity)
            
            if (event == null) {
                Log.warning(TAG, TAG, "Failed to decode event | Type: $type | ID: ${entity.uniqueIdentifier}")
                return@withLock true  // Remove corrupted data
            }
            
            val eventId = event.uniqueIdentifier
            
            // If already dispatched to Edge, remove from disk
            if (dispatchedEventIds.contains(eventId)) {
                Log.trace(TAG, TAG, "Event dispatched, removing from disk | ID: $eventId")
                return@withLock true  // Remove from disk
            }
            
            // Otherwise, accumulate in memory but keep on disk until dispatched
            val alreadyAccumulated = accumulatedEvents.any { it.uniqueIdentifier == eventId }
            
            if (!alreadyAccumulated) {
                accumulatedEvents.add(event)
                Log.trace(TAG, TAG, "Event accumulated, keeping on disk | Type: $type | ID: $eventId")
            }
            
            return@withLock false  // Keep on disk until dispatched to Edge
        }
    }
    
    /**
     * Process all accumulated events and dispatch via callback.
     * 
     * @return The events being processed (for tracking dispatched events)
     */
    suspend fun processAccumulatedEvents(): List<Event> {
        val eventsToProcess = mutex.withLock {
            if (accumulatedEvents.isEmpty()) {
                return@withLock emptyList<Event>()
            }
            
            Log.debug(TAG, TAG, "Processing ${accumulatedEvents.size} accumulated $type events")
            
            val events = accumulatedEvents.toList()
            accumulatedEvents.clear()
            events
        }
        
        if (eventsToProcess.isNotEmpty()) {
            processingCallback?.invoke(eventsToProcess)
        }
        
        return eventsToProcess
    }
    
    /**
     * Mark events as dispatched to Edge Network.
     * 
     * Called by BatchCoordinator after events have been sent to Edge.
     * This allows processHit() to remove these events from disk on next processing cycle.
     * 
     * @param events Events that have been successfully dispatched
     */
    suspend fun markEventsAsDispatched(events: List<Event>) {
        mutex.withLock {
            events.forEach { event ->
                dispatchedEventIds.add(event.uniqueIdentifier)
            }
            Log.debug(TAG, TAG, "Marked ${events.size} $type events as dispatched")
        }
    }
    
    /**
     * Clear all accumulated events without processing.
     */
    suspend fun clear() {
        mutex.withLock {
            accumulatedEvents.clear()
            dispatchedEventIds.clear()
        }
    }
    
    /**
     * Get count of accumulated events.
     */
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

