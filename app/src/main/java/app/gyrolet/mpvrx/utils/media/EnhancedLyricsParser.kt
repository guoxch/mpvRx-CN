/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * TTML and QRC parsing adapted for mpvRx from ArchiveTune/lyrics (GPL-3.0),
 * revision fe895389128705a7653dadb4536e07efbaa4bbd5.
 */

package app.gyrolet.mpvrx.utils.media

import app.gyrolet.mpvrx.domain.lyrics.Lyrics
import app.gyrolet.mpvrx.domain.lyrics.LyricsSourceType
import app.gyrolet.mpvrx.domain.lyrics.SyncedLine
import app.gyrolet.mpvrx.domain.lyrics.SyncedWord
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

object EnhancedLyricsParser {
  private val qrcLineRegex = Regex("""^\[(\d{1,10}),(\d{1,10})](.*)$""")
  private val qrcWordRegex = Regex("""(.*?)\((\d{1,10}),(\d{1,10})(?:,\d{1,10})?\)""")
  private val qrcContentRegex = Regex("""LyricContent\s*=\s*"([\s\S]*?)"""", RegexOption.IGNORE_CASE)
  private val numericEntityRegex = Regex("""&#(x?[0-9A-Fa-f]+);""")
  private val whitespaceRegex = Regex("\s+")

  fun canParse(content: String): Boolean {
    val trimmed = content.trimStart()
    return trimmed.startsWith("<tt") ||
      trimmed.startsWith("<?xml") ||
      trimmed.contains("<QrcInfos", ignoreCase = true) ||
      trimmed.contains("LyricContent=", ignoreCase = true) ||
      trimmed.lineSequence().any { qrcLineRegex.matches(it.trim()) && qrcWordRegex.containsMatchIn(it) }
  }

  fun parse(
    content: String,
    sourceType: LyricsSourceType,
  ): Lyrics? =
    if (looksLikeTtml(content)) {
      parseTtml(content, sourceType)
    } else {
      parseQrc(content, sourceType)
    }

  private fun parseTtml(
    content: String,
    sourceType: LyricsSourceType,
  ): Lyrics? =
    runCatching {
      val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        setExpandEntityReferences(false)
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      }
      val document = factory.newDocumentBuilder().parse(InputSource(StringReader(content)))
      val root = document.documentElement
      val timing = TimingContext.from(root)
      val transliterations = readSupplementalText(root, "transliteration")
      val translations = readSupplementalText(root, "translation")
      val lines = mutableListOf<SyncedLine>()
      val elements = root.getElementsByTagName("*")

      for (index in 0 until elements.length) {
        val paragraph = elements.item(index) as? Element ?: continue
        if (!paragraph.localTagName().equals("p", ignoreCase = true)) continue
        val begin = paragraph.attributeBySuffix("begin") ?: continue
        val lineStart = parseTimeMs(begin, timing)
        val lineEnd =
          paragraph.attributeBySuffix("end")?.let { parseTimeMs(it, timing) }
            ?: paragraph.attributeBySuffix("dur")?.let { lineStart + parseTimeMs(it, timing) }
            ?: (lineStart + 5_000L)
        val words = mutableListOf<SyncedWord>()
        collectTimedWords(paragraph, lineStart, lineEnd, timing, words)
        val text = normalizeText(paragraph.textContent).takeIf(String::isNotBlank) ?: continue
        val key = paragraph.attributeBySuffix("key")
        val romanization = key?.let(transliterations::get)?.takeUnless { it.equals(text, ignoreCase = true) }
        val translation = key?.let(translations::get)?.takeUnless { it.equals(text, ignoreCase = true) }

        lines +=
          SyncedLine(
            time = lineStart.toSafeInt(),
            line = text,
            words = words.takeIf(List<SyncedWord>::isNotEmpty),
            translation = translation,
            romanization = romanization,
          )
      }

      val sorted = mergeDuplicateLines(lines.sortedBy(SyncedLine::time))
      Lyrics(
        plain = sorted.map(SyncedLine::line),
        synced = sorted,
        areFromRemote = sourceType == LyricsSourceType.ONLINE,
        sourceType = sourceType,
        isWordSynced = sorted.any { !it.words.isNullOrEmpty() },
      ).takeIf(Lyrics::isValid)
    }.getOrNull()

