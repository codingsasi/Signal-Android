/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.flair

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.Shader
import java.text.BreakIterator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws a single frame of animated text onto a [Canvas]. Used both for the live preview in
 * [FlairBottomSheet] and for every frame fed to [AnimatedTextMp4Encoder], so what you see is what gets sent.
 *
 * All motion is expressed in terms of a normalized loop phase so the resulting video loops seamlessly, and
 * every random-looking value is derived from a hash of the frame and glyph index rather than a RNG, so the
 * preview and the encoded video agree frame for frame.
 */
object AnimatedTextRenderer {

  const val VIDEO_WIDTH = 512
  const val VIDEO_HEIGHT = 288
  const val FRAMES_PER_SECOND = 20
  const val MAX_TEXT_LENGTH = 80

  private const val BASE_LOOP_SECONDS = 3f
  private const val HORIZONTAL_INSET_FRACTION = 0.08f
  private const val VERTICAL_FILL_FRACTION = 0.72f
  private const val EMOJI_FILL_FRACTION = 0.92f
  private const val MAX_LINES = 3
  private const val CONFETTI_COUNT = 46

  private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val confettiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
  private val photoDestination = Rect()
  private val glyphPath = Path()
  private val strokeSegment = Path()
  private val pathMeasure = PathMeasure()

  private val confettiColors = intArrayOf(0xFFFF5C7A.toInt(), 0xFFFFD166.toInt(), 0xFF6BCB77.toInt(), 0xFF4D96FF.toInt(), 0xFFB983FF.toInt())

  /** Length of one full animation loop for the given text and effect. */
  fun durationSeconds(text: String, effect: FlairEffect): Float {
    val count = glyphs(text.take(MAX_TEXT_LENGTH)).size
    return when (effect) {
      FlairEffect.TYPEWRITER -> max(2.5f, count * 0.09f + 1.4f)
      FlairEffect.HANDWRITING -> max(3f, count * 0.22f + 1.2f)
      FlairEffect.SLIDE_IN -> max(3f, count * 0.07f + 1.6f)
      else -> BASE_LOOP_SECONDS
    }
  }

  fun draw(canvas: Canvas, width: Int, height: Int, text: String, style: FlairStyle, timeSeconds: Float) {
    val effect = style.effect
    val duration = durationSeconds(text, effect)
    val phase = ((timeSeconds % duration) + duration) % duration / duration
    val frameIndex = floor(timeSeconds * FRAMES_PER_SECOND).toInt()

    drawBackground(canvas, width, height, style.background)

    if (effect == FlairEffect.CONFETTI) {
      drawConfetti(canvas, width, height, phase)
    }

    val glyphs = glyphs(text.take(MAX_TEXT_LENGTH))
    if (glyphs.isEmpty()) {
      return
    }

    textPaint.typeface = style.typeface
    strokePaint.typeface = style.typeface

    val emojiOnly = isEmojiOnly(glyphs)
    val lines = layoutLines(glyphs, width, height, emojiOnly)
    val lineHeight = textPaint.fontSpacing
    var baseline = (height - lineHeight * lines.size) / 2f - textPaint.fontMetrics.ascent

    val total = glyphs.size
    val visibleCount = if (effect == FlairEffect.TYPEWRITER) typewriterVisibleCount(phase, total) else Int.MAX_VALUE
    val heartbeatScale = if (effect == FlairEffect.HEARTBEAT) heartbeatScale(phase) else 1f

    if (effect == FlairEffect.SHINE) {
      applyShineShader(width, phase)
    } else {
      textPaint.shader = null
    }

    var index = 0
    for (line in lines) {
      val lineWidth = line.sumOf { textPaint.measureText(it).toDouble() }.toFloat()
      var x = (width - lineWidth) / 2f

      for (glyph in line) {
        val advance = textPaint.measureText(glyph)
        if (index < visibleCount) {
          val (dx, dy) = offsetFor(effect, phase, index, total, frameIndex, height)
          val alpha = alphaFor(effect, phase, index, total)

          canvas.save()
          if (heartbeatScale != 1f) {
            canvas.scale(heartbeatScale, heartbeatScale, width / 2f, baseline - textPaint.textSize / 3f)
          }

          when (effect) {
            FlairEffect.NEON -> drawNeonGlyph(canvas, glyph, x + dx, baseline + dy, index, frameIndex)
            FlairEffect.HANDWRITING -> drawHandwrittenGlyph(canvas, glyph, x + dx, baseline + dy, index, total, phase)
            else -> {
              if (effect != FlairEffect.SHINE) {
                textPaint.color = colorFor(effect, phase, index, total)
              } else {
                textPaint.color = Color.WHITE
              }
              textPaint.alpha = alpha
              canvas.drawText(glyph, x + dx, baseline + dy, textPaint)
              textPaint.alpha = 255
            }
          }
          canvas.restore()
        } else if (effect == FlairEffect.TYPEWRITER && index == visibleCount && (frameIndex / (FRAMES_PER_SECOND / 2)) % 2 == 0) {
          textPaint.shader = null
          textPaint.color = Color.WHITE
          canvas.drawText("▌", x, baseline, textPaint)
        }

        x += advance
        index++
      }
      baseline += lineHeight
    }

    textPaint.shader = null
  }

