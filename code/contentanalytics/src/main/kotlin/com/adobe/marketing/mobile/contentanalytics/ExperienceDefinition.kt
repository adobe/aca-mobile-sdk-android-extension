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

/**
 * Definition of an experience with all its content
 */
data class ExperienceDefinition(
    val experienceId: String,
    val assets: List<String>,
    val texts: List<ContentItem>,
    val ctas: List<ContentItem>?,
    val sentToFeaturization: Boolean = false
) {
    /**
     * Convert to map for event data
     */
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "experienceId" to experienceId,
            ContentAnalyticsConstants.EventDataKeys.ASSETS to assets,
            ContentAnalyticsConstants.EventDataKeys.TEXTS to texts.map { it.toMap() }
        )
        
        ctas?.let {
            map[ContentAnalyticsConstants.EventDataKeys.CTAS] = it.map { cta -> cta.toMap() }
        }
        
        return map
    }
    
    companion object {
        /**
         * Parse from event data map
         */
        fun fromMap(map: Map<String, Any?>): ExperienceDefinition? {
            val experienceId = map["experienceId"] as? String ?: return null
            
            val assets = (map[ContentAnalyticsConstants.EventDataKeys.ASSETS] as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()
            
            val texts = ContentItem.fromList(
                map[ContentAnalyticsConstants.EventDataKeys.TEXTS] as? List<*>
            )
            
            val ctas = ContentItem.fromList(
                map[ContentAnalyticsConstants.EventDataKeys.CTAS] as? List<*>
            ).takeIf { it.isNotEmpty() }
            
            return ExperienceDefinition(
                experienceId = experienceId,
                assets = assets,
                texts = texts,
                ctas = ctas
            )
        }
    }
}

