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

import org.junit.Assert.*
import org.junit.Test

class ContentItemTest {
    
    @Test
    fun `test toMap serialization with no styles`() {
        val item = ContentItem("Welcome!")
        
        val map = item.toMap()
        
        assertEquals("Welcome!", map["value"])
        assertFalse(map.containsKey("style"))
    }
    
    @Test
    fun `test toMap serialization with styles`() {
        val item = ContentItem("Click Here", mapOf("role" to "primary", "enabled" to true))
        
        val map = item.toMap()
        
        assertEquals("Click Here", map["value"])
        val styles = map["style"] as? Map<String, Any>
        assertNotNull(styles)
        assertEquals("primary", styles?.get("role"))
        assertEquals(true, styles?.get("enabled"))
    }
    
    @Test
    fun `test fromMap deserialization`() {
        val map = mapOf(
            "value" to "Click Here",
            "style" to mapOf("role" to "primary")
        )
        
        val item = ContentItem.fromMap(map)
        
        assertNotNull(item)
        assertEquals("Click Here", item?.value)
        assertEquals("primary", item?.styles?.get("role"))
    }
    
    @Test
    fun `test fromMap with invalid data returns null`() {
        val map = mapOf("style" to mapOf<String, Any>())  // Missing value
        
        val item = ContentItem.fromMap(map)
        
        assertNull(item)
    }
    
    @Test
    fun `test fromList parses multiple items`() {
        val list = listOf(
            mapOf("value" to "Text 1", "style" to mapOf("role" to "headline")),
            mapOf("value" to "Text 2"),
            mapOf("value" to "Button", "style" to mapOf("enabled" to true))
        )
        
        val items = ContentItem.fromList(list)
        
        assertEquals(3, items.size)
        assertEquals("Text 1", items[0].value)
        assertEquals("headline", items[0].styles["role"])
        assertEquals("Text 2", items[1].value)
        assertTrue(items[1].styles.isEmpty())
        assertEquals("Button", items[2].value)
        assertEquals(true, items[2].styles["enabled"])
    }
}

