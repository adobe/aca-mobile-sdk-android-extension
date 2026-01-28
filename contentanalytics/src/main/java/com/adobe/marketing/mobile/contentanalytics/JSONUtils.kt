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

import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility functions for JSON serialization/deserialization using org.json
 * Replaces the need for 3rd party libraries like Gson
 */
internal object JSONUtils {
    
    /**
     * Convert a Map to JSONObject
     */
    fun mapToJSONObject(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) ->
            json.put(key, convertToJSON(value))
        }
        return json
    }
    
    /**
     * Convert JSONObject to Map recursively
     */
    fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = convertFromJSON(json.get(key))
        }
        return map
    }
    
    /**
     * Convert JSONArray to List recursively
     */
    fun jsonArrayToList(jsonArray: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until jsonArray.length()) {
            list.add(convertFromJSON(jsonArray.get(i)))
        }
        return list
    }
    
    /**
     * Convert Kotlin types to JSON-compatible types
     */
    private fun convertToJSON(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                // Safe cast: JSON maps always have String keys
                val stringMap = value.mapKeys { it.key.toString() }
                    .mapValues { it.value }
                mapToJSONObject(stringMap)
            }
            is List<*> -> {
                val jsonArray = JSONArray()
                value.forEach { jsonArray.put(convertToJSON(it)) }
                jsonArray
            }
            is String, is Number, is Boolean -> value
            else -> value.toString()
        }
    }
    
    /**
     * Convert JSON types to Kotlin types
     */
    private fun convertFromJSON(value: Any?): Any? {
        return when (value) {
            is JSONObject -> jsonObjectToMap(value)
            is JSONArray -> jsonArrayToList(value)
            JSONObject.NULL -> null
            else -> value
        }
    }
}

