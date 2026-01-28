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

import com.adobe.marketing.mobile.contentanalytics.helpers.TestDataBuilder
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for extras merging logic: conflict detection and resolution strategies.
 * 
 * Tests the critical `ContentAnalyticsUtilities` functions:
 * - `hasConflictingExtras()` - Detects conflicts in batched extras
 * - `processExtras()` - Merges or wraps extras based on conflicts
 * 
 * This is critical for data integrity in batched events where multiple
 * events with different extras values are aggregated.
 */
class ContentAnalyticsExtrasMergingTest {
    
    // MARK: - hasConflictingExtras() Tests
    
    @Test
    fun testHasConflictingExtras_EmptyArray_ReturnsFalse() {
        // Arrange
        val extrasArray = emptyList<Map<String, Any>>()
        
        // Act
        val result = ContentAnalyticsUtilities.hasConflictingExtras(extrasArray)
        
        // Assert
        assertFalse("Empty array should not have conflicts", result)
    }
    
    @Test
    fun testHasConflictingExtras_SingleExtras_ReturnsFalse() {
        // Arrange
        val extrasArray = listOf(
            mapOf("campaign" to "summer-sale", "variant" to "A")
        )
        
        // Act
        val result = ContentAnalyticsUtilities.hasConflictingExtras(extrasArray)
        
        // Assert
        assertFalse("Single extras should not have conflicts", result)
    }
    
    @Test
    fun testHasConflictingExtras_IdenticalExtras_ReturnsFalse() {
        // Arrange - Multiple events with identical extras
        val extrasArray = listOf(
            mapOf("campaign" to "summer-sale", "variant" to "A"),
            mapOf("campaign" to "summer-sale", "variant" to "A"),
            mapOf("campaign" to "summer-sale", "variant" to "A")
        )
        
        // Act
        val result = ContentAnalyticsUtilities.hasConflictingExtras(extrasArray)
        
        // Assert
        assertFalse("Identical extras should not have conflicts", result)
    }
    
    @Test
    fun testHasConflictingExtras_DifferentKeys_ReturnsFalse() {
        // Arrange - Events with different keys (no overlap)
        val extrasArray = listOf(
            mapOf("campaign" to "summer-sale"),
            mapOf("variant" to "A"),
            mapOf("region" to "US")
        )
        
        // Act
        val result = ContentAnalyticsUtilities.hasConflictingExtras(extrasArray)
        
        // Assert
        assertFalse("Different keys (no overlap) should not have conflicts", result)
    }
    
    @Test
    fun testHasConflictingExtras_SameKeyDifferentValues_ReturnsTrue() {
        // Arrange - Events with same key but different values (CONFLICT!)
        val extrasArray = listOf(
            mapOf("campaign" to "summer-sale"),
            mapOf("campaign" to "winter-sale")
        )
        
        // Act
        val result = ContentAnalyticsUtilities.hasConflictingExtras(extrasArray)
        
        // Assert
        assertTrue("Same key with different values should have conflicts", result)
    }
    
    @Test
    fun testHasConflictingExtras_PartialOverlapWithConflict_ReturnsTrue() {
        // Arrange - Some keys match, some conflict
        val extrasArray = listOf(
            mapOf("campaign" to "summer-sale", "variant" to "A"),
            mapOf("campaign" to "summer-sale", "variant" to "B") // variant conflicts!
        )
        
        // Act
        val result = ContentAnalyticsUtilities.hasConflictingExtras(extrasArray)
        
        // Assert
        assertTrue("Partial overlap with any conflict should return true", result)
    }
    
    @Test
    fun testHasConflictingExtras_DifferentDataTypes_ReturnsTrue() {
        // Arrange - Same key, different data types
        val extrasArray = listOf(
            mapOf("value" to "string"),
            mapOf("value" to 42) // different type = conflict
        )
        
        // Act
        val result = ContentAnalyticsUtilities.hasConflictingExtras(extrasArray)
        
        // Assert
        assertTrue("Different data types for same key should be a conflict", result)
    }
    
