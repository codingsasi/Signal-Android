/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.TextFields
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.MessageTagTable
import org.thoughtcrime.securesms.database.SignalDatabase

/**
 * Picks which private tags apply to one message. Tapping a row toggles it and saves immediately, so there is
 * no separate confirm step. Tags are device-local and never sent or synced.
 */
class MessageTagSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    const val REQUEST_KEY = "MessageTagSheet"
    private const val ARG_MESSAGE_ID = "message_id"
    private const val ARG_THREAD_ID = "thread_id"

    fun show(fragmentManager: FragmentManager, messageId: Long, threadId: Long) {
      MessageTagSheet().apply {
        arguments = bundleOf(ARG_MESSAGE_ID to messageId, ARG_THREAD_ID to threadId)
      }.show(fragmentManager, REQUEST_KEY)
    }
  }

  override val peekHeightPercentage: Float = 0.7f

  @Composable
  override fun SheetContent() {
    TagPickerContent(
      messageId = requireArguments().getLong(ARG_MESSAGE_ID),
      threadId = requireArguments().getLong(ARG_THREAD_ID),
      onDone = { dismissAllowingStateLoss() }
    )
  }
}

@Composable
private fun TagPickerContent(messageId: Long, threadId: Long, onDone: () -> Unit) {
  val scope = rememberCoroutineScope()
  val selected = remember { mutableStateListOf<String>() }
  var creating by remember { mutableStateOf(false) }
  var newTag by remember { mutableStateOf("") }
  var version by remember { mutableIntStateOf(0) }
  var initialised by remember { mutableStateOf(false) }

  val available: List<String> by produceState(initialValue = emptyList(), messageId, version) {
    val current = withContext(Dispatchers.IO) { SignalDatabase.messageTags.getTagsForMessage(messageId) }
    if (!initialised) {
      selected.clear()
      selected.addAll(current)
      initialised = true
    }
    value = withContext(Dispatchers.IO) { SignalDatabase.messageTags.getAllTags() }
  }

  /** Writes the current selection straight away, so closing the sheet never loses a change. */
  fun persist() {
    val snapshot = selected.toList()
    scope.launch {
      withContext(Dispatchers.IO) { SignalDatabase.messageTags.setTags(messageId, threadId, snapshot) }
    }
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
      text = stringResource(R.string.MessageTags__tag_message),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )

    Text(
      text = stringResource(R.string.MessageTags__private_to_you),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    if (available.isEmpty() && !creating) {
      Text(
        text = stringResource(R.string.MessageTags__no_tags_yet_manage),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp)
      )
    }

    available.forEach { tag ->
      val checked = selected.contains(tag)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .clickable {
            if (checked) selected.remove(tag) else selected.add(tag)
            persist()
          }
          .padding(vertical = 6.dp)
      ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(modifier = Modifier.size(12.dp))
        Text(text = tag, style = MaterialTheme.typography.bodyLarge)
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (creating) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextFields.TextField(
          value = newTag,
          onValueChange = { newTag = it.take(MessageTagTable.MAX_TAG_LENGTH) },
          label = { Text(stringResource(R.string.MessageTags__new_tag)) },
          singleLine = true,
          modifier = Modifier.weight(1f)
        )
        TextButton(
          onClick = {
            val name = MessageTagTable.normalize(newTag)
            newTag = ""
            creating = false
            if (name.isNotEmpty()) {
              if (!selected.contains(name)) {
                selected.add(name)
              }
              scope.launch {
                withContext(Dispatchers.IO) {
                  SignalDatabase.messageTags.defineTag(name)
                  SignalDatabase.messageTags.setTags(messageId, threadId, selected.toList())
                }
                version++
              }
            }
          },
          enabled = newTag.isNotBlank()
        ) {
          Text(stringResource(R.string.MessageTags__add))
        }
      }
    } else {
      TextButton(onClick = { creating = true }) {
        Text(stringResource(R.string.MessageTags__new_tag))
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    TextButton(
      onClick = onDone,
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(stringResource(R.string.MessageTags__done))
    }
  }
}
