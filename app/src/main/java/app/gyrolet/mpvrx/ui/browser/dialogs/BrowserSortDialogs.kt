package app.gyrolet.mpvrx.ui.browser.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
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
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
  val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      label = stringResource(
        R.string.dialog_grid_columns_device,
        stringResource(if (isLandscape) R.string.pref_player_orientation_landscape else R.string.pref_player_orientation_portrait),
      ),
      currentValue = folderGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.folderGridColumnsLandscape.set(it)
        else browserPreferences.folderGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 2f..4f,
      steps = if (isLandscape) 1 else 1,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      label = stringResource(
        R.string.dialog_video_grid_columns_device,
        stringResource(if (isLandscape) R.string.pref_player_orientation_landscape else R.string.pref_player_orientation_portrait),
      ),
      currentValue = videoGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.videoGridColumnsLandscape.set(it)
        else browserPreferences.videoGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 1f..3f,
      steps = if (isLandscape) 1 else 1,
    )
  } else null

  val isAlbumView = folderViewMode == FolderViewMode.AlbumView
  val sortAZ = stringResource(R.string.sort_a_z)
  val sortZA = stringResource(R.string.sort_z_a)
  val sortOldest = stringResource(R.string.sort_oldest)
  val sortNewest = stringResource(R.string.sort_newest)
  val sortSmallest = stringResource(R.string.sort_smallest)
  val sortLargest = stringResource(R.string.sort_largest)
  val sortAsc = stringResource(R.string.sort_asc)
  val sortDesc = stringResource(R.string.sort_desc)

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = stringResource(if (isAlbumView) R.string.dialog_sort_view_options else R.string.dialog_view_options),
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
        FolderSortType.Title.displayName -> Pair(sortAZ, sortZA)
        FolderSortType.Date.displayName -> Pair(sortOldest, sortNewest)
        FolderSortType.Size.displayName -> Pair(sortSmallest, sortLargest)
        else -> Pair(sortAsc, sortDesc)
      }
    },
    showSortOptions = isAlbumView,
    viewModeSelector = MultiViewModeSelector(
      label = stringResource(R.string.view_mode_section_label),
      options = listOf(
        ViewModeOption(
          label = stringResource(R.string.view_mode_folder),
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.AlbumView) }
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_tree),
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.FileManager) }
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_library),
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary) }
        ),
      )
    ),
    layoutModeSelector = ViewModeSelector(
      label = stringResource(R.string.layout_section_label),
      firstOptionLabel = stringResource(R.string.layout_list),
      secondOptionLabel = stringResource(R.string.layout_grid),
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
          label = stringResource(R.string.toggle_full_name),
          checked = unlimitedNameLines,
          onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.toggle_path),
          checked = showFolderPath,
          onCheckedChange = { browserPreferences.showFolderPath.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.toggle_total_videos),
          checked = showTotalVideosChip,
          onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.toggle_total_duration),
          checked = showTotalDurationChip,
          onCheckedChange = { browserPreferences.showTotalDurationChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.toggle_folder_size),
          checked = showTotalSizeChip,
          onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = stringResource(R.string.toggle_date),
          checked = showDateChip,
          onCheckedChange = { browserPreferences.showDateChip.set(it) },
        )
      )
      if (mediaLayoutMode == MediaLayoutMode.GRID) {
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_folder_thumbnails),
            checked = showFolderThumbnails,
            onCheckedChange = { browserPreferences.showFolderThumbnails.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_center_titles),
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
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

  val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait
  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
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

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      label = stringResource(
        R.string.dialog_video_grid_columns_device,
        stringResource(if (isLandscape) R.string.pref_player_orientation_landscape else R.string.pref_player_orientation_portrait),
      ),
      currentValue = folderGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.folderGridColumnsLandscape.set(it)
        else browserPreferences.folderGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 2f..4f,
      steps = if (isLandscape) 1 else 1,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      label = stringResource(
        R.string.dialog_grid_columns_device,
        stringResource(if (isLandscape) R.string.pref_player_orientation_landscape else R.string.pref_player_orientation_portrait),
      ),
      currentValue = videoGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.videoGridColumnsLandscape.set(it)
        else browserPreferences.videoGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 1f..3f,
      steps = if (isLandscape) 1 else 1,
    )
  } else null
  val sortAZ = stringResource(R.string.sort_a_z)
  val sortZA = stringResource(R.string.sort_z_a)
  val sortShortest = stringResource(R.string.sort_shortest)
  val sortLongest = stringResource(R.string.sort_longest)
  val sortOldest = stringResource(R.string.sort_oldest)
  val sortNewest = stringResource(R.string.sort_newest)
  val sortSmallest = stringResource(R.string.sort_smallest)
  val sortBiggest = stringResource(R.string.sort_biggest)
  val sortAsc = stringResource(R.string.sort_asc)
  val sortDesc = stringResource(R.string.sort_desc)

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = stringResource(R.string.dialog_sort_view_options),
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
        VideoSortType.Title.displayName -> Pair(sortAZ, sortZA)
        VideoSortType.Duration.displayName -> Pair(sortShortest, sortLongest)
        VideoSortType.Date.displayName -> Pair(sortOldest, sortNewest)
        VideoSortType.Size.displayName -> Pair(sortSmallest, sortBiggest)
        else -> Pair(sortAsc, sortDesc)
      }
    },
    viewModeSelector = MultiViewModeSelector(
      label = stringResource(R.string.view_mode_section_label),
      options = listOf(
        ViewModeOption(
          label = stringResource(R.string.view_mode_folder),
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.AlbumView) },
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_tree),
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.FileManager) },
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_library),
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary) },
        ),
      ),
    ),
    layoutModeSelector = ViewModeSelector(
      label = stringResource(R.string.layout_section_label),
      firstOptionLabel = stringResource(R.string.layout_list),
      secondOptionLabel = stringResource(R.string.layout_grid),
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
            label = stringResource(R.string.toggle_thumbnails),
            checked = showThumbnails,
            onCheckedChange = { browserPreferences.showVideoThumbnails.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_extension),
            checked = showExtensionField,
            onCheckedChange = { browserPreferences.showExtensionField.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_duration),
            checked = showDurationField,
            onCheckedChange = { browserPreferences.showDurationField.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_subtitle_indicator),
            checked = showSubtitleIndicator,
            onCheckedChange = { browserPreferences.showSubtitleIndicator.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_full_name),
            checked = unlimitedNameLines,
            onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_size),
            checked = showSizeChip,
            onCheckedChange = { browserPreferences.showSizeChip.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_resolution),
            checked = showResolutionChip,
            onCheckedChange = { browserPreferences.showResolutionChip.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_framerate),
            checked = showFramerateInResolution,
            onCheckedChange = { browserPreferences.showFramerateInResolution.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_date),
            checked = showDateChip,
            onCheckedChange = { browserPreferences.showDateChip.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = stringResource(R.string.toggle_progress_bar),
            checked = showProgressBar,
            onCheckedChange = { browserPreferences.showProgressBar.set(it) },
          )
        )
        if (mediaLayoutMode == MediaLayoutMode.GRID && videoGridColumns > 1) {
          add(
            VisibilityToggle(
              label = stringResource(R.string.toggle_center_titles),
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
  val sortAZ = stringResource(R.string.sort_a_z)
  val sortZA = stringResource(R.string.sort_z_a)
  val sortOldest = stringResource(R.string.sort_oldest)
  val sortNewest = stringResource(R.string.sort_newest)
  val sortSmallest = stringResource(R.string.sort_smallest)
  val sortLargest = stringResource(R.string.sort_largest)
  val sortAsc = stringResource(R.string.sort_asc)
  val sortDesc = stringResource(R.string.sort_desc)

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = stringResource(R.string.dialog_sort_view_options),
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
        FolderSortType.Title.displayName -> Pair(sortAZ, sortZA)
        FolderSortType.Date.displayName -> Pair(sortOldest, sortNewest)
        FolderSortType.Size.displayName -> Pair(sortSmallest, sortLargest)
        else -> Pair(sortAsc, sortDesc)
      }
    },
    showSortOptions = true,
    viewModeSelector = MultiViewModeSelector(
      label = stringResource(R.string.view_mode_section_label),
      options = listOf(
        ViewModeOption(
          label = stringResource(R.string.view_mode_folder),
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.AlbumView) }
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_tree),
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.FileManager) }
        ),
        ViewModeOption(
          label = stringResource(R.string.view_mode_library),
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary) }
        ),
      )
    ),
    layoutModeSelector = ViewModeSelector(
      label = stringResource(R.string.layout_section_label),
      firstOptionLabel = stringResource(R.string.layout_list),
      secondOptionLabel = stringResource(R.string.layout_grid),
      firstOptionIcon = Icons.Filled.ViewList,
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = true, // Always list mode
      onViewModeChange = { /* Disabled - do nothing */ },
    ),
    folderGridColumnSelector = null,
    videoGridColumnSelector = null,
    enableViewModeOptions = isAtRoot,
    enableLayoutModeOptions = false, // Disabled/grayed out
    visibilityToggles = listOf(
      VisibilityToggle(
        label = stringResource(R.string.toggle_video_thumbnails),
        checked = showVideoThumbnails,
        onCheckedChange = { browserPreferences.showVideoThumbnails.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_full_name),
        checked = unlimitedNameLines,
        onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_extension),
        checked = showExtensionField,
        onCheckedChange = { browserPreferences.showExtensionField.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_duration),
        checked = showDurationField,
        onCheckedChange = { browserPreferences.showDurationField.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_path),
        checked = showFolderPath,
        onCheckedChange = { browserPreferences.showFolderPath.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_total_videos),
        checked = showTotalVideosChip,
        onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_folder_size),
        checked = showTotalSizeChip,
        onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_size),
        checked = showSizeChip,
        onCheckedChange = { browserPreferences.showSizeChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_resolution),
        checked = showResolutionChip,
        onCheckedChange = { browserPreferences.showResolutionChip.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_framerate),
        checked = showFramerateInResolution,
        onCheckedChange = { browserPreferences.showFramerateInResolution.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_subtitle),
        checked = showSubtitleIndicator,
        onCheckedChange = { browserPreferences.showSubtitleIndicator.set(it) },
      ),
      VisibilityToggle(
        label = stringResource(R.string.toggle_progress_bar),
        checked = showProgressBar,
        onCheckedChange = { browserPreferences.showProgressBar.set(it) },
      ),
    )
  )
}
