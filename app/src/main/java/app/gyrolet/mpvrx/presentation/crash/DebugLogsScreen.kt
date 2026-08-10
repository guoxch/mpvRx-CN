/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.presentation.crash

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.utils.clipboard.SafeClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val DEBUG_LOG_POLL_INTERVAL_MS = 1_500L
private const val DEBUG_LOG_ENTRY_LIMIT = 1_000

private enum class DebugLogLevel(
  val code: String,
  val label: String,
) {
  Verbose("V", "Verbose"),
  Debug("D", "Debug"),
  Info("I", "Info"),
  Warn("W", "Warn"),
  Error("E", "Error"),
}

private data class DebugLogEntry(
  val timeMillis: Long,
  val timestamp: String,
  val level: DebugLogLevel,
  val tag: String,
  val message: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebugLogsScreen(onNavigateBack: () -> Unit) {
  val context = LocalContext.current
  val listState = rememberLazyListState()
  var query by remember { mutableStateOf("") }
  var selectedLevels by remember { mutableStateOf(DebugLogLevel.entries.toSet()) }
  var entries by remember { mutableStateOf<List<DebugLogEntry>>(emptyList()) }
  var isPaused by remember { mutableStateOf(false) }
  var menuExpanded by remember { mutableStateOf(false) }
  var clearedAt by remember { mutableStateOf(0L) }
  var readError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(isPaused, clearedAt) {
    while (isActive) {
      if (!isPaused) {
        val result = withContext(Dispatchers.IO) { runCatching { readAppLogcat() } }
        result
          .onSuccess { latest ->
            entries = latest.filter { it.timeMillis >= clearedAt }
            readError = null
          }.onFailure { error ->
            readError = error.message ?: "Unable to read app logs"
          }
      }
      delay(DEBUG_LOG_POLL_INTERVAL_MS)
    }
  }

  val filteredEntries =
    remember(entries, query, selectedLevels) {
      val needle = query.trim()
      entries.filter { entry ->
        entry.level in selectedLevels &&
          (needle.isEmpty() ||
            entry.tag.contains(needle, ignoreCase = true) ||
            entry.message.contains(needle, ignoreCase = true))
      }
    }

  LaunchedEffect(filteredEntries.size, isPaused) {
    if (!isPaused && filteredEntries.isNotEmpty()) {
      listState.scrollToItem(filteredEntries.lastIndex)
    }
  }

  fun visibleText(): String = formatDebugLogs(filteredEntries)

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Debug Logs", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              text =
                if (query.isBlank() && selectedLevels.size == DebugLogLevel.entries.size) {
                  "All"
                } else {
                  "${filteredEntries.size} visible"
                },
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.RoundedFilled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { isPaused = !isPaused }) {
            Icon(
              imageVector = if (isPaused) Icons.RoundedFilled.PlayArrow else Icons.RoundedFilled.Pause,
              contentDescription = if (isPaused) "Resume logs" else "Pause logs",
            )
          }
          Box {
            IconButton(onClick = { menuExpanded = true }) {
              Icon(Icons.RoundedFilled.MoreVert, contentDescription = "Log options")
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false },
            ) {
              DropdownMenuItem(
                text = { Text("Share visible logs") },
                leadingIcon = { Icon(Icons.RoundedFilled.Share, contentDescription = null) },
                enabled = filteredEntries.isNotEmpty(),
                onClick = {
                  menuExpanded = false
                  shareDebugLogText(context, visibleText())
                },
              )
              DropdownMenuItem(
                text = { Text("Export visible logs") },
                leadingIcon = { Icon(Icons.RoundedFilled.Download, contentDescription = null) },
                enabled = filteredEntries.isNotEmpty(),
                onClick = {
                  menuExpanded = false
                  exportDebugLogText(context, visibleText())
                },
              )
              DropdownMenuItem(
                text = { Text("Copy visible logs") },
                leadingIcon = { Icon(Icons.RoundedFilled.ContentCopy, contentDescription = null) },
                enabled = filteredEntries.isNotEmpty(),
                onClick = {
                  menuExpanded = false
                  SafeClipboard.copyPlainText(
                    context = context,
                    label = "mpvrx_debug_logs",
                    text = visibleText(),
                  )
                },
              )
              DropdownMenuItem(
                text = { Text("Clear") },
                leadingIcon = { Icon(Icons.RoundedFilled.Clear, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  clearedAt = System.currentTimeMillis()
                  entries = emptyList()
                },
              )
            }
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier =
          Modifier
            .widthIn(max = 900.dp)
            .fillMaxSize(),
      ) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          placeholder = { Text("Search") },
          leadingIcon = { Icon(Icons.RoundedFilled.Search, contentDescription = null) },
          trailingIcon = {
            if (query.isNotEmpty()) {
              IconButton(onClick = { query = "" }) {
                Icon(Icons.RoundedFilled.Close, contentDescription = "Clear search")
              }
            }
          },
        )

        Spacer(Modifier.height(8.dp))

        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DebugLogLevel.entries.forEach { level ->
            FilterChip(
              selected = level in selectedLevels,
              onClick = {
                selectedLevels =
                  if (level in selectedLevels) selectedLevels - level else selectedLevels + level
              },
              label = { Text(level.label) },
              leadingIcon = { DebugLogLevelBadge(level) },
            )
          }
        }

        when {
          readError != null && entries.isEmpty() -> {
            DebugLogMessageState(readError.orEmpty(), isError = true)
          }
          filteredEntries.isEmpty() -> {
            DebugLogMessageState(if (isPaused) "Logs paused" else "No matching logs")
          }
          else -> {
            LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              // Do not provide a content key here: Android can emit multiple identical log lines
              // in the same millisecond, and index identity correctly preserves those duplicates.
              items(filteredEntries) { entry ->
                DebugLogEntryCard(entry)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DebugLogMessageState(
  text: String,
  isError: Boolean = false,
) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
private fun DebugLogEntryCard(entry: DebugLogEntry) {
  val levelColor = debugLogLevelColor(entry.level)
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        DebugLogLevelBadge(entry.level)
        Text(
          text = entry.timestamp,
          style = MaterialTheme.typography.labelMedium,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.tag.isNotBlank()) {
          Text(
            text = entry.tag,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = levelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Text(
        text = entry.message,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = levelColor,
      )
    }
  }
}

@Composable
private fun DebugLogLevelBadge(level: DebugLogLevel) {
  Surface(
    modifier = Modifier.size(28.dp),
    shape = RoundedCornerShape(8.dp),
    color = debugLogLevelColor(level),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = level.code,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.surface,
      )
    }
  }
}

@Composable
private fun debugLogLevelColor(level: DebugLogLevel) =
  when (level) {
    DebugLogLevel.Verbose -> MaterialTheme.colorScheme.onSurfaceVariant
    DebugLogLevel.Debug -> MaterialTheme.colorScheme.secondary
    DebugLogLevel.Info -> MaterialTheme.colorScheme.primary
    DebugLogLevel.Warn -> MaterialTheme.colorScheme.tertiary
    DebugLogLevel.Error -> MaterialTheme.colorScheme.error
  }

private fun readAppLogcat(): List<DebugLogEntry> {
  val logcatProcess =
    ProcessBuilder(
      "logcat",
      "--pid=${Process.myPid()}",
      "-v",
      "time",
      "-t",
      DEBUG_LOG_ENTRY_LIMIT.toString(),
    ).redirectErrorStream(true)
      .start()

  return try {
    val entries = ArrayList<DebugLogEntry>(DEBUG_LOG_ENTRY_LIMIT)
    BufferedReader(InputStreamReader(logcatProcess.inputStream)).use { reader ->
      reader.forEachLine { line ->
        parseDebugLogLine(line)?.let(entries::add)
      }
    }
    val exitCode = logcatProcess.waitFor()
    if (exitCode != 0) error("logcat exited with code $exitCode")
    entries
  } finally {
    logcatProcess.destroy()
  }
}

private fun parseDebugLogLine(line: String): DebugLogEntry? {
  val classicPattern =
    Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^\(]+)\(\s*\d+\):\s?(.*)$""")
  val threadTimePattern =
    Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+):\s?(.*)$""")
  val match = classicPattern.matchEntire(line) ?: threadTimePattern.matchEntire(line) ?: return null
  val timestamp = match.groupValues[1]
  val level =
    when (match.groupValues[2]) {
      "V" -> DebugLogLevel.Verbose
      "D" -> DebugLogLevel.Debug
      "I" -> DebugLogLevel.Info
      "W" -> DebugLogLevel.Warn
      "E", "F" -> DebugLogLevel.Error
      else -> return null
    }
  val timeMillis = parseDebugLogTimestamp(timestamp) ?: return null
  return DebugLogEntry(
    timeMillis = timeMillis,
    timestamp = timestamp.substringAfter(' '),
    level = level,
    tag = match.groupValues[3].trim(),
    message = match.groupValues[4],
  )
}

private fun parseDebugLogTimestamp(timestamp: String): Long? =
  runCatching {
    val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    val parsed = formatter.parse(timestamp) ?: return@runCatching null
    Calendar
      .getInstance()
      .apply {
        time = parsed
        set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
      }.timeInMillis
  }.getOrNull()

private fun formatDebugLogs(entries: List<DebugLogEntry>): String =
  entries.joinToString(separator = "\n") { entry ->
    "${entry.timestamp} ${entry.level.code}/${entry.tag}: ${entry.message}"
  }

private fun shareDebugLogText(
  context: Context,
  text: String,
) {
  if (text.isBlank()) return
  context.startActivity(
    Intent.createChooser(
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
      },
      "Share debug logs",
    ),
  )
}

private fun exportDebugLogText(
  context: Context,
  text: String,
) {
  if (text.isBlank()) return
  val file = File(context.cacheDir, "mpvrx-debug-${System.currentTimeMillis()}.txt")
  file.writeText(text)
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
  context.startActivity(
    Intent.createChooser(
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      },
      "Export debug logs",
    ),
  )
}
