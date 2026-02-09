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
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for featurization service: data structures, encoding, serialization, and error handling.
 * Network integration requires complex mocking, so we focus on testable components.
 */
class FeaturizationTest {
    
    // MARK: - ContentData Tests
    
    @Test
    fun `ContentData initialization creates valid structure`() {
        // Given
        val images = listOf(mapOf("url" to "https://example.com/image.jpg"))
        val texts = listOf(mapOf("value" to "Welcome", "role" to "headline"))
        val ctas = listOf(mapOf("value" to "Buy Now", "enabled" to true))
        
        // When
        val contentData = ContentData(images = images, texts = texts, ctas = ctas)
        
        // Then
        assertEquals("Should have 1 image", 1, contentData.images.size)
        assertEquals("Should have 1 text", 1, contentData.texts.size)
        assertEquals("Should have 1 CTA", 1, contentData.ctas?.size)
    }
    
    @Test
    fun `ContentData with null CTAs handles correctly`() {
        // Given
        val images = listOf(mapOf("url" to "https://example.com/image.jpg"))
        val texts = listOf(mapOf("value" to "Welcome"))
        
        // When
        val contentData = ContentData(images = images, texts = texts, ctas = null)
        
        // Then
        assertEquals("Should have 1 image", 1, contentData.images.size)
        assertEquals("Should have 1 text", 1, contentData.texts.size)
        assertNull("CTAs should be null", contentData.ctas)
    }
    
    @Test
    fun `ContentData toMap produces valid structure`() {
        // Given
        val images = listOf(mapOf("url" to "https://example.com/image.jpg"))
        val texts = listOf(mapOf("value" to "Welcome"))
        val contentData = ContentData(images = images, texts = texts, ctas = null)
        
        // When
        val map = contentData.toMap()
        
        // Then
        assertTrue("Should contain images key", map.containsKey("images"))
        assertTrue("Should contain texts key", map.containsKey("texts"))
        assertFalse("Should not contain ctas key when null", map.containsKey("ctas"))
        assertEquals("Images should match", images, map["images"])
        assertEquals("Texts should match", texts, map["texts"])
    }
    
    @Test
    fun `ContentData toMap includes CTAs when present`() {
        // Given
        val images = listOf(mapOf("url" to "https://example.com/image.jpg"))
        val texts = listOf(mapOf("value" to "Welcome"))
        val ctas = listOf(mapOf("value" to "Buy Now"))
        val contentData = ContentData(images = images, texts = texts, ctas = ctas)
        
        // When
        val map = contentData.toMap()
        
        // Then
        assertTrue("Should contain ctas key", map.containsKey("ctas"))
        assertEquals("CTAs should match", ctas, map["ctas"])
    }
    
    @Test
    fun `ContentData fromMap deserializes correctly`() {
        // Given
        val map = mapOf(
            "images" to listOf(mapOf("url" to "https://example.com/image.jpg")),
            "texts" to listOf(mapOf("value" to "Welcome")),
            "ctas" to listOf(mapOf("value" to "Buy Now"))
        )
        
        // When
        val contentData = ContentData.fromMap(map)
        
        // Then
        assertEquals("Should have 1 image", 1, contentData.images.size)
        assertEquals("Should have 1 text", 1, contentData.texts.size)
        assertEquals("Should have 1 CTA", 1, contentData.ctas?.size)
    }
    
    @Test
    fun `ContentData fromMap handles missing CTAs`() {
        // Given
        val map = mapOf(
            "images" to listOf(mapOf("url" to "https://example.com/image.jpg")),
            "texts" to listOf(mapOf("value" to "Welcome"))
        )
        
        // When
        val contentData = ContentData.fromMap(map)
        
        // Then
        assertNull("CTAs should be null when missing", contentData.ctas)
    }
    
