package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamQualityOrderingTest {

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
    fun sortsFromHighestResolutionToLowestAndUnknownLast() {
        val streams = listOf(
            stream("Release 720p"),
            stream("Release 1080p"),
            stream("Release without quality"),
            stream("Release 4K")
        )

        val sorted = sortStreamsByQuality(streams) { streamQualityFacts(it) }

        assertEquals(
            listOf("Release 4K", "Release 1080p", "Release 720p", "Release without quality"),
            sorted.map { it.name }
        )
    }

    @Test
    fun keepsProviderOrderForEqualQuality() {
        val first = stream("First 1080p", addon = "A")
        val second = stream("Second 1080p", addon = "B")
        val third = stream("Third 1080p", addon = "A")

        val sorted = sortStreamsByQuality(listOf(first, second, third)) { streamQualityFacts(it) }

        assertEquals(listOf(first, second, third), sorted)
    }

    @Test
    fun showsASectionHeaderOnlyWhenTheTierChanges() {
        val uhd = stream("Movie 4K")
        val fhd = stream("Movie 1080p")
        val anotherFhd = stream("Another movie 1080p")
        val tierOf = { value: Stream -> streamQualityFacts(value).tier }

        assertEquals(StreamQualityTier.UHD, streamQualityHeaderTier(uhd, null, tierOf))
        assertEquals(StreamQualityTier.FHD, streamQualityHeaderTier(fhd, uhd, tierOf))
        assertNull(streamQualityHeaderTier(anotherFhd, fhd, tierOf))
    }
}
