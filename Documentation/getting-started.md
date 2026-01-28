# Getting Started with Content Analytics Android SDK

## Installation

### Gradle

Add the following dependencies to your app's `build.gradle.kts`:

```kotlin
dependencies {
    // Use the BOM to manage Adobe Mobile SDK versions
    implementation(platform("com.adobe.marketing.mobile:sdk-bom:3.+"))
    
    // Adobe Mobile SDK dependencies (versions managed by BOM)
    implementation("com.adobe.marketing.mobile:core")
    implementation("com.adobe.marketing.mobile:edge")
    implementation("com.adobe.marketing.mobile:edgeidentity")
    
    // Content Analytics (not yet in BOM - specify version explicitly)
    implementation("com.adobe.marketing.mobile:contentanalytics:1.0.0")
}
```

> **Note:** The BOM (Bill of Materials) allows all Adobe Mobile SDK dependencies to be updated together. Once Content Analytics is officially released, it will be added to the BOM and you can remove the explicit version.

## Setup

### 1. Initialize the SDK

In your `Application` class:

```kotlin
import android.app.Application
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.MobileCore

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        MobileCore.setLogLevel(LoggingMode.DEBUG) // For development
        
        // Initialize SDK with your Launch environment ID
        // This automatically discovers and registers Content Analytics extension
        MobileCore.initialize(this, "YOUR_ENVIRONMENT_ID")
    }
}
```

> **Note:** The `initialize` API is the recommended approach. It automatically discovers and registers extensions (including Content Analytics) using the `ExtensionDiscoveryService` defined in the manifest. If you need to manually register extensions, use `MobileCore.registerExtensions()` instead.

### 2. Configure in Adobe Launch

Add the **Content Analytics** extension to your Launch property:

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 10,
  "contentanalytics.batchFlushInterval": 2000,
  "contentanalytics.trackExperiences": true
}
```

## Basic Usage

### Track Asset Views

```kotlin
import com.adobe.marketing.mobile.contentanalytics.ContentAnalytics

// Track when an image is viewed
ContentAnalytics.trackAssetView(
    assetURL = "https://example.com/images/product-hero.jpg",
    assetLocation = "product-detail"
)
```

### Track Asset Clicks

```kotlin
// Track when an image is clicked
ContentAnalytics.trackAssetClick(
    assetURL = "https://example.com/images/banner.jpg",
    assetLocation = "homepage",
    additionalData = mapOf("campaign" to "summer-sale")
)
```

### Track Experiences

An **experience** is a complex UI component containing multiple assets, text, and CTAs:

```kotlin
import com.adobe.marketing.mobile.contentanalytics.ContentItem

// 1. Register the experience definition (once) - returns auto-generated ID
val experienceId = ContentAnalytics.registerExperience(
    assets = listOf(
        ContentItem("https://example.com/images/hero-background.jpg"),
        ContentItem("https://example.com/images/hero-product.png")
    ),
    texts = listOf(
        ContentItem("Summer Sale - 50% Off!", mapOf("role" to "headline")),
        ContentItem("Limited time offer", mapOf("role" to "subtitle"))
    ),
    ctas = listOf(
        ContentItem("Shop Now", mapOf("enabled" to true, "role" to "primary")),
        ContentItem("Learn More", mapOf("enabled" to true, "role" to "secondary"))
    )
)

// 2. Track when the experience is viewed
ContentAnalytics.trackExperienceView(
    experienceId = experienceId,
    experienceLocation = "homepage"
)

// 3. Track when the experience is clicked
ContentAnalytics.trackExperienceClick(
    experienceId = experienceId,
    experienceLocation = "homepage",
    additionalData = mapOf("ctaClicked" to "Shop Now")
)
```

## Advanced Configuration

### Filter Content

Exclude certain assets or experiences from tracking using regex patterns:

```json
{
  "contentanalytics.excludedAssetLocationsRegexp": "^(debug|test).*",
  "contentanalytics.excludedAssetUrlsRegexp": ".*\\.(gif|svg)$",
  "contentanalytics.excludedExperienceLocationsRegexp": "^(test|admin).*"
}
```

### Datastream Override

Route Content Analytics events to a separate datastream:

```json
{
  "contentanalytics.configId": "your-content-analytics-datastream-id"
}
```

### ML Featurization

Send experience definitions to a featurization service:

```json
{
  "contentanalytics.featurizationServiceUrl": "https://your-ml-service.example.com"
}
```

## Example: RecyclerView Tracking

```kotlin
class ProductAdapter : RecyclerView.Adapter<ProductViewHolder>() {
    
    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        
        // Load image
        Glide.with(holder.itemView)
            .load(product.imageUrl)
            .into(holder.imageView)
        
        // Track image view
        ContentAnalytics.trackAssetView(
            assetURL = product.imageUrl,
            assetLocation = "product-list"
        )
        
        // Track image click
        holder.imageView.setOnClickListener {
            ContentAnalytics.trackAssetClick(
                assetURL = product.imageUrl,
                assetLocation = "product-list",
                additionalData = mapOf(
                    "productId" to product.id,
                    "position" to position
                )
            )
        }
    }
}
```

## Privacy & Consent

Content Analytics respects AEP Consent extension settings. Events are automatically blocked if consent is denied.

## Next Steps

- [API Reference](api-reference.md) - Complete API documentation
- [Advanced Configuration](advanced-configuration.md) - Performance tuning and filtering
- [Troubleshooting](troubleshooting.md) - Common issues and solutions

## Support

For issues and questions:
- GitHub Issues: https://github.com/adobe/aca-mobile-sdk-android-extension/issues
- Adobe Experience League: https://experienceleaguecommunities.adobe.com/

---

Copyright 2025 Adobe. All rights reserved.

