/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.flair

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import org.thoughtcrime.securesms.R

/**
 * Typefaces offered for animated text. Bundled fonts live in res/font (licenses alongside this package);
 * the rest map to system families so they cost nothing.
 */
enum class FlairFont(
  @StringRes val titleRes: Int,
  @FontRes private val fontRes: Int,
  private val systemFamily: String?,
  private val bold: Boolean
) {
  BOLD(R.string.FlairBottomSheet__font_bold, 0, null, true),
  SERIF(R.string.FlairBottomSheet__font_serif, 0, "serif", true),
  MONO(R.string.FlairBottomSheet__font_mono, 0, "monospace", true),
  MARKER(R.string.FlairBottomSheet__font_marker, R.font.flair_marker, "casual", false),
  SCRIPT(R.string.FlairBottomSheet__font_script, R.font.flair_script, "cursive", false),
  COMIC(R.string.FlairBottomSheet__font_comic, R.font.flair_comic, "sans-serif-black", false);

  fun typeface(context: Context): Typeface {
    val bundled = if (fontRes != 0) ResourcesCompat.getFont(context, fontRes) else null
    if (bundled != null) {
      return bundled
    }
    val style = if (bold) Typeface.BOLD else Typeface.NORMAL
    return if (systemFamily != null) Typeface.create(systemFamily, style) else Typeface.create(Typeface.DEFAULT, style)
  }
}
