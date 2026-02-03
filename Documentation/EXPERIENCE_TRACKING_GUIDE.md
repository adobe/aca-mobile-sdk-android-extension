# Experience Tracking Usage Guide

Experience tracking measures how users interact with complete experiences (combinations of images, text, and CTAs) in your app.

## Registration Required

You must register an experience definition before tracking views or clicks. If you don't:
- Asset attribution won't work
- Featurization hits won't be sent
- A warning will be logged

## Basic Usage

Register the experience once with all its content:

```kotlin
val experienceId = ContentAnalytics.trackExperience(
    interactionType = InteractionType.DEFINITION,
    assetURLs = listOf(
        "https://example.com/hero.jpg",
        "https://example.com/icon.png"
    ),
    texts = listOf(
        ContentItem("iPhone 16 Pro", mapOf("role" to "headline")),
        ContentItem("Forged in titanium", mapOf("role" to "body")),
        ContentItem("$999", mapOf("role" to "body"))
    ),
    ctas = listOf(
        ContentItem("Buy Now", mapOf("enabled" to true))
    ),
    location = "product.detail.iphone16pro"
)
```

Then track interactions:

```kotlin
ContentAnalytics.trackExperience(
    interactionType = InteractionType.VIEW,
    experienceId = experienceId
)

ContentAnalytics.trackExperience(
    interactionType = InteractionType.CLICK,
    experienceId = experienceId
)
```

## Cross-Session Persistence

Experience definitions persist across app sessions. You don't need to re-register after app restarts, crashes, or backgrounding.

```kotlin
// Session 1
val expId = ContentAnalytics.trackExperience(
    interactionType = InteractionType.DEFINITION,
    ...
)
ContentAnalytics.trackExperience(
    interactionType = InteractionType.VIEW,
    experienceId = expId
)

// [App exits or crashes]

// Session 2 - works without re-registering
ContentAnalytics.trackExperience(
    interactionType = InteractionType.VIEW,
    experienceId = expId
)
```

Only re-register if the content changes:

```kotlin
val newExpId = ContentAnalytics.trackExperience(
    interactionType = InteractionType.DEFINITION,
    assetURLs = listOf("https://example.com/new-hero.jpg"), // Different content
    ...
)
```

## Implementation Patterns

### Single Screen

```kotlin
class ProductDetailActivity : AppCompatActivity() {
    private var experienceId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_detail)
        
        experienceId = ContentAnalytics.trackExperience(
            interactionType = InteractionType.DEFINITION,
            assetURLs = product.imageURLs,
            texts = listOf(
                ContentItem(product.name, mapOf("role" to "headline")),
                ContentItem(product.price, mapOf("role" to "body"))
            ),
            ctas = listOf(ContentItem("Add to Cart", mapOf("enabled" to true))),
            location = "product.detail.${product.id}"
        )
    }
    
    override fun onResume() {
        super.onResume()
        experienceId?.let { expId ->
            ContentAnalytics.trackExperience(
                interactionType = InteractionType.VIEW,
                experienceId = expId
            )
        }
    }
    
    fun onBuyButtonClicked() {
        experienceId?.let { expId ->
            ContentAnalytics.trackExperience(
                interactionType = InteractionType.CLICK,
                experienceId = expId
            )
        }
    }
}
```

### Collection/Feed

```kotlin
class FeedFragment : Fragment() {
    private val experienceIds = mutableMapOf<String, String>()
    
    fun displayProduct(product: Product) {
        if (!experienceIds.containsKey(product.id)) {
            val expId = ContentAnalytics.trackExperience(
                interactionType = InteractionType.DEFINITION,
                assetURLs = product.imageURLs,
                texts = listOf(ContentItem(product.name, mapOf("role" to "headline"))),
                ctas = null,
                location = "feed.item.${product.id}"
            )
            experienceIds[product.id] = expId
        }
    }
    
    fun onProductCellVisible(product: Product) {
        experienceIds[product.id]?.let { expId ->
            ContentAnalytics.trackExperience(
                interactionType = InteractionType.VIEW,
                experienceId = expId
            )
        }
    }
}
```

### Persistent IDs

If you have stable server-provided IDs, store and reuse them:

```kotlin
val prefs = context.getSharedPreferences("experiences", Context.MODE_PRIVATE)
val experienceIdKey = "exp_${product.id}"

val experienceId = prefs.getString(experienceIdKey, null) ?: run {
    val expId = ContentAnalytics.trackExperience(
        interactionType = InteractionType.DEFINITION,
        ...
    )
    prefs.edit().putString(experienceIdKey, expId).apply()
    expId
}
```

## Missing Registration Warning

If you track without registering:

```
⚠️ Experience definition not found for 'exp-123'. 
   Make sure to call ContentAnalytics.trackExperience() with 
   interactionType: DEFINITION (including assetURLs and texts) before tracking views/clicks.
```

This means:
- View/click events still go to Analytics
- But asset attribution won't work
- Featurization service won't get the data

Fix by registering first:

```kotlin
// Wrong
ContentAnalytics.trackExperience(
    interactionType = InteractionType.VIEW,
    experienceId = "exp-123"
)

// Correct
val expId = ContentAnalytics.trackExperience(
    interactionType = InteractionType.DEFINITION,
    ...
)
ContentAnalytics.trackExperience(
    interactionType = InteractionType.VIEW,
    experienceId = expId
)
```

## Performance Notes

The extension keeps 100 most recent definitions in memory (LRU cache). Older ones are evicted but remain on disk. They're loaded transparently when needed.

Definitions persist until app uninstall or `MobileCore.resetIdentities()`. If you have > 500 unique experiences, a performance warning is logged.

Best practices:
- Reuse experience IDs when possible
- Use stable server-provided IDs
- Call `MobileCore.resetIdentities()` on logout to clear old data

## Testing

Enable verbose logging:

```kotlin
MobileCore.setLogLevel(LoggingMode.VERBOSE)
```

Look for registration confirmation:
```
[ContentAnalytics] Definition persisted to disk | ID: exp-abc123
```

And tracking confirmation:
```
[ContentAnalytics] Experience event processed successfully: track-view - exp-abc123
```

Test cross-session: register, force quit, relaunch, track same ID. No warning should appear.

## Troubleshooting

**"Experience definition not found" warning**

Register the experience before tracking it.

**Assets not attributed**

Same issue - register with `assetURLs` before tracking.

**Duplicate registrations**

Check if already registered before calling with `InteractionType.DEFINITION`:

```kotlin
if (!experienceIds.containsKey(productId)) {
    experienceIds[productId] = ContentAnalytics.trackExperience(
        interactionType = InteractionType.DEFINITION,
        ...
    )
}
```

Or use stable IDs stored in SharedPreferences.

## See Also

- [API Reference](api-reference.md) - Complete API documentation
- [Crash Recovery](crash-recovery.md) - Persistence implementation details
- [Troubleshooting](troubleshooting.md) - Common issues and solutions