  // region Backgrounds

  private fun drawBackground(canvas: Canvas, width: Int, height: Int, background: ResolvedBackground) {
    when (background) {
      is ResolvedBackground.Gradient -> {
        backgroundPaint.shader = LinearGradient(
          0f,
          0f,
          0f,
          height.toFloat(),
          Color.rgb(0x1B, 0x1F, 0x3B),
          Color.rgb(0x35, 0x23, 0x5B),
          Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        backgroundPaint.shader = null
      }

      is ResolvedBackground.Solid -> {
        backgroundPaint.shader = null
        backgroundPaint.color = background.color
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
      }

      is ResolvedBackground.Photo -> {
        backgroundPaint.shader = null
        photoDestination.set(0, 0, width, height)
        canvas.drawBitmap(background.bitmap, null, photoDestination, backgroundPaint)
        // Darken so white text stays readable over any photo.
        backgroundPaint.color = 0x66000000
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
      }
    }
  }

  private fun drawConfetti(canvas: Canvas, width: Int, height: Int, phase: Float) {
    val size = height * 0.028f
    for (i in 0 until CONFETTI_COUNT) {
      val seed = i.toLong() * 6364136223846793005L
      val startX = (hashToUnit(seed) * 0.5f + 0.5f) * width
      val speed = 0.6f + (hashToUnit(seed xor 11L) * 0.5f + 0.5f) * 0.8f
      val drift = hashToUnit(seed xor 22L) * width * 0.06f
      val offset = hashToUnit(seed xor 33L) * 0.5f + 0.5f

      val travel = ((phase * speed + offset) % 1f)
      val y = travel * (height + size * 4) - size * 2
      val x = startX + sin(travel * 2 * PI).toFloat() * drift
      val spin = travel * 720f + i * 37f

      confettiPaint.color = confettiColors[i % confettiColors.size]
      confettiPaint.alpha = 190
      canvas.save()
      canvas.rotate(spin, x, y)
      canvas.drawRect(x - size / 2f, y - size / 4f, x + size / 2f, y + size / 4f, confettiPaint)
      canvas.restore()
    }
  }

  // endregion

  // region Per-effect glyph drawing

  private fun drawNeonGlyph(canvas: Canvas, glyph: String, x: Float, y: Float, index: Int, frameIndex: Int) {
    val flickering = isFlickering(index, frameIndex)
    val core = if (flickering) 0xFF6A5A7A.toInt() else Color.WHITE
    val glow = if (flickering) 0x33FF3D9A else 0xCCFF3D9A.toInt()

    glowPaint.set(textPaint)
    glowPaint.color = glow
    glowPaint.maskFilter = BlurMaskFilter(textPaint.textSize * 0.28f, BlurMaskFilter.Blur.NORMAL)
    canvas.drawText(glyph, x, y, glowPaint)
    glowPaint.maskFilter = null

    textPaint.color = core
    canvas.drawText(glyph, x, y, textPaint)
  }

  /** One letter at a time drops out for a couple of frames, like a failing tube. */
  private fun isFlickering(index: Int, frameIndex: Int): Boolean {
    val window = frameIndex / 4
    val chosen = (abs(hashToUnit(window.toLong() * 2654435761L)) * 997).toInt()
    return chosen % 11 == index % 11 && hashToUnit(window.toLong() xor 77L) > 0.2f
  }

  private fun drawHandwrittenGlyph(canvas: Canvas, glyph: String, x: Float, y: Float, index: Int, total: Int, phase: Float) {
    val perGlyph = 1f / max(total, 1)
    val progress = ((phase - index * perGlyph * 0.85f) / perGlyph).coerceIn(0f, 1f)
    if (progress <= 0f) {
      return
    }

    glyphPath.reset()
    textPaint.getTextPath(glyph, 0, glyph.length, x, y, glyphPath)

    if (progress >= 1f) {
      strokePaint.color = Color.WHITE
      strokePaint.strokeWidth = max(2f, textPaint.textSize * 0.055f)
      canvas.drawPath(glyphPath, strokePaint)
      return
    }

    strokePaint.color = Color.WHITE
    strokePaint.strokeWidth = max(2f, textPaint.textSize * 0.055f)
    pathMeasure.setPath(glyphPath, false)
    do {
      val length = pathMeasure.length
      if (length > 0f) {
        strokeSegment.reset()
        pathMeasure.getSegment(0f, length * progress, strokeSegment, true)
        canvas.drawPath(strokeSegment, strokePaint)
      }
    } while (pathMeasure.nextContour())
  }

  private fun applyShineShader(width: Int, phase: Float) {
    val bandWidth = width * 0.35f
    val center = (phase * (width + bandWidth * 2)) - bandWidth
    textPaint.shader = LinearGradient(
      center - bandWidth / 2f,
      0f,
      center + bandWidth / 2f,
      0f,
      intArrayOf(0xFF9AA0A6.toInt(), Color.WHITE, 0xFF9AA0A6.toInt()),
      floatArrayOf(0f, 0.5f, 1f),
      Shader.TileMode.CLAMP
    )
  }

  // endregion

  // region Motion

  private fun offsetFor(effect: FlairEffect, phase: Float, index: Int, count: Int, frameIndex: Int, height: Int): Pair<Float, Float> {
    val amplitude = height * 0.11f
    return when (effect) {
      FlairEffect.BOUNCE -> 0f to -abs(sin(PI * 3 * phase + index * 0.4)).toFloat() * amplitude
      FlairEffect.WAVE -> 0f to sin(2 * PI * 3 * phase + index * 0.55).toFloat() * amplitude * 0.6f
      FlairEffect.RAINBOW -> 0f to sin(2 * PI * 2 * phase + index * 0.35).toFloat() * amplitude * 0.25f
      FlairEffect.SHAKE -> {
        val jitter = height * 0.02f
        val seed = (index * 7919 + frameIndex * 104729).toLong()
        hashToUnit(seed) * jitter to hashToUnit(seed xor 0x5DEECE66DL) * jitter
      }
      FlairEffect.SLIDE_IN -> slideInOffset(phase, index, count, height)
      FlairEffect.NEON, FlairEffect.CONFETTI, FlairEffect.HEARTBEAT, FlairEffect.SHINE, FlairEffect.TYPEWRITER, FlairEffect.HANDWRITING -> 0f to 0f
    }
  }

  /** Letters fly in from alternating sides and settle with a damped overshoot, then hold. */
  private fun slideInOffset(phase: Float, index: Int, count: Int, height: Int): Pair<Float, Float> {
    val stagger = 0.5f / max(count, 1)
    val local = ((phase - index * stagger) / 0.35f).coerceIn(0f, 1f)
    if (local >= 1f) {
      return 0f to 0f
    }
    val distance = height * 1.4f * (if (index % 2 == 0) -1f else 1f)
    val settle = 1f - exp(-6f * local) * kotlin.math.cos(10f * local)
    return distance * (1f - settle) to 0f
  }

  private fun heartbeatScale(phase: Float): Float {
    val beat = { t: Float -> exp(-28f * t * t) }
    val first = beat(phase - 0.12f)
    val second = beat(phase - 0.30f) * 0.65f
    return 1f + (first + second) * 0.16f
  }

  private fun alphaFor(effect: FlairEffect, phase: Float, index: Int, count: Int): Int {
    if (effect != FlairEffect.SLIDE_IN) {
      return 255
    }
    val stagger = 0.5f / max(count, 1)
    val local = ((phase - index * stagger) / 0.2f).coerceIn(0f, 1f)
    return (local * 255).toInt().coerceIn(0, 255)
  }

  private fun colorFor(effect: FlairEffect, phase: Float, index: Int, count: Int): Int {
    return when (effect) {
      FlairEffect.RAINBOW -> {
        val hue = ((phase * 360f) + index * (360f / max(count, 1))) % 360f
        Color.HSVToColor(floatArrayOf(hue, 0.75f, 1f))
      }
      else -> Color.WHITE
    }
  }

  private fun typewriterVisibleCount(phase: Float, count: Int): Int {
    val pauseSlots = 8
    val slot = floor(phase * (count + pauseSlots)).toInt()
    return slot.coerceIn(0, count)
  }

  /** Deterministic pseudo-random value in [-1, 1] so effects are reproducible between preview and encode. */
  private fun hashToUnit(seed: Long): Float {
    var x = seed
    x = x xor (x ushr 33)
    x *= -0xae502812aa7333L
    x = x xor (x ushr 33)
    x *= -0x3b314601e57a13adL
    x = x xor (x ushr 33)
    return ((x and 0xFFFF) / 32767.5f) - 1f
  }

  // endregion

  // region Layout

  /** Splits into user-perceived characters so emoji and combining marks move as one unit. */
  private fun glyphs(text: String): List<String> {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val result = ArrayList<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
      result.add(text.substring(start, end))
      start = end
      end = iterator.next()
    }
    return result
  }

