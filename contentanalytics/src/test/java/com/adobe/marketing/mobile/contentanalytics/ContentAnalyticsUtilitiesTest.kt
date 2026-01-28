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

/**
 * Tests for ContentAnalyticsUtilities (experience ID generation, extras processing).
 */
class ContentAnalyticsUtilitiesTest {
    
    // MARK: - Experience ID Generation Tests
    
    @Test
    fun testGenerateExperienceId_SameContent_GeneratesSameId() {
        // Arrange
        val assets = listOf(ContentItem("https://example.com/image.jpg"))
        val texts = listOf(ContentItem("Welcome"))
        
        // Act
        val id1 = ContentAnalyticsUtilities.generateExperienceId(assets, texts)
        val id2 = ContentAnalyticsUtilities.generateExperienceId(assets, texts)
        
        // Assert
        assertEquals("Should generate same ID for same content", id1, id2)
        assertTrue("Should have mobile- prefix", id1.startsWith("mobile-"))
        assertEquals("Should be 'mobile-' (7) + 12 hex chars = 19", 19, id1.length)
    }
    
    @Test
    fun testGenerateExperienceId_DifferentOrder_GeneratesSameId() {
        // Arrange - Same content in different order
        val assets1 = listOf(
            ContentItem("https://example.com/a.jpg"),
            ContentItem("https://example.com/b.jpg")
        )
        val texts1 = listOf(
            ContentItem("Text A"),
            ContentItem("Text B")
        )
        
        val assets2 = listOf(
            ContentItem("https://example.com/b.jpg"),
            ContentItem("https://example.com/a.jpg")
        )
        val texts2 = listOf(
            ContentItem("Text B"),
            ContentItem("Text A")
        )
        
        // Act
        val id1 = ContentAnalyticsUtilities.generateExperienceId(assets1, texts1)
        val id2 = ContentAnalyticsUtilities.generateExperienceId(assets2, texts2)
        
        // Assert
        assertEquals("Should generate same ID regardless of content order (sorted internally)", id1, id2)
    }
    
    @Test
    fun testGenerateExperienceId_DifferentContent_GeneratesDifferentIds() {
        // Arrange
        val assets1 = listOf(ContentItem("https://example.com/image1.jpg"))
        val texts1 = listOf(ContentItem("Welcome"))
        
        val assets2 = listOf(ContentItem("https://example.com/image2.jpg"))
        val texts2 = listOf(ContentItem("Welcome"))
        
        // Act
        val id1 = ContentAnalyticsUtilities.generateExperienceId(assets1, texts1)
        val id2 = ContentAnalyticsUtilities.generateExperienceId(assets2, texts2)
        
        // Assert
        assertNotEquals("Different content should generate different IDs", id1, id2)
    }
    
    @Test
    fun testGenerateExperienceId_WithCTAs_AffectsId() {
        // Arrange
        val assets = listOf(ContentItem("https://example.com/image.jpg"))
        val texts = listOf(ContentItem("Welcome"))
        val ctas1 = listOf(ContentItem("Buy Now"))
        val ctas2 = listOf(ContentItem("Learn More"))
        
        // Act
        val idWithCTA1 = ContentAnalyticsUtilities.generateExperienceId(assets, texts, ctas1)
        val idWithCTA2 = ContentAnalyticsUtilities.generateExperienceId(assets, texts, ctas2)
        val idWithoutCTA = ContentAnalyticsUtilities.generateExperienceId(assets, texts, null)
        
        // Assert
        assertNotEquals("Different CTAs should generate different IDs", idWithCTA1, idWithCTA2)
        assertNotEquals("With/without CTAs should generate different IDs", idWithCTA1, idWithoutCTA)
    }
    
    @Test
    fun testGenerateExperienceId_EmptyArrays_GeneratesValidId() {
        // Arrange
        val assets = emptyList<ContentItem>()
        val texts = emptyList<ContentItem>()
        
        // Act
        val id = ContentAnalyticsUtilities.generateExperienceId(assets, texts)
        
        // Assert
        assertTrue("Should generate valid ID even with empty arrays", id.startsWith("mobile-"))
        assertEquals("Should have correct length", 19, id.length)
    }
    
    @Test
    fun testGenerateExperienceId_WithUnicode_HandlesCorrectly() {
        // Arrange
        val assets = listOf(ContentItem("https://example.com/image.jpg"))
        val texts = listOf(ContentItem("欢迎 🎉 مرحبا"))
        
        // Act
        val id1 = ContentAnalyticsUtilities.generateExperienceId(assets, texts)
        val id2 = ContentAnalyticsUtilities.generateExperienceId(assets, texts)
        
        // Assert
        assertEquals("Should handle Unicode consistently", id1, id2)
        assertTrue("Should generate valid ID with Unicode", id1.startsWith("mobile-"))
    }
    
    @Test
    fun testGenerateExperienceId_WithLargeContent_HandlesCorrectly() {
        // Arrange - Large arrays
        val assets = (0 until 100).map { 
            ContentItem("https://example.com/image$it.jpg") 
        }
        val texts = (0 until 100).map { 
            ContentItem("Text $it") 
        }
        
        // Act
        val id = ContentAnalyticsUtilities.generateExperienceId(assets, texts)
        
        // Assert
        assertTrue("Should handle large content arrays", id.startsWith("mobile-"))
        assertEquals("Should maintain correct length", 19, id.length)
    }
    
    @Test
    fun testGenerateExperienceId_DifferentContentCombinations_GeneratesUniqueIds() {
        // Arrange - Different content combinations
        val pairs = listOf(
            Pair(
                listOf(ContentItem("https://a.com")),
                listOf(ContentItem("Text B"))
            ),
            Pair(
                listOf(ContentItem("https://b.com")),
                listOf(ContentItem("Text A"))
            ),
            Pair(
                listOf(ContentItem("https://a.com")),
                listOf(ContentItem("Text A"))
            )
        )
        
        // Act
        val ids = pairs.map { 
            ContentAnalyticsUtilities.generateExperienceId(it.first, it.second) 
        }
        
        // Assert - Verify all IDs are unique
        val uniqueIDs = ids.toSet()
        assertEquals("All different content should generate unique IDs", ids.size, uniqueIDs.size)
    }
    
    @Test
    fun testGenerateExperienceId_Stability_MultipleCalls() {
        // Arrange
        val assets = listOf(ContentItem("https://example.com/hero.jpg"))
        val texts = listOf(ContentItem("Welcome"))
        val ctas = listOf(ContentItem("Click Me"))
        
        // Act - Generate 100 times
        val ids = (1..100).map {
            ContentAnalyticsUtilities.generateExperienceId(assets, texts, ctas)
        }
        
        // Assert - All should be identical
        val uniqueIDs = ids.toSet()
        assertEquals("Should generate identical ID across multiple calls", 1, uniqueIDs.size)
    }
    
    @Test
    fun testGenerateExperienceId_HexCharactersOnly() {
        // Arrange
        val assets = listOf(ContentItem("https://example.com/test.jpg"))
        val texts = listOf(ContentItem("Test"))
        
        // Act
        val id = ContentAnalyticsUtilities.generateExperienceId(assets, texts)
        
        // Assert
        val hashPart = id.removePrefix("mobile-")
        assertTrue("Hash should contain only hex characters", 
            hashPart.all { it in '0'..'9' || it in 'a'..'f' })
    }
}

