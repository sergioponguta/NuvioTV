package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamClientResolve
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCacheFilterTest {

    private fun stream(
        name: String,
        url: String? = "https://example.test/$name",
        infoHash: String? = null,
        resolve: StreamClientResolve? = null,
        cache: StreamDebridCacheStatus? = null,
        description: String? = null
    ) = Stream(
        name = name,
        title = name,
        description = description,
        url = url,
        ytId = null,
        infoHash = infoHash,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = "Addon",
        addonLogo = null,
        clientResolve = resolve,
        debridCacheStatus = cache
    )

    private fun directDebrid(name: String) = StreamClientResolve(
        type = "debrid",
        infoHash = "abcdef0123456789abcdef0123456789abcdef01",
        fileIdx = 0,
        magnetUri = null,
        sources = null,
        torrentName = name,
        filename = name,
        mediaType = "movie",
        mediaId = "tt0000001",
        mediaOnlyId = null,
        title = name,
        season = null,
        episode = null,
        service = "torbox",
        serviceIndex = 0,
        serviceExtension = null,
        isCached = true
    )

    private fun cacheStatus(state: StreamDebridCacheState) = StreamDebridCacheStatus(
        providerId = "torbox",
        providerName = "Torbox",
        state = state
    )

    private fun magnet(name: String) = stream(
        name = name,
        url = null,
        infoHash = "abcdef0123456789abcdef0123456789abcdef02"
    )

    @Test
    fun directPlaybackHasItsOwnBucket() {
        // MoviePlus and other direct addons report no debrid cache state; the
        // link plays as-is, so it is neither a cache hit nor a download.
        assertEquals(StreamCacheTier.DIRECT, streamCacheTier(stream("MoviePlus 1080p")))
    }

    @Test
    fun readsTheCacheMarkerAddonsPutInTheVisibleText() {
        assertEquals(
            StreamCacheTier.CACHED,
            streamCacheTier(stream("[TB ⚡] Latinobrid\n4K"))
        )
        assertEquals(
            StreamCacheTier.NOT_CACHED,
            streamCacheTier(stream("[TB ⏳] Latinobrid\n4K"))
        )

        val selection = stream("🔥4K UHD")
        assertEquals(
            StreamCacheTier.CACHED,
            streamCacheTier(selection.copy(description = "SGF · Torrentio · ⚡Ready (TB)"))
        )
        assertEquals(
            StreamCacheTier.NOT_CACHED,
            streamCacheTier(selection.copy(description = "SGF · Comet · ❌ Not Ready (TB)"))
        )
    }

    @Test
    fun structuredStatusBeatsTheTextMarker() {
        assertEquals(
            StreamCacheTier.NOT_CACHED,
            streamCacheTier(
                stream("⚡ label but uncached", cache = cacheStatus(StreamDebridCacheState.NOT_CACHED))
            )
        )
    }

    @Test
    fun aTorrentWithoutADirectUrlMustResolveEvenIfLabeledCached() {
        assertEquals(
            StreamCacheTier.NOT_CACHED,
            streamCacheTier(
                stream(
                    name = "[TB ⚡] Latinobrid",
                    url = null,
                    infoHash = "abcdef0123456789abcdef0123456789abcdef06"
                )
            )
        )
    }

    @Test
    fun cachedDebridSourcesCountAsCached() {
        assertEquals(
            StreamCacheTier.CACHED,
            streamCacheTier(
                stream(
                    name = "Direct debrid",
                    url = null,
                    infoHash = "abcdef0123456789abcdef0123456789abcdef01",
                    resolve = directDebrid("Direct debrid")
                )
            )
        )
        assertEquals(
            StreamCacheTier.CACHED,
            streamCacheTier(
                stream(
                    name = "Verified torrent",
                    url = null,
                    infoHash = "abcdef0123456789abcdef0123456789abcdef03",
                    cache = cacheStatus(StreamDebridCacheState.CACHED)
                )
            )
        )
    }

    @Test
    fun torrentsWithoutACacheHitNeedDownload() {
        assertEquals(StreamCacheTier.NOT_CACHED, streamCacheTier(magnet("Magnet")))
        assertEquals(
            StreamCacheTier.NOT_CACHED,
            streamCacheTier(
                magnet("Not cached").copy(
                    debridCacheStatus = cacheStatus(StreamDebridCacheState.NOT_CACHED)
                )
            )
        )

        // Checking/unknown is not a confirmed hit yet, so it stays on the
        // download side until the availability check answers.
        assertEquals(
            StreamCacheTier.NOT_CACHED,
            streamCacheTier(
                magnet("Checking").copy(
                    debridCacheStatus = cacheStatus(StreamDebridCacheState.CHECKING)
                )
            )
        )
    }

    @Test
    fun listsPresentBucketsCachedFirst() {
        val streams = listOf(
            stream("Direct 1080p"),
            magnet("Magnet 4K"),
            stream(
                name = "Verified torrent",
                url = null,
                infoHash = "abcdef0123456789abcdef0123456789abcdef03",
                cache = cacheStatus(StreamDebridCacheState.CACHED)
            )
        )

        assertEquals(
            listOf(StreamCacheTier.CACHED, StreamCacheTier.DIRECT, StreamCacheTier.NOT_CACHED),
            availableCacheTiers(streams)
        )
    }

    @Test
    fun hidesTheRowWhenEveryStreamIsInTheSameBucket() {
        val streams = listOf(stream("Direct A"), stream("Direct B"))

        assertEquals(emptyList<StreamCacheTier>(), availableCacheTiers(streams))
        assertEquals(emptyList<StreamCacheTier>(), availableCacheTiers(emptyList()))
    }

    @Test
    fun filtersByBucketKeepingTheAddonOrder() {
        val direct = stream("Direct")
        val cached = stream(
            name = "Cached",
            url = null,
            infoHash = "abcdef0123456789abcdef0123456789abcdef03",
            cache = cacheStatus(StreamDebridCacheState.CACHED)
        )
        val magnet = magnet("Magnet")
        val streams = listOf(magnet, direct, cached)

        assertEquals(listOf(direct), filterStreamsByCache(streams, StreamCacheTier.DIRECT))
        assertEquals(listOf(cached), filterStreamsByCache(streams, StreamCacheTier.CACHED))
        assertEquals(listOf(magnet), filterStreamsByCache(streams, StreamCacheTier.NOT_CACHED))
        assertEquals(streams, filterStreamsByCache(streams, null))
    }
}
