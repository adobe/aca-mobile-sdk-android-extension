/*
 * Copyright 2025 Adobe. All rights reserved.
 */

package com.adobe.marketing.mobile.contentanalytics.sample

import android.app.Application
import com.adobe.marketing.mobile.AdobeCallback
import com.adobe.marketing.mobile.Assurance
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.contentanalytics.ContentAnalytics
import com.adobe.marketing.mobile.edge.identity.Identity as EdgeIdentity
import com.adobe.marketing.mobile.LoggingMode

class SampleApplication : Application() {
    
    companion object {
        private const val ENVIRONMENT_FILE_ID = "staging/b42a0d18ad1d/20b6e71fd073/launch-d7aa2913937f-development"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // CRITICAL: Set application context FIRST
        MobileCore.setApplication(this)
        
        // Set logging level (verbose for testing)
        MobileCore.setLogLevel(LoggingMode.VERBOSE)
        
        // Register extensions
        MobileCore.registerExtensions(
            listOf(
                Edge.EXTENSION,
                EdgeIdentity.EXTENSION,
                Assurance.EXTENSION,
                ContentAnalytics.EXTENSION  // Register Content Analytics
            )
        ) {
            // Configure SDK with Launch environment ID
            MobileCore.configureWithAppID(ENVIRONMENT_FILE_ID)
            
            println("🚀 ========================================")
            println("✅ AEP SDK initialized successfully")
            println("📱 Environment ID: $ENVIRONMENT_FILE_ID")
            println("🚀 ========================================")
        }
    }
}