    @Test
    fun testHasConflictingExtras_MultipleConflicts_ReturnsTrue() {
        // Arrange - Multiple keys with conflicts
        val extrasArray = listOf(
            mapOf("campaign" to "A", "variant" to "1", "region" to "US"),
            mapOf("campaign" to "B", "variant" to "2", "region" to "EU")
        )
        
        // Act
        val result = ContentAnalyticsUtilities.hasConflictingExtras(extrasArray)
        
        // Assert
        assertTrue("Multiple conflicts should return true", result)
    }
    
    // MARK: - processExtras() Tests
    
    @Test
    fun testProcessExtras_EmptyArray_ReturnsNull() {
        // Arrange
        val extrasArray = emptyList<Map<String, Any>>()
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertNull("Empty array should return null", result)
    }
    
    @Test
    fun testProcessExtras_SingleExtras_ReturnsDirectly() {
        // Arrange
        val extrasArray = listOf(
            mapOf("campaign" to "summer-sale", "variant" to "A")
        )
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertNotNull("Should return the extras", result)
        assertEquals("campaign should be present", "summer-sale", result?.get("campaign"))
        assertEquals("variant should be present", "A", result?.get("variant"))
        assertFalse("Should not wrap in 'all' array", result?.containsKey("all") == true)
    }
    
    @Test
    fun testProcessExtras_NoConflicts_ReturnsMerged() {
        // Arrange - Multiple extras with different keys (mergeable)
        val extrasArray = listOf(
            mapOf("campaign" to "summer-sale"),
            mapOf("variant" to "A"),
            mapOf("region" to "US")
        )
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertNotNull("Should return merged extras", result)
        assertEquals("campaign should be present", "summer-sale", result?.get("campaign"))
        assertEquals("variant should be present", "A", result?.get("variant"))
        assertEquals("region should be present", "US", result?.get("region"))
        assertFalse("Should not wrap in 'all' array", result?.containsKey("all") == true)
    }
    
    @Test
    fun testProcessExtras_IdenticalExtras_ReturnsMerged() {
        // Arrange - Multiple events with identical extras
        val extrasArray = listOf(
            mapOf("campaign" to "summer-sale", "variant" to "A"),
            mapOf("campaign" to "summer-sale", "variant" to "A"),
            mapOf("campaign" to "summer-sale", "variant" to "A")
        )
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertNotNull("Should return merged extras", result)
        assertEquals("campaign should be present", "summer-sale", result?.get("campaign"))
        assertEquals("variant should be present", "A", result?.get("variant"))
        assertFalse("Should not wrap in 'all' array for identical extras", result?.containsKey("all") == true)
    }
    
    @Test
    fun testProcessExtras_Conflicts_ReturnsAllArray() {
        // Arrange - Conflicting extras (same key, different values)
        val extras1 = mapOf("campaign" to "summer-sale", "variant" to "A")
        val extras2 = mapOf("campaign" to "winter-sale", "variant" to "B")
        val extrasArray = listOf(extras1, extras2)
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertNotNull("Should return result with 'all' array", result)
        assertTrue("Should contain 'all' key for conflicts", result?.containsKey("all") == true)
        
        val allArray = result?.get("all") as? List<Map<String, Any>>
        assertNotNull("'all' should be an array", allArray)
        assertEquals("'all' array should contain all extras", 2, allArray?.size)
        assertEquals("First element should match", extras1, allArray?.get(0))
        assertEquals("Second element should match", extras2, allArray?.get(1))
    }
    
    @Test
    fun testProcessExtras_PartialConflict_ReturnsAllArray() {
        // Arrange - Some keys match, one conflicts
        val extras1 = mapOf("campaign" to "summer-sale", "variant" to "A", "region" to "US")
        val extras2 = mapOf("campaign" to "summer-sale", "variant" to "B", "region" to "US")
        val extrasArray = listOf(extras1, extras2)
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertTrue("Should use 'all' array when any conflict exists", result?.containsKey("all") == true)
        
        val allArray = result?.get("all") as? List<Map<String, Any>>
        assertEquals("'all' array should preserve all extras objects", 2, allArray?.size)
    }
    
    @Test
    fun testProcessExtras_ManyEvents_MergesCorrectly() {
        // Arrange - Many events with non-conflicting extras
        val extrasArray = (1..10).map { index ->
            mapOf("key$index" to "value$index")
        }
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertNotNull("Should return merged extras", result)
        assertEquals("Should have all 10 keys", 10, result?.size)
        (1..10).forEach { index ->
            assertEquals("key$index should be present", "value$index", result?.get("key$index"))
        }
    }
    