  /** True when the text is nothing but emoji and spaces, which we blow up to fill the frame. */
  private fun isEmojiOnly(glyphs: List<String>): Boolean {
    var sawEmoji = false
    for (glyph in glyphs) {
      if (glyph.isBlank()) {
        continue
      }
      val codePoint = glyph.codePointAt(0)
      val emoji = codePoint >= 0x1F000 ||
        (codePoint in 0x2190..0x2BFF) ||
        (codePoint in 0xFE00..0xFE0F) ||
        (codePoint in 0x2600..0x27BF)
      if (!emoji) {
        return false
      }
      sawEmoji = true
    }
    return sawEmoji
  }

  /** Picks the largest font size whose word-wrapped layout fits, and returns the wrapped lines as glyph lists. */
  private fun layoutLines(glyphs: List<String>, width: Int, height: Int, emojiOnly: Boolean): List<List<String>> {
    val maxWidth = width * (1 - 2 * HORIZONTAL_INSET_FRACTION)
    val maxHeight = height * (if (emojiOnly) EMOJI_FILL_FRACTION else VERTICAL_FILL_FRACTION)
    var size = height * (if (emojiOnly) 0.8f else 0.32f)
    val minSize = height * 0.08f

    while (true) {
      textPaint.textSize = size
      strokePaint.textSize = size
      val lines = wrap(glyphs, maxWidth)
      val fits = lines.size <= MAX_LINES &&
        lines.size * textPaint.fontSpacing <= maxHeight &&
        lines.all { line -> line.sumOf { textPaint.measureText(it).toDouble() } <= maxWidth }
      if (fits || size <= minSize) {
        return lines
      }
      size -= height * 0.02f
    }
  }

