package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.core.debrid.DirectDebridStreamFilter
import com.nuvio.tv.domain.model.DebridSettings
import com.nuvio.tv.domain.model.DebridStreamResolution
import com.nuvio.tv.domain.model.Stream

/**
 * Resolution buckets used to group the source list when "order by quality" is on.
 * The label is technical (4K/1080p), so it is not localized; UNKNOWN borrows the
 * translated "unknown quality" string at render time.
 */
internal enum class StreamQualityTier(val label: String) {
    UHD("4K"),
    QHD("1440p"),
    FHD("1080p"),
    HD("720p"),
    SD("SD"),
    UNKNOWN("")
}

internal data class StreamQualityFacts(
    val tier: StreamQualityTier,
    val resolution: Int,
    val visualRank: Int,
    val qualityRank: Int,
    val sizeBytes: Long?
)

/**
 * Reuses the Direct Debrid release-name parser so addon streams get the same
 * resolution/HDR/rip detection as debrid ones. The DebridSettings default is
 * deliberate: ranking must not depend on the user's debrid filters.
 */
internal fun streamQualityFacts(stream: Stream): StreamQualityFacts {
    val facts = DirectDebridStreamFilter.facts(stream, DebridSettings())
    val tier = when (facts.resolution) {
        DebridStreamResolution.P2160 -> StreamQualityTier.UHD
        DebridStreamResolution.P1440 -> StreamQualityTier.QHD
        DebridStreamResolution.P1080 -> StreamQualityTier.FHD
        DebridStreamResolution.P720 -> StreamQualityTier.HD
        DebridStreamResolution.P576,
        DebridStreamResolution.P480,
        DebridStreamResolution.P360 -> StreamQualityTier.SD
        DebridStreamResolution.UNKNOWN -> StreamQualityTier.UNKNOWN
    }
    return StreamQualityFacts(
        tier = tier,
        resolution = facts.resolution.value,
        visualRank = facts.visualRank,
        qualityRank = facts.qualityRank,
        sizeBytes = facts.size
    )
}

private fun compareQuality(left: StreamQualityFacts, right: StreamQualityFacts): Int {
    if (left.resolution != right.resolution) return right.resolution.compareTo(left.resolution)
    if (left.visualRank != right.visualRank) return left.visualRank.compareTo(right.visualRank)
    if (left.qualityRank != right.qualityRank) return left.qualityRank.compareTo(right.qualityRank)
    // Bigger release first; missing size goes last.
    return (right.sizeBytes ?: -1L).compareTo(left.sizeBytes ?: -1L)
}

/**
 * Stable sort: streams with the same quality keep the addon/provider order they
 * already had, so the list stays deterministic between emissions.
 */
internal fun sortStreamsByQuality(
    streams: List<Stream>,
    factsOf: (Stream) -> StreamQualityFacts
): List<Stream> {
    if (streams.size < 2) return streams
    val facts = streams.map(factsOf)
    return streams.indices
        .sortedWith { left, right -> compareQuality(facts[left], facts[right]) }
        .map(streams::get)
}

/** Tier for the section header, or null when the previous stream shares it. */
internal fun streamQualityHeaderTier(
    stream: Stream,
    previous: Stream?,
    tierOf: (Stream) -> StreamQualityTier
): StreamQualityTier? {
    val tier = tierOf(stream)
    if (previous != null && tierOf(previous) == tier) return null
    return tier
}