    @Test
    fun testProcessExtras_ManyEventsWithConflict_ReturnsAllArray() {
        // Arrange - Many events with one conflicting key
        val extrasArray = (1..10).map { index ->
            mapOf("shared" to "value$index", "unique$index" to index)
        }
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertTrue("Should use 'all' array for conflicts", result?.containsKey("all") == true)
        
        val allArray = result?.get("all") as? List<Map<String, Any>>
        assertEquals("'all' array should contain all 10 extras objects", 10, allArray?.size)
    }
    
    @Test
    fun testProcessExtras_ComplexNestedData_HandlesCorrectly() {
        // Arrange - Extras with nested objects
        val extras1 = mapOf(
            "simple" to "value",
            "nested" to mapOf("key" to "value1")
        )
        val extras2 = mapOf(
            "simple" to "value",
            "nested" to mapOf("key" to "value2") // nested conflict!
        )
        val extrasArray = listOf(extras1, extras2)
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertTrue("Nested conflicts should trigger 'all' array", result?.containsKey("all") == true)
    }
    
    @Test
    fun testProcessExtras_DifferentDataTypes_ReturnsAllArray() {
        // Arrange - Same key, different data types
        val extras1 = mapOf("value" to "string")
        val extras2 = mapOf("value" to 42)
        val extras3 = mapOf("value" to true)
        val extrasArray = listOf(extras1, extras2, extras3)
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertTrue("Different data types should trigger 'all' array", result?.containsKey("all") == true)
        
        val allArray = result?.get("all") as? List<Map<String, Any>>
        assertEquals("'all' array should preserve all data types", 3, allArray?.size)
    }
    
    @Test
    fun testProcessExtras_EmptyExtrasInArray_HandlesCorrectly() {
        // Arrange - Mix of empty and non-empty extras
        val extrasArray = listOf(
            mapOf("campaign" to "summer-sale"),
            emptyMap(), // empty extras
            mapOf("variant" to "A")
        )
        
        // Act
        val result = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertNotNull("Should return merged extras", result)
        assertEquals("campaign should be present", "summer-sale", result?.get("campaign"))
        assertEquals("variant should be present", "A", result?.get("variant"))
    }
    
    // MARK: - Integration Tests (Full Flow)
    
    @Test
    fun testExtrasFlow_AssetEvents_NoConflicts() {
        // Arrange - Simulate batched asset events with compatible extras
        val event1Extras = mapOf("campaign" to "summer", "source" to "email")
        val event2Extras = mapOf("variant" to "A")
        val event3Extras = mapOf("region" to "US")
        
        val extrasArray = listOf(event1Extras, event2Extras, event3Extras)
        
        // Act
        val hasConflicts = ContentAnalyticsUtilities.hasConflictingExtras(extrasArray)
        val processed = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertFalse("Should not detect conflicts", hasConflicts)
        assertNotNull("Should return merged extras", processed)
        assertEquals(4, processed?.size) // All keys merged
        assertFalse("Should not use 'all' array", processed?.containsKey("all") == true)
    }
    
    @Test
    fun testExtrasFlow_AssetEvents_WithConflicts() {
        // Arrange - Simulate batched asset events with conflicting extras
        val event1Extras = mapOf("campaign" to "summer", "variant" to "A")
        val event2Extras = mapOf("campaign" to "winter", "variant" to "A")
        val event3Extras = mapOf("campaign" to "spring", "variant" to "A")
        
        val extrasArray = listOf(event1Extras, event2Extras, event3Extras)
        
        // Act
        val hasConflicts = ContentAnalyticsUtilities.hasConflictingExtras(extrasArray)
        val processed = ContentAnalyticsUtilities.processExtras(extrasArray)
        
        // Assert
        assertTrue("Should detect conflicts", hasConflicts)
        assertNotNull("Should return processed extras", processed)
        assertTrue("Should use 'all' array for conflicts", processed?.containsKey("all") == true)
        
        val allArray = processed?.get("all") as? List<Map<String, Any>>
        assertEquals("Should preserve all 3 extras objects", 3, allArray?.size)
    }
}

