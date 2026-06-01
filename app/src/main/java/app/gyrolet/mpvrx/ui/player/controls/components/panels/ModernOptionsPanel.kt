package app.gyrolet.mpvrx.ui.player.controls.components.panels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.player.VideoAspect
import app.gyrolet.mpvrx.ui.player.PlayerViewModel
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import `is`.xyz.mpv.MPVLib
import kotlin.math.roundToInt
import kotlin.math.abs

@Composable
fun ModernOptionsPanel(
  viewModel: PlayerViewModel,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  DraggablePanel(
    modifier = modifier,
    header = {
      ModernOptionsHeader(
        onResetAll = {
          // Reset all values to defaults
          // Aspect Ratio
          viewModel.changeVideoAspect(VideoAspect.Fit)
          // Panscan / Crop
          MPVLib.setPropertyDouble("panscan", 0.0)
          // Rotate
          MPVLib.setPropertyInt("video-rotate", 0)
          // Zoom
          viewModel.setVideoZoom(0f)
          // Filters
          MPVLib.setPropertyInt("contrast", 0)
          MPVLib.setPropertyInt("brightness", 0)
          MPVLib.setPropertyInt("gamma", 0)
          MPVLib.setPropertyInt("saturation", 0)
          MPVLib.setPropertyInt("hue", 0)
          // Delays
          MPVLib.setPropertyDouble("sub-delay", 0.0)
          MPVLib.setPropertyDouble("audio-delay", 0.0)
          // Playback Speed
          MPVLib.setPropertyFloat("speed", 1.0f)
        },
        onClose = onDismissRequest
      )
    }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Aspect Ratio
      val aspect by viewModel.videoAspect.collectAsState()
      val currentCustomRatio by viewModel.currentAspectRatio.collectAsState()
      val aspectOptions = listOf("Original", "16:9", "4:3", "21:9", "Stretch")
      val selectedAspect = when {
        currentCustomRatio > 0 -> {
          when {
            abs(currentCustomRatio - 16.0 / 9.0) < 0.01 -> "16:9"
            abs(currentCustomRatio - 4.0 / 3.0) < 0.01 -> "4:3"
            abs(currentCustomRatio - 21.0 / 9.0) < 0.01 -> "21:9"
            else -> String.format("%.2f:1", currentCustomRatio)
          }
        }
        aspect == VideoAspect.Stretch -> "Stretch"
        aspect == VideoAspect.Crop -> "Original"
        else -> "Original"
      }
      
      ModernDropdownOptionRow(
        label = "Aspect Ratio",
        value = selectedAspect,
        options = aspectOptions,
        onReset = { viewModel.changeVideoAspect(VideoAspect.Fit) },
        onSelected = { selected ->
          when (selected) {
            "Original" -> viewModel.changeVideoAspect(VideoAspect.Fit)
            "Stretch" -> viewModel.changeVideoAspect(VideoAspect.Stretch)
            "16:9" -> viewModel.setCustomAspectRatio(16.0 / 9.0)
            "4:3" -> viewModel.setCustomAspectRatio(4.0 / 3.0)
            "21:9" -> viewModel.setCustomAspectRatio(21.0 / 9.0)
          }
        }
      )

      // 2. Crop
      var panscan by remember { mutableStateOf(0.0) }
      LaunchedEffect(Unit) {
        panscan = MPVLib.getPropertyDouble("panscan") ?: 0.0
      }
      val cropOptions = listOf("Original", "Crop (Full)", "0.25", "0.50", "0.75")
      val selectedCrop = when {
        panscan >= 1.0 -> "Crop (Full)"
        panscan <= 0.0 -> "Original"
        else -> String.format("%.2f", panscan)
      }

      ModernDropdownOptionRow(
        label = "Crop",
        value = selectedCrop,
        options = cropOptions,
        onReset = {
          MPVLib.setPropertyDouble("panscan", 0.0)
          panscan = 0.0
        },
        onSelected = { selected ->
          val value = when (selected) {
            "Original" -> 0.0
            "Crop (Full)" -> 1.0
            "0.25" -> 0.25
            "0.50" -> 0.50
            "0.75" -> 0.75
            else -> 0.0
          }
          MPVLib.setPropertyDouble("panscan", value)
          panscan = value
        }
      )

      // 3. Rotate
      var rotate by remember { mutableStateOf(0) }
      LaunchedEffect(Unit) {
        rotate = MPVLib.getPropertyInt("video-rotate") ?: 0
      }

      ModernOptionRow(
        label = "Rotate",
        onReset = {
          MPVLib.setPropertyInt("video-rotate", 0)
          rotate = 0
        }
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          ModernCircularButton(
            text = "↺",
            onClick = {
              val newRotate = (rotate + 270) % 360
              MPVLib.setPropertyInt("video-rotate", newRotate)
              rotate = newRotate
            }
          )
          ModernCircularButton(
            text = "↻",
            onClick = {
              val newRotate = (rotate + 90) % 360
              MPVLib.setPropertyInt("video-rotate", newRotate)
              rotate = newRotate
            }
          )
        }
      }

      // 4. Zoom
      val zoom by viewModel.videoZoom.collectAsState()
      ModernIncrementOptionRow(
        label = "Zoom",
        value = String.format("%.2f", zoom),
        onReset = { viewModel.setVideoZoom(0f) },
        onDecrement = { viewModel.setVideoZoom((zoom - 0.1f).coerceIn(0f, 5f)) },
        onIncrement = { viewModel.setVideoZoom((zoom + 0.1f).coerceIn(0f, 5f)) }
      )

      // 5. Contrast
      var contrast by remember { mutableStateOf(0) }
      LaunchedEffect(Unit) {
        contrast = MPVLib.getPropertyInt("contrast") ?: 0
      }
      ModernIncrementOptionRow(
        label = "Contrast",
        value = contrast.toString(),
        onReset = {
          MPVLib.setPropertyInt("contrast", 0)
          contrast = 0
        },
        onDecrement = {
          val newVal = (contrast - 5).coerceIn(-100, 100)
          MPVLib.setPropertyInt("contrast", newVal)
          contrast = newVal
        },
        onIncrement = {
          val newVal = (contrast + 5).coerceIn(-100, 100)
          MPVLib.setPropertyInt("contrast", newVal)
          contrast = newVal
        }
      )

      // 6. Brightness
      var brightness by remember { mutableStateOf(0) }
      LaunchedEffect(Unit) {
        brightness = MPVLib.getPropertyInt("brightness") ?: 0
      }
      ModernIncrementOptionRow(
        label = "Brightness",
        value = brightness.toString(),
        onReset = {
          MPVLib.setPropertyInt("brightness", 0)
          brightness = 0
        },
        onDecrement = {
          val newVal = (brightness - 5).coerceIn(-100, 100)
          MPVLib.setPropertyInt("brightness", newVal)
          brightness = newVal
        },
        onIncrement = {
          val newVal = (brightness + 5).coerceIn(-100, 100)
          MPVLib.setPropertyInt("brightness", newVal)
          brightness = newVal
        }
      )

      // 7. Gamma
      var gamma by remember { mutableStateOf(0) }
      LaunchedEffect(Unit) {
        gamma = MPVLib.getPropertyInt("gamma") ?: 0
      }
      ModernIncrementOptionRow(
        label = "Gamma",
        value = gamma.toString(),
        onReset = {
          MPVLib.setPropertyInt("gamma", 0)
          gamma = 0
        },
        onDecrement = {
          val newVal = (gamma - 5).coerceIn(-100, 100)
          MPVLib.setPropertyInt("gamma", newVal)
          gamma = newVal
        },
        onIncrement = {
          val newVal = (gamma + 5).coerceIn(-100, 100)
          MPVLib.setPropertyInt("gamma", newVal)
          gamma = newVal
        }
      )

      // 8. Saturation
      var saturation by remember { mutableStateOf(0) }
      LaunchedEffect(Unit) {
        saturation = MPVLib.getPropertyInt("saturation") ?: 0
      }
      ModernIncrementOptionRow(
        label = "Saturation",
        value = saturation.toString(),
        onReset = {
          MPVLib.setPropertyInt("saturation", 0)
          saturation = 0
        },
        onDecrement = {
          val newVal = (saturation - 5).coerceIn(-100, 100)
          MPVLib.setPropertyInt("saturation", newVal)
          saturation = newVal
        },
        onIncrement = {
          val newVal = (saturation + 5).coerceIn(-100, 100)
          MPVLib.setPropertyInt("saturation", newVal)
          saturation = newVal
        }
      )

      // 9. Hue
      var hue by remember { mutableStateOf(0) }
      LaunchedEffect(Unit) {
        hue = MPVLib.getPropertyInt("hue") ?: 0
      }
      ModernIncrementOptionRow(
        label = "Hue",
        value = hue.toString(),
        onReset = {
          MPVLib.setPropertyInt("hue", 0)
          hue = 0
        },
        onDecrement = {
          val newVal = (hue - 5).coerceIn(-100, 100)
          MPVLib.setPropertyInt("hue", newVal)
          hue = newVal
        },
        onIncrement = {
          val newVal = (hue + 5).coerceIn(-100, 100)
          MPVLib.setPropertyInt("hue", newVal)
          hue = newVal
        }
      )

      // 10. Subtitle Delay
      var subDelay by remember { mutableStateOf(0.0) }
      LaunchedEffect(Unit) {
        subDelay = MPVLib.getPropertyDouble("sub-delay") ?: 0.0
      }
      ModernIncrementOptionRow(
        label = "Subtitle Delay",
        value = String.format("%.1f", subDelay),
        onReset = {
          MPVLib.setPropertyDouble("sub-delay", 0.0)
          subDelay = 0.0
        },
        onDecrement = {
          val newVal = subDelay - 0.1
          MPVLib.setPropertyDouble("sub-delay", newVal)
          subDelay = newVal
        },
        onIncrement = {
          val newVal = subDelay + 0.1
          MPVLib.setPropertyDouble("sub-delay", newVal)
          subDelay = newVal
        }
      )

      // 11. Audio Delay
      var audioDelay by remember { mutableStateOf(0.0) }
      LaunchedEffect(Unit) {
        audioDelay = MPVLib.getPropertyDouble("audio-delay") ?: 0.0
      }
      ModernIncrementOptionRow(
        label = "Audio Delay",
        value = String.format("%.1f", audioDelay),
        onReset = {
          MPVLib.setPropertyDouble("audio-delay", 0.0)
          audioDelay = 0.0
        },
        onDecrement = {
          val newVal = audioDelay - 0.1
          MPVLib.setPropertyDouble("audio-delay", newVal)
          audioDelay = newVal
        },
        onIncrement = {
          val newVal = audioDelay + 0.1
          MPVLib.setPropertyDouble("audio-delay", newVal)
          audioDelay = newVal
        }
      )

      // 12. Playback Speed
      var speed by remember { mutableStateOf(1.0f) }
      LaunchedEffect(Unit) {
        speed = MPVLib.getPropertyFloat("speed") ?: 1.0f
      }
      ModernIncrementOptionRow(
        label = "Playback Speed",
        value = String.format("%.2f", speed),
        onReset = {
          MPVLib.setPropertyFloat("speed", 1.0f)
          speed = 1.0f
        },
        onDecrement = {
          val newVal = (speed - 0.05f).coerceAtLeast(0.1f)
          MPVLib.setPropertyFloat("speed", newVal)
          speed = newVal
        },
        onIncrement = {
          val newVal = (speed + 0.05f).coerceAtMost(4.0f)
          MPVLib.setPropertyFloat("speed", newVal)
          speed = newVal
        }
      )
    }
  }
}

