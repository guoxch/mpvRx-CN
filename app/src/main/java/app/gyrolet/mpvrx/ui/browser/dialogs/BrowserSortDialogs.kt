package app.gyrolet.mpvrx.ui.browser.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.preferences.FolderSortType
import app.gyrolet.mpvrx.preferences.FolderViewMode
import app.gyrolet.mpvrx.preferences.MediaLayoutMode
import app.gyrolet.mpvrx.preferences.SortOrder
import app.gyrolet.mpvrx.preferences.VideoSortType
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.icons.Icons
import org.koin.compose.koinInject

@Composable
fun FolderSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  sortType: FolderSortType,
  sortOrder: SortOrder,
  onSortTypeChange: (FolderSortType) -> Unit,
  onSortOrderChange: (SortOrder) -> Unit,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalDurationChip by browserPreferences.showTotalDurationChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val showDateChip by browserPreferences.showDateChip.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val showFolderThumbnails by browserPreferences.showFolderThumbnails.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val centerGridTitles by browserPreferences.centerGridTitles.collectAsState()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val manualGridColumnsEnabled by browserPreferences.manualGridColumnsEnabled.collectAsState()
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
  val isTablet = configuration.smallestScreenWidthDp >= 600
  val maxColumns = if (isTablet || isLandscape) 8 else 4

  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
  val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait

  val screenWidthDp = configuration.screenWidthDp.dp
  val contentHorizontalPadding = 8.dp
  val itemSpacing = 2.dp
  val usableWidth = screenWidthDp - (contentHorizontalPadding * 2) - itemSpacing
  val folderMinWidth = 100.dp
  val videoMinWidth = 130.dp
  val dynamicFolderColumns = (usableWidth / folderMinWidth).toInt().coerceIn(1, maxColumns)
  val dynamicVideoColumns = (usableWidth / videoMinWidth).toInt().coerceIn(1, maxColumns)

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID && manualGridColumnsEnabled) {
    GridColumnSelector(
      label = stringResource(R.string.sort_folder_grid_columns) + " (${if (isLandscape) stringResource(R.string.sort_landscape) else stringResource(R.string.sort_portrait)})",
      currentValue = folderGridColumns.coerceIn(1, maxColumns),
      onValueChange = {
        if (isLandscape) browserPreferences.folderGridColumnsLandscape.set(it)
        else browserPreferences.folderGridColumnsPortrait.set(it)
      },
      valueRange = 1f..maxColumns.toFloat(),
      steps = maxColumns - 2,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID && manualGridColumnsEnabled) {
    GridColumnSelector(
      label = stringResource(R.string.sort_video_grid_columns) + " (${if (isLandscape) stringResource(R.string.sort_landscape) else stringResource(R.string.sort_portrait)})",
      currentValue = videoGridColumns.coerceIn(1, maxColumns),
      onValueChange = {
        if (isLandscape) browserPreferences.videoGridColumnsLandscape.set(it)
        else browserPreferences.videoGridColumnsPortrait.set(it)
      },
      valueRange = 1f..maxColumns.toFloat(),
      steps = maxColumns - 2,
    )
  } else null

  val isAlbumView = folderViewMode == FolderViewMode.AlbumView

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = if (isAlbumView) stringResource(R.string.sort_view_options_title) else stringResource(R.string.view_options_title),
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      FolderSortType.entries
        .find { it.displayName == typeName }
        ?.let(onSortTypeChange)
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      onSortOrderChange(if (isAsc) SortOrder.Ascending else SortOrder.Descending)
    },
    types = listOf(
      FolderSortType.Title.displayName,
      FolderSortType.Date.displayName,
      FolderSortType.Size.displayName,
    ),
    icons = listOf(
      Icons.Filled.Title,
      Icons.Filled.CalendarToday,
      Icons.Filled.SwapVert,
    ),
    getLabelForType = { type, _ ->
      when (type) {
        FolderSortType.Title.displayName -> Pair(stringResource(R.string.sort_order_asc_alpha), stringResource(R.string.sort_order_desc_alpha))
        FolderSortType.Date.displayName -> Pair(stringResource(R.string.sort_order_oldest), stringResource(R.string.sort_order_newest))
        FolderSortType.Size.displayName -> Pair(stringResource(R.string.sort_order_smallest), stringResource(R.string.sort_order_largest))
        else -> Pair(stringResource(R.string.sort_order_asc), stringResource(R.string.sort_order_desc))
      }
    },
    showSortOptions = isAlbumView,
    viewModeSelector = MultiViewModeSelector(
      label = stringResource(R.string.sort_view_mode_label),
      options = listOf(
        ViewModeOption(
          label = stringResource(R.string.sort_view_mode_folder),
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.AlbumView) }
        ),
        ViewModeOption(
          label = stringResource(R.string.sort_view_mode_tree),
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.FileManager) }
        ),
        ViewModeOption(
          label = stringResource(R.string.sort_view_mode_library),
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary) }
        ),
      )
    ),
    layoutModeSelector = ViewModeSelector(
      label = stringResource(R.string.sort_layout_label),
      firstOptionLabel = stringResource(R.string.sort_layout_list),
      secondOptionLabel = stringResource(R.string.sort_layout_grid),
      firstOptionIcon = Icons.Filled.ViewList,
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = mediaLayoutMode == MediaLayoutMode.LIST,
      onViewModeChange = { isFirstOption ->
        browserPreferences.mediaLayoutMode.set(
          if (isFirstOption) MediaLayoutMode.LIST else MediaLayoutMode.GRID
        )
      },
    ),
    visibilityToggles = buildList {
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_full_name),
          checked = unlimitedNameLines,
          onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_path),
          checked = showFolderPath,
          onCheckedChange = { browserPreferences.showFolderPath.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_total_videos),
          checked = showTotalVideosChip,
          onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_total_duration),
          checked = showTotalDurationChip,
          onCheckedChange = { browserPreferences.showTotalDurationChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_folder_size),
          checked = showTotalSizeChip,
          onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_date),
          checked = showDateChip,
          onCheckedChange = { browserPreferences.showDateChip.set(it) },
        )
      )
      if (mediaLayoutMode == MediaLayoutMode.GRID) {
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_manual_grid_columns),
            checked = manualGridColumnsEnabled,
            onCheckedChange = { enabled ->
              if (enabled) {
                if (isLandscape) {
                  browserPreferences.folderGridColumnsLandscape.set(dynamicFolderColumns)
                  browserPreferences.videoGridColumnsLandscape.set(dynamicVideoColumns)
                } else {
                  browserPreferences.folderGridColumnsPortrait.set(dynamicFolderColumns)
                  browserPreferences.videoGridColumnsPortrait.set(dynamicVideoColumns)
                }
              }
              browserPreferences.manualGridColumnsEnabled.set(enabled)
            },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_folder_thumbnails),
            checked = showFolderThumbnails,
            onCheckedChange = { browserPreferences.showFolderThumbnails.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_center_titles),
            checked = centerGridTitles,
            onCheckedChange = { browserPreferences.centerGridTitles.set(it) },
          )
        )
      }
    },
    folderGridColumnSelector = folderGridColumnSelector,
    videoGridColumnSelector = videoGridColumnSelector,
  )
}

