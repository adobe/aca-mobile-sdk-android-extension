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

import com.adobe.marketing.mobile.services.Log
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * State manager for Content Analytics extension.
 * Manages configuration and in-memory cache of experience definitions.
 */
internal class ContentAnalyticsStateManager(
    private val configManager: ConfigurationManaging = ConfigurationManager(),
    private val definitionCache: DefinitionCacheProtocol = DefinitionCache()
) {
    
    private val lock = ReentrantReadWriteLock()
    
    val configuration: ContentAnalyticsConfiguration?
        get() = configManager.getCurrentConfiguration()
    
    val batchingEnabled: Boolean
        get() = configManager.batchingEnabled
    
    fun updateConfiguration(config: ContentAnalyticsConfiguration) {
        configManager.updateConfiguration(config)
    }
    
    fun shouldTrackUrl(url: String): Boolean {
        return configManager.shouldTrackUrl(url)
    }
    
    fun shouldTrackAssetLocation(location: String?): Boolean {
        return configManager.shouldTrackAssetLocation(location)
    }
    
    fun shouldTrackExperience(location: String?): Boolean {
        return configManager.shouldTrackExperience(location)
    }
    
    fun registerExperienceDefinition(definition: ExperienceDefinition) = lock.write {
        definitionCache.store(definition)
    }
    
    fun getExperienceDefinition(experienceId: String): ExperienceDefinition? = lock.read {
        val definition = definitionCache.get(experienceId)
        if (definition == null) {
            Log.warning(
                ContentAnalyticsConstants.LOG_TAG,
                ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                "Experience definition not found for '$experienceId'. " +
                "Call ContentAnalytics.trackExperience() with interactionType: DEFINITION first."
            )
        }
        return definition
    }
    
    fun getAllExperienceDefinitions(): List<ExperienceDefinition> = lock.read {
        return definitionCache.getAllDefinitions()
    }
    
    fun clearExperienceDefinitions() = lock.write {
        definitionCache.removeAll()
    }
    
    fun hasExperienceDefinitionBeenSent(experienceId: String): Boolean = lock.read {
        return definitionCache.get(experienceId)?.sentToFeaturization ?: false
    }
    
    fun markExperienceDefinitionAsSent(experienceId: String) = lock.write {
        val definition = definitionCache.get(experienceId) ?: return@write
        val updatedDefinition = definition.copy(sentToFeaturization = true)
        definitionCache.update(updatedDefinition)
    }
    
    fun reset() = lock.write {
        configManager.reset()
        definitionCache.removeAll()
    }
    
    fun getAssetsForExperience(experienceId: String): List<String> = lock.read {
        return definitionCache.get(experienceId)?.assets ?: emptyList()
    }
    
    fun getExperienceDefinitionCount(): Int = lock.read {
        return definitionCache.count
    }
    
    fun getSentExperienceDefinitionCount(): Int = lock.read {
        return definitionCache.getSentCount()
    }
}