@Composable
fun ModernOptionsHeader(
  onResetAll: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 16.dp, top = 8.dp)
  ) {
    Button(
      onClick = onResetAll,
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        contentColor = MaterialTheme.colorScheme.onSurface
      ),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .weight(1f)
        .padding(end = 12.dp)
    ) {
      Text(
        "Reset All",
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
      )
    }
    IconButton(
      onClick = onClose,
      modifier = Modifier.size(36.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Close",
        tint = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
fun ModernOptionRow(
  label: String,
  onReset: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      IconButton(
        onClick = onReset,
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          Icons.Default.Refresh,
          contentDescription = "Reset",
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          modifier = Modifier.size(16.dp)
        )
      }
      Text(
        text = label,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
    content()
  }
}

@Composable
fun ModernDropdownOptionRow(
  label: String,
  value: String,
  options: List<String>,
  onReset: () -> Unit,
  onSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }

  ModernOptionRow(
    label = label,
    onReset = onReset,
    modifier = modifier
  ) {
    Box {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
          .clickable { expanded = true }
          .padding(horizontal = 12.dp, vertical = 6.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
          )
          Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
      ) {
        options.forEach { option ->
          DropdownMenuItem(
            text = { Text(option) },
            onClick = {
              onSelected(option)
              expanded = false
            }
          )
        }
      }
    }
  }
}

@Composable
fun ModernIncrementOptionRow(
  label: String,
  value: String,
  onReset: () -> Unit,
  onDecrement: () -> Unit,
  onIncrement: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ModernOptionRow(
    label = label,
    onReset = onReset,
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = value,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.widthIn(min = 36.dp)
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        ModernCircularButton(
          text = "-",
          onClick = onDecrement
        )
        ModernCircularButton(
          text = "+",
          onClick = onIncrement
        )
      }
    }
  }
}

@Composable
fun ModernCircularButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    modifier = modifier
      .size(32.dp)
      .clickable(onClick = onClick),
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}
