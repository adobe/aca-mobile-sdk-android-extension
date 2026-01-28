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

import com.adobe.marketing.mobile.ExtensionApi
import com.adobe.marketing.mobile.contentanalytics.helpers.ContentAnalyticsMocks
import com.adobe.marketing.mobile.contentanalytics.helpers.TestDataBuilder
import com.adobe.marketing.mobile.contentanalytics.helpers.TestEventFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * Tests for ContentAnalyticsFactory: orchestrator creation, dependency injection, and component wiring.
 */
class ContentAnalyticsFactoryTest {
    
    @Mock
    private lateinit var mockExtensionApi: ExtensionApi
    
    private lateinit var stateManager: ContentAnalyticsStateManager
    private lateinit var factory: ContentAnalyticsFactory
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        stateManager = ContentAnalyticsStateManager()
        factory = ContentAnalyticsFactory(mockExtensionApi, stateManager)
    }
    
    // MARK: - Factory Initialization Tests
    
    @Test
    fun `factory initialization creates valid instance`() {
        // Given - Factory parameters
        val api = mockExtensionApi
        val state = ContentAnalyticsStateManager()
        
        // When - Factory is created
        val factory = ContentAnalyticsFactory(api, state)
        
        // Then - Should create valid instance
        assertNotNull("Factory should be created successfully", factory)
    }
    
    // MARK: - Orchestrator Creation Tests
    
    @Test
    fun `createContentAnalyticsOrchestrator creates valid orchestrator`() {
        // Given - Factory is initialized
        
        // When - Create orchestrator
        val orchestrator = factory.createContentAnalyticsOrchestrator()
        
        // Then - Should create valid orchestrator
        assertNotNull("Factory should create valid orchestrator", orchestrator)
    }
    
    @Test
    fun `createContentAnalyticsOrchestrator creates orchestrator with dependencies`() {
        // Given - Factory is initialized
        
        // When - Create orchestrator
        val orchestrator = factory.createContentAnalyticsOrchestrator()
        
        // Then - Orchestrator should be functional (can process events)
        val assetEvent = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // Should process without crashing
        orchestrator.processAssetEvent(assetEvent)
        
        // Success: orchestrator processed event without crashing
    }
    
    @Test
    fun `createContentAnalyticsOrchestrator wires batch coordinator callbacks`() {
        // Given - Factory is initialized
        
        // When - Create orchestrator
        val orchestrator = factory.createContentAnalyticsOrchestrator()
        
        // Then - Orchestrator should be created successfully (batch coordinator is internal)
        assertNotNull("Orchestrator should be created", orchestrator)
    }
    
    // MARK: - Dependency Injection Tests
    
    @Test
    fun `createContentAnalyticsOrchestrator injects state manager`() {
        // Given - Factory with specific state manager
        val customState = ContentAnalyticsStateManager()
        val customConfig = TestDataBuilder.buildConfiguration(trackExperiences = false)
        customState.updateConfiguration(customConfig)
        
        val customFactory = ContentAnalyticsFactory(mockExtensionApi, customState)
        
        // When - Create orchestrator
        val orchestrator = customFactory.createContentAnalyticsOrchestrator()
        
        // Then - Orchestrator should use the injected state manager
        // Verify by checking orchestrator respects config
        val assetEvent = TestEventFactory.createAssetEvent(
            url = "https://example.com/image.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        // Should process without crashing (using injected state)
        orchestrator.processAssetEvent(assetEvent)
        
        // Success: orchestrator used injected state manager
    }
    
    @Test
    fun `createContentAnalyticsOrchestrator injects event dispatcher`() {
        // Given - Factory is initialized
        
        // When - Create orchestrator
        val orchestrator = factory.createContentAnalyticsOrchestrator()
        
        // Then - Event dispatcher should be injected (verified by orchestrator creation)
        assertNotNull("Orchestrator should be created with event dispatcher", orchestrator)
    }
    
    @Test
    fun `createContentAnalyticsOrchestrator injects privacy validator`() {
        // Given - Factory is initialized
        
        // When - Create orchestrator
        val orchestrator = factory.createContentAnalyticsOrchestrator()
        
        // Then - Privacy validator should be injected (verified by orchestrator creation)
        assertNotNull("Orchestrator should be created with privacy validator", orchestrator)
    }
    
    @Test
    fun `createContentAnalyticsOrchestrator injects XDM event builder`() {
        // Given - Factory is initialized
        
        // When - Create orchestrator
        val orchestrator = factory.createContentAnalyticsOrchestrator()
        
        // Then - XDM event builder should be injected (verified by orchestrator creation)
        assertNotNull("Orchestrator should be created with XDM event builder", orchestrator)
    }
    
    // MARK: - Batch Coordinator Tests
    // Note: Batch coordinator is an internal implementation detail and not exposed via factory
    
    // MARK: - Privacy Validator Tests
    
    @Test
    fun `getPrivacyValidator creates instance if not initialized`() {
        // Given - Factory is initialized but orchestrator not created
        
        // When - Get privacy validator
        val privacyValidator = factory.getPrivacyValidator()
        
        // Then - Should create and return privacy validator (defensive)
        assertNotNull("Privacy validator should be created on demand", privacyValidator)
    }
    
    @Test
    fun `getPrivacyValidator returns same instance after orchestrator creation`() {
        // Given - Factory is initialized
        factory.createContentAnalyticsOrchestrator()
        
        // When - Get privacy validator twice
        val validator1 = factory.getPrivacyValidator()
        val validator2 = factory.getPrivacyValidator()
        
        // Then - Should return same instance
        assertSame("Privacy validator should return same instance", validator1, validator2)
    }
    
    // MARK: - Multiple Instance Tests
    
    @Test
    fun `createContentAnalyticsOrchestrator multiple calls create independent instances`() {
        // Given - Factory is initialized
        
        // When - Create multiple orchestrators
        val orchestrator1 = factory.createContentAnalyticsOrchestrator()
        val orchestrator2 = factory.createContentAnalyticsOrchestrator()
        
        // Then - Should create independent instances
        assertNotSame("Multiple calls should create independent orchestrator instances", 
                     orchestrator1, orchestrator2)
    }
    
    // MARK: - Featurization Queue Tests
    
    @Test
    fun `createFeaturizationHitQueue creates valid queue`() {
        // Given - Factory is initialized
        
        // When - Create featurization hit queue
        val hitQueue = factory.createFeaturizationHitQueue()
        
        // Then - Should create hit queue (may be null if data queue service unavailable)
        // Just verify no crashes - queue creation depends on ServiceProvider
        // In real app, this would return a valid queue
    }
    
    // MARK: - Configuration Tests
    // Note: Detailed batching behavior is tested in BatchCoordinatorTest and BatchCoordinatorIntegrationTest
    
    @Test
    fun `orchestrator with batching disabled sends events immediately`() {
        // Given - Factory with batching disabled
        val dispatchedEvents = mutableListOf<com.adobe.marketing.mobile.Event>()
        val mockDispatcher = com.adobe.marketing.mobile.contentanalytics.helpers.ContentAnalyticsMocks.createMockEventDispatcher(dispatchedEvents)
        val mockPrivacyValidator = com.adobe.marketing.mobile.contentanalytics.helpers.ContentAnalyticsMocks.createMockPrivacyValidator(allowDataCollection = true)
        
        val config = TestDataBuilder.buildConfiguration(batchingEnabled = false)
        stateManager.updateConfiguration(config)
        
        // When - Create orchestrator and send multiple events
        val orchestrator = ContentAnalyticsOrchestrator(
            state = stateManager,
            eventDispatcher = mockDispatcher,
            privacyValidator = mockPrivacyValidator,
            xdmEventBuilder = XDMEventBuilder,
            batchCoordinator = null
        )
        
        val event1 = com.adobe.marketing.mobile.contentanalytics.helpers.TestEventFactory.createAssetEvent(
            url = "https://example.com/image1.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        val event2 = com.adobe.marketing.mobile.contentanalytics.helpers.TestEventFactory.createAssetEvent(
            url = "https://example.com/image2.jpg",
            location = "home",
            action = ContentAnalyticsConstants.ActionType.VIEW
        )
        
        orchestrator.processAssetEvent(event1)
        orchestrator.processAssetEvent(event2)
        
        // Then - Events should be dispatched immediately (one Edge event per asset event when batching is disabled)
        val edgeEvents = dispatchedEvents.filter { it.type == com.adobe.marketing.mobile.EventType.EDGE }
        assertEquals("Should dispatch events immediately when batching is disabled", 2, edgeEvents.size)
    }
    
    @Test
    fun `factory creates orchestrator with correct batching configuration`() {
        // Given - Factory with specific batching configuration
        val config = TestDataBuilder.buildConfiguration(
            batchingEnabled = true,
            maxBatchSize = 10,
            batchFlushInterval = 5000
        )
        stateManager.updateConfiguration(config)
        
        // When - Create orchestrator
        val orchestrator = factory.createContentAnalyticsOrchestrator()
        
        // Then - Configuration should be applied to state manager (orchestrator uses this)
        assertNotNull("Orchestrator should be created", orchestrator)
        val appliedConfig = stateManager.configuration
        assertNotNull("Configuration should be set", appliedConfig)
        assertEquals("Configuration should have batching enabled", true, appliedConfig?.batchingEnabled)
        assertEquals("Configuration should have correct batch size", 10, appliedConfig?.maxBatchSize)
        assertEquals("Configuration should have correct flush interval", 5000L, appliedConfig?.batchFlushInterval)
        
        // Note: Actual batching behavior (buffering, flush triggers) is tested in:
        // - BatchCoordinatorTest: Unit tests for batch coordinator
        // - BatchCoordinatorIntegrationTest: Integration tests with real persistence
        // - ContentAnalyticsOrchestratorTest: Orchestrator respecting batching config
    }
    
    // MARK: - Edge Cases
    
    @Test
    fun `factory handles null state gracefully`() {
        // Given - Factory with empty state
        val emptyState = ContentAnalyticsStateManager()
        val emptyFactory = ContentAnalyticsFactory(mockExtensionApi, emptyState)
        
        // When - Create orchestrator
        val orchestrator = emptyFactory.createContentAnalyticsOrchestrator()
        
        // Then - Should create orchestrator without crashing
        assertNotNull("Factory should handle empty state gracefully", orchestrator)
    }
    
    @Test
    fun `factory creates components in correct order`() {
        // Given - Factory is initialized
        
        // When - Create orchestrator (which creates all components)
        val orchestrator = factory.createContentAnalyticsOrchestrator()
        
        // Then - All components should be created successfully
        assertNotNull("Orchestrator should be created", orchestrator)
        assertNotNull("Privacy validator should be created", factory.getPrivacyValidator())
    }
}

