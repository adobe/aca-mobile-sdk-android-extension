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
import org.json.JSONObject

/**
 * Helper functions for serializing/deserializing events with DataEntity.
 */
internal object DataEntityHelper {
    
    /**
     * Creates a DataEntity from an Event.
     */
    fun fromEvent(event: Event): com.adobe.marketing.mobile.services.DataEntity {
        val eventData = mapOf(
            "id" to event.uniqueIdentifier,
            "name" to event.name,
            "type" to event.type,
            "source" to event.source,
            "eventData" to event.eventData,
            "timestamp" to System.currentTimeMillis()
        )
        
        // Use JSONUtils to convert Map to JSON
        val json = JSONUtils.mapToJSONObject(eventData).toString()
        
        return com.adobe.marketing.mobile.services.DataEntity(json)
    }
    
    /**
     * Converts a DataEntity back to an Event.
     */
    fun toEvent(entity: com.adobe.marketing.mobile.services.DataEntity): Event? {
        return try {
            val json = entity.data
            val jsonObject = JSONObject(json)
            
            val name = jsonObject.optString("name") ?: return null
            val eventType = jsonObject.optString("type") ?: return null
            val source = jsonObject.optString("source") ?: return null
            
            val eventData = if (jsonObject.has("eventData")) {
                // Use JSONUtils to convert JSONObject to Map
                JSONUtils.jsonObjectToMap(jsonObject.getJSONObject("eventData"))
            } else {
                null
            }
            
            Event.Builder(name, eventType, source)
                .apply { eventData?.let { setEventData(it) } }
                .build()
        } catch (e: Exception) {
            android.util.Log.e(
                ContentAnalyticsConstants.LOG_TAG,
                "Failed to convert DataEntity to Event: ${e.message}"
            )
            null
        }
    }
}

