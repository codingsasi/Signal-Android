/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.flair

import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Parcelable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.TextFields
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R

/**
 * Lets the user turn the text they are composing into an animated GIF, or send it hidden behind a spoiler
 * that the recipient taps to reveal. Opt-in only: nothing happens to ordinary messages.
 */
class FlairBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    const val REQUEST_KEY = "FlairBottomSheet"
    const val RESULT_KEY = "FlairBottomSheet.result"
    private const val ARG_INITIAL_TEXT = "initial_text"
    private const val ARG_CHAT_COLOR = "chat_color"

    fun show(fragmentManager: FragmentManager, initialText: String, chatColor: Int) {
      FlairBottomSheet().apply {
        arguments = bundleOf(ARG_INITIAL_TEXT to initialText, ARG_CHAT_COLOR to chatColor)
      }.show(fragmentManager, REQUEST_KEY)
    }
  }

  sealed class Result : Parcelable {
    /** Render the request into a looping video GIF. */
    @Parcelize
    data class Gif(val request: FlairRequest) : Result()

    /** Send [text] as a normal message wrapped entirely in a spoiler, so it is hidden until tapped. */
    @Parcelize
    data class TapToReveal(val text: String) : Result()
  }

  override val peekHeightPercentage: Float = 1f

  @Composable
  override fun SheetContent() {
    FlairSheetContent(
      initialText = requireArguments().getString(ARG_INITIAL_TEXT) ?: "",
      chatColor = requireArguments().getInt(ARG_CHAT_COLOR),
      onSend = { result ->
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY to result))
        dismissAllowingStateLoss()
      }
    )
  }
}

private enum class FlairFormat { GIF, HIDDEN_TEXT }

private enum class BackgroundChoice { GRADIENT, CHAT_COLOR, PHOTO }

@Composable
private fun FlairSheetContent(
  initialText: String,
  chatColor: Int,
  onSend: (FlairBottomSheet.Result) -> Unit
) {
  var format by remember { mutableStateOf(FlairFormat.GIF) }
  var text by remember { mutableStateOf(initialText) }
  var effect by remember { mutableStateOf(FlairEffect.BOUNCE) }
  var font by remember { mutableStateOf(FlairFont.BOLD) }
  var backgroundChoice by remember { mutableStateOf(BackgroundChoice.GRADIENT) }
  var photoUri by remember { mutableStateOf<Uri?>(null) }

  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      photoUri = uri
      backgroundChoice = BackgroundChoice.PHOTO
    }
  }

  val background: FlairBackground = when (backgroundChoice) {
    BackgroundChoice.GRADIENT -> FlairBackground.Gradient
    BackgroundChoice.CHAT_COLOR -> FlairBackground.Solid(chatColor)
    BackgroundChoice.PHOTO -> photoUri?.let { FlairBackground.Photo(it) } ?: FlairBackground.Gradient
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp)
      .padding(bottom = 24.dp)
  ) {
    BottomSheets.Handle(modifier = Modifier.align(Alignment.CenterHorizontally))

    Text(
      text = stringResource(R.string.FlairBottomSheet__flair),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )

    ChipRow(labelRes = R.string.FlairBottomSheet__send_as) {
      FilterChip(
        selected = format == FlairFormat.GIF,
        onClick = { format = FlairFormat.GIF },
        label = { Text(stringResource(R.string.FlairBottomSheet__format_gif)) }
      )
      FilterChip(
        selected = format == FlairFormat.HIDDEN_TEXT,
        onClick = { format = FlairFormat.HIDDEN_TEXT },
        label = { Text(stringResource(R.string.FlairBottomSheet__format_hidden_text)) }
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextFields.TextField(
      value = text,
      onValueChange = { text = it },
      label = { Text(stringResource(R.string.FlairBottomSheet__your_text)) },
      singleLine = false,
      maxLines = 4,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    when (format) {
      FlairFormat.GIF -> {
        AnimatedTextPreview(
          text = text.take(AnimatedTextRenderer.MAX_TEXT_LENGTH),
          effect = effect,
          font = font,
          background = background
        )

        Spacer(modifier = Modifier.height(16.dp))

        ChipRow(labelRes = R.string.FlairBottomSheet__effect) {
          FlairEffect.entries.forEach { candidate ->
            FilterChip(
              selected = candidate == effect,
              onClick = { effect = candidate },
              label = { Text(stringResource(candidate.titleRes)) }
            )
          }
        }

        ChipRow(labelRes = R.string.FlairBottomSheet__font) {
          FlairFont.entries.forEach { candidate ->
            FilterChip(
              selected = candidate == font,
              onClick = { font = candidate },
              label = { Text(stringResource(candidate.titleRes)) }
            )
          }
        }

        ChipRow(labelRes = R.string.FlairBottomSheet__background) {
          FilterChip(
            selected = backgroundChoice == BackgroundChoice.GRADIENT,
            onClick = { backgroundChoice = BackgroundChoice.GRADIENT },
            label = { Text(stringResource(R.string.FlairBottomSheet__background_gradient)) }
          )
          FilterChip(
            selected = backgroundChoice == BackgroundChoice.CHAT_COLOR,
            onClick = { backgroundChoice = BackgroundChoice.CHAT_COLOR },
            label = { Text(stringResource(R.string.FlairBottomSheet__background_chat_color)) }
          )
          FilterChip(
            selected = backgroundChoice == BackgroundChoice.PHOTO,
            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            label = { Text(stringResource(R.string.FlairBottomSheet__background_photo)) }
          )
        }
      }

      FlairFormat.HIDDEN_TEXT -> {
        HiddenTextPreview(text = text)
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Buttons.LargeTonal(
      onClick = {
        when (format) {
          FlairFormat.GIF -> onSend(
            FlairBottomSheet.Result.Gif(
              FlairRequest(
                text = text.trim().take(AnimatedTextRenderer.MAX_TEXT_LENGTH),
                effect = effect,
                font = font,
                background = background
              )
            )
          )

          FlairFormat.HIDDEN_TEXT -> onSend(FlairBottomSheet.Result.TapToReveal(text.trim()))
        }
      },
      enabled = text.isNotBlank(),
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(
        stringResource(
          when (format) {
            FlairFormat.GIF -> R.string.FlairBottomSheet__send_gif
            FlairFormat.HIDDEN_TEXT -> R.string.FlairBottomSheet__send_hidden_message
          }
        )
      )
    }
  }
}

/** Approximates how the message lands for her: the whole body masked until she taps it. */
@Composable
private fun HiddenTextPreview(text: String) {
  val masked = remember(text) {
    text.map { if (it.isWhitespace()) it else '\u2588' }.joinToString("")
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = masked.ifBlank { stringResource(R.string.FlairBottomSheet__type_something_to_preview) },
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 16.dp, vertical = 14.dp)
    )

    Text(
      text = stringResource(R.string.FlairBottomSheet__hidden_text_explainer),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp)
    )
  }
}

@Composable
private fun ChipRow(labelRes: Int, content: @Composable () -> Unit) {
  Text(
    text = stringResource(labelRes),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
  )
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.horizontalScroll(rememberScrollState())
  ) {
    content()
  }
}

