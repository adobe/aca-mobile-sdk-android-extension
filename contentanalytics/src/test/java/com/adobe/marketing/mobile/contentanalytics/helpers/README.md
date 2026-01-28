# Content Analytics Test Helpers

Test helper utilities for Content Analytics Android extension tests.

## Available Helpers

### 1. TestEventFactory.kt

Factory for creating test events.

```kotlin
// Create asset event
val event = TestEventFactory.createAssetEvent(
    url = "https://example.com/image.jpg",
    location = "homepage",
    action = ContentAnalyticsConstants.ActionType.VIEW,
    extras = mapOf("campaign" to "summer-sale")
)

// Create experience event
val event = TestEventFactory.createExperienceEvent(
    experienceId = "exp-123",
    location = "homepage-hero",
    action = ContentAnalyticsConstants.ActionType.VIEW
)

// Create configuration
val config = TestEventFactory.createConfigurationEvent(
    trackExperiences = true,
    batchingEnabled = true,
    maxBatchSize = 10
)
```

### 2. TestDataBuilder.kt

Builder pattern for creating test data structures.

```kotlin
// Create ContentItems
val assets = TestDataBuilder.buildContentItems(
    count = 3,
    prefix = "hero",
    type = ContentItem.ContentType.IMAGE
)

// Create ExperienceDefinition
val experience = TestDataBuilder.buildExperienceDefinition(
    experienceId = "exp-test",
    assetCount = 2,
    textCount = 1
)

// Create Configuration
val config = TestDataBuilder.buildConfiguration(
    batchingEnabled = true,
    maxBatchSize = 5
)

// Create test extras
val extras = TestDataBuilder.buildExtras(
    "campaign" to "summer-sale",
    "variant" to "A"
)
```

### 3. ContentAnalyticsMocks.kt

Mock implementations for testing.

```kotlin
// Create mock EventDispatcher
val dispatchedEvents = mutableListOf<Event>()
val eventDispatcher = ContentAnalyticsMocks.createMockEventDispatcher(dispatchedEvents)

// Create mock PrivacyValidator
val privacyValidator = ContentAnalyticsMocks.createMockPrivacyValidator(
    allowDataCollection = true
)

// Create dynamic privacy validator
val dynamicValidator = ContentAnalyticsMocks.createDynamicPrivacyValidator()
dynamicValidator.optOut() // Change consent
dynamicValidator.optIn()  // Restore consent
```

### 4. TestAssertions.kt

Custom assertions for Content Analytics tests.

```kotlin
// Assert event has XDM
TestAssertions.assertEventHasXDM(event, "content.contentEngagement")

// Assert event has asset XDM data
TestAssertions.assertEventHasAssetXDM(
    event = event,
    expectedAssetURL = "https://example.com/image.jpg",
    expectedLocation = "homepage",
    expectedViewCount = 1.0
)

// Assert event has experience XDM data
TestAssertions.assertEventHasExperienceXDM(
    event = event,
    expectedExperienceId = "exp-123",
    expectedViewCount = 2.0
)

// Assert event has extras
TestAssertions.assertEventHasExtras(
    event = event,
    expectedExtras = mapOf("campaign" to "summer-sale"),
    extrasPath = "assetExtras"
)

// Assert event has datastream override
TestAssertions.assertEventHasDatastreamOverride(
    event = event,
    expectedDatastreamId = "test-datastream-id"
)
```

### 5. ContentAnalyticsTestBase.kt

Base class for tests providing common setup.

```kotlin
class MyTest : ContentAnalyticsTestBase() {
    
    @Test
    fun testSomething() {
        // dispatchedEvents, eventDispatcher, state, orchestrator are available
        
        orchestrator.processAssetEvent(event)
        
        // Get dispatched events
        val edgeEvents = getEdgeEvents()
        assertEdgeEventCount(1)
        
        // Clear events
        clearDispatchedEvents()
        
        // Wait for condition
        waitFor { getEdgeEvents().isNotEmpty() }
    }
}
```

## Best Practices

1. Use TestEventFactory instead of manually creating events
2. Use TestDataBuilder for consistent test data
3. Extend ContentAnalyticsTestBase for common setup
4. Use TestAssertions for reusable assertions
5. Use ContentAnalyticsMocks for dependency injection

## Example Test

```kotlin
class ContentAnalyticsAPITest : ContentAnalyticsTestBase() {
    
    @Test
    fun testTrackAssetView() {
        // Arrange
        val config = TestDataBuilder.buildConfiguration(batchingEnabled = false)
        state.updateConfiguration(config)
        
        val assetURL = TestDataBuilder.buildAssetURL("hero")
        val event = TestEventFactory.createAssetEvent(
            url = assetURL,
            location = "homepage"
        )
        
        // Act
        orchestrator.processAssetEvent(event)
        
        // Assert
        assertEdgeEventCount(1)
        
        val edgeEvent = getFirstEdgeEvent()!!
        TestAssertions.assertEventHasAssetXDM(
            event = edgeEvent,
            expectedAssetURL = assetURL,
            expectedLocation = "homepage",
            expectedViewCount = 1.0
        )
    }
}
```
