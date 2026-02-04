# Crash Recovery Architecture

## Overview

Content Analytics uses `PersistentHitQueue` to protect against data loss during the batching window (0-5 seconds). Events are written to disk immediately and cleared during crash recovery after being accumulated in memory.

## How It Works

```
User tracks event
  └─> Event added to memory + disk (crash-safe)
      │
      └─> Batching (0-5 seconds)
          │
          └─> Flush triggered
              │
              ├─> Process accumulated events
              ├─> Calculate aggregated metrics
              └─> Dispatch to Edge Network (Edge guarantees delivery)
```

## Architecture Components

### BatchCoordinator
**Responsibilities:**
- Manages batching logic (count threshold + time-based flush)
- Writes incoming events to disk immediately via `PersistentHitQueue`
- Maintains in-memory event counters
- Triggers flush when threshold reached (10 events or 5 seconds)
- Coordinates between `DirectHitProcessor` and `ContentAnalyticsOrchestrator`

**Key Methods:**
```kotlin
fun addAssetEvent(event: Event)
  ├─> assetHitProcessor.accumulateEvent(event)      // Add to memory
  ├─> persistEventImmediately(event, queue)         // Write to disk
  └─> checkAndFlushIfNeeded()                       // Check thresholds

suspend fun performFlush()
  ├─> val events = assetHitProcessor.processAccumulatedEvents()
  └─> [Orchestrator processes events → dispatches to Edge]
      └─> Edge guarantees delivery from here
```

### DirectHitProcessor
**Responsibilities:**
- Implements `HitProcessor` interface for `PersistentHitQueue` integration
- Accumulates events in memory for fast batching
- Clears events from disk during crash recovery (after accumulating in memory)

**Event Lifecycle:**
```kotlin
override suspend fun processHit(entity: DataEntity): Boolean
  ├─> Decode event from disk
  ├─> Accumulate in memory (if not already present)
  └─> return true → clear from disk (event now in memory)
```

### PersistentHitQueue (AEPServices)
**Provides:**
- Two separate queues: `asset.events` and `experience.events`
- SQLite-backed persistence (survives crashes, force-quit, background termination)
- Automatic processing via `beginProcessing()`
- Thread-safe operations via coroutines

**Storage:**
- Events encoded via `DataEntityHelper`
- Each event wrapped with type metadata (`asset` or `experience`)
- Unique identifier: `event.uniqueIdentifier`

## Detailed Timeline Example

```
Time   │ Event                                │ Memory │ Disk │ Safe?
───────┼──────────────────────────────────────┼────────┼──────┼───────
00.00s │ User views Asset A                   │ ✓      │ ✓    │ ✅ YES
00.01s │ Event written to disk                │ ✓      │ ✓    │ ✅ YES
00.50s │ User clicks Asset B                  │ ✓      │ ✓    │ ✅ YES
01.00s │ User clicks Asset B                  │ ✓      │ ✓    │ ✅ YES
       │ [Batching window - events on disk]   │        │      │
02.00s │ Timer fires → Flush triggered        │ ✓      │ ✓    │ ✅ YES
02.01s │ Process accumulated events           │ ✓      │ ✓    │ ✅ YES
02.02s │ Calculate metrics (1 view, 2 clicks) │ ✓      │ ✓    │ ✅ YES
02.03s │ Dispatch to Edge Network             │ ✗      │ ✗    │ ✅ YES*
       │ (*Edge guarantees delivery)          │        │      │

Legend:
✓ = Present
✗ = Not present
```

Events stay on disk during the entire batching window. Once we hand off to Edge, their persistence takes over.

## Crash Scenarios

### Scenario 1: Crash During Batching (0-5s window)
```
Status: Events in memory + disk
Crash:  ⚡ App terminated
        └─> Memory lost ✗
        └─> Disk persists ✓

Recovery on Next Launch:
1. PersistentHitQueue.beginProcessing() starts
2. DirectHitProcessor.processHit() called for each persisted event
3. Events accumulated in memory, cleared from disk
4. Normal batch processing resumes

Result: ✅ ZERO DATA LOSS
```

### Scenario 2: Crash During Flush
```
Status: Events being processed
Crash:  ⚡ App terminated mid-dispatch
        └─> Memory lost ✗
        └─> Events may still be on disk if not yet processed

Recovery on Next Launch:
1. Any remaining events on disk are recovered
2. Re-accumulated and dispatched on next flush

Result: ✅ ZERO DATA LOSS (possible duplicate if crash after Edge dispatch)
```

### Scenario 3: Crash After Edge Dispatch
```
Status: Events dispatched to Edge
Crash:  ⚡ App terminated
        └─> Disk already cleared during processHit()
        └─> Edge has the events

Result: ✅ ZERO DATA LOSS - Edge guarantees delivery
```

## Data Loss Windows

