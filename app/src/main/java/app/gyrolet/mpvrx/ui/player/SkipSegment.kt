package app.gyrolet.mpvrx.ui.player

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.gyrolet.mpvrx.R

enum class SkipSegmentType(@StringRes val labelRes: Int) {
  INTRO(R.string.skip_intro),
  RECAP(R.string.skip_recap),
  OUTRO(R.string.skip_outro),
  CREDITS(R.string.skip_credits),
  PREVIEW(R.string.skip_preview),
  ;

  fun label(context: Context): String = context.getString(labelRes)

  val accentColor: Color
    get() =
      when (this) {
        INTRO -> Color(0xFFFF7A00)
        RECAP -> Color(0xFF2F80FF)
        OUTRO -> Color(0xFFE05666)
        CREDITS -> Color(0xFFA64DFF)
        PREVIEW -> Color(0xFF00D4C7)
      }
}

data class SkipSegment(
  val type: SkipSegmentType,
  val startSeconds: Double,
  val endSeconds: Double,
  val source: String,
) {
  val isValid: Boolean
    get() = endSeconds > startSeconds

  fun label(context: Context): String = type.label(context)
}
