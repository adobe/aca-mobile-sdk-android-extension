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

import org.json.JSONObject
import java.util.Date

/**
 * Featurization request persisted to disk for retries
 * Matches iOS FeaturizationHit.swift
 */
internal data class FeaturizationHit(
    val experienceId: String,
    val imsOrg: String,
    val content: ExperienceContent,
    val timestamp: Long = Date().time,
    val attemptCount: Int = 0
) {
    /**
     * Serialize to JSON for DataQueue persistence
     */
    fun toJson(): String {
        val data = mapOf(
            "experienceId" to experienceId,
            "imsOrg" to imsOrg,
            "content" to content.toMap(),
            "timestamp" to timestamp,
            "attemptCount" to attemptCount
        )
        return JSONUtils.mapToJSONObject(data).toString()
    }
    
    companion object {
        /**
         * Deserialize from JSON stored in DataQueue
         */
        fun fromJson(json: String): FeaturizationHit? {
            return try {
                val jsonObject = JSONObject(json)
                val contentJson = jsonObject.getJSONObject("content")
                
                FeaturizationHit(
                    experienceId = jsonObject.getString("experienceId"),
                    imsOrg = jsonObject.getString("imsOrg"),
                    content = ExperienceContent.fromMap(JSONUtils.jsonObjectToMap(contentJson)),
                    timestamp = jsonObject.optLong("timestamp", Date().time),
                    attemptCount = jsonObject.optInt("attemptCount", 0)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Content structure nested within FeaturizationExperienceContent
 * Matches iOS ContentData struct
 */
internal data class ContentData(
    val images: List<Map<String, Any>>,
    val texts: List<Map<String, Any>>,
    val ctas: List<Map<String, Any>>?
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "images" to images,
            "texts" to texts
        )
        ctas?.let { map["ctas"] = it }
        return map
    }
    
    companion object {
        fun fromMap(map: Map<String, Any?>): ContentData {
            return ContentData(
                images = (map["images"] as? List<Map<String, Any>>) ?: emptyList(),
                texts = (map["texts"] as? List<Map<String, Any>>) ?: emptyList(),
                ctas = map["ctas"] as? List<Map<String, Any>>
            )
        }
    }
}

/**
 * Experience content structure for featurization service
 * Matches iOS ExperienceContent struct
 */
internal data class ExperienceContent(
    val content: ContentData,
    val orgId: String,
    val datastreamId: String,
    val experienceId: String
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "content" to content.toMap(),
            "orgId" to orgId,
            "datastreamId" to datastreamId,
            "experienceId" to experienceId
        )
    }
    
    companion object {
        fun fromMap(map: Map<String, Any?>): ExperienceContent {
            return ExperienceContent(
                content = ContentData.fromMap((map["content"] as? Map<String, Any?>) ?: emptyMap()),
                orgId = map["orgId"] as? String ?: "",
                datastreamId = map["datastreamId"] as? String ?: "",
                experienceId = map["experienceId"] as? String ?: ""
            )
        }
    }
}

