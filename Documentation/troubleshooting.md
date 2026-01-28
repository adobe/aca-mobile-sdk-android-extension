# Troubleshooting

Common issues and solutions for the AEP Content Analytics Android extension.

## Events Not Being Sent

### Check 1: Extension Registration

Verify the extension is registered:

```kotlin
MobileCore.setLogLevel(LoggingMode.DEBUG)

// Look for this log message:
// "ContentAnalytics extension registered"
```

### Check 2: Configuration Loaded

Ensure Launch configuration is loaded:

```kotlin
// Verify configuration event in logs:
// "Configuration response | Data: {...}"
```

Common issues:
- Invalid Environment ID
- Network connectivity issues
- Configuration not published in Launch

### Check 3: Privacy/Consent Status

Verify user has opted in:

```kotlin
MobileCore.getPrivacyStatus { status ->
    Log.d("ContentAnalytics", "Privacy status: $status")
}
```

If `OPT_OUT` or `UNKNOWN`, events won't be sent.

### Check 4: Asset URL Validity

Ensure asset URLs are valid:

```kotlin
// ✅ Good
ContentAnalytics.trackAssetView("https://example.com/image.jpg")

// ❌ Bad - empty URL
ContentAnalytics.trackAssetView("")

// ❌ Bad - null URL (compile error in Kotlin)
```

---

## Events Delayed

### Batching Behavior

Events are batched by default. Check your configuration:

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 10,
  "contentanalytics.batchFlushInterval": 2000
}
```

Events are sent when:
- Batch reaches `maxBatchSize`
- Timer reaches `batchFlushInterval`
- App backgrounds

### Force Immediate Send

For testing, disable batching temporarily:

```json
{
  "contentanalytics.batchingEnabled": false
}
```

---

## Experience Tracking Not Working

### Check 1: Experience Tracking Enabled

Verify in Launch configuration:

```json
{
  "contentanalytics.trackExperiences": true
}
```

### Check 2: Register Before Track

Always call `registerExperience()` before tracking:

```kotlin
// ✅ Correct order
val expId = ContentAnalytics.registerExperience(
    assets = listOf(ContentItem("https://example.com/image.jpg")),
    texts = listOf(ContentItem("Title"))
)
ContentAnalytics.trackExperienceView(expId, "homepage")

// ❌ Wrong - tracking without registration
ContentAnalytics.trackExperienceView("unknown-id", "homepage")
```

### Check 3: Valid Experience ID

The experience ID returned from `registerExperience()` must be used:

```kotlin
val expId = ContentAnalytics.registerExperience(...)
// Use expId, not a custom string
ContentAnalytics.trackExperienceView(experienceId = expId)
```

---

## Filtered/Excluded Events

### Check Exclusion Patterns

If events are being filtered, verify regex patterns:

```json
{
  "contentanalytics.excludedAssetUrlsRegexp": ".*\\.gif$",
  "contentanalytics.excludedAssetLocationsRegexp": "^debug.*",
  "contentanalytics.excludedExperienceLocationsRegexp": "^test.*"
}
```

### Test Your Patterns

```kotlin
// Will be excluded if excludedAssetUrlsRegexp = ".*\\.gif$"
ContentAnalytics.trackAssetView("https://example.com/loading.gif")

// Will be tracked
ContentAnalytics.trackAssetView("https://example.com/hero.jpg")
```

### Debug Exclusion

Enable verbose logging to see exclusion decisions:

```kotlin
MobileCore.setLogLevel(LoggingMode.VERBOSE)

// Look for logs like:
// "Asset excluded by pattern"
// "Experience excluded by pattern"
```

---

## Featurization Service Issues

### Check 1: Required Configuration

Featurization requires:

```json
{
  "experienceCloud.org": "YOUR_ORG_ID@AdobeOrg",
  "edge.domain": "edge.adobedc.net",
  "contentanalytics.configId": "your-datastream-id"
}
```

### Check 2: Region Detection

Verify region is being detected:

```kotlin
// Look for logs like:
// "Featurization URL | Domain: edge.adobedc.net | Region: va7"
```

### Check 3: Consent

Featurization requires explicit consent:

```kotlin
// Consent must be OPT_IN for featurization
MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN)
```

---

## Network Issues

### Edge Network Connectivity

Verify Edge Network is reachable:

```kotlin
// Check for Edge events in logs:
// "Dispatched Asset event to Edge Network"
// "Dispatched Experience event to Edge Network"
```

### Datastream Configuration

Verify your datastream is:
1. Enabled in Adobe Data Collection
2. Has Content Analytics service enabled
3. Is associated with your environment

### Proxy/Firewall Issues

Ensure your network allows connections to:
- `edge.adobedc.net` (or your regional domain)
- `*.adobe.io`

---

## Debug Logging

Enable comprehensive logging:

```kotlin
// In your Application class
override fun onCreate() {
    super.onCreate()
    
    // Enable verbose logging
    MobileCore.setLogLevel(LoggingMode.VERBOSE)
    
    // Initialize SDK
    MobileCore.initialize(this, "YOUR_ENVIRONMENT_ID")
}
```

### Key Log Tags

Filter by these tags in Logcat:

| Tag | Description |
|-----|-------------|
| `Content Analytics` | Main extension |
| `ContentAnalytics.Orchestrator` | Event processing |
| `ContentAnalytics.BatchCoordinator` | Batch management |
| `ContentAnalytics.PrivacyValidator` | Consent checks |
| `ContentAnalytics.Config` | Configuration parsing |

### Sample Debug Session

```
D/Content Analytics: ContentAnalytics extension registered
D/Content Analytics: Configuration applied | Batching: true | BatchSize: 10
D/ContentAnalytics.Orchestrator: Processing validated asset event: https://example.com/hero.jpg
D/ContentAnalytics.Orchestrator: Added asset event to batch
D/ContentAnalytics.BatchCoordinator: Batch flush triggered | Events: 10
D/ContentAnalytics.Orchestrator: Dispatched Asset event to Edge Network
```

---

## Common Error Messages

### "Configuration event has no data"

**Cause:** Launch configuration not loaded.
**Solution:** Check Environment ID and network connectivity.

### "Asset event missing required fields"

**Cause:** Empty or null asset URL.
**Solution:** Ensure `assetURL` is a valid, non-empty string.

### "Experience tracking disabled"

**Cause:** `trackExperiences` is `false` in configuration.
**Solution:** Enable in Launch: `contentanalytics.trackExperiences: true`

### "Consent denied - clearing pending batch"

**Cause:** User opted out of data collection.
**Solution:** This is expected behavior. Respect user consent.

### "Skipping featurization - IMS Org not configured"

**Cause:** `experienceCloud.org` not set in Launch.
**Solution:** Add org ID to Launch configuration.

---

## Getting Help

If issues persist:

1. **Collect logs** - Enable verbose logging and capture relevant output
2. **Document steps** - Note exactly what you're trying to do
3. **Check configuration** - Export Launch configuration for review

**Support Channels:**
- GitHub Issues: https://github.com/adobe/aca-mobile-sdk-android-extension/issues
- Adobe Experience League: https://experienceleaguecommunities.adobe.com/
- Adobe Support: Contact your Adobe representative

---

## Additional Resources

- [Getting Started](getting-started.md)
- [API Reference](api-reference.md)
- [Advanced Configuration](advanced-configuration.md)

---

Copyright 2025 Adobe. All rights reserved.

