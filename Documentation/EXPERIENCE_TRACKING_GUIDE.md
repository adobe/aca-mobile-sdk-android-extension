# Experience Tracking Usage Guide

Experience tracking measures how users interact with complete experiences (combinations of images, text, and CTAs) in your app.

## Quick Start

```kotlin
// 1. Register (once per experience)
val expId = ContentAnalytics.registerExperience(
    assets = listOf(ContentItem("https://example.com/hero.jpg", emptyMap())),
    texts = listOf(ContentItem("Buy Now", mapOf("role" to "headline"))),
    ctas = listOf(ContentItem("Shop", mapOf("enabled" to true)))
)

// 2. Track view (when visible)
ContentAnalytics.trackExperienceView(expId, "homepage.hero")

// 3. Track click (on tap)
ContentAnalytics.trackExperienceClick(expId, "homepage.hero")
```

That's it. Register first, then track views/clicks using the returned ID.

---

## Registration Required

You must register an experience definition before tracking views or clicks. If you don't:
- Asset attribution won't work
- Featurization hits won't be sent
- A warning will be logged

## Basic Usage

Register the experience once with all its content:

```kotlin
val experienceId = ContentAnalytics.registerExperience(
    assets = listOf(
        ContentItem("https://example.com/hero.jpg", emptyMap()),
        ContentItem("https://example.com/icon.png", emptyMap())
    ),
    texts = listOf(
        ContentItem("iPhone 16 Pro", mapOf("role" to "headline")),
        ContentItem("Forged in titanium", mapOf("role" to "body")),
        ContentItem("$999", mapOf("role" to "price"))
    ),
    ctas = listOf(
        ContentItem("Buy Now", mapOf("enabled" to true))
    )
)
```

Then track interactions:

```kotlin
ContentAnalytics.trackExperienceView(experienceId, "product.detail")
ContentAnalytics.trackExperienceClick(experienceId, "product.detail")
```

## Session Lifecycle

Experience definitions are cached in memory for the duration of the app session. After app restart or crash, you'll need to re-register experiences before tracking.

```kotlin
// Each app session
val expId = ContentAnalytics.registerExperience(
    assets = listOf(ContentItem("https://example.com/hero.jpg", emptyMap())),
    texts = listOf(ContentItem("Title", mapOf("role" to "headline")))
)
ContentAnalytics.trackExperienceView(expId, "home")
```

Re-registration is idempotent - calling `registerExperience()` with the same content returns the same ID with no negative side effects. The featurization service is also idempotent, so even if the same experience definition is sent multiple times (e.g., after cache eviction or app restart), there's no duplication or data inconsistency on the backend.

### Cache Behavior

The SDK uses an LRU (Least Recently Used) cache with a capacity of **100 experience definitions**:

- **Capacity:** 100 definitions max
- **Eviction:** When full, least recently used definitions are removed
- **Memory-only:** Not persisted to disk

**Benefits:**
- Fast lookups for asset attribution
- Bounded memory usage (~20-40KB worst case)
- Automatic cleanup of stale definitions
- No disk I/O overhead
- **Safe re-registration:** Featurization service handles duplicates gracefully

For most apps, 100 definitions is sufficient. If you're registering more unique experiences per session, consider reusing experience IDs where content is identical (same content = same ID).

## Implementation Patterns

### Single Screen

```kotlin
class ProductDetailActivity : AppCompatActivity() {
    private var experienceId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_detail)
        
        experienceId = ContentAnalytics.registerExperience(
            assets = product.imageURLs.map { ContentItem(it, emptyMap()) },
            texts = listOf(
                ContentItem(product.name, mapOf("role" to "headline")),
                ContentItem(product.price, mapOf("role" to "price"))
            ),
            ctas = listOf(ContentItem("Add to Cart", mapOf("enabled" to true)))
        )
    }
    
    override fun onResume() {
        super.onResume()
        experienceId?.let { expId ->
            ContentAnalytics.trackExperienceView(expId, "product.detail.${product.id}")
        }
    }
    
    fun onBuyButtonClicked() {
        experienceId?.let { expId ->
            ContentAnalytics.trackExperienceClick(expId, "product.detail.${product.id}")
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
            val expId = ContentAnalytics.registerExperience(
                assets = product.imageURLs.map { ContentItem(it, emptyMap()) },
                texts = listOf(ContentItem(product.name, mapOf("role" to "headline")))
            )
            experienceIds[product.id] = expId
        }
    }
    
    fun onProductCellVisible(product: Product) {
        experienceIds[product.id]?.let { expId ->
            ContentAnalytics.trackExperienceView(expId, "feed.item.${product.id}")
        }
    }
}
```

