package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState

/**
 * Cache buckets of the cache chip row.
 *
 * CACHED is a debrid cache hit: a direct debrid link, a torrent the local
 * availability check marked CACHED, or an addon stream that labels itself
 * cached. DIRECT is plain direct playback with no cache label (HTTP/HLS links,
 * external pages, YouTube): addons such as MoviePlus have nothing to cache.
 * NOT_CACHED is anything that has to be downloaded/resolved first.
 */
enum class StreamCacheTier {
    CACHED,
    DIRECT,
    NOT_CACHED
}

// Addons label their streams in the visible text instead of the structured
// fields: Latinobrid uses "[TB ⚡]" / "[TB ⏳]" in the name and AIOStreams uses
// "⚡Ready (TB)" / "❌ Not Ready (TB)" in the description.
private val cachedTextMarkers = listOf(
    "⚡",
    "cached",
    "cacheado",
    "instant",
    "instantáneo",
    "instantaneo",
    "en caché",
    "en cache"
)
private val uncachedTextMarkers = listOf(
    "⏳",
    "not ready",
    "uncached",
    "not cached",
    "no cacheado",
    "sin caché",
    "sin cache"
)

internal fun streamCacheTier(stream: Stream): StreamCacheTier {
    when (stream.debridCacheStatus?.state) {
        StreamDebridCacheState.CACHED -> return StreamCacheTier.CACHED
        StreamDebridCacheState.NOT_CACHED -> return StreamCacheTier.NOT_CACHED
        else -> Unit
    }
    if (stream.isDirectDebrid()) return StreamCacheTier.CACHED
    // A torrent without a direct URL must be resolved before it can play, no
    // matter what its label says.
    if (stream.needsLocalDebridResolve()) return StreamCacheTier.NOT_CACHED

    val text = listOfNotNull(stream.name, stream.title, stream.description)
        .joinToString("\n")
        .lowercase()
    if (uncachedTextMarkers.any(text::contains)) return StreamCacheTier.NOT_CACHED
    if (cachedTextMarkers.any(text::contains)) return StreamCacheTier.CACHED
    return StreamCacheTier.DIRECT
}

/**
 * Tiers present in [streams], cached first. Fewer than two means there is
 * nothing to choose between, so the chip row stays hidden (empty list).
 */
internal fun availableCacheTiers(
    streams: List<Stream>,
    tierOf: (Stream) -> StreamCacheTier = ::streamCacheTier
): List<StreamCacheTier> {
    val present = streams.mapTo(HashSet<StreamCacheTier>(), tierOf)
    if (present.size < 2) return emptyList()
    return StreamCacheTier.entries.filter { it in present }
}

/** Keeps the original (addon/provider) order; null tier means no filtering. */
internal fun filterStreamsByCache(
    streams: List<Stream>,
    tier: StreamCacheTier?,
    tierOf: (Stream) -> StreamCacheTier = ::streamCacheTier
): List<Stream> = if (tier == null) streams else streams.filter { tierOf(it) == tier }
