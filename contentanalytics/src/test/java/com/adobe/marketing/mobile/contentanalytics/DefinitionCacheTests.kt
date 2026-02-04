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

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DefinitionCacheTests {
    
    private lateinit var cache: DefinitionCache
    
    @Before
    fun setUp() {
        cache = DefinitionCache(capacity = 3) // Small capacity for testing
    }
    
    @After
    fun tearDown() {
        // Clean up
    }
    
    // MARK: - Store and Retrieve Tests
    
    @Test
    fun `store and retrieve definition`() {
        // Given
        val definition = createDefinition("exp1")
        
        // When
        cache.store(definition)
        
        // Then
        val retrieved = cache.get("exp1")
        assertNotNull(retrieved)
        assertEquals("exp1", retrieved?.experienceId)
    }
    
    @Test
    fun `get non-existent definition returns null`() {
        // When/Then
        assertNull(cache.get("nonexistent"))
    }
    
    @Test
    fun `contains returns true for existing definition`() {
        // Given
        val definition = createDefinition("exp1")
        cache.store(definition)
        
        // When/Then
        assertTrue(cache.contains("exp1"))
    }
    
    @Test
    fun `contains returns false for non-existent definition`() {
        // When/Then
        assertFalse(cache.contains("nonexistent"))
    }
    
    // MARK: - Update Tests
    
    @Test
    fun `update existing definition`() {
        // Given
        val definition = createDefinition("exp1", sentToFeaturization = false)
        cache.store(definition)
        
        // When
        val updated = definition.copy(sentToFeaturization = true)
        cache.update(updated)
        
        // Then
        val retrieved = cache.get("exp1")
        assertTrue(retrieved?.sentToFeaturization == true)
    }
    
    // MARK: - LRU Eviction Tests
    
    @Test
    fun `LRU eviction when capacity exceeded`() {
        // Given - cache capacity is 3
        cache.store(createDefinition("exp1"))
        cache.store(createDefinition("exp2"))
        cache.store(createDefinition("exp3"))
        
        // When - add 4th definition
        cache.store(createDefinition("exp4"))
        
        // Then - exp1 should be evicted
        assertNull(cache.get("exp1"))
        assertNotNull(cache.get("exp2"))
        assertNotNull(cache.get("exp3"))
        assertNotNull(cache.get("exp4"))
    }
    
    @Test
    fun `LRU eviction access updates recency`() {
        // Given - cache capacity is 3
        cache.store(createDefinition("exp1"))
        cache.store(createDefinition("exp2"))
        cache.store(createDefinition("exp3"))
        
        // When - access exp1 (making it most recent)
        cache.get("exp1")
        
        // Then add exp4, exp2 should be evicted (least recent)
        cache.store(createDefinition("exp4"))
        
        assertNotNull(cache.get("exp1")) // Still present
        assertNull(cache.get("exp2")) // Evicted
        assertNotNull(cache.get("exp3"))
        assertNotNull(cache.get("exp4"))
    }
    
    // MARK: - Featurization Tracking Tests
    
    @Test
    fun `markAsSent updates existing definition`() {
        // Given
        val definition = createDefinition("exp1", sentToFeaturization = false)
        cache.store(definition)
        
        // When
        val updated = cache.markAsSent("exp1")
        
        // Then
        assertNotNull(updated)
        assertTrue(updated?.sentToFeaturization == true)
        assertTrue(cache.hasBeenSent("exp1"))
    }
    
    @Test
    fun `markAsSent returns null for non-existent definition`() {
        // When/Then
        assertNull(cache.markAsSent("nonexistent"))
    }
    
    @Test
    fun `hasBeenSent returns true when sent`() {
        // Given
        val definition = createDefinition("exp1", sentToFeaturization = true)
        cache.store(definition)
        
        // When/Then
        assertTrue(cache.hasBeenSent("exp1"))
    }
    
    @Test
    fun `hasBeenSent returns false when not sent`() {
        // Given
        val definition = createDefinition("exp1", sentToFeaturization = false)
        cache.store(definition)
        
        // When/Then
        assertFalse(cache.hasBeenSent("exp1"))
    }
    
    @Test
    fun `getSentCount returns correct count`() {
        // Given
        cache.store(createDefinition("exp1", sentToFeaturization = true))
        cache.store(createDefinition("exp2", sentToFeaturization = false))
        cache.store(createDefinition("exp3", sentToFeaturization = true))
        
        // When/Then
        assertEquals(2, cache.getSentCount())
    }
    
    // MARK: - Collection Tests
    
    @Test
    fun `getAllDefinitions returns all definitions`() {
        // Given
        cache.store(createDefinition("exp1"))
        cache.store(createDefinition("exp2"))
        cache.store(createDefinition("exp3"))
        
        // When
        val all = cache.getAllDefinitions()
        
        // Then
        assertEquals(3, all.size)
        assertTrue(all.any { it.experienceId == "exp1" })
        assertTrue(all.any { it.experienceId == "exp2" })
        assertTrue(all.any { it.experienceId == "exp3" })
    }
    
    @Test
    fun `count returns correct count`() {
        // Given
        assertEquals(0, cache.count)
        
        cache.store(createDefinition("exp1"))
        assertEquals(1, cache.count)
        
        cache.store(createDefinition("exp2"))
        assertEquals(2, cache.count)
    }
    
    @Test
    fun `removeAll clears cache`() {
        // Given
        cache.store(createDefinition("exp1"))
        cache.store(createDefinition("exp2"))
        assertEquals(2, cache.count)
        
        // When
        cache.removeAll()
        
        // Then
        assertEquals(0, cache.count)
        assertNull(cache.get("exp1"))
        assertNull(cache.get("exp2"))
    }
    
    // MARK: - Helper Methods
    
    private fun createDefinition(id: String, sentToFeaturization: Boolean = false): ExperienceDefinition {
        return ExperienceDefinition(
            experienceId = id,
            assets = listOf("https://example.com/$id.jpg"),
            texts = listOf(ContentItem("Title $id")),
            ctas = null,
            sentToFeaturization = sentToFeaturization
        )
    }
}