    @Test
    fun `ContentData fromMap handles empty map`() {
        // Given
        val emptyMap = emptyMap<String, Any?>()
        
        // When
        val contentData = ContentData.fromMap(emptyMap)
        
        // Then
        assertTrue("Images should be empty", contentData.images.isEmpty())
        assertTrue("Texts should be empty", contentData.texts.isEmpty())
        assertNull("CTAs should be null", contentData.ctas)
    }
    
    // MARK: - ExperienceContent Tests
    
    @Test
    fun `ExperienceContent initialization creates valid structure`() {
        // Given
        val contentData = ContentData(
            images = listOf(mapOf("url" to "https://example.com/image.jpg")),
            texts = listOf(mapOf("value" to "Welcome")),
            ctas = null
        )
        val channel = "mobile"
        val orgId = "TEST_ORG@AdobeOrg"
        val datastreamId = "test-datastream-id"
        val experienceId = "test-experience-id"
        
        // When
        val experienceContent = ExperienceContent(
            content = contentData,
            orgId = orgId,
            datastreamId = datastreamId,
            experienceId = experienceId
        )
        
        // Then
        assertEquals("OrgId should match", orgId, experienceContent.orgId)
        assertEquals("Datastream ID should match", datastreamId, experienceContent.datastreamId)
        assertEquals("Experience ID should match", experienceId, experienceContent.experienceId)
        assertNotNull("Content should not be null", experienceContent.content)
    }
    
    @Test
    fun `ExperienceContent includes all required fields`() {
        // Given
        val contentData = ContentData(
            images = listOf(mapOf("url" to "https://example.com/image.jpg")),
            texts = listOf(mapOf("value" to "Welcome")),
            ctas = null
        )
        
        // When
        val experienceContent = ExperienceContent(
            content = contentData,
            orgId = "TEST_ORG@AdobeOrg",
            datastreamId = "test-datastream",
            experienceId = "test-experience-id"
        )
        
        // Then
        assertEquals("OrgId should match", "TEST_ORG@AdobeOrg", experienceContent.orgId)
        assertEquals("Datastream ID should match", "test-datastream", experienceContent.datastreamId)
        assertEquals("Experience ID should match", "test-experience-id", experienceContent.experienceId)
    }
    
    @Test
    fun `ExperienceContent toMap produces valid structure`() {
        // Given
        val contentData = ContentData(
            images = listOf(mapOf("url" to "https://example.com/image.jpg")),
            texts = listOf(mapOf("value" to "Welcome")),
            ctas = null
        )
        val experienceContent = ExperienceContent(
            content = contentData,
            orgId = "TEST_ORG@AdobeOrg",
            datastreamId = "test-datastream",
            experienceId = "test-experience-id"
        )
        
        // When
        val map = experienceContent.toMap()
        
        // Then
        assertTrue("Should contain content key", map.containsKey("content"))
        assertTrue("Should contain orgId key", map.containsKey("orgId"))
        assertTrue("Should contain datastreamId key", map.containsKey("datastreamId"))
        assertTrue("Should contain experienceId key", map.containsKey("experienceId"))
        assertEquals("OrgId should match", "TEST_ORG@AdobeOrg", map["orgId"])
        assertEquals("Datastream ID should match", "test-datastream", map["datastreamId"])
        assertEquals("Experience ID should match", "test-experience-id", map["experienceId"])
    }
    
    @Test
    fun `ExperienceContent fromMap deserializes correctly`() {
        // Given
        val map = mapOf(
            "content" to mapOf(
                "images" to listOf(mapOf("url" to "https://example.com/image.jpg")),
                "texts" to listOf(mapOf("value" to "Welcome"))
            ),
            "orgId" to "TEST_ORG@AdobeOrg",
            "datastreamId" to "test-datastream",
            "experienceId" to "test-experience-id"
        )
        
        // When
        val experienceContent = ExperienceContent.fromMap(map)
        
        // Then
        assertEquals("OrgId should match", "TEST_ORG@AdobeOrg", experienceContent.orgId)
        assertEquals("Datastream ID should match", "test-datastream", experienceContent.datastreamId)
        assertEquals("Experience ID should match", "test-experience-id", experienceContent.experienceId)
        assertNotNull("Content should not be null", experienceContent.content)
    }
    
