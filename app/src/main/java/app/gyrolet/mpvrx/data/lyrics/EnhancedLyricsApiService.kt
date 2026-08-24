/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Provider protocols adapted for mpvRx from ArchiveTune/lyrics (GPL-3.0),
 * revision fe895389128705a7653dadb4536e07efbaa4bbd5.
 */

package app.gyrolet.mpvrx.data.lyrics

import android.text.Html
import android.util.Base64
import app.gyrolet.mpvrx.domain.lyrics.LyricsProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.abs

data class LyricsSearchRequest(
  val title: String,
  val artist: String,
  val album: String? = null,
  val durationSeconds: Int = 0,
  val mediaId: String? = null,
)

class EnhancedLyricsApiService(
  client: OkHttpClient,
  private val json: Json,
) {
  private val providerClient =
    client.newBuilder()
      .cookieJar(CookieJar.NO_COOKIES)
      .connectTimeout(7, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(10, TimeUnit.SECONDS)
      .build()

  suspend fun fetch(
    provider: LyricsProviderId,
    request: LyricsSearchRequest,
  ): String? =
    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
      try {
        when (provider) {
          LyricsProviderId.BETTER_LYRICS -> fetchBetterLyrics(request, "getLyrics", "kugou/getLyrics")
          LyricsProviderId.BETTER_LYRICS_PORTATO -> fetchBetterLyrics(request, "qq/getLyrics")
          LyricsProviderId.YOULY_PLUS -> fetchYouLyPlus(request)
          LyricsProviderId.KUGOU -> fetchKuGou(request)
          LyricsProviderId.MEGALOBIZ -> fetchMegalobiz(request)
          LyricsProviderId.SIMP_MUSIC -> fetchSimpMusic(request)
          LyricsProviderId.UNISON -> fetchUnison(request)
          LyricsProviderId.PAXSENIX_APPLE_MUSIC -> fetchPaxsenixAppleMusic(request)
          LyricsProviderId.PAXSENIX_NETEASE -> fetchPaxsenixNetease(request)
          LyricsProviderId.PAXSENIX_SPOTIFY -> fetchPaxsenixSearchBackend(request, "spotify")
          LyricsProviderId.PAXSENIX_MUSIXMATCH -> fetchPaxsenixMusixmatch(request)
          LyricsProviderId.PAXSENIX_YOUTUBE -> fetchPaxsenixSearchBackend(request, "youtube")
          LyricsProviderId.LRCLIB -> null
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        null
      }
    }?.takeIf(String::isNotBlank)

  private suspend fun fetchBetterLyrics(
    request: LyricsSearchRequest,
    vararg paths: String,
  ): String? {
    if (request.artist.isBlank()) return null
    val params = metadataParams(request, titleKey = "s", artistKey = "a", albumKey = "al", durationKey = "d")
    return paths.firstNotNullOfOrNull { path ->
      val body = getBody("https://lyrics-api.boidu.dev/$path", params) ?: return@firstNotNullOfOrNull null
      body.takeIf(::looksLikeXml) ?: extractLyrics(parseJson(body))
    }
  }

  private suspend fun fetchYouLyPlus(request: LyricsSearchRequest): String? {
    if (request.artist.isBlank()) return null
    val mirrors =
      listOf(
        "https://lyricsplus.binimum.org/",
        "https://lyricsplus.prjktla.my.id/",
        "https://lyricsplus.prjktla.workers.dev/",
        "https://lyricsplus.atomix.one/",
        "https://lyricsplus-seven.vercel.app/",
      )
    val params = metadataParams(request, durationKey = "duration")
    mirrors.firstNotNullOfOrNull { base ->
      val body = getBody("${base}v1/ttml/get", params) ?: return@firstNotNullOfOrNull null
      body.takeIf(::looksLikeXml) ?: parseJson(body)?.asObject()?.get("ttml")?.asString()
    }?.let { return it }

    return mirrors.firstNotNullOfOrNull { base ->
      val root = getJson("${base}v2/lyrics/get", params)?.asObject() ?: return@firstNotNullOfOrNull null
      val type = root["type"]?.asString().orEmpty()
      val lines = root["lyrics"]?.asArray().orEmpty()
      lines.mapNotNull { lineElement ->
        val line = lineElement.asObject() ?: return@mapNotNull null
        val time = line["time"]?.asLong() ?: return@mapNotNull line["text"]?.asString()
        val syllables = line["syllabus"]?.asArray().orEmpty()
        buildString {
          append(formatLrcTimestamp(time, bracketed = true))
          if (type.equals("word", ignoreCase = true) && syllables.isNotEmpty()) {
            syllables.forEach { syllableElement ->
              val syllable = syllableElement.asObject() ?: return@forEach
              val text = syllable["text"]?.asString().orEmpty()
              val wordTime = syllable["time"]?.asLong()
              if (text.isNotBlank() && wordTime != null) {
                append(formatLrcTimestamp(wordTime, bracketed = false))
                append(text)
              }
            }
          } else {
            append(line["text"]?.asString().orEmpty())
          }
        }
      }.joinToString("\n").takeIf(String::isNotBlank)
    }
  }

  private suspend fun fetchKuGou(request: LyricsSearchRequest): String? {
    val keyword = listOf(request.title, request.artist).filter(String::isNotBlank).joinToString(" - ")
    val search =
      getJson(
        "https://lyrics.kugou.com/search",
        mapOf(
          "ver" to "1",
          "man" to "yes",
          "client" to "pc",
          "duration" to request.durationSeconds.takeIf { it > 0 }?.times(1000),
          "keyword" to keyword,
        ),
      )?.asObject() ?: return null
    val candidate = search["candidates"]?.asArray()?.firstOrNull()?.asObject() ?: return null
    val id = candidate["id"]?.asString() ?: return null
    val accessKey = candidate["accesskey"]?.asString() ?: return null
    val download =
      getJson(
        "https://lyrics.kugou.com/download",
        mapOf(
          "fmt" to "lrc",
          "charset" to "utf8",
          "client" to "pc",
          "ver" to "1",
          "id" to id,
          "accesskey" to accessKey,
        ),
      )?.asObject() ?: return null
    val encoded = download["content"]?.asString() ?: return null
    return runCatching { String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
  }

  private suspend fun fetchMegalobiz(request: LyricsSearchRequest): String? {
    val query = listOf(request.artist, request.title).filter(String::isNotBlank).joinToString(" ")
    val searchHtml =
      getBody(
        "https://www.megalobiz.com/searchall",
        mapOf("qry" to query),
        browserHeaders,
      ) ?: return null
    val path = Regex("""href=["'](/lrc/maker/download/[^"']+)["']""").find(searchHtml)?.groupValues?.get(1) ?: return null
    val detailHtml = getBody("https://www.megalobiz.com$path", headers = browserHeaders) ?: return null
    val raw =
      Regex("""id=["']lrc_[^"']*_details["'][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
        .find(detailHtml)
        ?.groupValues
        ?.get(1)
        ?: return null
    @Suppress("DEPRECATION")
    return Html.fromHtml(raw.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n"), Html.FROM_HTML_MODE_LEGACY)
      .toString()
      .trim()
  }

  private suspend fun fetchSimpMusic(request: LyricsSearchRequest): String? {
    val videoId = request.mediaId?.takeIf(::looksLikeYouTubeId) ?: findPaxsenixYouTubeId(request) ?: return null
    val root = getJson("https://api-lyrics.simpmusic.org/v1/$videoId")?.asObject() ?: return null
    if (root["success"]?.asBoolean() == false) return null
    val matches = root["data"]?.asArray().orEmpty().mapNotNull(JsonElement::asObject)
    val best = closestByDuration(matches, request.durationSeconds * 1000L) ?: return null
    return best["syncedLyrics"]?.asString() ?: best["plainLyrics"]?.asString()
  }

  private suspend fun fetchUnison(request: LyricsSearchRequest): String? {
    if (request.artist.isBlank()) return null
    val root =
      getJson(
        "https://unison.boidu.dev/lyrics/search",
        metadataParams(request, titleKey = "song", durationKey = "duration") + ("limit" to "5"),
      )?.asObject() ?: return null
    val entries = root["data"]?.asArray().orEmpty().mapNotNull(JsonElement::asObject)
    entries.firstNotNullOfOrNull { it["lyrics"]?.asString()?.takeIf(String::isNotBlank) }?.let { return it }
    val id = entries.firstOrNull()?.get("id")?.asString() ?: return null
    return extractLyrics(getJson("https://unison.boidu.dev/lyrics/$id"))
  }

  private suspend fun fetchPaxsenixAppleMusic(request: LyricsSearchRequest): String? {
    if (request.artist.isBlank()) return null
    val catalog =
      getJson(
        "https://itunes.apple.com/search",
        mapOf("term" to "${request.title} ${request.artist}", "media" to "music", "entity" to "song", "limit" to "10"),
      )?.asObject() ?: return null
    val songs = catalog["results"]?.asArray().orEmpty().mapNotNull(JsonElement::asObject)
    val match = bestMetadataMatch(songs, request, "trackName", "artistName", "trackTimeMillis") ?: return null
    val id = match["trackId"]?.asString() ?: return null
    val ttml = getBody("$PAXSENIX_BASE/apple-music/lyrics", mapOf("id" to id, "ttml" to "true"), paxsenixHeaders)
    ttml?.takeIf(::looksLikeXml)?.let { return it }
    extractLyrics(parseJson(ttml))?.let { return it }
    val fallback = getJson("$PAXSENIX_BASE/apple-music/lyrics", mapOf("id" to id), paxsenixHeaders)
    return convertAppleMusicToLrc(fallback) ?: extractLyrics(fallback)
  }

  private suspend fun fetchPaxsenixNetease(request: LyricsSearchRequest): String? {
    val root = getJson("$PAXSENIX_BASE/netease/search", mapOf("q" to "${request.title} ${request.artist}"), paxsenixHeaders)?.asObject()
      ?: return null
    val songs = root["result"]?.asObject()?.get("songs")?.asArray().orEmpty().mapNotNull(JsonElement::asObject)
    val best = closestByDuration(songs, request.durationSeconds * 1000L) ?: return null
    val actualDuration = durationMillis(best["duration"])
    if (request.durationSeconds > 0 && actualDuration > 0L && abs(actualDuration - request.durationSeconds * 1000L) > 10_000L) return null
    val id = best["id"]?.asString() ?: return null
    val lyrics = getJson("$PAXSENIX_BASE/netease/lyrics", mapOf("id" to id, "word" to "true"), paxsenixHeaders)?.asObject()
      ?: return null
    return lyrics["klyric"]?.asObject()?.get("lyric")?.asString()
      ?: lyrics["lrc"]?.asObject()?.get("lyric")?.asString()
  }

  private suspend fun fetchPaxsenixSearchBackend(
    request: LyricsSearchRequest,
    backend: String,
  ): String? {
    val root = getJson("$PAXSENIX_BASE/$backend/search", mapOf("q" to "${request.title} ${request.artist}"), paxsenixHeaders)
      ?: return null
    val items = root.asArray().orEmpty().mapNotNull(JsonElement::asObject)
    val best = bestMetadataMatch(items, request, durationKey = "duration") ?: return null
    val id = best["id"]?.asString() ?: best["trackId"]?.asString() ?: return null
    return extractLyrics(getJson("$PAXSENIX_BASE/$backend/lyrics", mapOf("id" to id), paxsenixHeaders))
  }

  private suspend fun fetchPaxsenixMusixmatch(request: LyricsSearchRequest): String? {
    val params =
      mapOf(
        "q" to "${request.title} ${request.artist}",
        "t" to request.title,
        "a" to request.artist,
        "d" to request.durationSeconds.toString(),
      )
    return extractLyrics(getJson("$PAXSENIX_BASE/musixmatch/lyrics", params + ("type" to "word"), paxsenixHeaders))
      ?: extractLyrics(getJson("$PAXSENIX_BASE/musixmatch/lyrics", params, paxsenixHeaders))
  }

  private suspend fun findPaxsenixYouTubeId(request: LyricsSearchRequest): String? {
    val root = getJson("$PAXSENIX_BASE/youtube/search", mapOf("q" to "${request.title} ${request.artist}"), paxsenixHeaders)
      ?: return null
    val items = root.asArray().orEmpty().mapNotNull(JsonElement::asObject)
    val best = bestMetadataMatch(items, request, durationKey = "duration") ?: return null
    return best["id"]?.asString() ?: best["trackId"]?.asString()
  }

  private suspend fun getJson(
    baseUrl: String,
    params: Map<String, Any?> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
  ): JsonElement? = parseJson(getBody(baseUrl, params, headers))

  private suspend fun getBody(
    baseUrl: String,
    params: Map<String, Any?> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
  ): String? {
    val url = buildUrl(baseUrl, params) ?: return null
    val request =
      Request.Builder()
        .url(url)
        .header("User-Agent", "mpvRx/2.2.2")
        .apply { headers.forEach(::header) }
        .get()
        .build()
    return suspendCancellableCoroutine { continuation ->
      val call = providerClient.newCall(request)
      continuation.invokeOnCancellation { call.cancel() }
      call.enqueue(
        object : Callback {
          override fun onFailure(
            call: Call,
            error: IOException,
          ) {
            if (continuation.isActive) continuation.resume(null)
          }

          override fun onResponse(
            call: Call,
            response: Response,
          ) {
            val body =
              response.use {
                if (!it.isSuccessful) null else runCatching { it.body.string() }.getOrNull()?.takeIf(String::isNotBlank)
              }
            if (continuation.isActive) continuation.resume(body)
          }
        },
      )
    }
  }

  private fun buildUrl(baseUrl: String, params: Map<String, Any?>): HttpUrl? =
    baseUrl.toHttpUrlOrNull()?.newBuilder()?.apply {
      params.forEach { (key, value) -> value?.toString()?.takeIf(String::isNotBlank)?.let { addQueryParameter(key, it) } }
    }?.build()

  private fun metadataParams(
    request: LyricsSearchRequest,
    titleKey: String = "title",
    artistKey: String = "artist",
    albumKey: String = "album",
    durationKey: String = "duration",
  ): Map<String, Any?> =
    mapOf(
      titleKey to request.title,
      artistKey to request.artist,
      albumKey to request.album,
      durationKey to request.durationSeconds.takeIf { it > 0 },
    )

  private fun bestMetadataMatch(
    items: List<JsonObject>,
    request: LyricsSearchRequest,
    titleKey: String = "title",
    artistKey: String = "artist",
    durationKey: String = "duration",
  ): JsonObject? {
    val scored = items.map { item ->
      val title = item[titleKey]?.asString() ?: item["name"]?.asString() ?: item["songName"]?.asString().orEmpty()
      val artist = item[artistKey]?.asString() ?: item["artistName"]?.asString().orEmpty()
      var score = similarityScore(title, request.title) * 3 + similarityScore(artist, request.artist) * 2
      val expectedDuration = request.durationSeconds * 1000L
      val actualDuration = durationMillis(item[durationKey])
      if (expectedDuration > 0L && actualDuration > 0L) {
        val difference = abs(actualDuration - expectedDuration)
        score += when {
          difference <= 3_000L -> 4
          difference <= 10_000L -> 2
          else -> -2
        }
      }
      item to score
    }
    return scored.maxByOrNull(Pair<JsonObject, Int>::second)?.takeIf { it.second >= MIN_METADATA_MATCH_SCORE }?.first
  }

  private fun convertAppleMusicToLrc(element: JsonElement?): String? {
    val lines = element?.asObject()?.get("content")?.asArray().orEmpty()
    return lines.mapNotNull { lineElement ->
      val line = lineElement.asObject() ?: return@mapNotNull null
      val timestamp = line["timestamp"]?.asLong() ?: return@mapNotNull null
      val words = line["text"]?.asArray().orEmpty().mapNotNull(JsonElement::asObject)
      buildString {
        append(formatLrcTimestamp(timestamp, bracketed = true))
        words.forEach { word ->
          val text = word["text"]?.asString().orEmpty()
          if (text.isBlank()) return@forEach
          word["timestamp"]?.asLong()?.let { append(formatLrcTimestamp(it, bracketed = false)) }
          append(text)
        }
      }.takeIf { it.length > 12 }
    }.joinToString("\n").takeIf(String::isNotBlank)
  }

  private fun closestByDuration(
    items: List<JsonObject>,
    durationMs: Long,
  ): JsonObject? =
    if (durationMs <= 0L) {
      items.firstOrNull()
    } else {
      items.minByOrNull { item -> abs(durationMillis(item["duration"]) - durationMs) }
    }

  private fun similarityScore(actual: String, expected: String): Int {
    val left = actual.trim().lowercase()
    val right = expected.trim().lowercase()
    return when {
      right.isBlank() -> 0
      left == right -> 4
      left.contains(right) || right.contains(left) -> 2
      else -> 0
    }
  }

  private fun durationMillis(element: JsonElement?): Long {
    val raw = element?.asString()?.trim().orEmpty()
    val numeric = raw.toLongOrNull()
    if (numeric != null) return if (numeric in 1..10_000) numeric * 1000L else numeric
    val parts = raw.split(':').mapNotNull(String::toLongOrNull)
    if (parts.size < 2) return 0L
    return parts.fold(0L) { total, part -> total * 60L + part } * 1000L
  }

  private fun extractLyrics(element: JsonElement?): String? =
    when (element) {
      null, JsonNull -> null
      is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let { value ->
        parseJson(value)?.takeUnless { it is JsonPrimitive }?.let(::extractLyrics) ?: value
      }
      is JsonArray -> element.mapNotNull(::extractLyrics).joinToString("\n").trim().takeIf(String::isNotBlank)
      is JsonObject -> {
        val error = element["error"]
        val hasError =
          when (error) {
            null, JsonNull -> false
            is JsonPrimitive -> error.asBoolean() ?: error.contentOrNull?.isNotBlank() == true
            is JsonArray -> error.isNotEmpty()
            is JsonObject -> error.isNotEmpty()
          }
        if (element["isError"]?.asBoolean() == true || hasError) {
          null
        } else {
          LYRICS_KEYS.firstNotNullOfOrNull { key -> extractLyrics(element[key]) }
            ?: element["data"]?.let(::extractLyrics)
        }
      }
    }

  private fun parseJson(body: String?): JsonElement? =
    body?.trim()?.takeIf(String::isNotBlank)?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

  private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
  private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray
  private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
  private fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull
  private fun JsonElement?.asBoolean(): Boolean? = (this as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

  private fun looksLikeXml(value: String): Boolean {
    val trimmed = value.trimStart()
    return trimmed.startsWith("<tt") || trimmed.startsWith("<?xml")
  }

  private fun looksLikeYouTubeId(value: String): Boolean = value.matches(Regex("^[A-Za-z0-9_-]{11}$"))

  private fun formatLrcTimestamp(
    timeMs: Long,
    bracketed: Boolean,
  ): String {
    val safeTime = timeMs.coerceAtLeast(0L)
    val timestamp = String.format(Locale.US, "%02d:%02d.%03d", safeTime / 60_000L, (safeTime % 60_000L) / 1000L, safeTime % 1000L)
    return if (bracketed) "[$timestamp]" else "<$timestamp>"
  }

  private companion object {
    const val PROVIDER_TIMEOUT_MS = 12_000L
    const val MIN_METADATA_MATCH_SCORE = 4
    const val PAXSENIX_BASE = "https://lyrics.paxsenix.org"
    val LYRICS_KEYS = listOf("ttml", "lyrics", "lrc", "content", "text", "plainLyrics", "syncedLyrics", "line", "lyric")
    val browserHeaders = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
    val paxsenixHeaders =
      mapOf(
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "en-US,en;q=0.9",
      )
  }
}