### Experience ID Generation

Experience IDs are deterministic - the same content always produces the same ID. The algorithm:

1. Sort text values alphabetically
2. Sort asset URLs alphabetically  
3. Sort CTA values alphabetically
4. Join all with `|` separator (texts, then assets, then CTAs)
5. SHA-1 hash the combined string
6. Take first 12 hex characters
7. Prefix with `mobile-`

**Example:**
```kotlin
// Content: texts=["$99", "Product"], assets=["img.jpg"], ctas=["Buy"]
// Sorted & joined: "Product|$99|img.jpg|Buy"
// SHA-1 → first 12 chars → "mobile-a1b2c3d4e5f6"
```

This means you can:
- **Pre-compute IDs server-side** for consistent cross-platform IDs
- **Cache by content hash** instead of arbitrary keys
- **Detect content changes** by comparing IDs

```kotlin
import java.security.MessageDigest

fun computeExperienceId(texts: List<String>, assets: List<String>, ctas: List<String>): String {
    val content = (texts.sorted() + assets.sorted() + ctas.sorted()).joinToString("|")
    val hash = MessageDigest.getInstance("SHA-1")
        .digest(content.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return "mobile-${hash.take(12)}"
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
ContentAnalytics.trackExperienceView("exp-123", "home")

// Correct
val expId = ContentAnalytics.registerExperience(
    assets = listOf(ContentItem("https://example.com/image.jpg", emptyMap())),
    texts = listOf(ContentItem("Title", mapOf("role" to "headline")))
)
ContentAnalytics.trackExperienceView(expId, "home")
```

## Asset Attribution

When you register an experience with assets, the SDK links those asset URLs to the experience. This enables **asset attribution** - connecting standalone asset tracking events to their parent experience.

> **Note:** Asset attribution works regardless of the `batchingEnabled` setting. The SDK caches experience definitions locally, so attribution is based on the registration cache - not on how events are batched for network delivery.

### How It Works

```kotlin
// 1. Register experience with assets
val expId = ContentAnalytics.registerExperience(
    assets = listOf(
        ContentItem("https://example.com/hero.jpg", emptyMap()),
        ContentItem("https://example.com/thumbnail.jpg", emptyMap())
    ),
    texts = listOf(ContentItem("Summer Sale", mapOf("role" to "headline")))
)

// 2. Track asset view (SDK knows this belongs to the experience above)
ContentAnalytics.trackAssetView("https://example.com/hero.jpg")

// 3. Track experience interaction
ContentAnalytics.trackExperienceView(expId, "homepage")
```

When the analytics backend receives `trackAssetView` for `hero.jpg`, it can attribute that view to the "Summer Sale" experience because the asset URL was registered.

### Without Attribution

If you track an asset without registering the experience first:

```kotlin
// Asset tracked standalone - no experience context
ContentAnalytics.trackAssetView("https://example.com/hero.jpg")
```

The asset view is still recorded, but it's not linked to any experience. You lose:
- Which experience contained this asset
- Performance metrics per experience
- A/B test attribution

### When to Use Each

| Scenario | Approach |
|----------|----------|
| Image in a banner/card with text/CTA | Register experience with assets, track both |
| Standalone image (no surrounding content) | Just `trackAssetView` |
| Image gallery | `trackAssetCollection` or individual `trackAssetView` |
| Product card with image + title + price | Register experience, attribution links them |

## Location Strategy

The `experienceLocation` and `assetLocation` parameters control how metrics are grouped in Customer Journey Analytics (CJA).

### With Location - Metrics Per Placement

```kotlin
// Same experience tracked at different locations
ContentAnalytics.trackExperienceView(expId, "homepage.hero")
ContentAnalytics.trackExperienceView(expId, "product.sidebar")
ContentAnalytics.trackExperienceView(expId, "checkout.upsell")
```

**CJA Report:**

| Experience | Location | Views | Clicks | CTR |
|------------|----------|-------|--------|-----|
| Summer Sale | homepage.hero | 10,000 | 500 | 5% |
| Summer Sale | product.sidebar | 3,000 | 90 | 3% |
| Summer Sale | checkout.upsell | 1,000 | 150 | 15% |

This lets you answer: *"Where does this experience perform best?"*

### Without Location - Global Metrics

```kotlin
// Track without location for aggregate metrics
ContentAnalytics.trackExperienceView(expId)
```

**CJA Report:**

| Experience | Views | Clicks | CTR |
|------------|-------|--------|-----|
| Summer Sale | 14,000 | 740 | 5.3% |

