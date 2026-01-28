# API Reference

Complete API reference for the AEP Content Analytics Android extension.

## Kotlin API

### Asset Tracking

#### trackAsset

Track asset interactions with explicit interaction type.

```kotlin
fun trackAsset(
    assetURL: String,
    interactionType: InteractionType = InteractionType.VIEW,
    assetLocation: String? = null,
    additionalData: Map<String, Any>? = null
)
```

**Parameters:**
- `assetURL`: Asset URL being tracked
- `interactionType`: `InteractionType.VIEW` or `InteractionType.CLICK` (default: `VIEW`)
- `assetLocation`: Optional semantic location (e.g., "home.hero", "product.gallery")
- `additionalData`: Optional custom data

**Example:**
```kotlin
// With default view
ContentAnalytics.trackAsset(
    assetURL = "https://example.com/hero.jpg",
    assetLocation = "home.hero"
)

// Explicit interaction type
ContentAnalytics.trackAsset(
    assetURL = "https://example.com/banner.jpg",
    interactionType = InteractionType.CLICK,
    assetLocation = "home.cta",
    additionalData = mapOf("campaign" to "summer-sale")
)
```

---

#### trackAssetView

Convenience method for tracking asset views.

```kotlin
fun trackAssetView(
    assetURL: String,
    assetLocation: String? = null,
    additionalData: Map<String, Any>? = null
)
```

**Parameters:**
- `assetURL`: The URL of the asset being tracked
- `assetLocation`: (Optional) Semantic location identifier
- `additionalData`: (Optional) Additional custom data

**Example:**
```kotlin
ContentAnalytics.trackAssetView(
    assetURL = "https://example.com/hero.jpg",
    assetLocation = "home.hero"
)
```

---

#### trackAssetClick

Convenience method for tracking asset clicks.

```kotlin
fun trackAssetClick(
    assetURL: String,
    assetLocation: String? = null,
    additionalData: Map<String, Any>? = null
)
```

**Parameters:**
- `assetURL`: The URL of the asset being clicked
- `assetLocation`: (Optional) Semantic location identifier
- `additionalData`: (Optional) Additional custom data

**Example:**
```kotlin
ContentAnalytics.trackAssetClick(
    assetURL = "https://example.com/button.jpg",
    assetLocation = "home.cta"
)
```

---

### Experience Tracking

#### registerExperience

Registers an experience and returns an ID for future tracking. The returned ID is content-based (same content = same ID).

```kotlin
fun registerExperience(
    assets: List<ContentItem>,
    texts: List<ContentItem>,
    ctas: List<ContentItem>? = null
): String
```

**Parameters:**
- `assets`: Array of asset content items with value (URL) and styles
- `texts`: Array of text content items with value (text) and styles
- `ctas`: (Optional) Array of CTA/button content items

**Returns:** Experience ID string (e.g., "mobile-abc123...")

**Example:**
```kotlin
val expId = ContentAnalytics.registerExperience(
    assets = listOf(
        ContentItem("https://example.com/product.jpg")
    ),
    texts = listOf(
        ContentItem("iPhone 16 Pro", mapOf("role" to "headline")),
        ContentItem("$999", mapOf("role" to "price"))
    ),
    ctas = listOf(
        ContentItem("Buy Now", mapOf("enabled" to true))
    )
)
```

---

#### trackExperienceView

Tracks when an experience is viewed.

```kotlin
fun trackExperienceView(
    experienceId: String,
    experienceLocation: String? = null,
    additionalData: Map<String, Any>? = null
)
```

**Parameters:**
- `experienceId`: The ID returned from `registerExperience()`
- `experienceLocation`: (Optional) Location where the experience is displayed
- `additionalData`: (Optional) Additional custom data

**Example:**
```kotlin
ContentAnalytics.trackExperienceView(
    experienceId = expId,
    experienceLocation = "homepage",
    additionalData = mapOf("viewDuration" to 5.2)
)
```

---

#### trackExperienceClick

Tracks when an experience is clicked.

```kotlin
fun trackExperienceClick(
    experienceId: String,
    experienceLocation: String? = null,
    additionalData: Map<String, Any>? = null
)
```

**Parameters:**
- `experienceId`: The ID returned from `registerExperience()`
- `experienceLocation`: (Optional) Location where the experience is displayed
- `additionalData`: (Optional) Additional custom data

**Example:**
```kotlin
ContentAnalytics.trackExperienceClick(
    experienceId = expId,
    experienceLocation = "homepage",
    additionalData = mapOf("element" to "buyNow")
)
```

