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

## Session Lifecycle

Experience definitions are cached in memory for the duration of the app session. After app restart or crash, you'll need to re-register experiences before tracking.

```kotlin
// Each app session
val expId = ContentAnalytics.trackExperience(
    interactionType = InteractionType.DEFINITION,
    assetURLs = listOf("https://example.com/hero.jpg"),
    texts = listOf(...),
    ...
)
ContentAnalytics.trackExperience(
    interactionType = InteractionType.VIEW,
    experienceId = expId
)
```

Re-registration is idempotent - calling `trackExperience()` with DEFINITION for the same content has no negative side effects.

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
   Call registerExperience() before tracking views/clicks.
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

## Best Practice

Always call `registerExperience()` before `trackExperience()` with VIEW/CLICK. Registration is idempotent - calling it multiple times has no negative effects.

## Testing

Enable verbose logging:

```kotlin
MobileCore.setLogLevel(LoggingMode.VERBOSE)
```

Look for registration confirmation:
```
[ContentAnalytics] Stored experience definition: exp-abc123 with 3 assets
```

And tracking confirmation:
```
[ContentAnalytics] Experience event processed successfully: track-view - exp-abc123
```

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
