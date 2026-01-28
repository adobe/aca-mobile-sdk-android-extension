/*
 * Copyright 2025 Adobe. All rights reserved.
 * This file is licensed to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

plugins {
    id("aep-library")
}

val mavenCoreVersion: String by project
val mavenEdgeVersion: String by project
val mavenEdgeIdentityVersion: String by project

aepLibrary {
    namespace = "com.adobe.marketing.mobile.contentanalytics"
    compose = false
    enableSpotless = false  // Disabled: wildcard imports common in tests, not all Adobe extensions use it
    enableDokkaDoc = true   // Generate API documentation (Javadoc format from KDoc)
    
    publishing {
        gitRepoName = "aca-mobile-sdk-android-extension"
        addCoreDependency(mavenCoreVersion)
        addEdgeDependency(mavenEdgeVersion)
        addEdgeIdentityDependency(mavenEdgeIdentityVersion)
    }
}

dependencies {
    // Adobe SDK dependencies (versions from gradle.properties)
    implementation("com.adobe.marketing.mobile:core:$mavenCoreVersion")
    implementation("com.adobe.marketing.mobile:edge:$mavenEdgeVersion")
    implementation("com.adobe.marketing.mobile:edgeidentity:$mavenEdgeIdentityVersion")

    // Testing dependencies - using older versions compatible with JVM 1.8
    testImplementation("io.mockk:mockk:1.12.0")  // Last version with JVM 1.8 support
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")  // JVM 1.8 compatible
    testImplementation("org.mockito:mockito-core:4.8.0")  // JVM 1.8 compatible
    testImplementation("org.robolectric:robolectric:4.9")  // JVM 1.8 compatible
}

// Note: The aep-library plugin automatically provides:
// - Adobe SDK dependencies (Core, Edge, EdgeIdentity via addXxxDependency())
// - Kotlin stdlib and coroutines
// - Common test dependencies (JUnit, AndroidX Test, Espresso)
// - Publishing configuration
// - Android build configuration (compileSdk, minSdk, Java version, etc.)

// The aep-library plugin configures Java toolchains automatically
// We rely on org.gradle.java.home in gradle.properties to point to Java 17


