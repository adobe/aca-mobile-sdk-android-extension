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

// Require JDK 17 (matches aepsdk-commons and other AEP Android extensions)
val requiredJavaVersion = JavaVersion.VERSION_17
if (JavaVersion.current() < requiredJavaVersion) {
    throw GradleException(
        "Java 17 or later is required to build this project (current: ${JavaVersion.current()}). " +
            "Set JAVA_HOME to JDK 17 or add org.gradle.java.home=<path-to-jdk17> to gradle.properties. " +
            "See CONTRIBUTING.md for setup."
    )
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.adobe.io/repository/releases/") }
    }
}

rootProject.name = "aca-mobile-sdk-android-extension"
include(":contentanalytics")
include(":sample-app")

