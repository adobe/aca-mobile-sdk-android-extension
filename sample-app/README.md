# Content Analytics Sample App

Interactive demo of the AEP Content Analytics Android Extension.

## Features Demonstrated

**Asset Tracking**
- Track asset views with location context
- Track asset clicks with extras metadata
- Real-time counter updates

**Experience Tracking**
- Register experiences with content metadata
- Track experience views/clicks
- Asset attribution in experience events

**Configuration**
- Batching and persistence
- Privacy consent integration
- Datastream overrides

## Setup Instructions

### 1. Configure Launch Environment

In `SampleApplication.kt`, replace:

```kotlin
private const val ENVIRONMENT_FILE_ID = "YOUR_ENVIRONMENT_FILE_ID"
```

With your Launch environment file ID from:
https://experience.adobe.com/launch

### 2. Update Launch Configuration

In your Launch property, configure Content Analytics extension:

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 50,
  "contentanalytics.batchFlushInterval": 30000,
  "contentanalytics.trackExperiences": true,
  "contentanalytics.configId": "your-ca-datastream-id"
}
```

### 3. Build and Run

```bash
cd sample-app
./gradlew clean build
./gradlew installDebug
```

## Testing

### With Adobe Assurance

1. Add Assurance dependency:
```kotlin
implementation("com.adobe.marketing.mobile:assurance:3.0.0")
```

2. Start Assurance session in the app
3. Interact with sample UI
4. View events in Assurance UI

### Expected Events

**Asset View:**
```json
{
  "xdm": {
    "eventType": "contentAnalytics.assetInteraction",
    "_adobeinternal": {
      "contentAnalytics": {
        "assetSource": "https://example.com/hero-banner.jpg",
        "assetLocation": "homepage",
        "action": "view",
        "metrics": {
          "views": { "value": 1 }
        }
      }
    }
  }
}
```

**Experience View:**
```json
{
  "xdm": {
    "eventType": "contentAnalytics.experienceInteraction",
    "_adobeinternal": {
      "contentAnalytics": {
        "experienceId": "homepage-hero",
        "experienceLocation": "homepage",
        "action": "view",
        "assets": [
          "https://example.com/hero1.jpg",
          "https://example.com/hero2.jpg"
        ],
        "metrics": {
          "views": { "value": 1 }
        }
      }
    }
  }
}
```

## Code Examples

### Track Asset View
```kotlin
ContentAnalytics.trackAssetView(
    assetURL = "https://example.com/hero.jpg",
    assetLocation = "homepage",
    additionalData = mapOf("campaign" to "summer-sale")
)
```

### Register and Track Experience
```kotlin
// Register once - returns generated experienceId
val experienceId = ContentAnalytics.registerExperience(
    assets = listOf(
        ContentItem("https://example.com/hero1.jpg"),
        ContentItem("https://example.com/hero2.jpg")
    ),
    texts = listOf(
        ContentItem("Welcome!", mapOf("role" to "headline")),
        ContentItem("Summer Sale", mapOf("role" to "promo"))
    ),
    ctas = listOf(
        ContentItem("Shop Now", mapOf("enabled" to true, "role" to "primary"))
    )
)

// Track interactions using the returned experienceId
ContentAnalytics.trackExperienceView(
    experienceId = experienceId,
    experienceLocation = "homepage",
    additionalData = mapOf("position" to 1)
)
```

## Architecture

```
┌─────────────────┐
│   Sample App    │
│   (Compose UI)  │
└────────┬────────┘
         │
         │ ContentAnalyticsAPI
         ▼
┌─────────────────┐
│ ContentAnalytics│
│   Extension     │
└────────┬────────┘
         │
         │ Edge Events
         ▼
┌─────────────────┐
│  AEP Edge       │
│  Network        │
└─────────────────┘
```

## Troubleshooting

### No Events Dispatched
- Check SDK initialization logs
- Verify Launch configuration
- Check consent state (should be opted in)

### Events Not Batching
- Verify `batchingEnabled: true` in Launch
- Check batch size doesn't exceed `maxBatchSize`
- Verify timer interval is reasonable

### Assets Not Attributed
- Ensure `registerExperience()` is called before tracking
- Verify asset URLs match exactly
- Check experience definition in state

## Next Steps

1. **Custom Configuration**: Add UI to change batch settings dynamically
2. **Offline Handling**: Test with airplane mode (persistence)
3. **Performance**: Profile with large volumes
4. **CJA Integration**: View data in Customer Journey Analytics