    // MARK: - FeaturizationHit Tests
    
    @Test
    fun `FeaturizationHit initialization creates valid structure`() {
        // Given
        val experienceId = "exp-123"
        val imsOrg = "TEST_ORG@AdobeOrg"
        val content = createTestExperienceContent()
        
        // When
        val hit = FeaturizationHit(
            experienceId = experienceId,
            imsOrg = imsOrg,
            content = content
        )
        
        // Then
        assertEquals("Experience ID should match", experienceId, hit.experienceId)
        assertEquals("IMS Org should match", imsOrg, hit.imsOrg)
        assertNotNull("Content should not be null", hit.content)
        assertEquals("Attempt count should be 0", 0, hit.attemptCount)
        assertTrue("Timestamp should be set", hit.timestamp > 0)
    }
    
    @Test
    fun `FeaturizationHit with custom attempt count`() {
        // Given
        val hit = FeaturizationHit(
            experienceId = "exp-123",
            imsOrg = "TEST_ORG@AdobeOrg",
            content = createTestExperienceContent(),
            attemptCount = 3
        )
        
        // When/Then
        assertEquals("Attempt count should be 3", 3, hit.attemptCount)
    }
    
    @Test
    fun `FeaturizationHit toJson produces valid JSON string`() {
        // Given
        val hit = FeaturizationHit(
            experienceId = "exp-123",
            imsOrg = "TEST_ORG@AdobeOrg",
            content = createTestExperienceContent()
        )
        
        // When
        val json = hit.toJson()
        
        // Then
        assertNotNull("JSON should not be null", json)
        assertTrue("JSON should contain experienceId", json.contains("experienceId"))
        assertTrue("JSON should contain imsOrg", json.contains("imsOrg"))
        assertTrue("JSON should contain content", json.contains("content"))
        assertTrue("JSON should contain timestamp", json.contains("timestamp"))
        assertTrue("JSON should contain attemptCount", json.contains("attemptCount"))
    }
    
    @Test
    fun `FeaturizationHit fromJson deserializes correctly`() {
        // Given
        val originalHit = FeaturizationHit(
            experienceId = "exp-123",
            imsOrg = "TEST_ORG@AdobeOrg",
            content = createTestExperienceContent(),
            attemptCount = 2
        )
        val json = originalHit.toJson()
        
        // When
        val deserializedHit = FeaturizationHit.fromJson(json)
        
        // Then
        assertNotNull("Deserialized hit should not be null", deserializedHit)
        assertEquals("Experience ID should match", originalHit.experienceId, deserializedHit?.experienceId)
        assertEquals("IMS Org should match", originalHit.imsOrg, deserializedHit?.imsOrg)
        assertEquals("Attempt count should match", originalHit.attemptCount, deserializedHit?.attemptCount)
        assertEquals("Timestamp should match", originalHit.timestamp, deserializedHit?.timestamp)
    }
    
    @Test
    fun `FeaturizationHit fromJson handles invalid JSON`() {
        // Given
        val invalidJson = "{ invalid json }"
        
        // When
        val hit = FeaturizationHit.fromJson(invalidJson)
        
        // Then
        assertNull("Hit should be null for invalid JSON", hit)
    }
    
    @Test
    fun `FeaturizationHit fromJson handles incomplete JSON`() {
        // Given
        val incompleteJson = """{"experienceId":"exp-123"}"""
        
        // When
        val hit = FeaturizationHit.fromJson(incompleteJson)
        
        // Then
        assertNull("Hit should be null for incomplete JSON", hit)
    }
    
