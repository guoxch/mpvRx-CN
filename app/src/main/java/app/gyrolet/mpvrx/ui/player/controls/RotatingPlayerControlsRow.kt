package app.gyrolet.mpvrx.ui.player.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.preferences.PlayerButton
import app.gyrolet.mpvrx.ui.theme.AppMotion
import app.gyrolet.mpvrx.ui.theme.spacing
import kotlin.math.abs

private data class CarouselFrame(
  val startIndex: Int,
  val direction: Int,
  val tick: Int,
)

@Composable
fun RotatingPlayerControlsRow(
  buttons: List<PlayerButton>,
  hideBackground: Boolean,
  modifier: Modifier = Modifier,
  buttonSize: Dp = 45.dp,
  maxVisibleButtons: Int = 5,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  itemContent: @Composable (PlayerButton) -> Unit,
) {
  if (buttons.isEmpty()) return

  val spacing = MaterialTheme.spacing.extraSmall

  BoxWithConstraints(modifier = modifier) {
    val widthSlots =
      if (maxWidth == Dp.Infinity) {
        maxVisibleButtons
      } else {
        ((maxWidth.value + spacing.value) / (buttonSize.value + spacing.value))
          .toInt()
          .coerceAtLeast(1)
      }
    val visibleCount = buttons.size.coerceAtMost(widthSlots.coerceAtMost(maxVisibleButtons))
    val isCarousel = buttons.size > visibleCount

    if (!isCarousel) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.align(alignmentFor(horizontalAlignment)),
      ) {
        buttons.forEach { button ->
          key(button) {
            itemContent(button)
          }
        }
      }
      return@BoxWithConstraints
    }

    var startIndex by remember(buttons) { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(1) }
    var tick by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(buttons.size) {
      startIndex = startIndex.floorMod(buttons.size)
    }

    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) {
      (buttonSize * 0.72f).toPx()
    }

    fun rotateControls(delta: Int) {
      if (delta == 0 || buttons.isEmpty()) return
      direction = if (delta > 0) 1 else -1
      startIndex = (startIndex + delta).floorMod(buttons.size)
      tick += 1
    }

    Surface(
      modifier =
        Modifier
          .align(alignmentFor(horizontalAlignment))
          .widthIn(max = maxWidth)
          .clip(CircleShape)
          .semantics {
            contentDescription = "Swipe horizontally to rotate player controls"
          },
      shape = CircleShape,
      color =
        if (hideBackground) {
          Color.Transparent
        } else {
          MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.26f)
        },
      contentColor = MaterialTheme.colorScheme.onSurface,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      border =
        if (hideBackground) {
          null
        } else {
          BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
        },
    ) {
      Box(
        modifier =
          Modifier
            .pointerInput(buttons, visibleCount) {
              detectHorizontalDragGestures(
                onDragEnd = {
                  if (abs(dragX) > swipeThreshold) {
                    rotateControls(if (dragX < 0f) 1 else -1)
                  }
                  dragX = 0f
                },
                onDragCancel = { dragX = 0f },
                onHorizontalDrag = { change, dragAmount ->
                  change.consume()
                  dragX += dragAmount
                },
              )
            }
            .padding(horizontal = 2.dp, vertical = 1.dp),
      ) {
        val reduceMotion = AppMotion.shouldReduceMotion()
        val slideSpec =
          if (reduceMotion) {
            AppMotion.ReducedOffset
          } else {
            spring<IntOffset>(dampingRatio = 0.9f, stiffness = 920f)
          }
        val alphaSpec = if (reduceMotion) AppMotion.ReducedAlpha else AppMotion.Effect.Alpha

        AnimatedContent(
          targetState = CarouselFrame(startIndex, direction, tick),
          transitionSpec = {
            val slideDirection = targetState.direction.coerceIn(-1, 1).takeIf { it != 0 } ?: 1
            (
              slideInHorizontally(animationSpec = slideSpec) { width -> width * slideDirection / 2 } +
                fadeIn(animationSpec = alphaSpec)
              ).togetherWith(
                slideOutHorizontally(animationSpec = slideSpec) { width -> -width * slideDirection / 2 } +
                  fadeOut(animationSpec = alphaSpec),
              ).using(SizeTransform(clip = false))
          },
          label = "player_controls_carousel",
        ) { frame ->
          Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            rotatingWindow(buttons, frame.startIndex, visibleCount).forEach { button ->
              key(button) {
                itemContent(button)
              }
            }
          }
        }
      }
    }
  }
}

private fun rotatingWindow(
  buttons: List<PlayerButton>,
  startIndex: Int,
  count: Int,
): List<PlayerButton> =
  List(count) { offset ->
    buttons[(startIndex + offset).floorMod(buttons.size)]
  }

private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod

private fun alignmentFor(horizontal: Alignment.Horizontal): Alignment =
  when (horizontal) {
    Alignment.Start -> Alignment.CenterStart
    Alignment.CenterHorizontally -> Alignment.Center
    Alignment.End -> Alignment.CenterEnd
    else -> Alignment.CenterStart
  }
