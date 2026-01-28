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

import android.os.Handler
import android.os.Looper
import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.services.DataQueue
import com.adobe.marketing.mobile.services.Log
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates event batching, disk persistence, and flush triggers.
 * Manages counters and timers to determine when to dispatch accumulated events.
 */
internal class BatchCoordinator(
    assetDataQueue: DataQueue,
    experienceDataQueue: DataQueue,
    private val state: ContentAnalyticsStateManager
) {
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
    }
    
    private val assetHitProcessor = DirectHitProcessor(BatchHitType.ASSET)
    private val experienceHitProcessor = DirectHitProcessor(BatchHitType.EXPERIENCE)
    private val assetHitQueue = PersistentHitQueue(assetDataQueue, assetHitProcessor)
    private val experienceHitQueue = PersistentHitQueue(experienceDataQueue, experienceHitProcessor)
    
    private var assetEventCount: Int = 0
    private var experienceEventCount: Int = 0
    private var firstTrackingTime: Date? = null
    
    @Volatile
    private var batchTimer: Handler? = null
    @Volatile
    private var flushRunnable: Runnable? = null
    
    @Volatile
    private var assetProcessingCallback: ((List<Event>) -> Unit)? = null
    @Volatile
    private var experienceProcessingCallback: ((List<Event>) -> Unit)? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateMutex = kotlinx.coroutines.sync.Mutex()
    
    init {
        assetHitProcessor.setCallback { events ->
            assetProcessingCallback?.invoke(events)
        }
        
        experienceHitProcessor.setCallback { events ->
            experienceProcessingCallback?.invoke(events)
        }
        
        assetHitQueue.beginProcessing()
        experienceHitQueue.beginProcessing()
        
        Log.debug(TAG, TAG, "BatchCoordinator initialized")
    }
    
    /**
     * Set callbacks for batch processing.
     */
    fun setCallbacks(
        assetCallback: (List<Event>) -> Unit,
        experienceCallback: (List<Event>) -> Unit
    ) {
        assetProcessingCallback = assetCallback
        experienceProcessingCallback = experienceCallback
    }
    
    /**
     * Update batching configuration.
     */
    fun updateConfiguration(newConfig: ContentAnalyticsConfiguration) {
        val oldConfig = state.configuration ?: ContentAnalyticsConfiguration()
        
        Log.debug(TAG, TAG, "Configuration updated | Batching: ${newConfig.batchingEnabled} | Interval: ${newConfig.batchFlushInterval}ms")
        
        // Flush if batch size reduced below current count
        scope.launch {
            stateMutex.lock()
            try {
                val totalCount = assetEventCount + experienceEventCount
                if (newConfig.maxBatchSize < oldConfig.maxBatchSize && 
                    totalCount >= newConfig.maxBatchSize) {
                    Log.debug(TAG, TAG, "Batch size reduced from ${oldConfig.maxBatchSize} to ${newConfig.maxBatchSize} - flushing")
                    performFlush()
                }
            } finally {
                stateMutex.unlock()
            }
        }
        
        // Update timer if interval changed
        if (newConfig.batchFlushInterval != oldConfig.batchFlushInterval && batchTimer != null) {
            Log.debug(TAG, TAG, "Flush interval changed from ${oldConfig.batchFlushInterval}ms to ${newConfig.batchFlushInterval}ms - rescheduling")
            cancelBatchTimer()
            if (newConfig.batchingEnabled) {
                scheduleBatchFlush(newConfig.batchFlushInterval)
            }
        } else if (newConfig.batchingEnabled) {
            scheduleBatchFlush(newConfig.batchFlushInterval)
        } else {
            cancelBatchTimer()
        }
    }
    
    /**
     * Add asset event to the current batch.
     */
    fun addAssetEvent(event: Event) {
        scope.launch {
            stateMutex.lock()
            try {
                assetHitProcessor.accumulateEvent(event)
                persistEventImmediately(event, assetHitQueue, BatchHitType.ASSET)
                assetEventCount++
                
                val maxBatchSize = state.configuration?.maxBatchSize 
                    ?: ContentAnalyticsConstants.Defaults.MAX_BATCH_SIZE
                
                Log.trace(TAG, TAG, "Asset event queued: $assetEventCount/$maxBatchSize")
                
                if (firstTrackingTime == null) {
                    firstTrackingTime = Date()
                    state.configuration?.let { config ->
                        if (config.batchingEnabled) {
                            scheduleBatchFlush(config.batchFlushInterval)
                        }
                    }
                }
                
                checkAndFlushIfNeeded()
            } finally {
                stateMutex.unlock()
            }
        }
    }
    
    /**
     * Add experience event to the current batch.
     */
    fun addExperienceEvent(event: Event) {
        scope.launch {
            stateMutex.lock()
            try {
                experienceHitProcessor.accumulateEvent(event)
                persistEventImmediately(event, experienceHitQueue, BatchHitType.EXPERIENCE)
                experienceEventCount++
                
                val maxBatchSize = state.configuration?.maxBatchSize 
                    ?: ContentAnalyticsConstants.Defaults.MAX_BATCH_SIZE
                
                Log.trace(TAG, TAG, "Experience event queued: $experienceEventCount/$maxBatchSize")
                
                if (firstTrackingTime == null) {
                    firstTrackingTime = Date()
                    state.configuration?.let { config ->
                        if (config.batchingEnabled) {
                            scheduleBatchFlush(config.batchFlushInterval)
                        }
                    }
                }
                
                checkAndFlushIfNeeded()
            } finally {
                stateMutex.unlock()
            }
        }
    }
    
    /**
     * Flush all pending batches immediately (public API).
     */
    fun flush() {
        Log.debug(TAG, TAG, "Flushing batches")
        
        scope.launch {
            stateMutex.lock()
            try {
                performFlush()
            } finally {
                stateMutex.unlock()
            }
        }
    }
    
    /**
     * Perform the actual flush logic.
     * Assumes stateMutex is already locked by the caller.
     */
    private suspend fun performFlush() {
        if (assetEventCount + experienceEventCount == 0) return
        
        Log.debug(TAG, TAG, "Batch flush: $assetEventCount assets, $experienceEventCount experiences")
        
        val assetEvents = assetHitProcessor.processAccumulatedEvents()
        val experienceEvents = experienceHitProcessor.processAccumulatedEvents()
        
        assetEventCount = 0
        experienceEventCount = 0
        firstTrackingTime = null
        
        withContext(Dispatchers.Main) {
            cancelBatchTimer()
        }
        
        // Mark as dispatched after sending to orchestrator
        if (assetEvents.isNotEmpty()) {
            assetHitProcessor.markEventsAsDispatched(assetEvents)
        }
        if (experienceEvents.isNotEmpty()) {
            experienceHitProcessor.markEventsAsDispatched(experienceEvents)
        }
    }
    
    /**
     * Flush pending events immediately (called on background or app close).
     */
    fun flushPendingEvents() {
        flush()
    }
    
    /**
     * Clear all pending batches and reset state.
     */
    fun clearPendingBatch() {
        scope.launch {
            stateMutex.lock()
            try {
                assetEventCount = 0
                experienceEventCount = 0
                firstTrackingTime = null
                
                assetHitProcessor.clear()
                experienceHitProcessor.clear()
                assetHitQueue.clear()
                experienceHitQueue.clear()
                
                Log.debug(TAG, TAG, "Pending batches cleared")
            } finally {
                stateMutex.unlock()
            }
        }
    }
    
    /**
     * Close the coordinator and release resources.
     */
    fun close() {
        flush()
        cancelBatchTimer()
        assetHitQueue.close()
        experienceHitQueue.close()
        scope.cancel()
        
        Log.debug(TAG, TAG, "BatchCoordinator closed")
    }
    
    /**
     * Persist event to disk immediately for crash recovery
     */
    private fun persistEventImmediately(
        event: Event,
        queue: PersistentHitQueue,
        type: BatchHitType
    ) {
        val entity = DataEntityHelper.fromEvent(event)
        
        if (queue.queue(entity)) {
            Log.trace(TAG, TAG, "Event persisted | Type: $type | ID: ${event.uniqueIdentifier}")
        } else {
            Log.warning(TAG, TAG, "Failed to persist event | Type: $type | ID: ${event.uniqueIdentifier}")
        }
    }
    
    /**
     * Check if the batch should be flushed based on size or time limits.
     * Assumes stateMutex is already locked by the caller.
     */
    private suspend fun checkAndFlushIfNeeded() {
        val config = state.configuration ?: return
        
        // Check if we've reached the maximum batch size
        val totalCount = assetEventCount + experienceEventCount
        if (totalCount >= config.maxBatchSize) {
            Log.debug(TAG, TAG, "Batch size limit reached ($totalCount >= ${config.maxBatchSize}), flushing")
            performFlush()
            return
        }
        
        // Check if we've exceeded the maximum wait time
        firstTrackingTime?.let { firstTime ->
            val timeElapsed = (Date().time - firstTime.time) / 1000.0
            if (timeElapsed >= config.maxWaitTime) {
                Log.debug(TAG, TAG, "Max wait time exceeded ($timeElapsed >= ${config.maxWaitTime}s), flushing")
                performFlush()
            }
        }
    }
    
    
    private fun scheduleBatchFlush(intervalMs: Long) {
        Handler(Looper.getMainLooper()).post {
            cancelBatchTimer()
            
            val handler = Handler(Looper.getMainLooper())
            val runnable = Runnable {
                Log.debug(TAG, TAG, "Batch timer triggered, flushing")
                flush()
            }
            
            handler.postDelayed(runnable, intervalMs)
            
            batchTimer = handler
            flushRunnable = runnable
            
            Log.trace(TAG, TAG, "Batch timer scheduled | Interval: ${intervalMs}ms")
        }
    }
    private fun cancelBatchTimer() {
        flushRunnable?.let { runnable ->
            batchTimer?.removeCallbacks(runnable)
        }
        batchTimer = null
        flushRunnable = null
    }
}
