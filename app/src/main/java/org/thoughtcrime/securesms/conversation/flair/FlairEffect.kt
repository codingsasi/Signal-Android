/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.flair

import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R

/**
 * Animation styles available for animated-text GIFs. Each effect is rendered by [AnimatedTextRenderer]
 * and is authored so that one full loop fits exactly in [AnimatedTextRenderer.durationSeconds].
 */
enum class FlairEffect(@StringRes val titleRes: Int) {
  BOUNCE(R.string.FlairBottomSheet__effect_bounce),
  WAVE(R.string.FlairBottomSheet__effect_wave),
  RAINBOW(R.string.FlairBottomSheet__effect_rainbow),
  TYPEWRITER(R.string.FlairBottomSheet__effect_typewriter),
  SHAKE(R.string.FlairBottomSheet__effect_shake),
  NEON(R.string.FlairBottomSheet__effect_neon),
  SLIDE_IN(R.string.FlairBottomSheet__effect_slide_in),
  CONFETTI(R.string.FlairBottomSheet__effect_confetti),
  HEARTBEAT(R.string.FlairBottomSheet__effect_heartbeat),
  SHINE(R.string.FlairBottomSheet__effect_shine),
  HANDWRITING(R.string.FlairBottomSheet__effect_handwriting)
}
