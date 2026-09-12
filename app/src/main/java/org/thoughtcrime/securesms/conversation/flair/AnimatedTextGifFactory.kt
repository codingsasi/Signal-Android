/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.flair

import android.content.Context
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.VideoSlide
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Renders animated text into a looping MP4 and wraps it as a "video GIF" slide, which stock Signal clients
 * autoplay and loop in the conversation exactly like a Giphy result.
 */
object AnimatedTextGifFactory {

  @WorkerThread
  @Throws(IOException::class)
  fun create(context: Context, request: FlairRequest): VideoSlide {
    val width = AnimatedTextRenderer.VIDEO_WIDTH
    val height = AnimatedTextRenderer.VIDEO_HEIGHT
    val tempFile = File(context.cacheDir, "flair-${System.currentTimeMillis()}.mp4")

    val background = request.background.resolve(context)
    val style = FlairStyle(
      effect = request.effect,
      typeface = request.font.typeface(context),
      background = background
    )

    try {
      AnimatedTextMp4Encoder(width, height, AnimatedTextRenderer.FRAMES_PER_SECOND)
        .encode(tempFile, AnimatedTextRenderer.durationSeconds(request.text, request.effect)) { canvas, time ->
          AnimatedTextRenderer.draw(canvas, width, height, request.text, style, time)
        }

      val size = tempFile.length()
      val uri = FileInputStream(tempFile).use { stream ->
        AppDependencies.blobs
          .forData(stream, size)
          .withMimeType(MediaUtil.VIDEO_MP4)
          .withFileName("flair.mp4")
          .createForSingleSessionOnDisk(context)
      }

      return VideoSlide(context, uri, size, true, width, height, null, null)
    } finally {
      tempFile.delete()
      (background as? ResolvedBackground.Photo)?.bitmap?.recycle()
    }
  }
}
