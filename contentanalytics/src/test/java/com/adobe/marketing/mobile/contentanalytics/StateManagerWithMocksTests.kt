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

import com.adobe.marketing.mobile.services.DataQueue
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Demonstrates the power of protocol-based dependency injection
 * All dependencies are mocked - NO disk I/O, NO real implementations
 * Tests are fast, isolated, and focused on StateManager logic only
 */
class StateManagerWithMocksTests {
    
    private lateinit var stateManager: ContentAnalyticsStateManager
    private lateinit var mockCache: MockDefinitionCache
    private lateinit var mockRepository: MockDefinitionRepository
    private lateinit var mockConfig: MockConfigurationManager
    
    @Before
    fun setUp() {
        // Create mocks
        mockCache = MockDefinitionCache()
        mockRepository = MockDefinitionRepository()
        mockConfig = MockConfigurationManager()
        
        // Inject mocks into StateManager
        stateManager = ContentAnalyticsStateManager(
            configManager = mockConfig,
            definitionCache = mockCache,
            definitionRepository = mockRepository
        )
    }
    
    @After
    fun tearDown() {
        // Clean up
    }
    
    // MARK: - Store Definition Tests
    
    @Test
    fun `storeExperienceDefinition stores in cache and repository`() {
        // Given
        val definition = createDefinition("exp1")
        
        // When
        stateManager.registerExperienceDefinition(definition)
        
        // Then - should store in both cache and repository
        assertEquals(1, mockCache.storedDefinitions.size)
        assertEquals(1, mockRepository.savedDefinitions.size)
        assertEquals("exp1", mockCache.storedDefinitions.first().experienceId)
        assertEquals("exp1", mockRepository.savedDefinitions.first().experienceId)
    }
    
    // MARK: - Get Definition Tests
    
    @Test
    fun `getExperienceDefinition cache hit returns from cache`() {
        // Given - definition in cache
        val definition = createDefinition("exp1")
        mockCache.definitions["exp1"] = definition
        
        // When
        val result = stateManager.getExperienceDefinition("exp1")
        
        // Then
        assertNotNull(result)
        assertEquals("exp1", result?.experienceId)
        assertEquals(1, mockCache.getCallCount)
        assertEquals(0, mockRepository.loadCallCount) // Repository not called
    }
    
    @Test
    fun `getExperienceDefinition cache miss loads from repository`() {
        // Given - definition only in repository
        val definition = createDefinition("exp1")
        mockRepository.persistedDefinitions["exp1"] = definition
        
        // When
        val result = stateManager.getExperienceDefinition("exp1")
        
        // Then
        assertNotNull(result)
        assertEquals("exp1", result?.experienceId)
        assertEquals(2, mockCache.getCallCount) // Called twice: read lock check + write lock double-check
        assertEquals(1, mockRepository.loadCallCount) // Repository called
        assertEquals(1, mockCache.storedDefinitions.size) // Restored to cache
    }
    
    @Test
    fun `getExperienceDefinition not found returns null`() {
        // Given - definition doesn't exist anywhere
        
        // When
        val result = stateManager.getExperienceDefinition("nonexistent")
        
        // Then
        assertNull(result)
    }
    
    // MARK: - Featurization Tests
    
    @Test
    fun `markExperienceDefinitionAsSent updates cache and repository`() {
        // Given - definition exists
        val definition = createDefinition("exp1", sentToFeaturization = false)
        mockCache.definitions["exp1"] = definition
        
        // When
        stateManager.markExperienceDefinitionAsSent("exp1")
        
        // Then
        assertEquals(1, mockCache.updateCallCount)
        assertEquals(1, mockRepository.saveCallCount)
        
        val updated = mockCache.definitions["exp1"]
        assertTrue(updated?.sentToFeaturization == true)
    }
    
    @Test
    fun `hasExperienceDefinitionBeenSent cache hit returns true`() {
        // Given
        val definition = createDefinition("exp1", sentToFeaturization = true)
        mockCache.definitions["exp1"] = definition
        
        // When
        val result = stateManager.hasExperienceDefinitionBeenSent("exp1")
        
        // Then
        assertTrue(result)
        assertEquals(0, mockRepository.loadCallCount) // Repository not called
    }
    
    @Test
    fun `hasExperienceDefinitionBeenSent cache miss loads from repository`() {
        // Given - only in repository
        val definition = createDefinition("exp1", sentToFeaturization = true)
        mockRepository.persistedDefinitions["exp1"] = definition
        
        // When
        val result = stateManager.hasExperienceDefinitionBeenSent("exp1")
        
        // Then
        assertTrue(result)
        assertEquals(1, mockRepository.loadCallCount)
    }
    
    // MARK: - Configuration Tests
    
    @Test
    fun `batchingEnabled delegates to configManager`() {
        // Given
        mockConfig.batchingEnabledValue = true
        
        // When
        val result = stateManager.batchingEnabled
        
        // Then
        assertTrue(result)
        assertEquals(1, mockConfig.batchingEnabledCallCount)
    }
    
