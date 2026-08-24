/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.domain.lyrics

import kotlinx.serialization.Serializable

enum class LyricsSourceType {
  EMBEDDED,
  ONLINE,
  LOCAL,
}

@Serializable
enum class LyricsProviderId(
  val displayName: String,
  val requiresVideoId: Boolean = false,
) {
  BETTER_LYRICS("BetterLyrics"),
  BETTER_LYRICS_PORTATO("BetterLyrics Portato"),
  YOULY_PLUS("YouLyPlus"),
  LRCLIB("LRCLIB"),
  KUGOU("KuGou"),
  MEGALOBIZ("Megalobiz"),
  SIMP_MUSIC("SimpMusic", requiresVideoId = true),
  UNISON("Unison"),
  PAXSENIX_APPLE_MUSIC("Paxsenix: Apple Music"),
  PAXSENIX_NETEASE("Paxsenix: NetEase"),
  PAXSENIX_SPOTIFY("Paxsenix: Spotify"),
  PAXSENIX_MUSIXMATCH("Paxsenix: Musixmatch"),
  PAXSENIX_YOUTUBE("Paxsenix: YouTube"),
  ;

  companion object {
    val DEFAULT_ORDER: List<LyricsProviderId> = entries.toList()
  }
}

@Serializable
data class Lyrics(
  val plain: List<String>? = null,
  val synced: List<SyncedLine>? = null,
  val areFromRemote: Boolean = false,
  val sourceType: LyricsSourceType = LyricsSourceType.ONLINE,
  val provider: LyricsProviderId? = null,
  val isWordSynced: Boolean = false,
) {
  fun isValid(): Boolean = !synced.isNullOrEmpty() || !plain.isNullOrEmpty()
}

@Serializable
data class SyncedLine(
  val time: Int,
  val line: String,
  val words: List<SyncedWord>? = null,
  val translation: String? = null,
  val romanization: String? = null,
)

@Serializable
data class SyncedWord(
  val time: Int,
  val word: String,
  val startsNewWord: Boolean = true,
)
