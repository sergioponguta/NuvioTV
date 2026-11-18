package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.core.debrid.DirectDebridStreamFilter
import com.nuvio.tv.domain.model.DebridSettings
import com.nuvio.tv.domain.model.DebridStreamResolution
import com.nuvio.tv.domain.model.Stream

/**
 * Resolution buckets of the quality chip row. Declaration order is the display
 * order (best first, unknown last). The label is technical (4K/1080p), so it is
 * not localized; UNKNOWN borrows the translated "unknown quality" string.
 */
enum class StreamQualityTier(val label: String) {
    UHD("4K"),
    QHD("1440p"),
    FHD("1080p"),
    HD("720p"),
    SD("SD"),
    UNKNOWN("")
}

/**
 * Reuses the Direct Debrid release-name parser so addon streams get the same
 * resolution detection as debrid ones. The DebridSettings default is deliberate:
 * the chips must not depend on the user's debrid filters.
 */
internal fun streamQualityTier(stream: Stream): StreamQualityTier =
    when (DirectDebridStreamFilter.facts(stream, DebridSettings()).resolution) {
        DebridStreamResolution.P2160 -> StreamQualityTier.UHD
        DebridStreamResolution.P1440 -> StreamQualityTier.QHD
        DebridStreamResolution.P1080 -> StreamQualityTier.FHD
        DebridStreamResolution.P720 -> StreamQualityTier.HD
        DebridStreamResolution.P576,
        DebridStreamResolution.P480,
        DebridStreamResolution.P360 -> StreamQualityTier.SD
        DebridStreamResolution.UNKNOWN -> StreamQualityTier.UNKNOWN
    }

/**
 * Tiers present in [streams], best first. Fewer than two means there is nothing
 * to choose between, so the chip row stays hidden (empty list).
 */
internal fun availableQualityTiers(
    streams: List<Stream>,
    tierOf: (Stream) -> StreamQualityTier
): List<StreamQualityTier> {
    val present = streams.mapTo(HashSet<StreamQualityTier>(), tierOf)
    if (present.size < 2) return emptyList()
    return StreamQualityTier.entries.filter { it in present }
}

/** Keeps the original (addon/provider) order; null tier means no filtering. */
internal fun filterStreamsByQuality(
    streams: List<Stream>,
    tier: StreamQualityTier?,
    tierOf: (Stream) -> StreamQualityTier
): List<Stream> = if (tier == null) streams else streams.filter { tierOf(it) == tier }
