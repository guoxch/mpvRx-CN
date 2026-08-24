/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.repository.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import app.gyrolet.mpvrx.data.lyrics.LyricsSearchRequest
import app.gyrolet.mpvrx.domain.lyrics.Lyrics
import app.gyrolet.mpvrx.domain.lyrics.LyricsSourceType
import app.gyrolet.mpvrx.utils.media.EmbeddedLyricsExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LyricsResult(
  val embeddedLyrics: Lyrics? = null,
  val onlineLyrics: Lyrics? = null,
  val activeLyrics: Lyrics? = null,
  val selectedSource: LyricsSourceType = LyricsSourceType.EMBEDDED,
  val availableSources: List<LyricsSourceType> = emptyList(),
)

class LyricsRepository(
  private val context: Context,
  private val providerRegistry: LyricsProviderRegistry,
) {
  companion object {
    private const val TAG = "LyricsRepository"
    private val BRACKETED_REGEX = Regex("""[\(\[\{\uFF08\uFF3B\uFF5B\u3010\u300E\u300C\u3014\u3008\u300A]([^)\]\}\uFF09\uFF3D\uFF5D\u3011\u300F\u300D\u3015\u3009\u300B]*)[\)\]\}\uFF09\uFF3D\uFF5D\u3011\u300F\u300D\u3015\u3009\u300B]""")
    private val TRACK_NO_REGEX = Regex("""^\s*\d{1,3}\s*[\._-]\s+""")
    private val AUDIO_EXT_REGEX = Regex("""\.(mp3|flac|m4a|aac|wav|ogg|opus|wma|alac|ape)$""", RegexOption.IGNORE_CASE)
    private val UNKNOWN_ARTISTS = setOf("", "unknown", "unknown artist", "<unknown>", "various artists", "various")
  }

  // Cache by media path -> LyricsResult
  private val cache = LruCache<String, LyricsResult>(64)

  private fun cleanTitle(title: String): String {
    return title
      .replace(AUDIO_EXT_REGEX, "")
      .replace(TRACK_NO_REGEX, "")
      .trim()
  }

  private fun superCleanTitle(title: String): String {
    val step1 = cleanTitle(title)
    val step2 = BRACKETED_REGEX.replace(step1, "").trim()
    val step3 = step2.split(" feat.", " ft.", " featuring", " Feat.", " Ft.").first().trim()
    return step3.ifBlank { step1 }
  }

  private fun cleanArtist(artist: String?): String {
    val raw = artist?.trim().orEmpty()
    if (raw.lowercase() in UNKNOWN_ARTISTS) return ""
    return raw.split(" feat.", " ft.", " featuring", " & ", " , ").first().trim()
  }

  suspend fun loadLyricsForTrack(
    mediaPath: String,
    title: String?,
    artist: String?,
    album: String? = null,
    durationSeconds: Int = 0,
    forceRefresh: Boolean = false,
  ): LyricsResult = withContext(Dispatchers.IO) {
    if (!forceRefresh) {
      cache.get(mediaPath)?.let { return@withContext it }
    }

    Log.d(TAG, "Loading lyrics for: $title by $artist ($mediaPath)")

    // 1. Check embedded lyrics first
    val embedded = EmbeddedLyricsExtractor.extractEmbeddedLyrics(context, mediaPath)?.let(providerRegistry::enhance)

    // 2. Query all enabled providers and keep the quality-ranked results.
    val rankedOnlineLyrics = fetchRankedOnlineLyrics(title, artist, album, durationSeconds, mediaPath)
    val online = rankedOnlineLyrics.firstOrNull()

    // 3. Determine available sources and default preference (Embedded first if available)
    val sources = mutableListOf<LyricsSourceType>()
    if (embedded != null && embedded.isValid()) {
      sources.add(if (embedded.sourceType == LyricsSourceType.LOCAL) LyricsSourceType.LOCAL else LyricsSourceType.EMBEDDED)
    }
    if (online != null && online.isValid()) {
      sources.add(LyricsSourceType.ONLINE)
    }

    val defaultSelected = when {
      embedded != null && embedded.isValid() -> embedded.sourceType
      online != null && online.isValid() -> LyricsSourceType.ONLINE
      else -> LyricsSourceType.EMBEDDED
    }

    val active = when (defaultSelected) {
      LyricsSourceType.EMBEDDED, LyricsSourceType.LOCAL -> embedded ?: online
      LyricsSourceType.ONLINE -> online ?: embedded
    }

    val result = LyricsResult(
      embeddedLyrics = embedded,
      onlineLyrics = online,
      activeLyrics = active,
      selectedSource = defaultSelected,
      availableSources = sources.distinct(),
    )

    cache.put(mediaPath, result)
    result
  }

  private suspend fun fetchRankedOnlineLyrics(
    rawTitle: String?,
    rawArtist: String?,
    album: String? = null,
    durationSeconds: Int = 0,
    mediaId: String? = null,
  ): List<Lyrics> = withContext(Dispatchers.IO) {
    if (rawTitle.isNullOrBlank()) return@withContext emptyList()

    var title = cleanTitle(rawTitle)
    var artist = cleanArtist(rawArtist)

    // Split "Artist - Title" if artist is unknown and title contains "-"
    if (artist.isBlank() && title.contains("-")) {
      val parts = title.split("-", limit = 2)
      if (parts.size == 2 && parts[0].trim().isNotBlank() && parts[1].trim().isNotBlank()) {
        artist = cleanArtist(parts[0])
        title = cleanTitle(parts[1])
      }
    }

    val request =
      LyricsSearchRequest(
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        mediaId = extractYouTubeId(mediaId),
      )
    return@withContext try {
      val exact = providerRegistry.fetchAll(request)
      if (exact.isNotEmpty()) {
        exact
      } else {
        val fallbackTitle = superCleanTitle(title)
        if (fallbackTitle == title) emptyList() else providerRegistry.fetchAll(request.copy(title = fallbackTitle))
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to fetch online lyrics: ${e.message}")
      emptyList()
    }
  }

  suspend fun refreshOnlineLyrics(
    mediaPath: String,
    title: String?,
    artist: String?,
    album: String? = null,
    durationSeconds: Int = 0,
  ): LyricsResult {
    val existing = cache.get(mediaPath) ?: LyricsResult()
    val rankedOnlineLyrics = fetchRankedOnlineLyrics(title, artist, album, durationSeconds, mediaPath)
    val online = rankedOnlineLyrics.firstOrNull()
    val sources =
      if (online != null) {
        (existing.availableSources + LyricsSourceType.ONLINE).distinct()
      } else {
        existing.availableSources - LyricsSourceType.ONLINE
      }
    val updated =
      existing.copy(
        onlineLyrics = online,
        activeLyrics = online ?: existing.embeddedLyrics,
        selectedSource = if (online != null) LyricsSourceType.ONLINE else existing.selectedSource,
        availableSources = sources,
      )
    cache.put(mediaPath, updated)
    return updated
  }

  fun switchSource(mediaPath: String, sourceType: LyricsSourceType): LyricsResult? {
    val existing = cache.get(mediaPath) ?: return null
    val newActive = when (sourceType) {
      LyricsSourceType.EMBEDDED, LyricsSourceType.LOCAL -> existing.embeddedLyrics ?: existing.onlineLyrics
      LyricsSourceType.ONLINE -> existing.onlineLyrics ?: existing.embeddedLyrics
    }
    val updated = existing.copy(
      selectedSource = sourceType,
      activeLyrics = newActive,
    )
    cache.put(mediaPath, updated)
    return updated
  }

  private fun extractYouTubeId(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return raw
    return Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?.*?v=|shorts/|embed/))([A-Za-z0-9_-]{11})""", RegexOption.IGNORE_CASE)
      .find(raw)
      ?.groupValues
      ?.get(1)
  }
}
