/*
 * Copyright 2025 Adobe. All rights reserved.
 * This file is licensed to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations in the License.
 */

apply(plugin = "aep-license")

buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        mavenLocal()
    }
    dependencies {
        classpath("com.github.adobe:aepsdk-commons:gp-3.4.1")
        // Kotlin Gradle plugin so IDE can resolve kotlin { compilerOptions { } } in subprojects
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
    }
}
