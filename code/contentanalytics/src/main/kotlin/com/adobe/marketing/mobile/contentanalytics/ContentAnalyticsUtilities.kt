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

import java.security.MessageDigest

internal object ContentAnalyticsUtilities {
    
    /**
     * Generates deterministic experience ID from content.
     * Same content always produces same ID. Order must match iOS.
     * 
     * @return Experience ID in format "mobile-{12hexchars}"
     */
    fun generateExperienceId(
        assets: List<ContentItem>,
        texts: List<ContentItem>,
        ctas: List<ContentItem>? = null
    ): String {
        val sortedTexts = texts.map { it.value }.sorted()
        val sortedImages = assets.map { it.value }.sorted()
        val sortedCtas = (ctas ?: emptyList()).map { it.value }.sorted()
        
        // Order must match iOS for consistent IDs
        val contentParts = mutableListOf<String>()
        contentParts.addAll(sortedTexts)
        contentParts.addAll(sortedImages)
        contentParts.addAll(sortedCtas)
        
        val contentString = contentParts.joinToString("|")
        
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(contentString.toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        return "mobile-${hash.take(12)}"
    }
    
    fun hasConflictingExtras(extrasArray: List<Map<String, Any>>): Boolean {
        if (extrasArray.size <= 1) return false
        
        val allKeys = extrasArray.flatMap { it.keys }.toSet()
        
        for (key in allKeys) {
            val values = extrasArray.mapNotNull { it[key] }.toSet()
            if (values.size > 1) {
                return true
            }
        }
        
        return false
    }
    
    /** Merges extras. On conflict, wraps in "all" array */
    fun processExtras(extrasArray: List<Map<String, Any>>): Map<String, Any>? {
        if (extrasArray.isEmpty()) return null
        if (extrasArray.size == 1) return extrasArray[0]
        
        val mergedExtras = mutableMapOf<String, Any>()
        for (extras in extrasArray) {
            mergedExtras.putAll(extras)
        }
        
        return if (hasConflictingExtras(extrasArray)) {
            mapOf("all" to extrasArray)
        } else {
            mergedExtras
        }
    }
}
