package app.gyrolet.mpvrx.ui.mediainfo

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.theme.DarkMode
import app.gyrolet.mpvrx.ui.theme.MpvrxTheme
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.utils.clipboard.SafeClipboard
import app.gyrolet.mpvrx.utils.media.MediaInfoOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File

class MediaInfoActivity : ComponentActivity() {
  private val appearancePreferences by inject<AppearancePreferences>()

  override fun attachBaseContext(newBase: Context?) {
    if (newBase == null) {
      super.attachBaseContext(null)
      return
    }
    super.attachBaseContext(app.gyrolet.mpvrx.utils.LocaleHelper.wrapContext(newBase))
  }
  private val TAG = "MediaInfoActivity"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      val dark by appearancePreferences.darkMode.collectAsState()
      val isSystemInDarkTheme = isSystemInDarkTheme()
      val isDarkMode = dark == DarkMode.Dark || (dark == DarkMode.System && isSystemInDarkTheme)

      enableEdgeToEdge(
        SystemBarStyle.auto(
          lightScrim = Color.White.toArgb(),
          darkScrim = Color.Transparent.toArgb(),
        ) { isDarkMode },
      )

      MpvrxTheme {
        Surface {
          MediaInfoScreen(
            onBack = { finish() },
            isDarkMode = isDarkMode,
          )
        }
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun MediaInfoScreen(
    onBack: () -> Unit,
    isDarkMode: Boolean,
  ) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var textContent by remember { mutableStateOf<String?>(null) }
    var fullMediaInfoText by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("Media File") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var mediaInfo by remember { mutableStateOf<MediaInfoOps.MediaInfoData?>(null) }

    LaunchedEffect(Unit) {
      val uri = when (intent?.action) {
        Intent.ACTION_SEND -> {
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
          } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
          }
        }

        Intent.ACTION_VIEW -> {
          intent.data
        }

        else -> null
      }

      if (uri == null) {
        error = context.getString(R.string.media_info_no_file)
        isLoading = false
        return@LaunchedEffect
      }

      fileUri = uri

