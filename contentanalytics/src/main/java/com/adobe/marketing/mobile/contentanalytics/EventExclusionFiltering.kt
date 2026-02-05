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

/**
 * Interface for determining if events should be excluded based on configured patterns
 */
internal interface EventExclusionFiltering {
    /**
     * Determines if an asset event should be excluded based on URL and location patterns
     * @param event The asset event to check
     * @return True if the event should be excluded (not tracked)
     */
    fun shouldExcludeAsset(event: Event): Boolean

    /**
     * Determines if an experience event should be excluded based on location patterns
     * @param event The experience event to check
     * @return True if the event should be excluded (not tracked)
     */
    fun shouldExcludeExperience(event: Event): Boolean
}
