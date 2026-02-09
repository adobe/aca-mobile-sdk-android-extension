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
import com.adobe.marketing.mobile.SharedStateResult
import com.adobe.marketing.mobile.SharedStateStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class PrivacyValidatorTest {
    
    private lateinit var state: ContentAnalyticsStateManager
    private lateinit var extensionApi: ExtensionApi
    private lateinit var validator: ContentAnalyticsPrivacyValidator
    
    @Before
    fun setup() {
        state = ContentAnalyticsStateManager()
        extensionApi = mock()
        validator = ContentAnalyticsPrivacyValidator(state, extensionApi)
    }
    
    @Test
    fun `test isDataCollectionAllowed with no Hub state returns false`() {
        // Given - No Hub shared state
        whenever(extensionApi.getSharedState(eq(ContentAnalyticsConstants.ExternalExtensions.EVENT_HUB), any(), any(), any()))
            .thenReturn(null)
        
        // When
        val allowed = validator.isDataCollectionAllowed()
        
        // Then
        assertFalse(allowed)
    }
    
    @Test
    fun `test isDataCollectionAllowed with Consent not registered returns true`() {
        // Given - Hub state exists but Consent not registered
        val hubState = SharedStateResult(
            SharedStateStatus.SET,
            mapOf(ContentAnalyticsConstants.HubSharedState.EXTENSIONS_KEY to emptyMap<String, Any>())
        )
        whenever(extensionApi.getSharedState(
            eq(ContentAnalyticsConstants.ExternalExtensions.EVENT_HUB), 
            isNull(), 
            eq(false), 
            eq(com.adobe.marketing.mobile.SharedStateResolution.ANY)
        )).thenReturn(hubState)
        
        // When
        val allowed = validator.isDataCollectionAllowed()
        
        // Then
        assertTrue("Should allow if Consent not registered", allowed)
    }
    
    @Test
    fun `test isDataCollectionAllowed with Consent registered but no state blocks`() {
        // Given - Consent registered but no shared state yet
        val hubState = SharedStateResult(
            SharedStateStatus.SET,
            mapOf(ContentAnalyticsConstants.HubSharedState.EXTENSIONS_KEY to mapOf(ContentAnalyticsConstants.ExternalExtensions.CONSENT to mapOf<String, Any>()))
        )
        whenever(extensionApi.getSharedState(
            eq(ContentAnalyticsConstants.ExternalExtensions.EVENT_HUB), 
            isNull(), 
            eq(false), 
            eq(com.adobe.marketing.mobile.SharedStateResolution.ANY)
        )).thenReturn(hubState)
        
        whenever(extensionApi.getSharedState(
            eq(ContentAnalyticsConstants.ExternalExtensions.CONSENT), 
            isNull(), 
            eq(false), 
            eq(com.adobe.marketing.mobile.SharedStateResolution.ANY)
        )).thenReturn(null)
        
        // When
        val allowed = validator.isDataCollectionAllowed()
        
        // Then
        assertFalse("Should block if Consent registered but no state", allowed)
    }
    
    @Test
    fun `test isDataCollectionAllowed with consent yes allows`() {
        // Given - Consent is yes
        val hubState = SharedStateResult(
            SharedStateStatus.SET,
            mapOf(ContentAnalyticsConstants.HubSharedState.EXTENSIONS_KEY to mapOf(ContentAnalyticsConstants.ExternalExtensions.CONSENT to mapOf<String, Any>()))
        )
        val consentState = SharedStateResult(
            SharedStateStatus.SET,
            mapOf(
                "consents" to mapOf(
                    "collect" to mapOf("val" to "y")
                )
            )
        )
        
        whenever(extensionApi.getSharedState(
            eq(ContentAnalyticsConstants.ExternalExtensions.EVENT_HUB), 
            isNull(), 
            eq(false), 
            eq(com.adobe.marketing.mobile.SharedStateResolution.ANY)
        )).thenReturn(hubState)
        
        whenever(extensionApi.getXDMSharedState(
            eq(ContentAnalyticsConstants.ExternalExtensions.CONSENT), 
            isNull(), 
            eq(false), 
            eq(com.adobe.marketing.mobile.SharedStateResolution.ANY)
        )).thenReturn(consentState)
        
        // When
        val allowed = validator.isDataCollectionAllowed()
        
        // Then
        assertTrue(allowed)
    }
    
    @Test
    fun `test isDataCollectionAllowed with consent no blocks`() {
        // Given - Consent is no
        val hubState = SharedStateResult(
            SharedStateStatus.SET,
            mapOf(ContentAnalyticsConstants.HubSharedState.EXTENSIONS_KEY to mapOf(ContentAnalyticsConstants.ExternalExtensions.CONSENT to mapOf<String, Any>()))
        )
        val consentState = SharedStateResult(
            SharedStateStatus.SET,
            mapOf(
                "consents" to mapOf(
                    "collect" to mapOf("val" to "n")
                )
            )
        )
        
        whenever(extensionApi.getSharedState(
            eq(ContentAnalyticsConstants.ExternalExtensions.EVENT_HUB), 
            isNull(), 
            eq(false), 
            eq(com.adobe.marketing.mobile.SharedStateResolution.ANY)
        )).thenReturn(hubState)
        
        whenever(extensionApi.getXDMSharedState(
            eq(ContentAnalyticsConstants.ExternalExtensions.CONSENT), 
            isNull(), 
            eq(false), 
            eq(com.adobe.marketing.mobile.SharedStateResolution.ANY)
        )).thenReturn(consentState)
        
        // When
        val allowed = validator.isDataCollectionAllowed()
        
        // Then
        assertFalse(allowed)
    }
}