    @Test
    fun `shouldTrackUrl delegates to configManager`() {
        // Given
        val url = "https://example.com"
        mockConfig.urlTrackingResult = true
        
        // When
        val result = stateManager.shouldTrackUrl(url)
        
        // Then
        assertTrue(result)
        assertEquals(1, mockConfig.shouldTrackUrlCallCount)
    }
    
    // MARK: - Reset Tests
    
    @Test
    fun `reset clears all components`() {
        // When
        stateManager.reset()
        
        // Then
        assertEquals(1, mockConfig.resetCallCount)
        assertEquals(1, mockCache.removeAllCallCount)
        assertEquals(1, mockRepository.clearAllCallCount)
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

// MARK: - Mock Implementations

/** Mock cache that tracks all calls and stores definitions in memory */
internal class MockDefinitionCache : DefinitionCacheProtocol {
    val definitions = mutableMapOf<String, ExperienceDefinition>()
    val storedDefinitions = mutableListOf<ExperienceDefinition>()
    
    var getCallCount = 0
    var storeCallCount = 0
    var updateCallCount = 0
    var removeAllCallCount = 0
    
    override fun store(definition: ExperienceDefinition) {
        storeCallCount++
        storedDefinitions.add(definition)
        definitions[definition.experienceId] = definition
    }
    
    override fun get(experienceId: String): ExperienceDefinition? {
        getCallCount++
        return definitions[experienceId]
    }
    
    override fun contains(experienceId: String): Boolean {
        return definitions.containsKey(experienceId)
    }
    
    override fun update(definition: ExperienceDefinition) {
        updateCallCount++
        definitions[definition.experienceId] = definition
    }
    
    override fun getAllDefinitions(): List<ExperienceDefinition> {
        return definitions.values.toList()
    }
    
    override val count: Int
        get() = definitions.size
    
    override fun markAsSent(experienceId: String): ExperienceDefinition? {
        val definition = definitions[experienceId] ?: return null
        val updated = definition.copy(sentToFeaturization = true)
        definitions[experienceId] = updated
        return updated
    }
    
    override fun hasBeenSent(experienceId: String): Boolean {
        return definitions[experienceId]?.sentToFeaturization ?: false
    }
    
    override fun getSentCount(): Int {
        return definitions.values.count { it.sentToFeaturization }
    }
    
    override fun removeAll() {
        removeAllCallCount++
        definitions.clear()
        storedDefinitions.clear()
    }
}

/** Mock repository that tracks all calls and stores definitions in memory */
internal class MockDefinitionRepository : DefinitionRepositoryProtocol {
    val persistedDefinitions = mutableMapOf<String, ExperienceDefinition>()
    val savedDefinitions = mutableListOf<ExperienceDefinition>()
    
    var saveCallCount = 0
    var loadCallCount = 0
    var clearAllCallCount = 0
    
    override fun setDataQueue(queue: DataQueue?) {
        // No-op for mock
    }
    
    override fun save(definition: ExperienceDefinition) {
        saveCallCount++
        savedDefinitions.add(definition)
        persistedDefinitions[definition.experienceId] = definition
    }
    
    override fun load(experienceId: String): ExperienceDefinition? {
        loadCallCount++
        return persistedDefinitions[experienceId]
    }
    
    override fun restoreAll(capacity: Int): List<ExperienceDefinition> {
        return persistedDefinitions.values.take(capacity)
    }
    
    override fun contains(experienceId: String): Boolean {
        return persistedDefinitions.containsKey(experienceId)
    }
    
    override fun clearAll() {
        clearAllCallCount++
        persistedDefinitions.clear()
        savedDefinitions.clear()
    }
}

/** Mock configuration manager that tracks all calls */
internal class MockConfigurationManager : ConfigurationManaging {
    var config: ContentAnalyticsConfiguration? = null
    var batchingEnabledValue = false
    var urlTrackingResult = true
    var experienceTrackingResult = true
    var assetTrackingResult = true
    
    var batchingEnabledCallCount = 0
    var shouldTrackUrlCallCount = 0
    var shouldTrackExperienceCallCount = 0
    var shouldTrackAssetLocationCallCount = 0
    var resetCallCount = 0
    
    override fun updateConfiguration(config: ContentAnalyticsConfiguration) {
        this.config = config
    }
    
    override fun getCurrentConfiguration(): ContentAnalyticsConfiguration? {
        return config
    }
    
    override val batchingEnabled: Boolean
        get() {
            batchingEnabledCallCount++
            return batchingEnabledValue
        }
    
    override fun shouldTrackUrl(url: String): Boolean {
        shouldTrackUrlCallCount++
        return urlTrackingResult
    }
    
    override fun shouldTrackExperience(location: String?): Boolean {
        shouldTrackExperienceCallCount++
        return experienceTrackingResult
    }
    
    override fun shouldTrackAssetLocation(location: String?): Boolean {
        shouldTrackAssetLocationCallCount++
        return assetTrackingResult
    }
    
    override fun reset() {
        resetCallCount++
        config = null
    }
}