This lets you answer: *"How is this experience performing overall?"*

### Same Asset, Different Locations

```kotlin
val heroImage = "https://example.com/hero.jpg"

// Track per location
ContentAnalytics.trackAssetView(heroImage, "homepage")
ContentAnalytics.trackAssetView(heroImage, "category.electronics")
ContentAnalytics.trackAssetView(heroImage, "search.results")
```

**CJA Report:**

| Asset | Location | Views | Clicks |
|-------|----------|-------|--------|
| hero.jpg | homepage | 50,000 | 2,500 |
| hero.jpg | category.electronics | 8,000 | 320 |
| hero.jpg | search.results | 3,000 | 45 |

### Location Naming Conventions

Use a consistent hierarchy for easier filtering in CJA:

```
screen.section.subsection
```

Examples:
- `homepage.hero`
- `homepage.featured`
- `product.detail.recommendations`
- `cart.upsell`
- `search.results.sponsored`

### When to Use Location

| Goal | Location |
|------|----------|
| Compare same content across placements | ✅ Set location |
| A/B test content in a specific spot | ✅ Set location |
| Track overall content performance | ❌ Omit location |
| Simple asset tracking (no placement analysis) | ❌ Omit location |

## ML-Powered Analytics

When you register experiences, the featurization service analyzes the content and extracts ML attributes like **persuasion strategy**, **emotional tone**, **content category**, etc. These attributes are then available in CJA for advanced analysis.

### Performance by Persuasion Strategy

After featurization, CJA can show which persuasion strategies work best in each location:

**CJA Report - Persuasion Strategy by Location:**

| Location | Persuasion Strategy | Views | Clicks | CTR |
|----------|---------------------|-------|--------|-----|
| homepage.hero | Urgency | 10,000 | 800 | 8% |
| homepage.hero | Social Proof | 10,000 | 650 | 6.5% |
| homepage.hero | Scarcity | 10,000 | 720 | 7.2% |
| checkout.upsell | Urgency | 2,000 | 300 | 15% |
| checkout.upsell | Social Proof | 2,000 | 180 | 9% |

*Insight: "Urgency" messaging performs best at checkout (+15% CTR), while "Social Proof" works better on homepage.*

### Performance by Content Category

**CJA Report - Asset Category Performance:**

| Asset Category | Location | Views | Engagement |
|----------------|----------|-------|------------|
| Lifestyle | homepage | 50,000 | 12% |
| Product-focused | homepage | 50,000 | 8% |
| Lifestyle | product.detail | 20,000 | 6% |
| Product-focused | product.detail | 20,000 | 14% |

*Insight: Lifestyle imagery works on homepage, but product-focused images convert better on detail pages.*

### How It Works

1. **You track** - `registerExperience()` sends content to featurization service
2. **ML analyzes** - Service extracts persuasion strategy, tone, category, etc.
3. **Attributes stored** - ML attributes are linked to the experience/asset
4. **CJA queries** - Reports can segment by any ML attribute + location

```kotlin
// You just track normally - ML attributes are automatic
val expId = ContentAnalytics.registerExperience(
    assets = listOf(ContentItem("https://example.com/urgency-banner.jpg", emptyMap())),
    texts = listOf(
        ContentItem("Only 3 left!", mapOf("role" to "headline")),
        ContentItem("Order now before it's gone", mapOf("role" to "body"))
    )
)
// Featurization service detects: persuasion_strategy = "scarcity + urgency"

ContentAnalytics.trackExperienceView(expId, "product.detail")
```

In CJA, you can then filter/group by `persuasion_strategy` to see what messaging resonates in each location.

## Custom Metrics with additionalData

The `additionalData` parameter lets you attach custom metrics to tracking events. These appear in CJA as additional dimensions/metrics.

### Asset Performance Metrics

```kotlin
// Track asset load time
val loadStart = System.currentTimeMillis()
// ... load image ...
val loadTime = System.currentTimeMillis() - loadStart

ContentAnalytics.trackAssetView(
    assetURL = imageURL,
    assetLocation = "product.gallery",
    additionalData = mapOf(
        "assetLoadTime" to loadTime,         // How long to load (ms)
        "assetSize" to imageData.size,       // Bytes
        "assetSource" to "cdn"               // Cache vs CDN
    )
)
```

### Asset View Duration