### Protected (✅ SAFE):
- **0-5 seconds (batching):** Events on disk ✓
- **During flush/dispatch:** Events on disk until processed ✓
- **After Edge dispatch:** Edge's persistence takes over ✓

Most Adobe extensions don't persist before Edge dispatch. We do because of the batching window - without it, crashes during batching would lose data.

## Edge Network Handoff

Once we dispatch to Edge extension:

```
ContentAnalytics → extensionApi.dispatch(event) → Event Hub → Edge Extension
                                                              └─> Edge.PersistentHitQueue
                                                                  └─> Network retries
                                                                  └─> Exponential backoff
```

**Handoff Point:** After `eventDispatcher.dispatch()` completes, Edge extension owns persistence.

**Edge Guarantees:** Once Edge receives the event, it handles persistence, retries, and delivery confirmation.

## Metrics Calculation

Metrics are **derived from events**, not stored separately:

```kotlin
// On flush (ContentAnalyticsOrchestrator.kt)
private fun buildAssetMetricsCollection(events: List<Event>): AssetMetricsCollection {
    val groupedEvents = events.groupBy { it.assetKey ?: "" }
    val metricsMap = mutableMapOf<String, AssetMetrics>()
    
    for ((key, events) in groupedEvents) {
        val views = events.count { it.interactionType == InteractionType.VIEW }
        val clicks = events.count { it.interactionType == InteractionType.CLICK }
        metricsMap[key] = AssetMetrics(viewCount = views, clickCount = clicks, ...)
    }
    
    return AssetMetricsCollection(metricsMap)
}
```

This avoids state sync issues - we just count events on flush. If the app crashes, the restored events give us the same metrics.

## Configuration

```json
{
  "contentanalytics.batchingEnabled": true,
  "contentanalytics.maxBatchSize": 10,
  "contentanalytics.batchFlushInterval": 2000
}
```

**Parameters:**
- `maxBatchSize`: Event count threshold (default: 10)
- `batchFlushInterval`: Timer interval for periodic flush in ms (default: 2000ms = 2s)
- `batchingEnabled`: Set to `false` for immediate dispatch (no batching)

## Performance Characteristics

| Operation | Time | Notes |
|-----------|------|-------|
| Event persistence | ~1-2ms | SQLite write |
| Event recovery | ~5-10ms | SQLite read on launch |
| Batch flush | ~10-20ms | Metrics calculation + Edge dispatch |
| Memory per event | ~2KB | Event object + metadata |
| Disk per event | ~1-2KB | Serialized format |

**Memory Usage:** With default batch size (10), worst-case memory is ~20-40KB (negligible).

**Network Efficiency:** Batching reduces Edge Network calls by 10x for high-volume tracking.

## Thread Safety

All operations use Kotlin coroutines with `Mutex` for thread-safe access:

```kotlin
// BatchCoordinator
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
private val stateMutex = kotlinx.coroutines.sync.Mutex()

// DirectHitProcessor
private val mutex = Mutex()

// All state mutations wrapped in mutex.withLock { }
```

The `CoroutineScope` gets canceled on `close()`, which cleans up any in-flight operations.

## Testing Crash Recovery

### Test 1: Crash During Batching
```kotlin
1. Track 5 asset events
2. DO NOT wait for flush timer
3. Force-kill app (adb shell am force-stop <package>)
4. Relaunch app
5. Track 5 more asset events
6. Wait 2 seconds for flush
7. Verify: 1 Edge event with 10 aggregated interactions
```

### Test 2: Crash During Flush
```kotlin
1. Track 10 asset events (triggers immediate flush)
2. Set breakpoint in sendToEdge()
3. Force-kill app at breakpoint
4. Relaunch app
5. Wait 5 seconds
6. Verify: Events re-dispatched (possible duplicate)
```

### Test 3: Background Termination
```kotlin
1. Track events
2. Background app
3. Android terminates app (memory pressure)
4. Relaunch app
5. Verify: Events recovered and dispatched
```

## Logging

Enable verbose logging to debug crash recovery:

```kotlin
LoggingMode.VERBOSE
```

Look for:
```
[ContentAnalytics] Accumulated ASSET event | ID: <uuid>
[ContentAnalytics] Recovered event from disk | Type: ASSET | ID: <uuid>
[ContentAnalytics] Processing 5 accumulated ASSET events
```

## Implementation Details

### Key Files
- `BatchCoordinator.kt` - Batching logic and persistence coordination
- `DirectHitProcessor.kt` - Crash recovery and event accumulation
- `ContentAnalyticsOrchestrator.kt` - Metrics calculation and Edge dispatch
- `PersistentHitQueue` (AEPServices) - SQLite-backed queue