    @Test
    fun `FeaturizationHit serialization roundtrip preserves data`() {
        // Given
        val originalHit = FeaturizationHit(
            experienceId = "exp-456",
            imsOrg = "ROUNDTRIP_ORG@AdobeOrg",
            content = createTestExperienceContent(),
            timestamp = 1234567890L,
            attemptCount = 5
        )
        
        // When
        val json = originalHit.toJson()
        val deserializedHit = FeaturizationHit.fromJson(json)
        
        // Then
        assertNotNull("Deserialized hit should not be null", deserializedHit)
        assertEquals("Experience ID should survive roundtrip", originalHit.experienceId, deserializedHit?.experienceId)
        assertEquals("IMS Org should survive roundtrip", originalHit.imsOrg, deserializedHit?.imsOrg)
        assertEquals("Timestamp should survive roundtrip", originalHit.timestamp, deserializedHit?.timestamp)
        assertEquals("Attempt count should survive roundtrip", originalHit.attemptCount, deserializedHit?.attemptCount)
        assertEquals("Content orgId should survive roundtrip", originalHit.content.orgId, deserializedHit?.content?.orgId)
        assertEquals("Content datastreamId should survive roundtrip", originalHit.content.datastreamId, deserializedHit?.content?.datastreamId)
        assertEquals("Content experienceId should survive roundtrip", originalHit.content.experienceId, deserializedHit?.content?.experienceId)
    }
    
    // MARK: - FeaturizationError Tests
    
    @Test
    fun `FeaturizationError InvalidURL has correct message`() {
        // Given
        val error = FeaturizationError.InvalidURL("https://invalid url")
        
        // When
        val message = error.message
        
        // Then
        assertNotNull("Message should not be null", message)
        assertTrue("Message should contain URL", message?.contains("https://invalid url") == true)
        assertTrue("Message should mention invalid URL", message?.contains("Invalid") == true)
    }
    
    @Test
    fun `FeaturizationError InvalidResponse has correct message`() {
        // Given
        val error = FeaturizationError.InvalidResponse
        
        // When
        val message = error.message
        
        // Then
        assertNotNull("Message should not be null", message)
        assertTrue("Message should mention invalid response", message?.contains("Invalid response") == true)
    }
    
    @Test
    fun `FeaturizationError HttpError has correct message`() {
        // Given
        val error = FeaturizationError.HttpError(500)
        
        // When
        val message = error.message
        
        // Then
        assertNotNull("Message should not be null", message)
        assertTrue("Message should contain status code", message?.contains("500") == true)
        assertTrue("Message should mention HTTP error", message?.contains("HTTP error") == true)
    }
    
    @Test
    fun `FeaturizationError NetworkError has correct message`() {
        // Given
        val error = FeaturizationError.NetworkError("Connection timeout")
        
        // When
        val message = error.message
        
        // Then
        assertNotNull("Message should not be null", message)
        assertTrue("Message should contain error detail", message?.contains("Connection timeout") == true)
        assertTrue("Message should mention network error", message?.contains("Network error") == true)
    }
    
    @Test
    fun `FeaturizationError types are distinguishable`() {
        // Given
        val invalidUrlError = FeaturizationError.InvalidURL("test")
        val invalidResponseError = FeaturizationError.InvalidResponse
        val httpError = FeaturizationError.HttpError(404)
        val networkError = FeaturizationError.NetworkError("test")
        
        // When/Then
        assertTrue("Should be InvalidURL type", invalidUrlError is FeaturizationError.InvalidURL)
        assertTrue("Should be InvalidResponse type", invalidResponseError is FeaturizationError.InvalidResponse)
        assertTrue("Should be HttpError type", httpError is FeaturizationError.HttpError)
        assertTrue("Should be NetworkError type", networkError is FeaturizationError.NetworkError)
        
        // All should be FeaturizationError
        assertTrue("InvalidURL should be FeaturizationError", invalidUrlError is FeaturizationError)
        assertTrue("InvalidResponse should be FeaturizationError", invalidResponseError is FeaturizationError)
        assertTrue("HttpError should be FeaturizationError", httpError is FeaturizationError)
        assertTrue("NetworkError should be FeaturizationError", networkError is FeaturizationError)
    }
    