---

#### trackAssetCollection

Tracks multiple assets with the same interaction type.

```kotlin
fun trackAssetCollection(
    assetURLs: List<String>,
    interactionType: InteractionType = InteractionType.VIEW,
    assetLocation: String? = null
)
```

**Example:**
```kotlin
ContentAnalytics.trackAssetCollection(
    assetURLs = listOf(
        "https://example.com/img1.jpg",
        "https://example.com/img2.jpg"
    ),
    assetLocation = "product-carousel"
)
```

---

## Java API

All Kotlin APIs are Java-compatible via `@JvmStatic` and `@JvmOverloads` annotations.

### Asset Tracking

```java
// Track view
ContentAnalytics.trackAssetView(
    "https://example.com/hero.jpg",
    "home.hero",
    null
);

// Track click
ContentAnalytics.trackAssetClick(
    "https://example.com/button.jpg",
    "home.cta",
    Collections.singletonMap("campaign", "summer-sale")
);

// Using InteractionType enum directly
ContentAnalytics.trackAsset(
    "https://example.com/image.jpg",
    InteractionType.VIEW,
    "home",
    null
);
```

### Experience Tracking

```java
// Register experience
List<ContentItem> assets = Arrays.asList(
    new ContentItem("https://example.com/product.jpg")
);
List<ContentItem> texts = Arrays.asList(
    new ContentItem("Product Name", Collections.singletonMap("role", "headline")),
    new ContentItem("$99.99", Collections.singletonMap("role", "price"))
);
List<ContentItem> ctas = Arrays.asList(
    new ContentItem("Add to Cart", Collections.singletonMap("enabled", true))
);

String expId = ContentAnalytics.registerExperience(assets, texts, ctas);

// Track view
ContentAnalytics.trackExperienceView(expId, "product.detail", null);

// Track click
ContentAnalytics.trackExperienceClick(expId, "product.detail", null);
```

---

## Data Types

### InteractionType

Interaction type enum.

```kotlin
enum class InteractionType {
    VIEW,
    CLICK,
    DEFINITION;
    
    val stringValue: String
        get() = name.lowercase()
}
```

**Usage:**
```kotlin
ContentAnalytics.trackAsset(
    assetURL = "https://example.com/hero.jpg",
    interactionType = InteractionType.VIEW
)
```

### ContentItem

Represents content within an experience (assets, texts, or CTAs).

```kotlin
data class ContentItem(
    val value: String,
    val styles: Map<String, Any> = emptyMap()
) {
    fun toMap(): Map<String, Any>
}
```

**Examples:**
```kotlin
// Asset with URL
ContentItem("https://example.com/hero.jpg")

// Text with role
ContentItem("Welcome!", mapOf("role" to "headline"))

// CTA with enabled state
ContentItem("Shop Now", mapOf("enabled" to true, "role" to "primary"))
```

---

## Configuration

Managed through Adobe Data Collection (Launch):

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `contentanalytics.configId` | String | - | Custom datastream for Content Analytics events |
| `contentanalytics.batchingEnabled` | Boolean | `true` | Enable event batching |
| `contentanalytics.maxBatchSize` | Integer | `10` | Max events per batch |
| `contentanalytics.batchFlushInterval` | Long | `2000` | Flush interval (milliseconds) |
| `contentanalytics.trackExperiences` | Boolean | `true` | Enable experience tracking |
| `contentanalytics.excludedAssetLocationsRegexp` | String | - | Asset location regex pattern to exclude |
| `contentanalytics.excludedAssetUrlsRegexp` | String | - | Asset URL regex pattern to exclude |
| `contentanalytics.excludedExperienceLocationsRegexp` | String | - | Experience location regex pattern |
| `contentanalytics.region` | String | - | Org's home region (e.g., "va7", "irl1") |

---

## Privacy & Consent

Content Analytics respects the AEP Consent extension settings:

```kotlin
// User opts in
MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN)

// User opts out - events will not be sent
MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_OUT)
```

When consent is denied:
- Pending batched events are cleared
- New events are not processed
- Featurization requests are blocked

---

## Extension Information

```kotlin
// Get extension version
val version = ContentAnalytics.EXTENSION_VERSION

// Extension class for registration
val extensionClass = ContentAnalytics.EXTENSION
```

---

## Additional Resources

- [Getting Started](getting-started.md)
- [Advanced Configuration](advanced-configuration.md)
- [Troubleshooting](troubleshooting.md)

---

Copyright 2025 Adobe. All rights reserved.