      // Get the file name
      fileName = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
          if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex) ?: uri.lastPathSegment ?: "Unknown"
          } else {
            uri.lastPathSegment ?: "Unknown"
          }
        } ?: uri.lastPathSegment ?: "Unknown"
      } catch (e: Exception) {
        Log.e(TAG, "Error getting file name", e)
        uri.lastPathSegment ?: "Unknown"
      }

      // Load media info
      scope.launch {
        try {
          val result = MediaInfoOps.getMediaInfo(context, uri, fileName)
          result.onSuccess { mediaInfoResult ->
            mediaInfo = mediaInfoResult

            // Also generate text content for sharing/copying
            val textResult = MediaInfoOps.generateTextOutput(context, uri, fileName)
            textResult.onSuccess { text ->
              textContent = text
              fullMediaInfoText = text
            }

            isLoading = false
          }.onFailure { e ->
            error = e.message ?: context.getString(R.string.media_info_load_failed)
            isLoading = false
          }
        } catch (e: Exception) {
          error = e.message ?: context.getString(R.string.error_unknown)
          isLoading = false
        }
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = stringResource(R.string.media_info_screen_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          },
          navigationIcon = {
            IconButton(onClick = onBack) {
              Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
          },
          actions = {
            if (!isLoading && error == null && textContent != null) {
              Row(modifier = Modifier.padding(end = 12.dp)) {
                FilledTonalIconButton(
                  onClick = {
                    scope.launch {
                      copyToClipboard(textContent!!, fileName)
                    }
                  },
                  colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  ),
                ) {
                  Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy),
                  )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalIconButton(
                  onClick = {
                    scope.launch {
                      shareMediaInfo(textContent!!, fileName, fileUri)
                    }
                  },
                  colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  ),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.cd_share),
                  )
                }
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
        )
      },
    ) { padding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
      ) {
        when {
          isLoading -> LoadingContent()
          error != null -> ErrorContent(error!!)
          mediaInfo != null -> MediaInfoContent(mediaInfo!!, fileName, fullMediaInfoText)
        }
      }
    }
  }

  @Composable
  private fun LoadingContent() {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        CircularProgressIndicator(
          color = MaterialTheme.colorScheme.primary,
          strokeWidth = 4.dp,
          modifier = Modifier.size(48.dp),
        )
        Text(
          text = stringResource(R.string.media_info_analyzing),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  @Composable
  private fun ErrorContent(errorMessage: String) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text(
          text = stringResource(R.string.media_info_error_prefix, errorMessage),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(24.dp),
        )
      }
    }
  }

  enum class InfoTab(@StringRes val titleRes: Int) {
    OVERVIEW(R.string.media_info_tab_overview),
    VIDEO(R.string.media_info_tab_video),
    AUDIO(R.string.media_info_tab_audio),
    SUBTITLES(R.string.media_info_tab_subtitles),
    CHAPTERS(R.string.media_info_tab_chapters)
  }

  @Composable
  private fun MediaInfoContent(
    mediaInfo: MediaInfoOps.MediaInfoData,
    fileName: String,
    fullMediaInfoText: String?
  ) {
    if (fullMediaInfoText == null) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
      return
    }

    val sections = remember(fullMediaInfoText) { parseMediaInfoText(fullMediaInfoText) }

    // Group sections dynamically
    val videoSections = remember(sections) { sections.filter { it.name.startsWith("Video", ignoreCase = true) } }
    val audioSections = remember(sections) { sections.filter { it.name.startsWith("Audio", ignoreCase = true) } }
    val subtitleSections = remember(sections) {
      sections.filter {
        it.name.startsWith("Text", ignoreCase = true) ||
        it.name.startsWith("Subtitle", ignoreCase = true)
      }
    }
    val menuSections = remember(sections) {
      sections.filter {
        it.name.equals("Menu", ignoreCase = true) ||
        it.name.startsWith("Chapter", ignoreCase = true)
      }
    }

    // Determine available tabs
    val availableTabs = remember(sections) {
      buildList {
        add(InfoTab.OVERVIEW)
        if (videoSections.isNotEmpty()) add(InfoTab.VIDEO)
        if (audioSections.isNotEmpty()) add(InfoTab.AUDIO)
        if (subtitleSections.isNotEmpty()) add(InfoTab.SUBTITLES)
        if (menuSections.isNotEmpty()) add(InfoTab.CHAPTERS)
      }
    }

    var selectedTab by remember(availableTabs) { mutableStateOf(InfoTab.OVERVIEW) }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(
          brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
              MaterialTheme.colorScheme.background,
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
          )
        )
    ) {
      // Tab Content
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
      ) {
        when (selectedTab) {
          InfoTab.OVERVIEW -> OverviewTabContent(
            mediaInfo,
            fileName,
            sections,
            videoSections.size,
            audioSections.size,
            subtitleSections.size,
            menuSections.firstOrNull()?.properties?.size ?: 0
          )
          InfoTab.VIDEO -> StreamTabContent(videoSections, stringResource(R.string.media_info_video_stream))
          InfoTab.AUDIO -> StreamTabContent(audioSections, stringResource(R.string.media_info_audio_stream))
          InfoTab.SUBTITLES -> StreamTabContent(subtitleSections, stringResource(R.string.media_info_subtitle_track))
          InfoTab.CHAPTERS -> ChaptersTabContent(menuSections)
        }
      }

      // Horizontal Scrollable Inspired Tab Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        availableTabs.forEach { tab ->
          val isSelected = selectedTab == tab
          val containerColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            label = "TabContainerColor"
          )
          val contentColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "TabContentColor"
          )

          Surface(
            onClick = { selectedTab = tab },
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.animateContentSize(),
            border = BorderStroke(
              1.dp,
              if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              val icon = when (tab) {
                InfoTab.OVERVIEW -> Icons.Filled.Info
                InfoTab.VIDEO -> Icons.Default.Videocam
                InfoTab.AUDIO -> Icons.Default.VolumeUp
                InfoTab.SUBTITLES -> Icons.Default.Subtitles
                InfoTab.CHAPTERS -> Icons.Default.ViewList
              }
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(tab.titleRes),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
              )
            }
          }
        }
      }
    }
  }

  @Composable
  private fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    content: @Composable ColumnScope.() -> Unit
  ) {
    Card(
      modifier = modifier,
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = BorderStroke(1.dp, borderColor),
      content = content
    )
  }

  @Composable
  private fun QuickStatCard(
    title: String,
    value: String,
    icon: app.gyrolet.mpvrx.ui.icons.AppIcon,
    accentColor: Color,
    modifier: Modifier = Modifier
  ) {
    val context = LocalContext.current
    GlassmorphicCard(
      modifier = modifier.clickable {
        SafeClipboard.copyPlainText(context, title, value)
        Toast.makeText(context, context.getString(R.string.media_info_copied_value_toast, value), Toast.LENGTH_SHORT).show()
      },
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
    ) {
      Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = accentColor.copy(alpha = 0.12f),
          contentColor = accentColor,
          modifier = Modifier.size(40.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accentColor)
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }

  @Composable
  private fun HeroChipRow(chips: List<String>) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      chips.forEach { label ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
          contentColor = MaterialTheme.colorScheme.primary,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
          Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
          )
        }
      }
    }
  }

  @Composable
  private fun OverviewTabContent(
    mediaInfo: MediaInfoOps.MediaInfoData,
    fileName: String,
    sections: List<InfoSection>,
    videoCount: Int,
    audioCount: Int,
    subtitleCount: Int,
    chapterCount: Int
  ) {
    // Quick Stat values
    val context = LocalContext.current
    val primaryVideo = mediaInfo.videoStreams.firstOrNull()
    val resolutionLabel = remember(primaryVideo) {
      if (primaryVideo != null) {
        val w = primaryVideo.width.filter { it.isDigit() }
        val h = primaryVideo.height.filter { it.isDigit() }
        if (h == "2160" || w == "3840") context.getString(R.string.media_info_res_4k)
        else if (h == "1440" || w == "2560") context.getString(R.string.media_info_res_2k)
        else if (h == "1080") context.getString(R.string.media_info_res_1080p)
        else if (h == "720") context.getString(R.string.media_info_res_720p)
        else if (w.isNotEmpty() && h.isNotEmpty()) "${w}x${h}"
        else context.getString(R.string.media_info_unknown)
      } else context.getString(R.string.media_info_no_video)
    }

    val sizeLabel = mediaInfo.general.fileSize.ifBlank { context.getString(R.string.media_info_unknown) }
    val durationLabel = mediaInfo.general.duration.ifBlank { context.getString(R.string.media_info_unknown) }
    val formatLabel = mediaInfo.general.format.ifBlank { context.getString(R.string.media_info_unknown) }

    val heroChips = remember(mediaInfo, context) {
      buildList {
        primaryVideo?.let { v ->
          val w = v.width.filter { it.isDigit() }.toIntOrNull() ?: 0
          val h = v.height.filter { it.isDigit() }.toIntOrNull() ?: 0
          val res = when {
            w >= 3840 || h >= 2160 -> context.getString(R.string.media_info_hero_res_4k)
            w >= 2560 || h >= 1440 -> context.getString(R.string.media_info_hero_res_2k)
            w >= 1920 || h >= 1080 -> context.getString(R.string.media_info_hero_res_1080p)
            w >= 1280 || h >= 720  -> context.getString(R.string.media_info_hero_res_720p)
            else -> null
          }
          res?.let { add(it) }
          if (v.format.isNotBlank() && v.format != "---") add(v.format)
        }
        mediaInfo.audioStreams.firstOrNull()?.let { a ->
          if (a.format.isNotBlank() && a.format != "---") add(a.format)
        }
        val unknown = context.getString(R.string.media_info_unknown)
        if (sizeLabel != unknown) add(sizeLabel)
        if (durationLabel != unknown) add(durationLabel)
      }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Neon Header Banner
      GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
          ) {
            Text(
              text = formatLabel.uppercase(),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }

          Text(
            text = fileName,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Hero summary chips inline row
      if (heroChips.isNotEmpty()) {
        HeroChipRow(heroChips)
      }

      // Quick Specs Grid (2 columns)
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          QuickStatCard(
            title = context.getString(R.string.media_info_quick_stat_resolution),
            value = resolutionLabel,
            icon = Icons.Default.Videocam,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
          )
          QuickStatCard(
            title = context.getString(R.string.media_info_quick_stat_file_size),
            value = sizeLabel,
            icon = Icons.Default.SdCard,
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          QuickStatCard(
            title = context.getString(R.string.media_info_quick_stat_duration),
            value = durationLabel,
            icon = Icons.Outlined.Timer,
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
          )
          QuickStatCard(
            title = context.getString(R.string.media_info_quick_stat_bitrate),
            value = mediaInfo.general.overallBitRate.ifBlank { context.getString(R.string.media_info_unknown) },
            icon = Icons.Default.Speed,
            accentColor = Color(0xFFFFB300),
            modifier = Modifier.weight(1f)
          )
        }
      }

      // Stream summary blocks
      GlassmorphicCard(
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(
            text = context.getString(R.string.media_info_tracks_summary),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            TrackSummaryItem(videoCount, context.getString(R.string.media_info_tab_video), Icons.Default.Videocam, MaterialTheme.colorScheme.primary)
            TrackSummaryItem(audioCount, context.getString(R.string.media_info_tab_audio), Icons.Default.VolumeUp, MaterialTheme.colorScheme.secondary)
            TrackSummaryItem(subtitleCount, context.getString(R.string.media_info_tab_subtitles), Icons.Default.Subtitles, MaterialTheme.colorScheme.tertiary)
            if (chapterCount > 0) {
              TrackSummaryItem(chapterCount, context.getString(R.string.media_info_tab_chapters), Icons.Default.ViewList, Color(0xFFFFB300))
            }
          }
        }
      }

      // Detailed metadata list
      val generalSection = sections.firstOrNull { it.name.equals("General", ignoreCase = true) }
      if (generalSection != null) {
        GlassmorphicCard(
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text(
              text = context.getString(R.string.media_info_container_metadata),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )

            SelectionContainer {
              Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                generalSection.properties.forEach { (key, value) ->
                  // Avoid showing details already on Quick Stats to keep clean
                  if (key != "Format" && key != "File size" && key != "Duration" && key != "Overall bit rate") {
                    PropertyRow(key, value)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun TrackSummaryItem(
    count: Int,
    label: String,
    icon: app.gyrolet.mpvrx.ui.icons.AppIcon,
    color: Color
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        modifier = Modifier.size(48.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
        }
      }
      Text(
        text = "$count $label",
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }

  @Composable
  private fun StreamCard(
    title: String,
    badge: String?,
    icon: app.gyrolet.mpvrx.ui.icons.AppIcon,
    headerBgColor: Color,
    headerTextColor: Color,
    properties: List<Pair<String, String>>,
    modifier: Modifier = Modifier
  ) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    GlassmorphicCard(
      modifier = modifier.fillMaxWidth()
    ) {
      Column {
        // Dynamic strip header representing the track class, inspired by mpvFlux cards
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(headerBgColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .background(
                color = headerTextColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp)
              ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = headerTextColor)
          }
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = headerTextColor,
            modifier = Modifier.weight(1f)
          )
          if (badge != null) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = headerTextColor.copy(alpha = 0.15f)
            ) {
              Text(
                text = badge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = headerTextColor
              )
            }
          }

          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(headerTextColor.copy(alpha = 0.15f))
              .clickable {
                scope.launch {
                  val content = properties.joinToString("\n") { "${it.first}: ${it.second}" }
                  SafeClipboard.copyPlainText(context, title, content)
                  Toast.makeText(context, context.getString(R.string.media_info_copied_specs_toast), Toast.LENGTH_SHORT).show()
                }
              },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.ContentCopy,
              contentDescription = stringResource(R.string.cd_copy_all),
              tint = headerTextColor.copy(alpha = 0.8f),
              modifier = Modifier.size(16.dp)
            )
          }
        }

        // Two-column chunked Stat Tiles inspired by the premium mpvFlux UI
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val chunked = properties.chunked(2)
          chunked.forEach { pair ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              pair.forEach { (label, value) ->
                StatTile(
                  label = label,
                  value = value,
                  modifier = Modifier.weight(1f)
                )
              }
              if (pair.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
  ) {
    val context = LocalContext.current
    Surface(
      modifier = modifier.clickable {
        SafeClipboard.copyPlainText(context, label, value)
        Toast.makeText(context, context.getString(R.string.media_info_copied_value_toast, value), Toast.LENGTH_SHORT).show()
      },
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = value,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          ),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }

  @Composable
  private fun StreamTabContent(
    sections: List<InfoSection>,
    streamTypeLabel: String
  ) {
    val context = LocalContext.current
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      sections.forEachIndexed { index, section ->
        val format = section.properties.firstOrNull { it.first.equals("Format", ignoreCase = true) }?.second ?: context.getString(R.string.media_info_unknown)
        val language = section.properties.firstOrNull { it.first.equals("Language", ignoreCase = true) }?.second

        val badgeLabel = if (language != null) "$format ($language)" else format

        val (headerBgColor, headerTextColor, icon) = when {
          streamTypeLabel.contains("Video", ignoreCase = true) -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.Videocam
          )
          streamTypeLabel.contains("Audio", ignoreCase = true) -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.VolumeUp
          )
          else -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Default.Subtitles
          )
        }

        val filteredProperties = section.properties.filter { it.first != "Format" }

        StreamCard(
          title = "$streamTypeLabel #${index + 1}",
          badge = badgeLabel,
          icon = icon,
          headerBgColor = headerBgColor,
          headerTextColor = headerTextColor,
          properties = filteredProperties
        )
      }
    }
  }

  @Composable
  private fun ChaptersTabContent(
    sections: List<InfoSection>
  ) {
    val context = LocalContext.current
    val menuSection = sections.firstOrNull() ?: return

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      GlassmorphicCard(
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(
            text = context.getString(R.string.media_info_chapters_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )

          Column(
            modifier = Modifier.padding(start = 8.dp)
          ) {
            menuSection.properties.forEachIndexed { index, (timestamp, rawName) ->
              val context = LocalContext.current
              val scope = rememberCoroutineScope()

              // Clean chapter name: strip leading ": en:" / ": " language prefix artifacts
              val chapterName = rawName
                .trimStart()
                .removePrefix(":")
                .trimStart()
                .let { s ->
                  // Strip "en:" / "und:" / "jpn:" etc. language tag if present at start
                  val langTagRegex = Regex("^[a-z]{2,3}:")
                  if (s.matches(Regex("^[a-z]{2,3}:.*"))) s.replaceFirst(langTagRegex, "").trimStart()
                  else s
                }
                .ifBlank { context.getString(R.string.media_info_chapter_default, index + 1) }

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(IntrinsicSize.Min)
                  .clickable {
                    scope.launch {
                      SafeClipboard.copyPlainText(context, "Chapter timestamp", timestamp)
                      Toast.makeText(context, context.getString(R.string.media_info_copied_value_toast, timestamp), Toast.LENGTH_SHORT).show()
                    }
                  }
                  .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top
              ) {
                // Connected timeline — dot + line that dynamically fills row height
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                ) {
                  // Top padding so dot lines up with the chapter name text
                  Spacer(modifier = Modifier.height(3.dp))
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.size(12.dp)
                  ) {}

                  if (index < menuSection.properties.size - 1) {
                    Spacer(
                      modifier = Modifier
                        .width(2.dp)
                        .weight(1f)  // stretches to fill remaining row height
                        .background(
                          brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                              MaterialTheme.colorScheme.primary,
                              MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                            )
                          )
                        )
                    )
                  }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Chapter name + timestamp chip stacked vertically
                Column(
                  modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 10.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text(
                    text = chapterName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                  ) {
                    Text(
                      text = timestamp,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                      style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                      ),
                      color = MaterialTheme.colorScheme.primary
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun PropertyRow(label: String, value: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable {
          scope.launch {
            SafeClipboard.copyPlainText(context, label, value)
            Toast.makeText(context, context.getString(R.string.media_info_copied_value_toast, value), Toast.LENGTH_SHORT).show()
          }
        }
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .weight(1f)
          .padding(end = 12.dp),
      )

      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium.copy(
          fontFamily = FontFamily.Monospace
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1.5f),
      )
    }
  }

  private fun parseMediaInfoText(text: String): List<InfoSection> {
    val sections = mutableListOf<InfoSection>()
    var currentName: String? = null
    val currentProps = mutableListOf<Pair<String, String>>()
    text.lines().forEach { line ->
      val trimmed = line.trim()
      when {
        trimmed.isEmpty() || trimmed.startsWith("=") || line.contains("MEDIA INFO") -> {}
        !line.startsWith(" ") && !line.contains(" : ") -> {
          if (currentName != null && currentProps.isNotEmpty()) {
            sections.add(InfoSection(currentName, currentProps.toList()))
          }
          currentName = trimmed
          currentProps.clear()
        }
        line.contains(" : ") -> {
          val parts = line.split(" : ", limit = 2)
          if (parts.size == 2 && parts[0].trim().isNotEmpty() && parts[1].trim().isNotEmpty()) {
            currentProps.add(parts[0].trim() to parts[1].trim())
          }
        }
      }
    }
    if (currentName != null && currentProps.isNotEmpty()) {
      sections.add(InfoSection(currentName, currentProps.toList()))
    }
    return sections
  }

  private data class InfoSection(
    val name: String,
    val properties: List<Pair<String, String>>,
  )

  private suspend fun copyToClipboard(content: String, fileName: String) {
    withContext(Dispatchers.Main) {
      SafeClipboard.copyPlainText(
        context = this@MediaInfoActivity,
        label = "Media Info - $fileName",
        text = content,
      )
    }
  }

  private suspend fun shareMediaInfo(content: String, fileName: String, mediaUri: Uri?) {
    withContext(Dispatchers.IO) {
      try {
        val textFileName = "mediainfo_${fileName.substringBeforeLast('.')}.txt"
        val file = File(cacheDir, textFileName)
        file.writeText(content)

        withContext(Dispatchers.Main) {
          val fileUri = FileProvider.getUriForFile(
            this@MediaInfoActivity,
            "${packageName}.provider",
            file,
          )

          val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "Media Info - $fileName")
            putExtra(Intent.EXTRA_TEXT, "Media information for: $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }

          startActivity(Intent.createChooser(shareIntent, this@MediaInfoActivity.getString(R.string.media_info_share_chooser_title)))
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          Toast.makeText(
            this@MediaInfoActivity,
            this@MediaInfoActivity.getString(R.string.media_info_share_failed_toast, e.message ?: ""),
            Toast.LENGTH_LONG,
          ).show()
        }
      }
    }
  }
}
