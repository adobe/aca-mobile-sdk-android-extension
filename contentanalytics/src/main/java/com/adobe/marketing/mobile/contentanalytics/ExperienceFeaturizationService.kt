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

import com.adobe.marketing.mobile.services.HttpConnecting
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.NetworkCallback
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.Networking
import org.json.JSONObject

/**
 * Protocol for featurization service operations
 */
internal interface ExperienceFeaturizationServiceProtocol {
    /**
     * Check if experience exists in featurization service (single attempt, no retry)
     */
    fun checkExperienceExists(
        experienceId: String,
        imsOrg: String,
        datastreamId: String,
        completion: (Result<Boolean>) -> Unit
    )
    
    /**
     * Register experience for featurization
     */
    fun registerExperience(
        experienceId: String,
        imsOrg: String,
        datastreamId: String,
        content: ExperienceContent,
        completion: (Result<Unit>) -> Unit
    )
}

/**
 * Implementation of ExperienceFeaturizationService using AEP Networking
 */
internal class ExperienceFeaturizationService(
    private val baseUrl: String,
    private val networkService: Networking
) : ExperienceFeaturizationServiceProtocol {
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
    }
    
    override fun checkExperienceExists(
        experienceId: String,
        imsOrg: String,
        datastreamId: String,
        completion: (Result<Boolean>) -> Unit
    ) {
        val url = "$baseUrl/check/$imsOrg/$datastreamId/$experienceId"
        
        Log.trace(TAG, TAG, "Checking experience | ID: $experienceId")
        
        val request = NetworkRequest(
            url,
            com.adobe.marketing.mobile.services.HttpMethod.GET,
            ByteArray(0),
            mapOf("Content-Type" to "application/json"),
            5000,
            10000
        )
        
        networkService.connectAsync(request, object : NetworkCallback {
            override fun call(connection: HttpConnecting?) {
                handleCheckResponse(connection, experienceId, completion)
            }
        })
    }
    
    override fun registerExperience(
        experienceId: String,
        imsOrg: String,
        datastreamId: String,
        content: ExperienceContent,
        completion: (Result<Unit>) -> Unit
    ) {
        val url = "$baseUrl/"
        
        Log.debug(TAG, TAG, "Registering experience | ID: $experienceId")
        
        val contentJson = try {
            JSONUtils.mapToJSONObject(content.toMap()).toString()
        } catch (e: Exception) {
            Log.warning(TAG, TAG, "Failed to encode experience content | Error: ${e.message}")
            completion(Result.failure(FeaturizationError.InvalidResponse))
            return
        }
        
        val request = NetworkRequest(
            url,
            com.adobe.marketing.mobile.services.HttpMethod.POST,
            contentJson.toByteArray(Charsets.UTF_8),
            mapOf("Content-Type" to "application/json"),
            5000,
            30000
        )
        
        networkService.connectAsync(request, object : NetworkCallback {
            override fun call(connection: HttpConnecting?) {
                handleRegisterResponse(connection, experienceId, completion)
            }
        })
    }
    
    // MARK: - Response Handlers
    
    private fun handleCheckResponse(
        connection: HttpConnecting?,
        experienceId: String,
        completion: (Result<Boolean>) -> Unit
    ) {
        if (connection == null) {
            Log.warning(TAG, TAG, "Network error | ID: $experienceId | No connection")
            completion(Result.failure(FeaturizationError.NetworkError("No connection")))
            return
        }
        
        val responseBody = extractResponseBody(connection)
        val statusCode = connection.responseCode
        
        Log.trace(TAG, TAG, "Response | Status: $statusCode | Body: $responseBody")
        
        when (statusCode) {
            200 -> {
                val exists = try {
                    val json = JSONObject(responseBody)
                    if (!json.has("sendContent")) {
                        Log.warning(TAG, TAG, "Invalid response (missing 'sendContent') | ID: $experienceId")
                        completion(Result.failure(FeaturizationError.InvalidResponse))
                        return
                    }
                    
                    val sendContent = json.getBoolean("sendContent")
                    val existsValue = !sendContent
                    Log.debug(TAG, TAG, "Check succeeded | ID: $experienceId | sendContent: $sendContent | Exists: $existsValue")
                    existsValue
                } catch (e: Exception) {
                    Log.warning(TAG, TAG, "Failed to parse response | ID: $experienceId | Error: ${e.message}")
                    completion(Result.failure(FeaturizationError.InvalidResponse))
                    return
                }
                completion(Result.success(exists))
            }
            404 -> {
                Log.debug(TAG, TAG, "Not featurized (404) | ID: $experienceId")
                completion(Result.success(false))
            }
            else -> {
                Log.warning(TAG, TAG, "HTTP error: $statusCode | ID: $experienceId | Body: $responseBody")
                completion(Result.failure(FeaturizationError.HttpError(statusCode)))
            }
        }
        
        connection.close()
    }
    
    private fun handleRegisterResponse(
        connection: HttpConnecting?,
        experienceId: String,
        completion: (Result<Unit>) -> Unit
    ) {
        if (connection == null) {
            Log.warning(TAG, TAG, "Network error | ID: $experienceId | No connection")
            completion(Result.failure(FeaturizationError.NetworkError("No connection")))
            return
        }
        
        val responseBody = extractResponseBody(connection)
        val statusCode = connection.responseCode
        
        Log.trace(TAG, TAG, "Register response | Status: $statusCode | Body: $responseBody")
        
        when (statusCode) {
            in 200..299 -> {
                Log.debug(TAG, TAG, "Registered | ID: $experienceId | Status: $statusCode")
                completion(Result.success(Unit))
            }
            else -> {
                Log.warning(TAG, TAG, "Failed to register | Status: $statusCode | ID: $experienceId | Body: $responseBody")
                completion(Result.failure(FeaturizationError.HttpError(statusCode)))
            }
        }
        
        connection.close()
    }
    
    private fun extractResponseBody(connection: HttpConnecting): String {
        return try {
            connection.inputStream?.bufferedReader()?.use { it.readText() } ?: "nil"
        } catch (e: Exception) {
            "Error reading response: ${e.message}"
        }
    }
}

/**
 * Errors specific to featurization service
 */
internal sealed class FeaturizationError : Exception() {
    data class InvalidURL(val url: String) : FeaturizationError() {
        override val message: String = "Invalid featurization service URL: $url"
    }
    
    object InvalidResponse : FeaturizationError() {
        override val message: String = "Invalid response from featurization service"
    }
    
    data class HttpError(val statusCode: Int) : FeaturizationError() {
        override val message: String = "HTTP error from featurization service: $statusCode"
    }
    
    data class NetworkError(val error: String) : FeaturizationError() {
        override val message: String = "Network error: $error"
    }
}
