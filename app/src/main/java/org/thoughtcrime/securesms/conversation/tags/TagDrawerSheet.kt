/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.TextFields
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.MessageTagTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The tag drawer. Filter this conversation by a private tag, jump to any tagged message, and manage the
 * tag list itself. Everything here is device-local and is never sent or synced.
 */
class TagDrawerSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    const val REQUEST_KEY = "TagDrawerSheet"
    const val RESULT_MESSAGE_ID = "message_id"
    private const val ARG_THREAD_ID = "thread_id"

    fun show(fragmentManager: FragmentManager, threadId: Long) {
      TagDrawerSheet().apply {
        arguments = bundleOf(ARG_THREAD_ID to threadId)
      }.show(fragmentManager, REQUEST_KEY)
    }
  }

  override val peekHeightPercentage: Float = 1f

  @Composable
  override fun SheetContent() {
    TagDrawerContent(
      threadId = requireArguments().getLong(ARG_THREAD_ID),
      onMessageClick = { messageId ->
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_MESSAGE_ID to messageId))
        dismissAllowingStateLoss()
      }
    )
  }
}

private data class TaggedMessage(val id: Long, val preview: String, val dateSent: Long)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagDrawerContent(threadId: Long, onMessageClick: (Long) -> Unit) {
  var managing by remember { mutableStateOf(false) }
  var activeTag by remember { mutableStateOf<String?>(null) }
  var reloadToken by remember { mutableIntStateOf(0) }

  val tags: List<MessageTagTable.TagCount> by produceState(initialValue = emptyList(), threadId, reloadToken) {
    value = withContext(Dispatchers.IO) { SignalDatabase.messageTags.getTagsWithCounts(threadId) }
  }

  val messages: List<TaggedMessage> by produceState(initialValue = emptyList(), activeTag, threadId, reloadToken) {
    val tag = activeTag
    value = withContext(Dispatchers.IO) {
      if (tag == null) allTaggedMessages(threadId) else loadTaggedMessages(threadId, tag)
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

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(R.string.MessageTags__tags),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.weight(1f)
      )

      TextButton(onClick = { managing = !managing }) {
        Text(
          stringResource(
            if (managing) R.string.MessageTags__done else R.string.MessageTags__manage
          )
        )
      }
    }

    Text(
      text = stringResource(R.string.MessageTags__private_to_you),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 12.dp)
    )

    if (managing) {
      ManageTags(onChanged = { reloadToken++ })
      return@Column
    }

    if (tags.isEmpty()) {
      Text(
        text = stringResource(R.string.MessageTags__no_tags_yet),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp)
      )
      return@Column
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      FilterChip(
        selected = activeTag == null,
        onClick = { activeTag = null },
        label = { Text(stringResource(R.string.MessageTags__all)) }
      )
      tags.forEach { tagCount ->
        FilterChip(
          selected = activeTag == tagCount.tag,
          onClick = { activeTag = tagCount.tag },
          label = { Text("${tagCount.tag}  ${tagCount.count}") }
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (messages.isEmpty()) {
      Text(
        text = stringResource(R.string.MessageTags__nothing_here),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp)
      )
    } else {
      messages.forEachIndexed { index, message ->
        if (index > 0) {
          Dividers.Default()
        }
        MessageRow(message = message, onClick = { onMessageClick(message.id) })
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManageTags(onChanged: () -> Unit) {
  val scope = rememberCoroutineScope()
  var newTag by remember { mutableStateOf("") }
  var renaming by remember { mutableStateOf<String?>(null) }
  var renameText by remember { mutableStateOf("") }
  var version by remember { mutableIntStateOf(0) }

  val defined: List<String> by produceState(initialValue = emptyList(), version) {
    value = withContext(Dispatchers.IO) { SignalDatabase.messageTags.getDefinedTags() }
  }

  val counts: Map<String, Int> by produceState(initialValue = emptyMap(), version) {
    value = withContext(Dispatchers.IO) { SignalDatabase.messageTags.getGlobalUsageCounts() }
  }

  fun refresh() {
    version++
    onChanged()
  }

  Row(verticalAlignment = Alignment.CenterVertically) {
    TextFields.TextField(
      value = newTag,
      onValueChange = { newTag = it.take(MessageTagTable.MAX_TAG_LENGTH) },
      label = { Text(stringResource(R.string.MessageTags__new_tag)) },
      singleLine = true,
      modifier = Modifier.weight(1f)
    )

    Spacer(modifier = Modifier.size(8.dp))

    TextButton(
      onClick = {
        val name = newTag
        newTag = ""
        scope.launch {
          withContext(Dispatchers.IO) { SignalDatabase.messageTags.defineTag(name) }
          refresh()
        }
      },
      enabled = newTag.isNotBlank()
    ) {
      Text(stringResource(R.string.MessageTags__add))
    }
  }

  Spacer(modifier = Modifier.height(8.dp))

  if (defined.isEmpty()) {
    Text(
      text = stringResource(R.string.MessageTags__no_tags_yet_manage),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = 16.dp)
    )
    return
  }

  defined.forEach { tag ->
    if (renaming == tag) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
      ) {
        TextFields.TextField(
          value = renameText,
          onValueChange = { renameText = it.take(MessageTagTable.MAX_TAG_LENGTH) },
          singleLine = true,
          modifier = Modifier.weight(1f)
        )
        TextButton(
          onClick = {
            val from = tag
            val to = renameText
            renaming = null
            scope.launch {
              withContext(Dispatchers.IO) { SignalDatabase.messageTags.renameTag(from, to) }
              refresh()
            }
          }
        ) {
          Text(stringResource(R.string.MessageTags__save))
        }
      }
    } else {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 6.dp)
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = tag, style = MaterialTheme.typography.bodyLarge)
          Text(
            text = stringResource(R.string.MessageTags__used_on_d, counts[tag] ?: 0),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        TextButton(
          onClick = {
            renameText = tag
            renaming = tag
          }
        ) {
          Text(stringResource(R.string.MessageTags__rename))
        }

        IconButton(
          onClick = {
            scope.launch {
              withContext(Dispatchers.IO) { SignalDatabase.messageTags.deleteTagEverywhere(tag) }
              refresh()
            }
          }
        ) {
          Icon(
            painter = painterResource(org.signal.core.ui.R.drawable.symbol_trash_24),
            contentDescription = stringResource(R.string.MessageTags__delete)
          )
        }
      }
    }
    Dividers.Default()
  }
}

@Composable
private fun MessageRow(message: TaggedMessage, onClick: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp)
  ) {
    Text(
      text = message.preview,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()).format(Date(message.dateSent)),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

private fun loadTaggedMessages(threadId: Long, tag: String): List<TaggedMessage> {
  return toMessages(SignalDatabase.messageTags.getMessageIdsForTag(threadId, tag))
}

private fun allTaggedMessages(threadId: Long): List<TaggedMessage> {
  val ids = SignalDatabase.messageTags
    .getTagsWithCounts(threadId)
    .flatMap { SignalDatabase.messageTags.getMessageIdsForTag(threadId, it.tag) }
    .distinct()
  return toMessages(ids)
}

private fun toMessages(ids: List<Long>): List<TaggedMessage> {
  return ids
    .mapNotNull { id -> SignalDatabase.messages.getMessageRecordOrNull(id) }
    .sortedByDescending { it.dateSent }
    .map { record -> TaggedMessage(record.id, previewFor(record), record.dateSent) }
}

private fun previewFor(record: MessageRecord): String {
  val body = record.body.trim()
  return body.ifEmpty { "Media message" }
}