    @Test
    fun `FeaturizationError HttpError with different codes creates different instances`() {
        // Given
        val error404 = FeaturizationError.HttpError(404)
        val error500 = FeaturizationError.HttpError(500)
        
        // When/Then
        assertNotEquals("Different status codes should create different error messages", 
                       error404.message, error500.message)
        assertTrue("404 error should contain 404", error404.message?.contains("404") == true)
        assertTrue("500 error should contain 500", error500.message?.contains("500") == true)
    }
    
    // MARK: - Complex Scenarios
    
    @Test
    fun `ContentData handles multiple images, texts, and CTAs`() {
        // Given
        val images = listOf(
            mapOf("url" to "https://example.com/image1.jpg", "alt" to "Image 1"),
            mapOf("url" to "https://example.com/image2.jpg", "alt" to "Image 2"),
            mapOf("url" to "https://example.com/image3.jpg", "alt" to "Image 3")
        )
        val texts = listOf(
            mapOf("value" to "Heading", "role" to "headline"),
            mapOf("value" to "Body text", "role" to "body"),
            mapOf("value" to "Footer", "role" to "footer")
        )
        val ctas = listOf(
            mapOf("value" to "Buy Now", "enabled" to true),
            mapOf("value" to "Learn More", "enabled" to true)
        )
        
        // When
        val contentData = ContentData(images = images, texts = texts, ctas = ctas)
        val map = contentData.toMap()
        val roundtrip = ContentData.fromMap(map)
        
        // Then
        assertEquals("Should preserve 3 images", 3, roundtrip.images.size)
        assertEquals("Should preserve 3 texts", 3, roundtrip.texts.size)
        assertEquals("Should preserve 2 CTAs", 2, roundtrip.ctas?.size)
    }
    
    @Test
    fun `ExperienceContent handles complex nested content`() {
        // Given
        val complexContent = ContentData(
            images = listOf(
                mapOf("url" to "https://example.com/image.jpg", "metadata" to mapOf("size" to 1024, "type" to "jpeg"))
            ),
            texts = listOf(
                mapOf("value" to "Welcome", "attributes" to mapOf("bold" to true, "size" to 18))
            ),
            ctas = listOf(
                mapOf("value" to "Click", "action" to mapOf("type" to "navigate", "target" to "/products"))
            )
        )
        val experienceContent = ExperienceContent(
            content = complexContent,
            orgId = "TEST_ORG@AdobeOrg",
            datastreamId = "test-123",
            experienceId = "test-experience-id"
        )
        
        // When
        val map = experienceContent.toMap()
        val roundtrip = ExperienceContent.fromMap(map)
        
        // Then
        assertEquals("OrgId should survive roundtrip", "TEST_ORG@AdobeOrg", roundtrip.orgId)
        assertEquals("Datastream ID should survive roundtrip", "test-123", roundtrip.datastreamId)
        assertEquals("Experience ID should survive roundtrip", "test-experience-id", roundtrip.experienceId)
        assertEquals("Images should survive roundtrip", 1, roundtrip.content.images.size)
        assertEquals("Texts should survive roundtrip", 1, roundtrip.content.texts.size)
        assertEquals("CTAs should survive roundtrip", 1, roundtrip.content.ctas?.size)
    }
    
    // MARK: - Helper Methods
    
    private fun createTestExperienceContent(): ExperienceContent {
        val contentData = ContentData(
            images = listOf(mapOf("url" to "https://example.com/image.jpg")),
            texts = listOf(mapOf("value" to "Test content")),
            ctas = null
        )
        return ExperienceContent(
            content = contentData,
            orgId = "TEST_ORG@AdobeOrg",
            datastreamId = "test-datastream",
            experienceId = "test-experience-id"
        )
    }
}

