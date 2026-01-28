# Adobe Experience Platform Content Analytics Android Extension

[![Maven Central](https://img.shields.io/maven-central/v/com.adobe.marketing.mobile/contentanalytics?label=Maven%20Central&logo=android&logoColor=white&color=3DDC84)](https://search.maven.org/artifact/com.adobe.marketing.mobile/contentanalytics)
[![Build](https://github.com/adobe/aca-mobile-sdk-android-extension/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/adobe/aca-mobile-sdk-android-extension/actions)
[![Code Coverage](https://img.shields.io/codecov/c/github/adobe/aca-mobile-sdk-android-extension/main.svg?label=Coverage&logo=codecov)](https://codecov.io/gh/adobe/aca-mobile-sdk-android-extension/branch/main)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## About

The AEP Content Analytics Android extension tracks content and experience interactions in Android apps. It batches events for efficiency, persists them to disk to survive crashes, and can optionally send data to an ML featurization service.

Requires `AEPCore`, `AEPServices`, and `AEPEdge` extensions to send data to the Adobe Experience Platform Edge Network.

## Requirements

- Android API 21+ (Android 5.0 Lollipop)
- Kotlin 1.9+
- Android Gradle Plugin 8.2+

## Installation

### Gradle

```kotlin
dependencies {
    implementation("com.adobe.marketing.mobile:core:3.0.0")
    implementation("com.adobe.marketing.mobile:edge:3.0.0")
    implementation("com.adobe.marketing.mobile:edgeidentity:3.0.0")
    implementation("com.adobe.marketing.mobile:contentanalytics:1.0.0")
}
```

## Features

- Event batching with configurable size and flush interval
- Persistent storage for crash recovery
- Datastream overrides for flexible routing
- Content filtering (regex patterns, exact match)
- Privacy-compliant consent integration
- Location-based grouping for CJA breakdowns

## Quick Start

### 1. Register Extension

```kotlin
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.contentanalytics.ContentAnalytics

MobileCore.registerExtensions(
    listOf(Edge.EXTENSION, ContentAnalytics.EXTENSION)
) {
    MobileCore.configureWithAppID("YOUR_ENVIRONMENT_FILE_ID")
}
```

### 2. Track Assets

```kotlin
import com.adobe.marketing.mobile.contentanalytics.ContentAnalyticsAPI

// Track asset view
ContentAnalyticsAPI.trackAssetView(
    assetURL = "https://example.com/hero.jpg",
    assetLocation = "homepage",
    extras = mapOf("campaign" to "summer-sale")
)

// Track asset click
ContentAnalyticsAPI.trackAssetClick(
    assetURL = "https://example.com/hero.jpg",
    assetLocation = "homepage"
)
```

### 3. Track Experiences

```kotlin
import com.adobe.marketing.mobile.contentanalytics.ContentItem

// Register experience once
ContentAnalyticsAPI.registerExperience(
    experienceId = "homepage-hero",
    assets = listOf("https://example.com/hero.jpg"),
    texts = listOf(ContentItem("Welcome!", ContentItem.ContentType.TEXT)),
    ctas = listOf(ContentItem("Shop Now", ContentItem.ContentType.CTA))
)

// Track interactions
ContentAnalyticsAPI.trackExperienceView(
    experienceId = "homepage-hero",
    experienceLocation = "homepage"
)

ContentAnalyticsAPI.trackExperienceClick(
    experienceId = "homepage-hero",
    experienceLocation = "homepage"
)
```

### 4. Configure in Launch

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 50,
  "contentanalytics.batchFlushInterval": 30000,
  "contentanalytics.trackExperiences": true,
  "contentanalytics.configId": "your-ca-datastream-id"
}
```

See [Getting Started Guide](./Documentation/getting-started.md) for full setup instructions.

## Documentation

- **[Getting Started](./Documentation/getting-started.md)** - Installation and basic setup
- **[API Reference](./Documentation/api-reference.md)** - Complete API documentation (Kotlin & Java)
- **[Advanced Configuration](./Documentation/advanced-configuration.md)** - Batching, privacy, performance
- **[Troubleshooting](./Documentation/troubleshooting.md)** - Common issues and solutions

## Sample App

A demo application is available in the `sample-app` directory. See [sample-app/README.md](sample-app/README.md) for setup instructions.

## Contributing

Contributions are welcome. Please review our contributing guidelines before submitting pull requests.

## Licensing

This project is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for more information.

---

Copyright 2025 Adobe. All rights reserved.
