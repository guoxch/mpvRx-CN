/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.repository.lyrics

import app.gyrolet.mpvrx.data.lyrics.EnhancedLyricsApiService
import app.gyrolet.mpvrx.data.lyrics.LrcLibApiService
import app.gyrolet.mpvrx.data.lyrics.LyricsSearchRequest
import app.gyrolet.mpvrx.domain.lyrics.Lyrics
import app.gyrolet.mpvrx.domain.lyrics.LyricsProviderId
import app.gyrolet.mpvrx.domain.lyrics.LyricsSourceType
import app.gyrolet.mpvrx.preferences.AudioPreferences
import app.gyrolet.mpvrx.utils.media.LyricsRomanizationOptions
import app.gyrolet.mpvrx.utils.media.LyricsRomanizer
import app.gyrolet.mpvrx.utils.media.LyricsUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class LyricsProviderRegistry(
  private val preferences: AudioPreferences,
  private val lrcLibApiService: LrcLibApiService,
  private val enhancedLyricsApiService: EnhancedLyricsApiService,
) {
  suspend fun fetchAll(request: LyricsSearchRequest): List<Lyrics> =
    supervisorScope {
      val order = LyricsProviderId.DEFAULT_ORDER
      order
        .map { provider ->
          async {
            val raw =
              if (provider == LyricsProviderId.LRCLIB) {
                fetchLrcLib(request)
              } else {
                enhancedLyricsApiService.fetch(provider, request)
              } ?: return@async null
            LyricsUtils.parseLyrics(raw, LyricsSourceType.ONLINE)
              .takeIf(Lyrics::isValid)
              ?.let(::enhance)
              ?.copy(provider = provider)
          }
        }.awaitAll()
        .filterNotNull()
        .sortedWith(
          compareByDescending<Lyrics>(::qualityScore)
            .thenBy { lyrics -> order.indexOf(lyrics.provider).takeIf { it >= 0 } ?: Int.MAX_VALUE },
        )
    }

  fun enhance(lyrics: Lyrics): Lyrics = LyricsRomanizer.apply(lyrics, romanizationOptions())

  private suspend fun fetchLrcLib(request: LyricsSearchRequest): String? {
    if (request.artist.isNotBlank()) {
      lrcLibApiService
        .getLyrics(
          trackName = request.title,
          artistName = request.artist,
          albumName = request.album,
          duration = request.durationSeconds.takeIf { it > 0 },
        )?.let { response -> response.syncedLyrics ?: response.plainLyrics }
        ?.let { return it }
    }
    return lrcLibApiService
      .searchLyrics(
        trackName = request.title,
        artistName = request.artist.takeIf(String::isNotBlank),
      ).firstNotNullOfOrNull { response -> response.syncedLyrics ?: response.plainLyrics }
  }

  private fun qualityScore(lyrics: Lyrics): Int =
    when {
      lyrics.isWordSynced -> 3
      !lyrics.synced.isNullOrEmpty() -> 2
      !lyrics.plain.isNullOrEmpty() -> 1
      else -> 0
    }

  private fun romanizationOptions(): LyricsRomanizationOptions =
    LyricsRomanizationOptions(
      japanese = preferences.lyricsRomanizeJapanese.get(),
      korean = preferences.lyricsRomanizeKorean.get(),
      chinese = preferences.lyricsRomanizeChinese.get(),
      hindi = preferences.lyricsRomanizeHindi.get(),
      other = preferences.lyricsRomanizeOtherLanguages.get(),
    )
}