### Data Flow
```
Event tracked
  └─> BatchCoordinator.addAssetEvent()
      ├─> DirectHitProcessor.accumulateEvent()  [memory]
      ├─> PersistentHitQueue.queue()            [disk]
      └─> checkAndFlushIfNeeded()
          └─> performFlush()
              └─> DirectHitProcessor.processAccumulatedEvents()
                  └─> Orchestrator.processAssetEvents()
                      └─> EventDispatcher.dispatch()  [→ Edge]
```

### Callback Chain Architecture

The SDK uses a callback chain to decouple components while maintaining type safety:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           INITIALIZATION PHASE                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ContentAnalyticsFactory.createOrchestrator()                               │
│    │                                                                         │
│    ├─> Creates BatchCoordinator(assetQueue, experienceQueue, state)         │
│    │     └─> DirectHitProcessor initialized with no-op callbacks            │
│    │                                                                         │
│    ├─> Creates ContentAnalyticsOrchestrator(batchCoordinator, ...)          │
│    │                                                                         │
│    └─> Wires callbacks: batchCoordinator.setCallbacks(                      │
│          assetCallback: orchestrator::processAssetEvents,                   │
│          experienceCallback: orchestrator::processExperienceEvents          │
│        )                                                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                            RUNTIME DATA FLOW                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  User calls ContentAnalytics.trackAssetInteraction()                        │
│    │                                                                         │
│    v                                                                         │
│  ┌──────────────────┐                                                        │
│  │ BatchCoordinator │                                                        │
│  │  addAssetEvent() │──────────────────────────────────────────┐            │
│  └────────┬─────────┘                                          │            │
│           │                                                    │            │
│           v                                                    v            │
│  ┌────────────────────┐                           ┌─────────────────────┐   │
│  │ DirectHitProcessor │                           │ PersistentHitQueue  │   │
│  │ accumulateEvent()  │                           │ queue() [disk]      │   │
│  │ [memory buffer]    │                           └─────────────────────┘   │
│  └────────┬───────────┘                                                     │
│           │                                                                  │
│           │ (on flush trigger: count >= 10 or timer >= 2s)                  │
│           v                                                                  │
│  ┌────────────────────────────┐                                             │
│  │ DirectHitProcessor         │                                             │
│  │ processAccumulatedEvents() │                                             │
│  └────────┬───────────────────┘                                             │
│           │                                                                  │
│           │ invokes processingCallback(events)                              │
│           v                                                                  │
│  ┌─────────────────────────────────┐                                        │
│  │ ContentAnalyticsOrchestrator    │                                        │
│  │ processAssetEvents(events)      │                                        │
│  │   ├─> Group by asset key        │                                        │
│  │   ├─> Calculate metrics         │                                        │
│  │   └─> Build XDM payload         │                                        │
│  └────────┬────────────────────────┘                                        │
│           │                                                                  │
│           v                                                                  │
│  ┌───────────────────┐                                                      │
│  │ EdgeEventDispatcher│                                                      │
│  │ dispatch()         │──────────────> Edge Network                         │
│  └───────────────────┘                                                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

Callbacks avoid circular dependencies - BatchCoordinator doesn't need to import Orchestrator. Also makes testing easier since we can inject mocks.

## Comparison with Edge Extension

| Feature | Content Analytics | Edge Extension |
|---------|------------------|----------------|
| Pre-dispatch persistence | ✅ YES (0-5s) | ❌ NO |
| Batching | ✅ YES | ❌ NO |
| Post-dispatch persistence | ✅ Edge's queue | ✅ PersistentHitQueue |
| Network retries | ✅ Edge handles | ✅ Exponential backoff |
| Crash recovery during batch | ✅ FULL | N/A |

Content Analytics batches events for 0-5 seconds before dispatch. Without disk persistence during that window, crashes would lose data. Edge dispatches immediately so it doesn't need this.

## Known Limitations

1. **No dispatch confirmation:** Extensions cannot receive callbacks from Edge to confirm receipt
2. **Possible duplicates:** Crash during Edge dispatch may cause duplicate events (Edge deduplication handles this)
3. **Memory overhead:** Events held in memory + disk during batching (minimal: ~40KB)

## Maintenance Notes

### Adding New Event Types
When adding new event types beyond asset/experience:

1. Create new `DataQueue` in `ContentAnalyticsExtension`
2. Create new `DirectHitProcessor` instance
3. Add to `BatchCoordinator` initialization
4. Update `performFlush()` to handle new type

### Disk Cleanup
- Events cleared from disk during crash recovery (processHit)
- Manual cleanup: `clearPendingBatch()` for identity reset
- Queue close: `close()` on extension shutdown

### Monitoring
Track these metrics for health monitoring:
- Event recovery count on launch
- Average batch size
- Flush frequency
- Disk queue depth

---

**Last Updated:** 2026-01-29  
**Version:** 5.0.0  
**Platform:** Android