  private fun parseQrc(
    content: String,
    sourceType: LyricsSourceType,
  ): Lyrics? {
    val body =
      qrcContentRegex.find(content)?.groupValues?.get(1)?.let(::unescapeXml)
        ?: content
    val lines =
      body.lineSequence().mapNotNull { rawLine ->
        val match = qrcLineRegex.matchEntire(rawLine.trim()) ?: return@mapNotNull null
        val lineStart = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val lineDuration = match.groupValues[2].toLongOrNull() ?: 0L
        val words =
          qrcWordRegex.findAll(match.groupValues[3]).mapNotNull { wordMatch ->
            val text = wordMatch.groupValues[1].trim()
            val rawStart = wordMatch.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val wordTime = if (rawStart < lineStart && rawStart <= lineDuration) lineStart + rawStart else rawStart
            text.takeIf(String::isNotEmpty)?.let {
              SyncedWord(time = wordTime.toSafeInt(), word = it)
            }
          }.toList()
        val text = words.joinToString(separator = "", transform = SyncedWord::word).trim()
        if (text.isBlank()) return@mapNotNull null
        SyncedLine(
          time = lineStart.toSafeInt(),
          line = text,
          words = words.takeIf(List<SyncedWord>::isNotEmpty),
        )
      }.sortedBy(SyncedLine::time).toList()
    if (lines.isEmpty()) return null
    return Lyrics(
      plain = lines.map(SyncedLine::line),
      synced = lines,
      areFromRemote = sourceType == LyricsSourceType.ONLINE,
      sourceType = sourceType,
      isWordSynced = true,
    )
  }

  private fun collectTimedWords(
    element: Element,
    lineStart: Long,
    lineEnd: Long,
    timing: TimingContext,
    words: MutableList<SyncedWord>,
  ) {
    val children = element.childNodes
    for (index in 0 until children.length) {
      val child = children.item(index) as? Element ?: continue
      val begin = child.attributeBySuffix("begin")
      val end = child.attributeBySuffix("end")
      val duration = child.attributeBySuffix("dur")
      val isTimedSpan = child.localTagName().equals("span", ignoreCase = true) && begin != null && (end != null || duration != null)
      if (isTimedSpan) {
        val rawStart = parseTimeMs(begin, timing)
        val normalizedStart = normalizeChildTime(rawStart, lineStart, lineEnd)
        val text = normalizeText(child.textContent)
        if (text.isNotBlank()) {
          words += SyncedWord(time = normalizedStart.toSafeInt(), word = text)
        }
      } else {
        collectTimedWords(child, lineStart, lineEnd, timing, words)
      }
    }
  }

  private fun readSupplementalText(
    root: Element,
    containerName: String,
  ): Map<String, String> {
    val result = linkedMapOf<String, String>()
    val elements = root.getElementsByTagName("*")
    for (index in 0 until elements.length) {
      val container = elements.item(index) as? Element ?: continue
      if (!container.localTagName().equals(containerName, ignoreCase = true)) continue
      val descendants = container.getElementsByTagName("*")
      for (textIndex in 0 until descendants.length) {
        val textElement = descendants.item(textIndex) as? Element ?: continue
        if (!textElement.localTagName().equals("text", ignoreCase = true)) continue
        val key = textElement.attributeBySuffix("for") ?: continue
        normalizeText(textElement.textContent).takeIf(String::isNotBlank)?.let { result[key] = it }
      }
    }
    return result
  }

  private fun mergeDuplicateLines(lines: List<SyncedLine>): List<SyncedLine> {
    val merged = linkedMapOf<Int, SyncedLine>()
    lines.forEach { line ->
      val existing = merged[line.time]
      if (existing == null) {
        merged[line.time] = line
      } else if (existing.translation.isNullOrBlank() && !line.line.equals(existing.line, ignoreCase = true)) {
        merged[line.time] = existing.copy(translation = line.line)
      }
    }
    return merged.values.toList()
  }

