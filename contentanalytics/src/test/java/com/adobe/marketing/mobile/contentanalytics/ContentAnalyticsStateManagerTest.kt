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
import org.junit.Before
import org.junit.Test

class ContentAnalyticsStateManagerTest {
    
    private lateinit var stateManager: ContentAnalyticsStateManager
    
    @Before
    fun setup() {
        stateManager = ContentAnalyticsStateManager()
    }
    
    @Test
    fun `test updateConfiguration and retrieve`() {
        val config = ContentAnalyticsConfiguration(
            batchingEnabled = false,
            maxBatchSize = 20
        )
        
        stateManager.updateConfiguration(config)
        
        assertEquals(false, stateManager.batchingEnabled)
        assertEquals(config, stateManager.configuration)
    }
    
    @Test
    fun `test shouldTrackUrl with exclusion pattern`() {
        val config = ContentAnalyticsConfiguration(
            excludedAssetUrlsRegexp = ".*\\.gif$"
        )
        stateManager.updateConfiguration(config)
        
        assertFalse(stateManager.shouldTrackUrl("https://example.com/image.gif"))
        assertTrue(stateManager.shouldTrackUrl("https://example.com/image.jpg"))
    }
    
    @Test
    fun `test registerExperienceDefinition and retrieve`() {
        val definition = ExperienceDefinition(
            experienceId = "test-exp",
            assets = listOf("https://example.com/asset1.jpg"),
            texts = listOf(ContentItem("Hello")),
            ctas = null
        )
        
        stateManager.registerExperienceDefinition(definition)
        
        val retrieved = stateManager.getExperienceDefinition("test-exp")
        assertEquals(definition, retrieved)
    }
    
    @Test
    fun `test markExperienceDefinitionAsSent`() {
        val definition = ExperienceDefinition(
            experienceId = "test-exp",
            assets = listOf("https://example.com/asset1.jpg"),
            texts = emptyList(),
            ctas = null
        )
        
        stateManager.registerExperienceDefinition(definition)
        
        assertFalse(stateManager.hasExperienceDefinitionBeenSent("test-exp"))
        
        stateManager.markExperienceDefinitionAsSent("test-exp")
        
        assertTrue(stateManager.hasExperienceDefinitionBeenSent("test-exp"))
    }
    
    @Test
    fun `test reset clears all state`() {
        val config = ContentAnalyticsConfiguration()
        val definition = ExperienceDefinition(
            experienceId = "test-exp",
            assets = emptyList(),
            texts = emptyList(),
            ctas = null
        )
        
        stateManager.updateConfiguration(config)
        stateManager.registerExperienceDefinition(definition)
        stateManager.markExperienceDefinitionAsSent("test-exp")
        
        assertEquals(1, stateManager.getExperienceDefinitionCount())
        
        stateManager.reset()
        
        assertNull(stateManager.configuration)
        assertEquals(0, stateManager.getExperienceDefinitionCount())
        assertEquals(0, stateManager.getSentExperienceDefinitionCount())
    }
    
    @Test
    fun `test getAssetsForExperience`() {
        val assets = listOf("https://example.com/asset1.jpg", "https://example.com/asset2.jpg")
        val definition = ExperienceDefinition(
            experienceId = "test-exp",
            assets = assets,
            texts = emptyList(),
            ctas = null
        )
        
        stateManager.registerExperienceDefinition(definition)
        
        val retrievedAssets = stateManager.getAssetsForExperience("test-exp")
        assertEquals(assets, retrievedAssets)
    }
    
    @Test
    fun `test getAllExperienceDefinitions`() {
        val def1 = ExperienceDefinition("exp1", emptyList(), emptyList(), null)
        val def2 = ExperienceDefinition("exp2", emptyList(), emptyList(), null)
        
        stateManager.registerExperienceDefinition(def1)
        stateManager.registerExperienceDefinition(def2)
        
        val all = stateManager.getAllExperienceDefinitions()
        assertEquals(2, all.size)
        assertTrue(all.contains(def1))
        assertTrue(all.contains(def2))
    }
}

