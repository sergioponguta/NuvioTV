package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamQualityFilterTest {

    private fun stream(name: String, addon: String = "Addon") = Stream(
        name = name,
        title = name,
        description = null,
        url = "https://example.test/$addon/${name.hashCode()}",
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = addon,
        addonLogo = null
    )

    @Test
    fun detectsTheTierFromTheReleaseName() {
        assertEquals(StreamQualityTier.UHD, streamQualityTier(stream("Movie 2160p HDR")))
        assertEquals(StreamQualityTier.FHD, streamQualityTier(stream("Movie 1080p WEB-DL")))
        assertEquals(StreamQualityTier.HD, streamQualityTier(stream("Movie 720p")))
        assertEquals(StreamQualityTier.SD, streamQualityTier(stream("Movie 480p")))
        assertEquals(StreamQualityTier.UNKNOWN, streamQualityTier(stream("Movie without quality")))
    }

    @Test
    fun listsOnlyThePresentTiersBestFirst() {
        val streams = listOf(
            stream("Movie 720p"),
            stream("Movie 4K"),
            stream("Movie without quality"),
            stream("Another 720p")
        )

        assertEquals(
            listOf(StreamQualityTier.UHD, StreamQualityTier.HD, StreamQualityTier.UNKNOWN),
            availableQualityTiers(streams, ::streamQualityTier)
        )
    }

    @Test
    fun hidesTheRowWhenThereIsNothingToChooseFrom() {
        val streams = listOf(stream("Movie 1080p"), stream("Another 1080p"))

        assertEquals(emptyList<StreamQualityTier>(), availableQualityTiers(streams, ::streamQualityTier))
        assertEquals(emptyList<StreamQualityTier>(), availableQualityTiers(emptyList(), ::streamQualityTier))
    }

    @Test
    fun filtersByTierKeepingTheAddonOrder() {
        val a1080 = stream("A 1080p", addon = "A")
        val b720 = stream("B 720p", addon = "B")
        val c1080 = stream("C 1080p", addon = "C")
        val streams = listOf(a1080, b720, c1080)

        assertEquals(listOf(a1080, c1080), filterStreamsByQuality(streams, StreamQualityTier.FHD, ::streamQualityTier))
        assertEquals(streams, filterStreamsByQuality(streams, null, ::streamQualityTier))
    }
}
