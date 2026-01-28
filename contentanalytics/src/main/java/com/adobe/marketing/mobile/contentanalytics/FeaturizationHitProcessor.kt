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
import com.adobe.marketing.mobile.services.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.min
import kotlin.math.pow

/**
 * Processes featurization hits with automatic retry logic.
 */
internal class FeaturizationHitProcessor(
    private val featurizationService: ExperienceFeaturizationServiceProtocol
) : HitProcessor {
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
        
        /**
         * HTTP error codes that should trigger a retry.
         */
        private val RECOVERABLE_ERROR_CODES = setOf(
            408, // Request Timeout
            429, // Too Many Requests
            502, // Bad Gateway
            503, // Service Unavailable
            504  // Gateway Timeout
        )
        
        private const val BASE_RETRY_INTERVAL = 5.0 // seconds
        private const val MAX_RETRY_INTERVAL = 300.0 // 5 minutes
    }
    
    private val entityRetryIntervalMapping = ConcurrentHashMap<String, Double>()
    
    /**
     * Processes a featurization hit.
     */
    override suspend fun processHit(entity: DataEntity): Boolean {
        return suspendCancellableCoroutine { continuation ->
            processHitInternal(entity) { shouldRemove ->
                continuation.resume(shouldRemove)
            }
        }
    }
    
    private fun processHitInternal(entity: DataEntity, completion: (Boolean) -> Unit) {
        val hit = decodeFeaturizationHit(entity)
        if (hit == null) {
            Log.warning(TAG, TAG, "Failed to decode featurization hit | Entity: ${entity.uniqueIdentifier} - dropping")
            completion(true)
            return
        }
        
        Log.debug(TAG, TAG, "Processing featurization hit | ExperienceID: ${hit.experienceId} | Attempt: ${hit.attemptCount + 1}")
        
        // Check if experience already exists
        checkAndRegisterExperience(hit, entity.uniqueIdentifier, completion)
    }
    
    /**
     * Decodes FeaturizationHit from DataEntity
     */
    private fun decodeFeaturizationHit(entity: DataEntity): FeaturizationHit? {
        return try {
            // DataEntity.data is the JSON string directly
            val json = entity.data ?: return null
            FeaturizationHit.fromJson(json)
        } catch (e: Exception) {
            Log.warning(TAG, TAG, "Exception decoding featurization hit: ${e.message}")
            null
        }
    }
    
    /**
     * Checks if experience exists, and registers if not
     */
    private fun checkAndRegisterExperience(
        hit: FeaturizationHit,
        entityId: String,
        completion: (Boolean) -> Unit
    ) {
        // Validate datastreamId is present (required field)
        val datastreamId = hit.content.datastreamId
        if (datastreamId.isEmpty()) {
            Log.error(TAG, TAG, "❌ Cannot check experience - datastreamId is empty | ID: ${hit.experienceId}")
            completion(false) // Don't retry - configuration error
            return
        }
        
        // Check if experience exists (single attempt - PersistentHitQueue handles retries)
        featurizationService.checkExperienceExists(
            experienceId = hit.experienceId,
            imsOrg = hit.imsOrg,
            datastreamId = datastreamId
        ) { result ->
            when {
                result.isSuccess -> {
                    val exists = result.getOrNull() ?: false
                    if (exists) {
                        // Experience already featurized - success!
                        Log.debug(TAG, TAG, "Experience already featurized | ID: ${hit.experienceId}")
                        entityRetryIntervalMapping.remove(entityId)
                        completion(true) // Remove from queue
                    } else {
                        // Experience not featurized - register it
                        Log.debug(TAG, TAG, "Experience not featurized, registering | ID: ${hit.experienceId}")
                        registerExperience(hit, entityId, completion)
                    }
                }
                result.isFailure -> {
                    // Check failed - determine if recoverable
                    handleCheckFailure(result.exceptionOrNull() ?: Exception("Unknown error"), hit, entityId, completion)
                }
            }
        }
    }
    
    /**
     * Registers experience with featurization service via JAG Gateway
     */
    private fun registerExperience(
        hit: FeaturizationHit,
        entityId: String,
        completion: (Boolean) -> Unit
    ) {
        // Validate datastreamId is present (required field)
        val datastreamId = hit.content.datastreamId
        if (datastreamId.isEmpty()) {
            Log.error(TAG, TAG, "❌ Cannot register experience - datastreamId is empty | ID: ${hit.experienceId}")
            completion(false) // Don't retry - configuration error
            return
        }
        
        featurizationService.registerExperience(
            experienceId = hit.experienceId,
            imsOrg = hit.imsOrg,
            datastreamId = datastreamId,
            content = hit.content
        ) { result ->
            when {
                result.isSuccess -> {
                    // Registration successful
                    Log.debug(TAG, TAG, "Experience registered successfully | ID: ${hit.experienceId}")
                    entityRetryIntervalMapping.remove(entityId)
                    completion(true) // Remove from queue
                }
                result.isFailure -> {
                    // Registration failed - determine if recoverable
                    handleRegistrationFailure(result.exceptionOrNull() ?: Exception("Unknown error"), hit, entityId, completion)
                }
            }
        }
    }
    
    /**
     * Handles check failure - determines if recoverable
     */
    private fun handleCheckFailure(error: Throwable, hit: FeaturizationHit, entityId: String, completion: (Boolean) -> Unit) {
        when (error) {
            is FeaturizationError.HttpError -> {
                val statusCode = error.statusCode
                // Special case: 404 on check means experience not featurized yet - register it
                if (statusCode == 404) {
                    Log.debug(TAG, TAG, "404 response - registering experience | ID: ${hit.experienceId}")
                    registerExperience(hit, entityId, completion)
                    return
                }
                
                // Check if error is recoverable
                if (statusCode in RECOVERABLE_ERROR_CODES) {
                    // Recoverable error - retry with exponential backoff
                    retryWithBackoff(hit, entityId, statusCode = statusCode, operation = "checking", completion = completion)
                } else {
                    // Unrecoverable HTTP error (4xx client errors)
                    dropHit(entityId, statusCode, hit.experienceId, "checking", completion)
                }
            }
            else -> {
                // Network error or timeout - recoverable, retry
                retryWithBackoff(hit, entityId, error = error.message ?: "Unknown error", operation = "checking", completion = completion)
            }
        }
    }
    
    /**
     * Handles registration failure - determines if recoverable
     */
    private fun handleRegistrationFailure(error: Throwable, hit: FeaturizationHit, entityId: String, completion: (Boolean) -> Unit) {
        when (error) {
            is FeaturizationError.HttpError -> {
                val statusCode = error.statusCode
                // Check if error is recoverable
                if (statusCode in RECOVERABLE_ERROR_CODES) {
                    // Recoverable error - retry with exponential backoff
                    retryWithBackoff(hit, entityId, statusCode = statusCode, operation = "registering", completion = completion)
                } else {
                    // Unrecoverable HTTP error (4xx client errors)
                    dropHit(entityId, statusCode, hit.experienceId, "registering", completion)
                }
            }
            else -> {
                // Network error or timeout - recoverable, retry
                retryWithBackoff(hit, entityId, error = error.message ?: "Unknown error", operation = "registering", completion = completion)
            }
        }
    }
    
    /**
     * Retry hit with exponential backoff
     */
    private fun retryWithBackoff(
        hit: FeaturizationHit,
        entityId: String,
        statusCode: Int? = null,
        error: String? = null,
        operation: String,
        completion: (Boolean) -> Unit
    ) {
        val retryInterval = calculateRetryInterval(hit.attemptCount)
        
        if (statusCode != null) {
            Log.warning(TAG, TAG, "Recoverable error $operation ($statusCode) | ID: ${hit.experienceId} | Retry in: ${retryInterval}s")
        } else if (error != null) {
            Log.warning(TAG, TAG, "Network error $operation | ID: ${hit.experienceId} | Error: $error | Retry in: ${retryInterval}s")
        }
        
        entityRetryIntervalMapping[entityId] = retryInterval
        completion(false) // Keep in queue for retry
    }
    
    /**
     * Drop hit from queue (unrecoverable error)
     */
    private fun dropHit(
        entityId: String,
        statusCode: Int,
        experienceId: String,
        operation: String,
        completion: (Boolean) -> Unit
    ) {
        Log.warning(TAG, TAG, "Unrecoverable HTTP error $operation ($statusCode) | ID: $experienceId - dropping")
        entityRetryIntervalMapping.remove(entityId)
        completion(true) // Remove from queue
    }
    
    /**
     * Calculates retry interval with exponential backoff: 5s, 10s, 20s, 40s, 80s, caps at 5 minutes
     */
    private fun calculateRetryInterval(attemptCount: Int): Double {
        val exponentialInterval = BASE_RETRY_INTERVAL * 2.0.pow(attemptCount.toDouble())
        return min(exponentialInterval, MAX_RETRY_INTERVAL)
    }
}