```kotlin
class ImageFragment : Fragment() {
    private var viewStartTime: Long = 0
    private var imageURL: String? = null
    
    override fun onResume() {
        super.onResume()
        viewStartTime = System.currentTimeMillis()
        ContentAnalytics.trackAssetView(imageURL!!, "gallery")
    }
    
    override fun onPause() {
        super.onPause()
        val viewDuration = System.currentTimeMillis() - viewStartTime
        
        ContentAnalytics.trackAssetClick(
            assetURL = imageURL!!,
            assetLocation = "gallery",
            additionalData = mapOf(
                "assetViewDuration" to viewDuration  // Time spent viewing (ms)
            )
        )
    }
}
```

### Experience Engagement Metrics

```kotlin
@Composable
fun ProductCard(product: Product) {
    var expId by remember { mutableStateOf<String?>(null) }
    var appearTime by remember { mutableStateOf(0L) }
    
    LaunchedEffect(product.id) {
        appearTime = System.currentTimeMillis()
        expId = ContentAnalytics.registerExperience(
            assets = listOf(ContentItem(product.imageUrl, emptyMap())),
            texts = listOf(ContentItem(product.name, mapOf("role" to "headline")))
        )
        ContentAnalytics.trackExperienceView(expId!!, "homepage.featured")
    }
    
    Column(
        modifier = Modifier.clickable {
            val viewDuration = System.currentTimeMillis() - appearTime
            
            ContentAnalytics.trackExperienceClick(
                experienceId = expId!!,
                experienceLocation = "homepage.featured",
                additionalData = mapOf(
                    "experienceViewDuration" to viewDuration,  // Time before click
                    "scrollDepth" to currentScrollPercent,     // How far scrolled
                    "interactionIndex" to tapCount             // Nth interaction
                )
            )
        }
    ) {
        // ... UI content
    }
}
```

### Common Custom Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `assetLoadTime` | Long | Image/video load time (ms) |
| `assetViewDuration` | Long | Time asset was visible (ms) |
| `assetSize` | Int | Asset file size (bytes) |
| `experienceViewDuration` | Long | Time before interaction (ms) |
| `scrollDepth` | Double | Scroll position when viewed (%) |
| `viewportPosition` | String | "above_fold" / "below_fold" |
| `interactionIndex` | Int | Nth click on this session |
| `experimentVariant` | String | A/B test variant ID |
| `deviceOrientation` | String | "portrait" / "landscape" |

### CJA Report with Custom Metrics

**Average Load Time by Asset Location:**

| Location | Avg Load Time | Avg View Duration |
|----------|---------------|-------------------|
| homepage.hero | 120ms | 3.2s |
| product.gallery | 85ms | 8.5s |
| search.results | 45ms | 1.1s |

*Insight: Gallery images load slower but get 8x more viewing time.*

## Best Practice

Always call `registerExperience()` before `trackExperienceView()`/`trackExperienceClick()`. Registration is idempotent - calling it multiple times has no negative effects.

## Debugging with Assurance

Adobe Assurance (Project Griffon) lets you inspect tracking events in real-time. Connect your app to an Assurance session to see exactly what payloads are being sent.

### Setup

```kotlin
// In your Application class or Activity
import com.adobe.marketing.mobile.Assurance

// Start Assurance session (typically via deep link)
Assurance.startSession(assuranceDeepLink)
```

### What You'll See in Assurance

**1. Track Asset Events**

When you call `trackAssetView()` or `trackAssetClick()`, you'll see:

```
Event: Track Asset
Type: com.adobe.eventType.contentAnalytics
Source: com.adobe.eventSource.requestContent

Payload:
{
  "assetURL": "https://example.com/hero.jpg",
  "interactionType": "view",
  "assetLocation": "homepage.hero",
  "assetExtras": {
    "assetLoadTime": 120,
    "assetSize": 45000
  }
}
```

**2. Track Experience Events**

When you call `registerExperience()`:

```
Event: Track Experience
Type: com.adobe.eventType.contentAnalytics

Payload:
{
  "experienceId": "mobile-abc123...",
  "interactionType": "definition",
  "assetURLs": ["https://example.com/hero.jpg"],
  "texts": [
    {"value": "Summer Sale", "styles": {"role": "headline"}}
  ],
  "ctas": [
    {"value": "Shop Now", "styles": {"enabled": true}}
  ]
}
```

When you call `trackExperienceView()` or `trackExperienceClick()`:

```
Event: Track Experience
Type: com.adobe.eventType.contentAnalytics

Payload:
{
  "experienceId": "mobile-abc123...",
  "interactionType": "view",
  "experienceLocation": "homepage.hero",
  "experienceExtras": {
    "experienceViewDuration": 3500
  }
}
```

**3. Edge Network Events**

After batching, you'll see the Edge request:

