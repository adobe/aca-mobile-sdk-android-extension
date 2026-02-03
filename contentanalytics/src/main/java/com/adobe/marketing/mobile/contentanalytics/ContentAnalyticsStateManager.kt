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

import com.adobe.marketing.mobile.services.DataEntity
import com.adobe.marketing.mobile.services.DataQueue
import com.adobe.marketing.mobile.services.Log
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * State manager for Content Analytics extension
 */
internal class ContentAnalyticsStateManager(
    private val configManager: ConfigurationManaging = ConfigurationManager(),
    private val definitionCache: DefinitionCacheProtocol = DefinitionCache(),
    private val definitionRepository: DefinitionRepositoryProtocol = DefinitionRepository()
) {
    
    private val lock = ReentrantReadWriteLock()
    
    
    val configuration: ContentAnalyticsConfiguration?
        get() = configManager.getCurrentConfiguration()
    
    val batchingEnabled: Boolean
        get() = configManager.batchingEnabled
    
    fun updateConfiguration(config: ContentAnalyticsConfiguration) {
        configManager.updateConfiguration(config)
    }
    
    fun setDefinitionsDataQueue(queue: DataQueue?) {
        definitionRepository.setDataQueue(queue)
        
        if (queue != null) {
            lock.write {
                val cacheCapacity = 100
                val definitions = definitionRepository.restoreAll(cacheCapacity)
                for (definition in definitions) {
                    definitionCache.store(definition)
                }
                Log.debug(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "Restored ${definitions.size} definitions from disk to cache"
                )
            }
        }
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
        definitionRepository.save(definition)
    }
    
    
    /** Get definition (checks cache first, falls back to disk) */
    fun getExperienceDefinition(experienceId: String): ExperienceDefinition? {
        lock.read {
            definitionCache.get(experienceId)?.let { return it }
        }
        
        return lock.write {
            definitionCache.get(experienceId)?.let { return@write it }
            
            val definition = definitionRepository.load(experienceId)
            if (definition != null) {
                definitionCache.store(definition)
            } else {
                Log.warning(
                    ContentAnalyticsConstants.LOG_TAG,
                    ContentAnalyticsConstants.LogLabels.STATE_MANAGER,
                    "⚠️ Experience definition not found for '$experienceId'. " +
                    "Make sure to call ContentAnalytics.trackExperience() with " +
                    "interactionType: DEFINITION (including assetURLs and texts) before tracking views/clicks."
                )
            }
            definition
        }
    }
    
    fun getAllExperienceDefinitions(): List<ExperienceDefinition> = lock.read {
        return definitionCache.getAllDefinitions()
    }
    
    fun clearExperienceDefinitions() = lock.write {
        definitionCache.removeAll()
    }
    
    
    fun hasExperienceDefinitionBeenSent(experienceId: String): Boolean = lock.read {
        definitionCache.get(experienceId)?.let {
            return it.sentToFeaturization
        }
        val diskDef = definitionRepository.load(experienceId)
        return diskDef?.sentToFeaturization ?: false
    }
    
    fun markExperienceDefinitionAsSent(experienceId: String) = lock.write {
        var definition = definitionCache.get(experienceId)
        if (definition == null) {
            definition = definitionRepository.load(experienceId)
        }
        if (definition != null) {
            val updatedDefinition = definition.copy(sentToFeaturization = true)
            definitionCache.update(updatedDefinition)
            definitionRepository.save(updatedDefinition)
        }
    }
    
    
    fun reset() = lock.write {
        configManager.reset()
        definitionCache.removeAll()
        definitionRepository.clearAll()
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
