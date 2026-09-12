/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.flair

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import androidx.annotation.WorkerThread
import kotlinx.parcelize.Parcelize
import java.io.IOException
import kotlin.math.max

/** What sits behind the animated text. Chosen in the sheet, resolved to drawable form right before rendering. */
sealed class FlairBackground : Parcelable {

  @Parcelize
  object Gradient : FlairBackground()

  @Parcelize
  data class Solid(val color: Int) : FlairBackground()

  @Parcelize
  data class Photo(val uri: Uri) : FlairBackground()

  @WorkerThread
  @Throws(IOException::class)
  fun resolve(context: Context): ResolvedBackground {
    return when (this) {
      is Gradient -> ResolvedBackground.Gradient
      is Solid -> ResolvedBackground.Solid(color)
      is Photo -> ResolvedBackground.Photo(decodePhoto(context, uri))
    }
  }

  companion object {
    private const val TARGET_WIDTH = AnimatedTextRenderer.VIDEO_WIDTH * 2
    private const val TARGET_HEIGHT = AnimatedTextRenderer.VIDEO_HEIGHT * 2

    /** Decodes a downsampled, EXIF-corrected bitmap and center-crops it to the video aspect ratio. */
    @WorkerThread
    @Throws(IOException::class)
    fun decodePhoto(context: Context, uri: Uri): Bitmap {
      val decoded: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
          val sample = max(1, minOf(info.size.width / TARGET_WIDTH, info.size.height / TARGET_HEIGHT))
          decoder.setTargetSampleSize(sample)
          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          decoder.isMutableRequired = false
        }
      } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: throw IOException("Cannot open $uri")
        val options = BitmapFactory.Options().apply {
          inSampleSize = max(1, minOf(bounds.outWidth / TARGET_WIDTH, bounds.outHeight / TARGET_HEIGHT))
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: throw IOException("Cannot decode $uri")
      }

      val targetRatio = TARGET_WIDTH.toFloat() / TARGET_HEIGHT
      val sourceRatio = decoded.width.toFloat() / decoded.height
      val cropWidth: Int
      val cropHeight: Int
      if (sourceRatio > targetRatio) {
        cropHeight = decoded.height
        cropWidth = (cropHeight * targetRatio).toInt()
      } else {
        cropWidth = decoded.width
        cropHeight = (cropWidth / targetRatio).toInt()
      }
      val x = (decoded.width - cropWidth) / 2
      val y = (decoded.height - cropHeight) / 2
      val cropped = Bitmap.createBitmap(decoded, x, y, cropWidth, cropHeight)
      val scaled = Bitmap.createScaledBitmap(cropped, TARGET_WIDTH, TARGET_HEIGHT, true)
      if (cropped !== decoded && cropped !== scaled) {
        cropped.recycle()
      }
      if (decoded !== scaled) {
        decoded.recycle()
      }
      return scaled
    }
  }
}

sealed class ResolvedBackground {
  object Gradient : ResolvedBackground()
  data class Solid(val color: Int) : ResolvedBackground()
  data class Photo(val bitmap: Bitmap) : ResolvedBackground()
}

/** Everything the renderer needs besides the text and the time. */
data class FlairStyle(
  val effect: FlairEffect,
  val typeface: android.graphics.Typeface,
  val background: ResolvedBackground
)

/** Everything the sheet collected, carried back to the fragment as a fragment result. */
@Parcelize
data class FlairRequest(
  val text: String,
  val effect: FlairEffect,
  val font: FlairFont,
  val background: FlairBackground
) : Parcelable
