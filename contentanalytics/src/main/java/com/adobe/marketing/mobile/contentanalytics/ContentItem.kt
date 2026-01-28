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

/** Content item with value and optional metadata styles */
data class ContentItem @JvmOverloads constructor(
    val value: String,
    val styles: Map<String, Any> = emptyMap()
) {
    /** Serializes to map. Uses "style" (singular) to match iOS payload format */
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>("value" to value)
        if (styles.isNotEmpty()) {
            map["style"] = styles
        }
        return map
    }
    
    companion object {
        fun fromMap(map: Map<String, Any?>): ContentItem? {
            val value = map["value"] as? String ?: return null
            val styles = map["style"] as? Map<String, Any> ?: emptyMap()
            return ContentItem(value, styles)
        }
        
        fun fromList(list: List<*>?): List<ContentItem> {
            return list?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { map ->
                    fromMap(map as Map<String, Any?>)
                }
            } ?: emptyList()
        }
    }
}

