package app.gyrolet.mpvrx.preferences

import androidx.annotation.StringRes
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.preference.PreferenceStore
import app.gyrolet.mpvrx.preferences.preference.getEnum

/**
 * Preferences for the video browser (folder and video lists)
 */
class BrowserPreferences(
  preferenceStore: PreferenceStore,
  context: android.content.Context,
) {
  // Folder sorting preferences
  val folderSortType = preferenceStore.getEnum("folder_sort_type", FolderSortType.Title)
  val folderSortOrder = preferenceStore.getEnum("folder_sort_order", SortOrder.Ascending)

  // Video sorting preferences
  val videoSortType = preferenceStore.getEnum("video_sort_type", VideoSortType.Title)
  val videoSortOrder = preferenceStore.getEnum("video_sort_order", SortOrder.Ascending)

  val folderViewMode = preferenceStore.getEnum("folder_view_mode", FolderViewMode.AlbumView)

  private val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
  val folderGridColumnsPortrait = preferenceStore.getInt("folder_grid_columns_portrait", if (isTablet) 4 else 3)
  val folderGridColumnsLandscape = preferenceStore.getInt("folder_grid_columns_landscape", 5)

  val videoGridColumnsPortrait = preferenceStore.getInt("video_grid_columns_portrait", if (isTablet) 4 else 2)
  val videoGridColumnsLandscape = preferenceStore.getInt("video_grid_columns_landscape", 4)

  val showExtensionField = preferenceStore.getBoolean("show_extension_field", false)
  val showDurationField = preferenceStore.getBoolean("show_duration_field", true)

  // Visibility preferences for video card chips
  val showVideoThumbnails = preferenceStore.getBoolean("show_video_thumbnails", true)
  val thumbnailMode = preferenceStore.getEnum("thumbnail_mode", ThumbnailMode.Smart)
  val thumbnailFramePosition = preferenceStore.getFloat("thumbnail_frame_position", 33f)
  val showSizeChip = preferenceStore.getBoolean("show_size_chip", true)
  // Metadata-dependent chips (disabled by default for better performance)
  val showResolutionChip = preferenceStore.getBoolean("show_resolution_chip", false)
  val showFramerateInResolution = preferenceStore.getBoolean("show_framerate_in_resolution", false)
  val showSubtitleIndicator = preferenceStore.getBoolean("show_subtitle_indicator", false)
  val showProgressBar = preferenceStore.getBoolean("show_progress_bar", true)
  val centerGridTitles = preferenceStore.getBoolean("center_grid_titles", true)
  val mediaLayoutMode = preferenceStore.getEnum("media_layout_mode", MediaLayoutMode.LIST)

  // Visibility preferences for folder card chips
  val showTotalVideosChip = preferenceStore.getBoolean("show_total_videos_chip", true)
  // Metadata-dependent chips (disabled by default for better performance)
  val showTotalDurationChip = preferenceStore.getBoolean("show_total_duration_chip", false)
  val showTotalSizeChip = preferenceStore.getBoolean("show_total_size_chip", true)
  val showDateChip = preferenceStore.getBoolean("show_date_chip", false)
  val showFolderPath = preferenceStore.getBoolean("show_folder_path", true)
  val showFolderThumbnails = preferenceStore.getBoolean("show_folder_thumbnails", false)

  // Auto-scroll to last played media preference (like MX Player)
  val autoScrollToLastPlayed = preferenceStore.getBoolean("auto_scroll_to_last_played", false)

  // Watched threshold preference (percentage 1-100)
  val watchedThreshold = preferenceStore.getInt("watched_threshold", 95)
}

/**
 * Sort order options
 */
enum class SortOrder {
  Ascending,
  Descending,
  ;

  val isAscending: Boolean
    get() = this == Ascending
}

/**
 * Folder sorting options
 */
enum class FolderSortType(@StringRes val labelRes: Int) {
  Title(R.string.folder_sort_type_title),
  Date(R.string.folder_sort_type_date),
  Size(R.string.folder_sort_type_size),
  VideoCount(R.string.folder_sort_type_video_count),
  ;

  val displayName: String
    get() =
      when (this) {
        Title -> "Title"
        Date -> "Date"
        Size -> "Size"
        VideoCount -> "Count"
      }
}

/**
 * Video sorting options
 */
enum class VideoSortType(@StringRes val labelRes: Int) {
  Title(R.string.video_sort_type_title),
  Duration(R.string.video_sort_type_duration),
  Date(R.string.video_sort_type_date),
  Size(R.string.video_sort_type_size),
  ;

  val displayName: String
    get() =
      when (this) {
        Title -> "Title"
        Duration -> "Duration"
        Date -> "Date"
        Size -> "Size"
      }
}

/**
 * Folder view mode options
 */
enum class FolderViewMode(@StringRes val labelRes: Int) {
  AlbumView(R.string.folder_view_album),
  FileManager(R.string.folder_view_file_manager),
  MediaLibrary(R.string.folder_view_media_library),
  ;

  val displayName: String
    get() =
      when (this) {
        AlbumView -> "Folder View"
        FileManager -> "Tree View"
        MediaLibrary -> "Media Library"
      }
}

enum class MediaLayoutMode(@StringRes val labelRes: Int) {
  LIST(R.string.media_layout_list),
  GRID(R.string.media_layout_grid),
  ;

  val displayName: String
    get() = when (this) {
      LIST -> "List"
      GRID -> "Grid"
    }
}

enum class ThumbnailMode(@StringRes val labelRes: Int) {
  Smart(R.string.thumbnail_mode_smart),
  FirstFrame(R.string.thumbnail_mode_first_frame),
  FrameAtPosition(R.string.thumbnail_mode_frame_position),
  EmbeddedThumbnail(R.string.thumbnail_mode_embedded),
  ;

  val displayName: String
    get() =
      when (this) {
        Smart -> "Smart (embedded + 33%)"
        FirstFrame -> "First frame"
        FrameAtPosition -> "Frame position"
        EmbeddedThumbnail -> "Embedded thumbnail"
      }
}
