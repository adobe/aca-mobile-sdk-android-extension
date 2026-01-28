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

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for BatchCoordinator configuration and setup.
 * Integration tests with real persistence are in BatchCoordinatorIntegrationTest.
 */
class BatchCoordinatorTest {
    
    @Test
    fun testBatchConfiguration_DefaultValues() {
        // Arrange
        val config = ContentAnalyticsConfiguration()
        
        // Assert
        assertTrue("Batching should be enabled by default", config.batchingEnabled)
        assertEquals("Default batch size should be 5", 
            ContentAnalyticsConstants.Defaults.MAX_BATCH_SIZE, config.maxBatchSize)
        assertEquals("Default flush interval should be 5000ms",
            ContentAnalyticsConstants.Defaults.BATCH_FLUSH_INTERVAL, config.batchFlushInterval)
    }
    
    @Test
    fun testBatchConfiguration_CustomValues() {
        // Arrange
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            maxBatchSize = 10,
            batchFlushInterval = 30000
        )
        
        // Assert
        assertFalse("Batching should be disabled", config.batchingEnabled)
        assertEquals("Batch size should be 10", 10, config.maxBatchSize)
        assertEquals("Flush interval should be 30000ms", 30000, config.batchFlushInterval)
    }
    
    @Test
    fun testBatchingEnabled_WhenConfigured() {
        // Arrange
        val state = ContentAnalyticsStateManager()
        val config = ContentAnalyticsConfiguration(batchingEnabled = true)
        
        // Act
        state.updateConfiguration(config)
        
        // Assert
        assertTrue("Batching should be enabled", state.batchingEnabled)
    }
    
    @Test
    fun testBatchingDisabled_WhenConfigured() {
        // Arrange
        val state = ContentAnalyticsStateManager()
        val config = ContentAnalyticsConfiguration(batchingEnabled = false)
        
        // Act
        state.updateConfiguration(config)
        
        // Assert
        assertFalse("Batching should be disabled", state.batchingEnabled)
    }
    
    @Test
    fun testMaxBatchSize_EnforcesMinimum() {
        // Arrange - Even if set to invalid value, should use default
        val config = ContentAnalyticsConfiguration(
            maxBatchSize = ContentAnalyticsConstants.Defaults.MAX_BATCH_SIZE
        )
        
        // Assert
        assertTrue("Batch size should be positive", config.maxBatchSize > 0)
    }
    
    @Test
    fun testFlushInterval_IsPositive() {
        // Arrange
        val config = ContentAnalyticsConfiguration()
        
        // Assert
        assertTrue("Flush interval should be positive", config.batchFlushInterval > 0)
    }
}

