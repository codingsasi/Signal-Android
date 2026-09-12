/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.flair

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import org.signal.core.util.logging.Log
import java.io.File
import java.io.IOException

/**
 * Encodes Canvas-drawn frames into a small H.264 MP4 using MediaCodec with byte-buffer (YUV) input.
 *
 * Byte-buffer input is used instead of a Surface so every frame gets an exact presentation timestamp,
 * which keeps the loop timing identical to the preview regardless of how fast frames are drawn.
 */
class AnimatedTextMp4Encoder(
  private val width: Int,
  private val height: Int,
  private val framesPerSecond: Int
) {

  companion object {
    private val TAG = Log.tag(AnimatedTextMp4Encoder::class.java)
    private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val BIT_RATE = 1_500_000
    private const val TIMEOUT_US = 10_000L
  }

  @Throws(IOException::class)
  fun encode(output: File, durationSeconds: Float, drawFrame: (canvas: Canvas, timeSeconds: Float) -> Unit) {
    val totalFrames = (durationSeconds * framesPerSecond).toInt().coerceAtLeast(1)
    val frameDurationUs = 1_000_000L / framesPerSecond

    val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
      setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
      setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
      setInteger(MediaFormat.KEY_FRAME_RATE, framesPerSecond)
      setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    val codec = MediaCodec.createEncoderByType(MIME_TYPE)
    val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pixels = IntArray(width * height)
    val bufferInfo = MediaCodec.BufferInfo()

    var trackIndex = -1
    var muxerStarted = false

    try {
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      codec.start()

      var frameIndex = 0
      var inputDone = false
      var outputDone = false

      while (!outputDone) {
        if (!inputDone) {
          val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
          if (inputIndex >= 0) {
            if (frameIndex >= totalFrames) {
              codec.queueInputBuffer(inputIndex, 0, 0, frameIndex * frameDurationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              val capacity = codec.getInputBuffer(inputIndex)?.capacity() ?: throw IOException("No input buffer")
              val image = codec.getInputImage(inputIndex) ?: throw IOException("Encoder does not expose input images")

              drawFrame(canvas, frameIndex.toFloat() / framesPerSecond)
              bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
              writeYuv(image, pixels)

              codec.queueInputBuffer(inputIndex, 0, capacity, frameIndex * frameDurationUs, 0)
              frameIndex++
            }
          }
        }

        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
        when {
          outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            check(!muxerStarted) { "Format changed twice" }
            trackIndex = muxer.addTrack(codec.outputFormat)
            muxer.start()
            muxerStarted = true
          }
          outputIndex >= 0 -> {
            val encoded = codec.getOutputBuffer(outputIndex) ?: throw IOException("No output buffer")
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
              bufferInfo.size = 0
            }
            if (bufferInfo.size > 0) {
              check(muxerStarted) { "Muxer has not started" }
              encoded.position(bufferInfo.offset)
              encoded.limit(bufferInfo.offset + bufferInfo.size)
              muxer.writeSampleData(trackIndex, encoded, bufferInfo)
            }
            codec.releaseOutputBuffer(outputIndex, false)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
              outputDone = true
            }
          }
        }
      }
    } finally {
      try {
        codec.stop()
      } catch (e: IllegalStateException) {
        Log.w(TAG, "Failed to stop codec cleanly", e)
      }
      codec.release()
      if (muxerStarted) {
        try {
          muxer.stop()
        } catch (e: IllegalStateException) {
          Log.w(TAG, "Failed to stop muxer cleanly", e)
        }
      }
      muxer.release()
      bitmap.recycle()
    }
  }

  /** Converts ARGB pixels to the encoder's flexible YUV 4:2:0 layout, honoring each plane's row and pixel stride. */
  private fun writeYuv(image: Image, pixels: IntArray) {
    val planes = image.planes
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride

    for (row in 0 until height) {
      val pixelRow = row * width
      val yRow = row * yRowStride
      val chromaRow = row / 2
      for (col in 0 until width) {
        val argb = pixels[pixelRow + col]
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF

        val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
        yBuffer.put(yRow + col * yPixelStride, y.coerceIn(16, 235).toByte())

        if (row % 2 == 0 && col % 2 == 0) {
          val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
          val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
          val chromaCol = col / 2
          uBuffer.put(chromaRow * uRowStride + chromaCol * uPixelStride, u.coerceIn(16, 240).toByte())
          vBuffer.put(chromaRow * vRowStride + chromaCol * vPixelStride, v.coerceIn(16, 240).toByte())
        }
      }
    }
  }
}