```
Event: Edge Request
Type: com.adobe.eventType.edge

Payload:
{
  "xdm": {
    "eventType": "contentanalytics.asset.view",
    "_contentanalytics": {
      "asset": {
        "url": "https://example.com/hero.jpg",
        "location": "homepage.hero"
      }
    }
  }
}
```

### Debugging Checklist

| What to Check | Where in Assurance |
|---------------|-------------------|
| Event dispatched | Look for `Track Asset` / `Track Experience` events |
| Correct payload | Expand event → check `assetURL`, `experienceId`, etc. |
| Batching working | Multiple events → single Edge request |
| Edge delivery | Look for `Edge Request` after batch flush |
| Consent status | Check `Edge Consent` events |

### Common Issues in Assurance

**No events appearing:**
- Check extension is registered
- Verify `MobileCore.dispatch()` is being called

**Events but no Edge request:**
- Check consent status (must be "yes" or "pending")
- Wait for batch timeout (default 5s) or threshold (default 10 events)

**Missing experienceId in track events:**
- Ensure `registerExperience()` was called first
- Check the returned ID is being passed to track methods

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

Check if already registered before calling `registerExperience()`:

```kotlin
if (!experienceIds.containsKey(productId)) {
    experienceIds[productId] = ContentAnalytics.registerExperience(
        assets = listOf(ContentItem(product.imageUrl, emptyMap())),
        texts = listOf(ContentItem(product.name, mapOf("role" to "headline")))
    )
}
```

Or compute the ID yourself using the algorithm above for content-based caching.

## Common Patterns

### Carousel/Banner

```kotlin
class CarouselAdapter : RecyclerView.Adapter<CarouselViewHolder>() {
    private val experienceIds = mutableMapOf<Int, String>()
    
    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        val slide = slides[position]
        
        experienceIds[position] = ContentAnalytics.registerExperience(
            assets = listOf(ContentItem(slide.imageUrl, emptyMap())),
            texts = listOf(ContentItem(slide.title, mapOf("role" to "headline"))),
            ctas = slide.ctaText?.let { listOf(ContentItem(it, mapOf("enabled" to true))) }
        )
        
        holder.bind(slide)
    }
    
    override fun onViewAttachedToWindow(holder: CarouselViewHolder) {
        experienceIds[holder.adapterPosition]?.let { expId ->
            ContentAnalytics.trackExperienceView(expId, "home.carousel.${holder.adapterPosition}")
        }
    }
    
    fun onSlideClicked(position: Int) {
        experienceIds[position]?.let { expId ->
            ContentAnalytics.trackExperienceClick(expId, "home.carousel.$position")
        }
    }
}
```

### Product Grid (Compose)

```kotlin
@Composable
fun ProductCard(product: Product) {
    var expId by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(product.id) {
        expId = ContentAnalytics.registerExperience(
            assets = listOf(ContentItem(product.imageUrl, emptyMap())),
            texts = listOf(
                ContentItem(product.name, mapOf("role" to "headline")),
                ContentItem(product.price, mapOf("role" to "price"))
            )
        )
        expId?.let {
            ContentAnalytics.trackExperienceView(it, "catalog.product.${product.id}")
        }
    }
    
    Column(
        modifier = Modifier.clickable {
            expId?.let {
                ContentAnalytics.trackExperienceClick(it, "catalog.product.${product.id}")
            }
        }
    ) {
        AsyncImage(model = product.imageUrl, contentDescription = null)
        Text(product.name)
        Text(product.price)
    }
}
```

### Reusable Tracking Component

```kotlin
@Composable
fun TrackedExperience(
    assets: List<ContentItem>,
    texts: List<ContentItem>,
    location: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var expId by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(location) {
        expId = ContentAnalytics.registerExperience(assets = assets, texts = texts)
        expId?.let { ContentAnalytics.trackExperienceView(it, location) }
    }
    
    Box(
        modifier = Modifier.clickable {
            expId?.let { ContentAnalytics.trackExperienceClick(it, location) }
            onClick?.invoke()
        }
    ) {
        content()
    }
}

// Usage
TrackedExperience(
    assets = listOf(ContentItem(product.imageUrl, emptyMap())),
    texts = listOf(ContentItem(product.name, mapOf("role" to "headline"))),
    location = "product.${product.id}"
) {
    ProductCardView(product)
}
```

## See Also

- [API Reference](api-reference.md) - Complete API documentation
- [Crash Recovery](crash-recovery.md) - Persistence implementation details
- [Troubleshooting](troubleshooting.md) - Common issues and solutions
