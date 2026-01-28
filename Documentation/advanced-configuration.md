# Advanced Configuration

This guide covers advanced configuration options for the AEP Content Analytics Android extension.

## Batching Configuration

### Event Batching

Content Analytics batches events to reduce network overhead. Configure batching behavior in Launch:

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 10,
  "contentanalytics.batchFlushInterval": 2000
}
```

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| `batchingEnabled` | `true` | - | Enable/disable batching |
| `maxBatchSize` | `10` | 1-100 | Events before flush |
| `batchFlushInterval` | `2000` | 500-60000 | Milliseconds between flushes |

### Flush Triggers

Events are automatically flushed when:
1. Batch size reaches `maxBatchSize`
2. Timer reaches `batchFlushInterval`
3. App goes to background (Lifecycle close event)
4. `flushPendingEvents()` is called manually

### Disabling Batching

When batching is disabled, events are sent immediately:

```json
{
  "contentanalytics.batchingEnabled": false
}
```

> **Note:** Disabling batching increases network requests but reduces latency.

---

## Content Filtering

### Exclude Assets by URL Pattern

Use regex to exclude assets from tracking:

```json
{
  "contentanalytics.excludedAssetUrlsRegexp": ".*\\.(gif|svg|ico)$"
}
```

**Examples:**
```kotlin
// These will be excluded
"https://example.com/spinner.gif"     // matches .gif
"https://example.com/logo.svg"        // matches .svg

// These will be tracked
"https://example.com/hero.jpg"
"https://example.com/product.png"
```

### Exclude Assets by Location

Use regex to exclude by location:

```json
{
  "contentanalytics.excludedAssetLocationsRegexp": "^(debug|test|admin).*"
}
```

**Examples:**
```kotlin
// These locations will be excluded
"debug.panel"
"test.carousel"
"admin.dashboard"

// These will be tracked
"homepage.hero"
"product.gallery"
```

### Exclude Experiences by Location

Filter out specific experience locations:

```json
{
  "contentanalytics.excludedExperienceLocationsRegexp": "^(dev|staging).*"
}
```

---

## Datastream Configuration

### Separate Datastream for Content Analytics

Route Content Analytics events to a dedicated datastream:

```json
{
  "contentanalytics.configId": "your-content-analytics-datastream-id"
}
```

This is useful for:
- Separating content analytics data from other analytics
- Different processing rules per datastream
- Cost management and data isolation

### How It Works

When `contentanalytics.configId` is set:
1. Content Analytics events include a `datastreamIdOverride`
2. Edge Network routes events to the specified datastream
3. Other SDK events continue using `edge.configId`

---

## Region Configuration

### Automatic Region Detection

Content Analytics automatically detects your org's region from `edge.domain`:

| Edge Domain | Detected Region |
|-------------|-----------------|
| `edge.adobedc.net` | `va7` (US Virginia) |
| `edge-eu.adobedc.net` | `irl1` (Ireland) |
| `edge-au.adobedc.net` | `aus3` (Australia) |
| `edge-jp.adobedc.net` | `jpn3` (Japan) |
| `edge-in.adobedc.net` | `ind1` (India) |
| `edge-sg.adobedc.net` | `sgp3` (Singapore) |

### Explicit Region Override

For custom domains, set the region explicitly:

```json
{
  "contentanalytics.region": "irl1"
}
```

This is required when using:
- First-party domains (e.g., `analytics.yourcompany.com`)
- Custom CNAME configurations

---

## Performance Tuning

### High-Volume Apps

For apps with frequent content interactions:

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 50,
  "contentanalytics.batchFlushInterval": 5000
}
```

This reduces network calls but may delay data arrival.

### Low-Latency Requirements

For real-time analytics needs:

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 5,
  "contentanalytics.batchFlushInterval": 1000
}
```

Or disable batching entirely for immediate sends.

### Memory Considerations

Events are persisted to disk for crash recovery. The system automatically manages:
- Disk space usage
- Old hit cleanup
- Failed request retries

---

## Experience Tracking

### Disable Experience Tracking

If you only need asset tracking:

```json
{
  "contentanalytics.trackExperiences": false
}
```

This:
- Ignores `registerExperience()` calls
- Ignores `trackExperienceView/Click()` calls
- Reduces processing overhead

### Experience Featurization

Experience definitions are sent to Adobe's ML featurization service for:
- Content feature extraction
- AI-powered analytics insights

Requirements:
- Valid `experienceCloud.org` configured
- Valid `edge.domain` configured
- User consent for data collection

---

## Debug Logging

Enable verbose logging for development:

```kotlin
MobileCore.setLogLevel(LoggingMode.VERBOSE)
```

Log tags to filter:
- `Content Analytics` - Main extension logs
- `ContentAnalytics.Orchestrator` - Event processing
- `ContentAnalytics.BatchCoordinator` - Batching logic
- `ContentAnalytics.PrivacyValidator` - Consent checks

---

## Configuration Examples

### E-Commerce App

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 20,
  "contentanalytics.batchFlushInterval": 3000,
  "contentanalytics.trackExperiences": true,
  "contentanalytics.excludedAssetUrlsRegexp": ".*placeholder.*"
}
```

### Media/News App

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 30,
  "contentanalytics.batchFlushInterval": 5000,
  "contentanalytics.excludedAssetLocationsRegexp": "^ad\\..*"
}
```

### Enterprise App

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 10,
  "contentanalytics.batchFlushInterval": 2000,
  "contentanalytics.configId": "enterprise-content-datastream-id",
  "contentanalytics.region": "irl1",
  "contentanalytics.excludedExperienceLocationsRegexp": "^(internal|debug).*"
}
```

---

## Best Practices

1. **Start with defaults** - They work well for most apps
2. **Monitor network usage** - Adjust batching based on your analytics
3. **Use exclusion patterns sparingly** - Over-filtering loses valuable data
4. **Test in staging first** - Validate configuration before production
5. **Enable debug logging during development** - Easier troubleshooting

---

## Additional Resources

- [Getting Started](getting-started.md)
- [API Reference](api-reference.md)
- [Troubleshooting](troubleshooting.md)

---

Copyright 2025 Adobe. All rights reserved.

