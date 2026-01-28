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

import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.ExtensionApi
import com.adobe.marketing.mobile.services.Log

/**
 * Interface for validating privacy and consent settings
 */
internal interface PrivacyValidator {
    /**
     * Check if data collection is allowed based on privacy settings
     */
    fun isDataCollectionAllowed(): Boolean
}

/**
 * Privacy validator implementation using state and consent checking with caching optimization
 * 
 * Matches iOS StatePrivacyValidator functionality:
 * 1. Checks Hub shared state (SDK initialization)
 * 2. Checks Consent extension registration
 * 3. Checks consent collect preference
 * 4. Falls back to allowing if Consent not registered
 * 5. Caches shared states to avoid repeated fetches (performance optimization)
 */
internal class ContentAnalyticsPrivacyValidator(
    private val state: ContentAnalyticsStateManager,
    private val extensionApi: ExtensionApi
) : PrivacyValidator {
    
    companion object {
        private const val TAG = ContentAnalyticsConstants.LOG_TAG
    }
    
    // Cached shared states for performance optimization (reduces repeated getSharedState calls)
    private val cacheLock = Any()
    private var cachedHubData: Map<String, Any?>? = null
    private var cachedConsentData: Map<String, Any?>? = null
    private var cachedIsConsentRegistered: Boolean = false
    private var isCacheInitialized: Boolean = false
    
    /**
     * Updates the cached shared states from the runtime.
     * Should be called when Hub or Consent shared state change events are received.
     */
    fun updateSharedStateCache() {
        synchronized(cacheLock) {
            refreshCacheSync()
        }
    }
    
    /**
     * Internal method to refresh cache synchronously
     */
    private fun refreshCacheSync() {
        // Fetch Hub shared state
        val hubSharedState = extensionApi.getSharedState(
            ContentAnalyticsConstants.ExternalExtensions.EVENT_HUB,
            null,
            false,
            com.adobe.marketing.mobile.SharedStateResolution.ANY
        )
        
        cachedHubData = hubSharedState?.value
        
        // Check if Consent extension is registered
        if (cachedHubData != null) {
            cachedIsConsentRegistered = isConsentExtensionRegistered(cachedHubData!!)
            
            if (cachedIsConsentRegistered) {
                // Fetch Consent XDM shared state (Consent extension publishes XDM, not standard)
                val consentSharedState = extensionApi.getXDMSharedState(
                    ContentAnalyticsConstants.ExternalExtensions.CONSENT,
                    null as Event?,
                    false,
                    com.adobe.marketing.mobile.SharedStateResolution.ANY
                )
                cachedConsentData = consentSharedState?.value
            } else {
                cachedConsentData = null
            }
        }
        
        isCacheInitialized = true
        
        Log.trace(TAG, TAG, "🔄 Shared state cache updated - Consent registered: $cachedIsConsentRegistered")
    }
    
    override fun isDataCollectionAllowed(): Boolean {
        synchronized(cacheLock) {
            // Lazy initialize cache on first access (important for tests that set mock states after init)
            if (!isCacheInitialized) {
                refreshCacheSync()
            }
            
            Log.trace(TAG, TAG, "🔒 Starting privacy validation (using cached states)")
            
            // Check if Hub shared state is available
            if (cachedHubData == null) {
                Log.debug(TAG, TAG, "⏸️ No Hub shared state available, blocking data collection (waiting for SDK init)")
                return false
            }
            
            Log.debug(TAG, TAG, "🔍 Consent extension registered: $cachedIsConsentRegistered")
            
            if (cachedIsConsentRegistered) {
                // Consent is registered - check its shared state
                if (cachedConsentData == null) {
                    Log.debug(TAG, TAG, "⏸️ Consent extension registered but no shared state yet - assuming pending, blocking data collection")
                    return false
                }
                
                // Consent shared state exists - use that value
                val consents = cachedConsentData!!["consents"] as? Map<*, *>
                val collect = consents?.get("collect") as? Map<*, *>
                val value = collect?.get("val") as? String
                
                Log.debug(TAG, TAG, "🔍 Consent collect value: $value")
                
                val allowed = when (value?.lowercase()) {
                    "y", "yes" -> {
                        Log.debug(TAG, TAG, "✅ Data collection allowed - consent granted")
                        true
                    }
                    "n", "no" -> {
                        Log.debug(TAG, TAG, "🚫 Data collection blocked - consent denied")
                        false
                    }
                    "p", "pending" -> {
                        Log.debug(TAG, TAG, "⏸️ Data collection blocked - consent pending")
                        false
                    }
                    null -> {
                        Log.debug(TAG, TAG, "⏸️ Data collection blocked - malformed consent data")
                        false
                    }
                    else -> {
                        Log.debug(TAG, TAG, "⏸️ Data collection blocked - unrecognized consent value: $value")
                        false
                    }
                }
                
                return allowed
            } else {
                // Consent is not registered - assume yes
                Log.debug(TAG, TAG, "✅ Data collection allowed - Consent extension not registered, assuming yes")
                return true
            }
        }
    }
    
    private fun isConsentExtensionRegistered(hubData: Map<String, Any?>): Boolean {
        val extensions = hubData[ContentAnalyticsConstants.HubSharedState.EXTENSIONS_KEY] as? Map<*, *>
        return extensions?.containsKey(ContentAnalyticsConstants.ExternalExtensions.CONSENT) == true
    }
}

