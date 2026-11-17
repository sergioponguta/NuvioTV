package com.nuvio.tv.data.trailer

import android.util.Log
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.domain.repository.MetaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val TAG = "LatinoTrailerLookup"
private const val SOURCE_SUFFICIENT_RETRIES = 3
private const val SOURCE_SUFFICIENT_RETRY_DELAY_MS = 500L

private fun isLatinAmericanSpanish(lang: String?): Boolean {
    val l = lang?.trim()?.lowercase() ?: return false
    return l.startsWith("es-") && l != "es-es"
}

/**
 * Asks the installed meta addons for a Latin American Spanish trailer (`lang` "es-419" or another
 * regional "es-XX" except "es-ES", as the "Trailers ES Latino" addon reports it) and returns its
 * YouTube id. A bare "es" or "es-ES" is the Spain dub that the stock metadata addons carry.
 *
 * Hover trailers used to take the first trailer of any language, which in practice was the
 * English one from the catalog or from TMDB. Titles with no Spanish trailer return null, so
 * nothing plays instead of an English video.
 *
 * Meta addons answer to IMDb ids only, so `tmdb:<n>` ids are translated first.
 */
suspend fun findLatinoTrailerYtId(
    metaRepository: MetaRepository,
    tmdbService: TmdbService,
    itemId: String,
    apiType: String
): String? {
    val baseId = itemId.substringBefore(':')
    val imdbId = if (baseId.startsWith("tt")) {
        baseId
    } else {
        val tmdbId = try {
            tmdbService.ensureTmdbId(itemId, apiType)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        tmdbId?.toIntOrNull()?.let { numericId ->
            try {
                tmdbService.tmdbToImdb(numericId, apiType)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    } ?: run {
        Log.d(TAG, "No IMDb id for $itemId; skipping")
        return null
    }

    repeat(SOURCE_SUFFICIENT_RETRIES) { attempt ->
        val result = try {
            metaRepository.getMetaFromAllAddons(apiType, imdbId)
                .first { it is NetworkResult.Success || it is NetworkResult.Error }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Meta lookup failed for $imdbId: ${e.message}")
            return null
        }
        when {
            result is NetworkResult.Success -> {
                val ytId = result.data.trailers
                    .firstOrNull { !it.ytId.isNullOrBlank() && isLatinAmericanSpanish(it.lang) }
                    ?.ytId
                Log.d(TAG, "$imdbId: ${if (ytId == null) "no Latin trailer" else "Latin trailer found"} (${result.data.trailers.size} trailers)")
                return ytId
            }
            // A lookup that was already in flight for this id was started with the catalog's own
            // addon as source and answered "the catalog meta is enough", which carries no trailers.
            // That call has finished by now, so asking again does the real request.
            result is NetworkResult.Error && result.code == NetworkResult.SOURCE_SUFFICIENT_CODE -> {
                Log.d(TAG, "$imdbId: shared in-flight lookup was source-sufficient, retry ${attempt + 1}")
                delay(SOURCE_SUFFICIENT_RETRY_DELAY_MS)
            }
            else -> {
                Log.d(TAG, "$imdbId: no meta from addons")
                return null
            }
        }
    }
    return null
}
