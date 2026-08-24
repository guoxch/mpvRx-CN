/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.utils.media

import android.icu.text.Transliterator
import app.gyrolet.mpvrx.domain.lyrics.Lyrics
import java.lang.Character.UnicodeScript

data class LyricsRomanizationOptions(
  val japanese: Boolean,
  val korean: Boolean,
  val chinese: Boolean,
  val hindi: Boolean,
  val other: Boolean,
) {
  val isEnabled: Boolean
    get() = japanese || korean || chinese || hindi || other
}

object LyricsRomanizer {
  private val transliterator =
    lazy {
      runCatching {
        Transliterator.getInstance("Any-Latin; NFD; [:Nonspacing Mark:] Remove; NFC")
      }.getOrNull()
    }

  fun apply(
    lyrics: Lyrics,
    options: LyricsRomanizationOptions,
  ): Lyrics {
    if (!options.isEnabled || lyrics.synced.isNullOrEmpty()) return lyrics
    return lyrics.copy(
      synced =
        lyrics.synced.map { line ->
          if (!shouldRomanize(line.line, options)) {
            line.copy(romanization = null)
          } else {
            val supplied = line.romanization?.normalizeRomanization(line.line)
            line.copy(romanization = supplied ?: romanize(line.line))
          }
        },
    )
  }

  private fun shouldRomanize(
    text: String,
    options: LyricsRomanizationOptions,
  ): Boolean {
    val scripts = text.asSequence().filter(Char::isLetter).map { UnicodeScript.of(it.code) }.toSet()
    return when {
      options.japanese && (UnicodeScript.HIRAGANA in scripts || UnicodeScript.KATAKANA in scripts) -> true
      options.korean && UnicodeScript.HANGUL in scripts -> true
      options.hindi && UnicodeScript.DEVANAGARI in scripts -> true
      options.chinese && UnicodeScript.HAN in scripts -> true
      options.other && scripts.any { it !in excludedScripts } -> true
      else -> false
    }
  }

  private fun romanize(text: String): String? {
    val engine = transliterator.value ?: return null
    return synchronized(engine) { engine.transliterate(text) }.normalizeRomanization(text)
  }

  private fun String.normalizeRomanization(original: String): String? =
    replace(Regex("""\s+"""), " ")
      .trim()
      .takeIf { it.isNotBlank() && !it.equals(original.trim(), ignoreCase = true) }

  private val excludedScripts =
    setOf(
      UnicodeScript.COMMON,
      UnicodeScript.INHERITED,
      UnicodeScript.LATIN,
      UnicodeScript.UNKNOWN,
    )
}