  private fun wrap(glyphs: List<String>, maxWidth: Float): List<List<String>> {
    val words = ArrayList<List<String>>()
    var current = ArrayList<String>()
    for (glyph in glyphs) {
      if (glyph == " ") {
        if (current.isNotEmpty()) {
          words.add(current)
          current = ArrayList()
        }
      } else if (glyph == "\n") {
        if (current.isNotEmpty()) {
          words.add(current)
          current = ArrayList()
        }
        words.add(emptyList())
      } else {
        current.add(glyph)
      }
    }
    if (current.isNotEmpty()) {
      words.add(current)
    }

    val lines = ArrayList<List<String>>()
    var line = ArrayList<String>()
    var lineWidth = 0f
    val spaceWidth = textPaint.measureText(" ")

    for (word in words) {
      if (word.isEmpty()) {
        lines.add(line)
        line = ArrayList()
        lineWidth = 0f
        continue
      }
      val wordWidth = word.sumOf { textPaint.measureText(it).toDouble() }.toFloat()
      val needed = if (line.isEmpty()) wordWidth else lineWidth + spaceWidth + wordWidth
      if (line.isNotEmpty() && needed > maxWidth) {
        lines.add(line)
        line = ArrayList()
        lineWidth = 0f
      }
      if (line.isNotEmpty()) {
        line.add(" ")
        lineWidth += spaceWidth
      }
      line.addAll(word)
      lineWidth += wordWidth
    }
    if (line.isNotEmpty() || lines.isEmpty()) {
      lines.add(line)
    }
    return lines
  }

  // endregion
}