@Composable
private fun AnimatedTextPreview(
  text: String,
  effect: FlairEffect,
  font: FlairFont,
  background: FlairBackground
) {
  val context = LocalContext.current
  var timeSeconds by remember { mutableFloatStateOf(0f) }

  val typeface: Typeface = remember(font) { font.typeface(context) }

  // Decoding a picked photo touches disk, so keep it off the frame loop and fall back to the gradient
  // until it is ready (or if it fails).
  val photoBitmap: Bitmap? by produceState<Bitmap?>(initialValue = null, background) {
    value = (background as? FlairBackground.Photo)?.let { photo ->
      try {
        withContext(Dispatchers.IO) { FlairBackground.decodePhoto(context, photo.uri) }
      } catch (e: Exception) {
        Log.w("FlairBottomSheet", "Could not decode background photo for preview", e)
        null
      }
    }
  }

  val resolved: ResolvedBackground = when (background) {
    is FlairBackground.Gradient -> ResolvedBackground.Gradient
    is FlairBackground.Solid -> ResolvedBackground.Solid(background.color)
    is FlairBackground.Photo -> photoBitmap?.let { ResolvedBackground.Photo(it) } ?: ResolvedBackground.Gradient
  }

  val style = remember(effect, typeface, resolved) { FlairStyle(effect, typeface, resolved) }

  LaunchedEffect(Unit) {
    val start = withFrameNanos { it }
    while (true) {
      withFrameNanos { now -> timeSeconds = (now - start) / 1_000_000_000f }
    }
  }

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(AnimatedTextRenderer.VIDEO_WIDTH.toFloat() / AnimatedTextRenderer.VIDEO_HEIGHT)
      .clip(RoundedCornerShape(18.dp))
  ) {
    drawIntoCanvas { canvas ->
      AnimatedTextRenderer.draw(canvas.nativeCanvas, size.width.toInt(), size.height.toInt(), text, style, timeSeconds)
    }
  }
}
