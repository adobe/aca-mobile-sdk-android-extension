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
import com.adobe.marketing.mobile.services.Log

/**
 * Determines if events should be excluded based on configured patterns
 */
internal class EventExclusionFilter(
    private val state: ContentAnalyticsStateManager
) : EventExclusionFiltering {

    companion object {
        private const val TAG = "EventExclusionFilter"
    }

    override fun shouldExcludeAsset(event: Event): Boolean {
        // Check URL pattern exclusion
        val assetURL = event.assetURL
        if (assetURL != null && !state.shouldTrackUrl(assetURL)) {
            Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Asset excluded by URL pattern: $assetURL")
            return true
        }

        // Check location exclusion
        if (!state.shouldTrackAssetLocation(event.assetLocation)) {
            Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Asset excluded by location pattern: ${event.assetLocation}")
            return true
        }

        return false
    }

    override fun shouldExcludeExperience(event: Event): Boolean {
        val excluded = !state.shouldTrackExperience(event.experienceLocation)
        if (excluded) {
            Log.debug(ContentAnalyticsConstants.LOG_TAG, TAG, "Experience excluded by location pattern: ${event.experienceLocation}")
        }
        return excluded
    }
}
