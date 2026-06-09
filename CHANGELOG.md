# Release Notes

## 3.0.3 (June 9, 2026)

### Bug Fixes
- **Remote configuration ingestion:** Fixed a parsing bug where the `contentanalytics.` prefix was stripped before lookup against fully-qualified constant keys, causing all `contentanalytics.*` Launch values (batching, exclusions, datastream, region, etc.) to silently revert to defaults.
- **`registerExperience` definition storage:** Definitions dispatched by the public `ContentAnalytics.registerExperience` API are now correctly stored in state. Previously the orchestrator only read a nested `experienceDefinition` map, so top-level `assets` / `texts` / `ctas` from the public API were ignored, breaking featurization and asset attribution.
- **Featurization definition state:** Experience definitions are now marked as sent to the featurization service only after the hit is accepted by the queue. Previously the state was flipped unconditionally, preventing in-session retries on failure.
- **`maxBatchSize` fallback:** When configuration is not yet available, `BatchCoordinator` now falls back to `DEFAULT_BATCH_SIZE` (10) instead of `MAX_BATCH_SIZE_LIMIT` (100).

---

## 3.0.2 (May 4, 2026)

### Features
- **Exclude assets from untracked experiences:** New configuration flag `excludeAssetsFromUntrackedExperience` — when enabled, asset events belonging to excluded experiences are suppressed, preventing orphaned asset tracking.

### Bug Fixes
- **Batch timing alignment:** `maxWaitTimeMs` now correctly uses milliseconds, consistent with `batchFlushInterval` and the iOS SDK.

---

## 3.0.1 (February 23, 2026)

### Bug Fixes
- **Batching configuration alignment:** `maxWaitTime` now uses milliseconds (was seconds). Both `batchFlushInterval` and `maxWaitTime` use milliseconds matching the Launch extension.

---

## 3.0.0 (January 29, 2026)

### General Availability

First production release. Same feature set as 3.0.0-beta.1 with stability improvements and refactored event processing (EventValidator, MetricsBuilder, dedicated asset/experience processors).

**Highlights**
- Channel and idSource in experienceContent XDM payload
- Orchestrator and factory aligned with validators/processors architecture
- JDK 17 recommended for unit tests (see build docs)

---

## 3.0.0-beta.1 (January 26, 2026)

### Initial Beta Release

> **⚠️ Beta Release:** This is a beta version intended for early testing with select customers. 
> Not recommended for production use. Please report any issues on GitHub.

### Initial Release

**Features**
- Asset tracking (views and clicks) for images and media
- Experience tracking for complex UI components
- Automatic event batching with configurable parameters
- Edge Network integration for data transmission
- Privacy-compliant tracking with consent management
- Crash-resistant delivery using PersistentHitQueue
- ML model featurization support (optional)
- Exclusion patterns for URL and experience filtering
- Region auto-detection from Edge domain
- Comprehensive test coverage

**Platforms**
- Android API 21+ (Android 5.0 Lollipop)
- Kotlin 1.9+

**Dependencies**
- AEPCore 3.0.0+
- AEPEdge 3.0.0+
- AEPEdgeIdentity 3.0.0+

**Documentation**
- Getting Started guide
- Complete API reference (Kotlin & Java)
- Advanced configuration guide
- Troubleshooting guide
- Sample application

---

## Development Releases

Development versions are available but not recommended for production use.

---

For detailed information about each release, see [Releases](https://github.com/adobe/aca-mobile-sdk-android-extension/releases).