  private fun normalizeChildTime(
    raw: Long,
    lineStart: Long,
    lineEnd: Long,
  ): Long {
    val lineDuration = (lineEnd - lineStart).coerceAtLeast(0L)
    val adjusted = if (raw < lineStart - 250L && raw <= lineDuration + 1_000L) lineStart + raw else raw
    return adjusted.coerceIn(lineStart.coerceAtLeast(0L), lineEnd.coerceAtLeast(lineStart))
  }

  private fun parseTimeMs(
    rawValue: String,
    timing: TimingContext,
  ): Long {
    val raw = rawValue.trim()
    Regex("""^([0-9]+(?:\.[0-9]+)?)(h|ms|m|s|f|t)$""", RegexOption.IGNORE_CASE)
      .matchEntire(raw)
      ?.let { match ->
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val seconds =
          when (match.groupValues[2].lowercase()) {
            "h" -> value * 3600.0
            "m" -> value * 60.0
            "s" -> value
            "ms" -> value / 1000.0
            "f" -> value / timing.frameRate
            "t" -> value / timing.tickRate
            else -> value
          }
        return (seconds * 1000.0).toLong()
      }

    val parts = raw.replace(';', ':').split(':')
    val seconds =
      when (parts.size) {
        2 -> (parts[0].toDoubleOrNull() ?: 0.0) * 60.0 + (parts[1].toDoubleOrNull() ?: 0.0)
        3 ->
          (parts[0].toDoubleOrNull() ?: 0.0) * 3600.0 +
            (parts[1].toDoubleOrNull() ?: 0.0) * 60.0 +
            (parts[2].toDoubleOrNull() ?: 0.0)
        4 ->
          (parts[0].toDoubleOrNull() ?: 0.0) * 3600.0 +
            (parts[1].toDoubleOrNull() ?: 0.0) * 60.0 +
            (parts[2].toDoubleOrNull() ?: 0.0) +
            (parts[3].toDoubleOrNull() ?: 0.0) / timing.frameRate
        else -> raw.toDoubleOrNull() ?: 0.0
      }
    return (seconds * 1000.0).toLong()
  }

  private fun Element.attributeBySuffix(suffix: String): String? {
    getAttribute(suffix).takeIf(String::isNotBlank)?.let { return it }
    val attributes = attributes ?: return null
    for (index in 0 until attributes.length) {
      val attribute = attributes.item(index) ?: continue
      if (attribute.nodeName.equals(suffix, ignoreCase = true) || attribute.nodeName.endsWith(":$suffix", ignoreCase = true)) {
        attribute.nodeValue?.trim()?.takeIf(String::isNotBlank)?.let { return it }
      }
    }
    return null
  }

  private fun Element.localTagName(): String = localName ?: tagName.substringAfter(':')

  private fun normalizeText(value: String): String = value.replace(whitespaceRegex, " ").trim()

  private fun unescapeXml(value: String): String =
    numericEntityRegex
      .replace(value) { match ->
        val raw = match.groupValues[1]
        val codePoint =
          if (raw.startsWith('x', ignoreCase = true)) raw.drop(1).toIntOrNull(16) else raw.toIntOrNull()
        codePoint?.takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) } ?: match.value
      }
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&amp;", "&")

  private fun looksLikeTtml(content: String): Boolean {
    val trimmed = content.trimStart()
    return trimmed.startsWith("<tt") || trimmed.startsWith("<?xml") && trimmed.contains("<tt")
  }

  private fun Long.toSafeInt(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

  private data class TimingContext(
    val tickRate: Double,
    val frameRate: Double,
  ) {
    companion object {
      fun from(root: Element): TimingContext {
        val frameRate = root.attributeBySuffix("frameRate")?.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 30.0
        val multiplier =
          root.attributeBySuffix("frameRateMultiplier")
            ?.split(Regex("\s+"))
            ?.mapNotNull(String::toDoubleOrNull)
            ?.takeIf { it.size == 2 && it[1] != 0.0 }
            ?.let { it[0] / it[1] }
            ?: 1.0
        val effectiveFrameRate = (frameRate * multiplier).coerceAtLeast(1.0)
        val tickRate = root.attributeBySuffix("tickRate")?.toDoubleOrNull()?.coerceAtLeast(1.0) ?: effectiveFrameRate
        return TimingContext(tickRate = tickRate, frameRate = effectiveFrameRate)
      }
    }
  }
}