@Composable
fun VideoSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  sortType: VideoSortType,
  sortOrder: SortOrder,
  onSortTypeChange: (VideoSortType) -> Unit,
  onSortOrderChange: (SortOrder) -> Unit,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showDateChip by browserPreferences.showDateChip.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val showExtensionField by browserPreferences.showExtensionField.collectAsState()
  val showDurationField by browserPreferences.showDurationField.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()
  val centerGridTitles by browserPreferences.centerGridTitles.collectAsState()
  val manualGridColumnsEnabled by browserPreferences.manualGridColumnsEnabled.collectAsState()
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
  val isTablet = configuration.smallestScreenWidthDp >= 600
  val maxColumns = if (isTablet || isLandscape) 8 else 4

  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
  val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait

  val screenWidthDp = configuration.screenWidthDp.dp
  val contentHorizontalPadding = 8.dp
  val itemSpacing = 2.dp
  val usableWidth = screenWidthDp - (contentHorizontalPadding * 2) - itemSpacing
  val folderMinWidth = 100.dp
  val videoMinWidth = 130.dp
  val dynamicFolderColumns = (usableWidth / folderMinWidth).toInt().coerceIn(1, maxColumns)
  val dynamicVideoColumns = (usableWidth / videoMinWidth).toInt().coerceIn(1, maxColumns)

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID && manualGridColumnsEnabled) {
    GridColumnSelector(
      label = stringResource(R.string.sort_folder_grid_columns) + " (${if (isLandscape) stringResource(R.string.sort_landscape) else stringResource(R.string.sort_portrait)})",
      currentValue = folderGridColumns.coerceIn(1, maxColumns),
      onValueChange = {
        if (isLandscape) browserPreferences.folderGridColumnsLandscape.set(it)
        else browserPreferences.folderGridColumnsPortrait.set(it)
      },
      valueRange = 1f..maxColumns.toFloat(),
      steps = maxColumns - 2,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID && manualGridColumnsEnabled) {
    GridColumnSelector(
      label = stringResource(R.string.sort_video_grid_columns) + " (${if (isLandscape) stringResource(R.string.sort_landscape) else stringResource(R.string.sort_portrait)})",
      currentValue = videoGridColumns.coerceIn(1, maxColumns),
      onValueChange = {
        if (isLandscape) browserPreferences.videoGridColumnsLandscape.set(it)
        else browserPreferences.videoGridColumnsPortrait.set(it)
      },
      valueRange = 1f..maxColumns.toFloat(),
      steps = maxColumns - 2,
    )
  } else null

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = stringResource(R.string.sort_view_options_title),
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      VideoSortType.entries.find { it.displayName == typeName }?.let(onSortTypeChange)
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      onSortOrderChange(if (isAsc) SortOrder.Ascending else SortOrder.Descending)
    },
    types =
      listOf(
        VideoSortType.Title.displayName,
        VideoSortType.Duration.displayName,
        VideoSortType.Date.displayName,
        VideoSortType.Size.displayName,
      ),
    icons =
      listOf(
        Icons.Filled.Title,
        Icons.Filled.AccessTime,
        Icons.Filled.CalendarToday,
        Icons.Filled.SwapVert,
      ),
    getLabelForType = { type, _ ->
      when (type) {
        VideoSortType.Title.displayName -> Pair(stringResource(R.string.sort_order_asc_alpha), stringResource(R.string.sort_order_desc_alpha))
        VideoSortType.Duration.displayName -> Pair(stringResource(R.string.sort_order_shortest), stringResource(R.string.sort_order_longest))
        VideoSortType.Date.displayName -> Pair(stringResource(R.string.sort_order_oldest), stringResource(R.string.sort_order_newest))
        VideoSortType.Size.displayName -> Pair(stringResource(R.string.sort_order_smallest), stringResource(R.string.sort_order_biggest))
        else -> Pair(stringResource(R.string.sort_order_asc), stringResource(R.string.sort_order_desc))
      }
    },
    viewModeSelector = MultiViewModeSelector(
      label = stringResource(R.string.sort_view_mode_label),
      options = listOf(
        ViewModeOption(
          label = stringResource(R.string.sort_view_mode_folder),
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.AlbumView) },
        ),
        ViewModeOption(
          label = stringResource(R.string.sort_view_mode_tree),
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.FileManager) },
        ),
        ViewModeOption(
          label = stringResource(R.string.sort_view_mode_library),
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary) },
        ),
      ),
    ),
    layoutModeSelector = ViewModeSelector(
      label = stringResource(R.string.sort_layout_label),
      firstOptionLabel = stringResource(R.string.sort_layout_list),
      secondOptionLabel = stringResource(R.string.sort_layout_grid),
      firstOptionIcon = Icons.Filled.ViewList,
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = mediaLayoutMode == MediaLayoutMode.LIST,
      onViewModeChange = { isFirstOption ->
        browserPreferences.mediaLayoutMode.set(
          if (isFirstOption) MediaLayoutMode.LIST else MediaLayoutMode.GRID
        )
      },
    ),
    visibilityToggles =
      buildList {
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_thumbnails),
            checked = showThumbnails,
            onCheckedChange = { browserPreferences.showVideoThumbnails.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_extension),
            checked = showExtensionField,
            onCheckedChange = { browserPreferences.showExtensionField.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_duration),
            checked = showDurationField,
            onCheckedChange = { browserPreferences.showDurationField.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_subtitle_indicator),
            checked = showSubtitleIndicator,
            onCheckedChange = { browserPreferences.showSubtitleIndicator.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_full_name),
            checked = unlimitedNameLines,
            onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_size),
            checked = showSizeChip,
            onCheckedChange = { browserPreferences.showSizeChip.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_resolution),
            checked = showResolutionChip,
            onCheckedChange = { browserPreferences.showResolutionChip.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_framerate),
            checked = showFramerateInResolution,
            onCheckedChange = { browserPreferences.showFramerateInResolution.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_date),
            checked = showDateChip,
            onCheckedChange = { browserPreferences.showDateChip.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_field_progress_bar),
            checked = showProgressBar,
            onCheckedChange = { browserPreferences.showProgressBar.set(it) },
          )
        )
        if (mediaLayoutMode == MediaLayoutMode.GRID) {
          add(
            VisibilityToggle(
              label = stringResource(R.string.sort_manual_grid_columns),
              checked = manualGridColumnsEnabled,
              onCheckedChange = { enabled ->
                if (enabled) {
                  if (isLandscape) {
                    browserPreferences.folderGridColumnsLandscape.set(dynamicFolderColumns)
                    browserPreferences.videoGridColumnsLandscape.set(dynamicVideoColumns)
                  } else {
                    browserPreferences.folderGridColumnsPortrait.set(dynamicFolderColumns)
                    browserPreferences.videoGridColumnsPortrait.set(dynamicVideoColumns)
                  }
                }
                browserPreferences.manualGridColumnsEnabled.set(enabled)
              },
            )
          )
          add(
            VisibilityToggle(
              label = stringResource(R.string.sort_center_titles),
              checked = centerGridTitles,
              onCheckedChange = { browserPreferences.centerGridTitles.set(it) },
            )
          )
        }
      },
    folderGridColumnSelector = folderGridColumnSelector,
    videoGridColumnSelector = videoGridColumnSelector,
  )
}

