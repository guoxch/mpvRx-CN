package app.gyrolet.mpvrx.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.icons.AppIcon
import app.gyrolet.mpvrx.ui.icons.Icons

/**
 * Represents a customizable button in the player controls.
 * Now includes an icon for the preference UI.
 */
enum class PlayerButton(
  val icon: AppIcon,
) {
  BACK_ARROW(Icons.Outlined.ArrowBack),
  VIDEO_TITLE(Icons.Outlined.Title),
  BOOKMARKS_CHAPTERS(Icons.Outlined.Bookmarks),
  PLAYBACK_SPEED(Icons.Outlined.Speed),
  DECODER(Icons.Default.DeveloperBoard),
  HDR_MODE(Icons.Default.HdrOff),
  SCREEN_ROTATION(Icons.Outlined.ScreenRotation),
  FRAME_NAVIGATION(Icons.Default.Screenshot),
  VIDEO_ZOOM(Icons.Outlined.ZoomIn),
  PICTURE_IN_PICTURE(Icons.Outlined.PictureInPictureAlt),
  ASPECT_RATIO(Icons.Outlined.AspectRatio),
  LOCK_CONTROLS(Icons.Outlined.LockOpen),
  AUDIO_TRACK(Icons.Outlined.Audiotrack),
  SUBTITLES(Icons.Outlined.Subtitles),
  MORE_OPTIONS(Icons.Outlined.MoreVert),
  CURRENT_CHAPTER(Icons.Outlined.Bookmarks), // <-- CHANGED ICON
  REPEAT_MODE(Icons.Filled.Repeat),
  SHUFFLE(Icons.Outlined.Shuffle),
  MIRROR(Icons.Outlined.Flip),
  VERTICAL_FLIP(Icons.Outlined.Flip),
  AB_LOOP(Icons.Outlined.Repeat),
  CUSTOM_SKIP(Icons.Outlined.FastForward),
  BACKGROUND_PLAYBACK(Icons.Outlined.Headset),
  AMBIENT_MODE(Icons.Outlined.BlurOff),
  TIME_NETWORK(Icons.Default.AccessTime),
  DELETE_VIDEO(Icons.Default.Delete),
  NONE(Icons.Outlined.Bookmarks),
}

/**
 * A list of all buttons that the user can choose from in the customization menu.
 * Excludes NONE (placeholder) and constant buttons (BACK_ARROW, VIDEO_TITLE).
 */
val allPlayerButtons =
  PlayerButton.values().filter {
    it != PlayerButton.NONE &&
      it != PlayerButton.BACK_ARROW &&
      it != PlayerButton.VIDEO_TITLE
  }

/**
 * Gets the human-readable label for a player button.
 * TODO: You must add these string resources to your `strings.xml` file.
 */
@Composable
fun getPlayerButtonLabel(button: PlayerButton): String =
  when (button) {
    PlayerButton.BACK_ARROW -> stringResource(R.string.btn_label_back)
    PlayerButton.VIDEO_TITLE -> stringResource(R.string.btn_label_title)
    PlayerButton.BOOKMARKS_CHAPTERS -> stringResource(R.string.btn_label_bookmarks)
    PlayerButton.PLAYBACK_SPEED -> stringResource(R.string.btn_label_speed)
    PlayerButton.DECODER -> stringResource(R.string.btn_label_decoder)
    PlayerButton.HDR_MODE -> stringResource(R.string.btn_label_hdr)
    PlayerButton.SCREEN_ROTATION -> stringResource(R.string.btn_label_rotation)
    PlayerButton.FRAME_NAVIGATION -> stringResource(R.string.btn_label_frame_nav)
    PlayerButton.VIDEO_ZOOM -> stringResource(R.string.btn_label_zoom)
    PlayerButton.PICTURE_IN_PICTURE -> stringResource(R.string.btn_label_pip)
    PlayerButton.ASPECT_RATIO -> stringResource(R.string.btn_label_aspect)
    PlayerButton.LOCK_CONTROLS -> stringResource(R.string.btn_label_lock)
    PlayerButton.AUDIO_TRACK -> stringResource(R.string.btn_label_audio)
    PlayerButton.SUBTITLES -> stringResource(R.string.btn_label_subtitles)
    PlayerButton.MORE_OPTIONS -> stringResource(R.string.btn_label_more)
    PlayerButton.CURRENT_CHAPTER -> stringResource(R.string.btn_label_chapter)
    PlayerButton.REPEAT_MODE -> stringResource(R.string.btn_label_repeat_mode)
    PlayerButton.SHUFFLE -> stringResource(R.string.btn_label_shuffle)
    PlayerButton.MIRROR -> stringResource(R.string.btn_label_mirror)
    PlayerButton.VERTICAL_FLIP -> stringResource(R.string.btn_label_vertical_flip)
    PlayerButton.AB_LOOP -> stringResource(R.string.btn_label_ab_loop)
    PlayerButton.CUSTOM_SKIP -> stringResource(R.string.btn_label_custom_skip)
    PlayerButton.BACKGROUND_PLAYBACK -> stringResource(R.string.btn_label_bg_playback)
    PlayerButton.AMBIENT_MODE -> stringResource(R.string.btn_label_ambient)
    PlayerButton.TIME_NETWORK -> stringResource(R.string.btn_label_time_network)
    PlayerButton.DELETE_VIDEO -> stringResource(R.string.btn_label_delete)
    PlayerButton.NONE -> stringResource(R.string.btn_label_none)
  }
