/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.messageinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.Dividers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase

/**
 * Shows everything this client knows about one message. Opened by double tapping a received message,
 * a gesture that is otherwise unused there because double tap to edit only applies to your own messages.
 */
class MessageInfoSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    private val TAG = Log.tag(MessageInfoSheet::class)
    private const val ARG_MESSAGE_ID = "message_id"

    fun show(fragmentManager: FragmentManager, messageId: Long) {
      MessageInfoSheet().apply {
        arguments = bundleOf(ARG_MESSAGE_ID to messageId)
      }.show(fragmentManager, "MessageInfoSheet")
    }
  }

  override val peekHeightPercentage: Float = 0.75f

  @Composable
  override fun SheetContent() {
    val context = LocalContext.current
    val messageId = requireArguments().getLong(ARG_MESSAGE_ID)

    val sections: List<InfoSection>? by produceState<List<InfoSection>?>(initialValue = null, messageId) {
      value = withContext(Dispatchers.IO) {
        try {
          MessageInfo.collect(context, SignalDatabase.messages.getMessageRecord(messageId))
        } catch (e: Exception) {
          Log.w(TAG, "Could not load details for message $messageId", e)
          emptyList()
        }
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)
        .padding(bottom = 32.dp)
    ) {
      BottomSheets.Handle(modifier = Modifier.align(Alignment.CenterHorizontally))

      Text(
        text = "Message info",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
      )

      when {
        sections == null -> Text(
          text = "Loading…",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
          textAlign = TextAlign.Center
        )

        sections!!.isEmpty() -> Text(
          text = "This message is no longer available.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 24.dp)
        )

        else -> sections!!.forEachIndexed { index, section ->
          if (index > 0) {
            Dividers.Default()
          }
          SectionBlock(section)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Signal does not transmit the sender's network, carrier, location, battery, or app version, so none of that exists to show.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun SectionBlock(section: InfoSection) {
  Text(
    text = section.title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
  )

  section.rows.forEach { row ->
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 3.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      Text(
        text = row.label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f)
      )

      Spacer(modifier = Modifier.width(12.dp))

      Text(
        text = row.value,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1.2f)
      )
    }
  }
}