@Composable
fun FileSystemSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  isAtRoot: Boolean = true,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()
  val folderSortType by browserPreferences.folderSortType.collectAsState()
  val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val showExtensionField by browserPreferences.showExtensionField.collectAsState()
  val showDurationField by browserPreferences.showDurationField.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val manualGridColumnsEnabled by browserPreferences.manualGridColumnsEnabled.collectAsState()
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
  val isTablet = configuration.smallestScreenWidthDp >= 600
  val maxColumns = if (isTablet || isLandscape) 8 else 4

  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
  val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait

  val screenWidthDp = configuration.screenWidthDp.dp
  val contentHorizontalPadding = 8.dp
  val itemSpacing = 2.dp
  val usableWidth = screenWidthDp - (contentHorizontalPadding * 2) - itemSpacing
  val folderMinWidth = 100.dp
  val videoMinWidth = 130.dp
  val dynamicFolderColumns = (usableWidth / folderMinWidth).toInt().coerceIn(1, maxColumns)
  val dynamicVideoColumns = (usableWidth / videoMinWidth).toInt().coerceIn(1, maxColumns)

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID && manualGridColumnsEnabled) {
    GridColumnSelector(
      label = stringResource(R.string.sort_folder_grid_columns) + " (${if (isLandscape) stringResource(R.string.sort_landscape) else stringResource(R.string.sort_portrait)})",
      currentValue = folderGridColumns.coerceIn(1, maxColumns),
      onValueChange = {
        if (isLandscape) browserPreferences.folderGridColumnsLandscape.set(it)
        else browserPreferences.folderGridColumnsPortrait.set(it)
      },
      valueRange = 1f..maxColumns.toFloat(),
      steps = maxColumns - 2,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID && manualGridColumnsEnabled) {
    GridColumnSelector(
      label = stringResource(R.string.sort_video_grid_columns) + " (${if (isLandscape) stringResource(R.string.sort_landscape) else stringResource(R.string.sort_portrait)})",
      currentValue = videoGridColumns.coerceIn(1, maxColumns),
      onValueChange = {
        if (isLandscape) browserPreferences.videoGridColumnsLandscape.set(it)
        else browserPreferences.videoGridColumnsPortrait.set(it)
      },
      valueRange = 1f..maxColumns.toFloat(),
      steps = maxColumns - 2,
    )
  } else null

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = stringResource(R.string.sort_view_options_title),
    sortType = folderSortType.displayName,
    onSortTypeChange = { typeName ->
      FolderSortType.entries.find { it.displayName == typeName }?.let {
        browserPreferences.folderSortType.set(it)
      }
    },
    sortOrderAsc = folderSortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      browserPreferences.folderSortOrder.set(
        if (isAsc) SortOrder.Ascending
        else SortOrder.Descending,
      )
    },
    types = listOf(
      FolderSortType.Title.displayName,
      FolderSortType.Date.displayName,
      FolderSortType.Size.displayName,
    ),
    icons = listOf(
      Icons.Filled.Title,
      Icons.Filled.CalendarToday,
      Icons.Filled.SwapVert,
    ),
    getLabelForType = { type, _ ->
      when (type) {
        FolderSortType.Title.displayName -> Pair(stringResource(R.string.sort_order_asc_alpha), stringResource(R.string.sort_order_desc_alpha))
        FolderSortType.Date.displayName -> Pair(stringResource(R.string.sort_order_oldest), stringResource(R.string.sort_order_newest))
        FolderSortType.Size.displayName -> Pair(stringResource(R.string.sort_order_smallest), stringResource(R.string.sort_order_largest))
        else -> Pair(stringResource(R.string.sort_order_asc), stringResource(R.string.sort_order_desc))
      }
    },
    showSortOptions = true,
    viewModeSelector = MultiViewModeSelector(
      label = stringResource(R.string.sort_view_mode_label),
      options = listOf(
        ViewModeOption(
          label = stringResource(R.string.sort_view_mode_folder),
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.AlbumView) }
        ),
        ViewModeOption(
          label = stringResource(R.string.sort_view_mode_tree),
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.FileManager) }
        ),
        ViewModeOption(
          label = stringResource(R.string.sort_view_mode_library),
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary) }
        ),
      )
    ),
    layoutModeSelector = ViewModeSelector(
      label = stringResource(R.string.sort_layout_label),
      firstOptionLabel = stringResource(R.string.sort_layout_list),
      secondOptionLabel = stringResource(R.string.sort_layout_grid),
      firstOptionIcon = Icons.Filled.ViewList,
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = mediaLayoutMode == MediaLayoutMode.LIST,
      onViewModeChange = { isFirstOption ->
        browserPreferences.mediaLayoutMode.set(
          if (isFirstOption) MediaLayoutMode.LIST else MediaLayoutMode.GRID
        )
      },
    ),
    folderGridColumnSelector = folderGridColumnSelector,
    videoGridColumnSelector = videoGridColumnSelector,
    enableViewModeOptions = isAtRoot,
    enableLayoutModeOptions = true, // Enabled layout selection
    visibilityToggles = buildList {
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_video_thumbnails),
          checked = showVideoThumbnails,
          onCheckedChange = { browserPreferences.showVideoThumbnails.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_full_name),
          checked = unlimitedNameLines,
          onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_extension),
          checked = showExtensionField,
          onCheckedChange = { browserPreferences.showExtensionField.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_duration),
          checked = showDurationField,
          onCheckedChange = { browserPreferences.showDurationField.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_path),
          checked = showFolderPath,
          onCheckedChange = { browserPreferences.showFolderPath.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_total_videos),
          checked = showTotalVideosChip,
          onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_folder_size),
          checked = showTotalSizeChip,
          onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_size),
          checked = showSizeChip,
          onCheckedChange = { browserPreferences.showSizeChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_resolution),
          checked = showResolutionChip,
          onCheckedChange = { browserPreferences.showResolutionChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_framerate),
          checked = showFramerateInResolution,
          onCheckedChange = { browserPreferences.showFramerateInResolution.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_subtitle),
          checked = showSubtitleIndicator,
          onCheckedChange = { browserPreferences.showSubtitleIndicator.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.sort_field_progress_bar),
          checked = showProgressBar,
          onCheckedChange = { browserPreferences.showProgressBar.set(it) },
        )
      )
      if (mediaLayoutMode == MediaLayoutMode.GRID) {
        add(
          VisibilityToggle(
            label = stringResource(R.string.sort_manual_grid_columns),
            checked = manualGridColumnsEnabled,
            onCheckedChange = { enabled ->
              if (enabled) {
                if (isLandscape) {
                  browserPreferences.folderGridColumnsLandscape.set(dynamicFolderColumns)
                  browserPreferences.videoGridColumnsLandscape.set(dynamicVideoColumns)
                } else {
                  browserPreferences.folderGridColumnsPortrait.set(dynamicFolderColumns)
                  browserPreferences.videoGridColumnsPortrait.set(dynamicVideoColumns)
                }
              }
              browserPreferences.manualGridColumnsEnabled.set(enabled)
            },
          )
        )
      }
    }
  )
}
