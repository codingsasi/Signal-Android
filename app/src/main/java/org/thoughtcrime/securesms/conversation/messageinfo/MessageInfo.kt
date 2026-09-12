/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.messageinfo

import android.content.Context
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.recipients.Recipient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One labelled fact about a message. */
data class InfoRow(val label: String, val value: String)

/** A titled group of [InfoRow]s, rendered as one block in the sheet. */
data class InfoSection(val title: String, val rows: List<InfoRow>)

/**
 * Collects everything this client actually knows about a single message.
 *
 * Note on what is deliberately absent: nothing here describes the sender's network, carrier, battery,
 * IP address, app version, or location. Signal does not transmit any of that, and with sealed sender the
 * service itself usually cannot tell who sent a message, so there is no such data to surface.
 */
object MessageInfo {

  private const val NOT_SET = "Not set"

  @WorkerThread
  fun collect(context: Context, record: MessageRecord): List<InfoSection> {
    val sections = mutableListOf<InfoSection>()

    sections += timing(record)
    sections += origin(context, record)
    sections += state(record)
    content(record)?.let { sections += it }
    attachments(record).forEach { sections += it }
    reactions(context, record)?.let { sections += it }
    edits(record)?.let { sections += it }
    retraction(record)?.let { sections += it }

    return sections
  }

  private fun timing(record: MessageRecord): InfoSection {
    val rows = mutableListOf<InfoRow>()
    rows += InfoRow("Sent by their device", formatTime(record.dateSent))
    rows += InfoRow("Received by server", formatTime(record.serverTimestamp))
    rows += InfoRow("Arrived on this phone", formatTime(record.dateReceived))

    // The three clocks come from three different machines, so the gaps are the only latency signal available.
    if (record.serverTimestamp > 0) {
      rows += InfoRow("Their clock vs server", formatOffset(record.dateSent - record.serverTimestamp))
      rows += InfoRow("Server to this phone", formatDuration(record.dateReceived - record.serverTimestamp))
    }

    if (record.notifiedTimestamp > 0) {
      rows += InfoRow("You were notified", formatTime(record.notifiedTimestamp))
    }

    return InfoSection("Timing", rows)
  }

  private fun origin(context: Context, record: MessageRecord): InfoSection {
    val rows = mutableListOf<InfoRow>()
    rows += InfoRow("From", record.fromRecipient.getDisplayName(context))
    rows += InfoRow("Their device", describeDevice(record.fromDeviceId))
    rows += InfoRow("Sealed sender", if (record.isUnidentified) "Yes, server could not see the sender" else "No")
    rows += InfoRow("End-to-end encrypted", if (record.isSecure || record.isPush) "Yes" else "No")
    rows += InfoRow("Safety number issue", if (record.isIdentityMismatchFailure) "Yes" else "None")
    return InfoSection("Origin", rows)
  }

  private fun state(record: MessageRecord): InfoSection {
    val rows = mutableListOf<InfoRow>()
    // Read state is only tracked on the MMS-backed record, which is what every modern message is.
    (record as? MmsMessageRecord)?.let { rows += InfoRow("Read by you", if (it.isRead) "Yes" else "No") }
    rows += InfoRow("Disappearing timer", if (record.expiresIn > 0) formatDuration(record.expiresIn) else "Off")
    if (record.expireStarted > 0) {
      rows += InfoRow("Disappears at", formatTime(record.expireStarted + record.expiresIn))
    }
    rows += InfoRow("View once", if (record.isViewOnce) "Yes" else "No")
    rows += InfoRow("Starred", if (record.isStarred) "Yes" else "No")
    if (record.isRemoteDelete) {
      rows += InfoRow("Deleted", "Yes, content is gone")
    }
    return InfoSection("State", rows)
  }

  private fun content(record: MessageRecord): InfoSection? {
    val rows = mutableListOf<InfoRow>()
    val body = record.body

    rows += InfoRow("Characters", body.length.toString())
    rows += InfoRow("Words", if (body.isBlank()) "0" else body.trim().split(Regex("\\s+")).size.toString())

    val styles = record.messageRanges?.ranges.orEmpty().mapNotNull { it.style?.name }
    if (styles.isNotEmpty()) {
      rows += InfoRow("Formatting", styles.joinToString(", ") { it.lowercase(Locale.US) })
    }

    val mms = record as? MmsMessageRecord
    if (mms?.quote != null) {
      rows += InfoRow("Replying to", "Yes")
    }
    if (mms?.linkPreviews?.isNotEmpty() == true) {
      rows += InfoRow("Link preview", mms.linkPreviews.first().url)
    }
    if (mms?.sharedContacts?.isNotEmpty() == true) {
      rows += InfoRow("Shared contacts", mms.sharedContacts.size.toString())
    }
    if (mms?.hasSelfMention() == true) {
      rows += InfoRow("Mentions you", "Yes")
    }

    return if (rows.isEmpty()) null else InfoSection("Content", rows)
  }

  private fun attachments(record: MessageRecord): List<InfoSection> {
    val slides: List<Slide> = (record as? MmsMessageRecord)?.slideDeck?.slides ?: return emptyList()

    return slides.mapIndexed { index, slide ->
      val rows = mutableListOf<InfoRow>()
      rows += InfoRow("Type", slide.contentType)
      rows += InfoRow("Size", formatBytes(slide.fileSize))
      slide.fileName.orElse(null)?.let { rows += InfoRow("Filename", it) }
      if (slide.asAttachment().width > 0) {
        rows += InfoRow("Dimensions", "${slide.asAttachment().width} x ${slide.asAttachment().height}")
      }
      slide.caption.orElse(null)?.let { rows += InfoRow("Caption", it) }
      rows += InfoRow("Looping GIF", if (slide.isVideoGif) "Yes" else "No")
      rows += InfoRow("Voice note", if (slide.asAttachment().voiceNote) "Yes" else "No")
      rows += InfoRow("Borderless", if (slide.isBorderless) "Yes" else "No")
      slide.asAttachment().blurHash?.hash?.let { rows += InfoRow("Blur hash", it) }
      rows += InfoRow("Transfer state", describeTransfer(slide.transferState))

      val title = if (slides.size == 1) "Attachment" else "Attachment ${index + 1}"
      InfoSection(title, rows)
    }
  }

  private fun reactions(context: Context, record: MessageRecord): InfoSection? {
    val reactions = record.reactions
    if (reactions.isEmpty()) {
      return null
    }
    return InfoSection(
      "Reactions",
      reactions.map { reaction ->
        val who = Recipient.resolved(reaction.author).getDisplayName(context)
        InfoRow(reaction.emoji, "$who at ${formatTime(reaction.dateReceived)}")
      }
    )
  }

  private fun edits(record: MessageRecord): InfoSection? {
    if (!record.isEditMessage) {
      return null
    }
    val rows = mutableListOf<InfoRow>()
    rows += InfoRow("Times edited", record.revisionNumber.toString())
    rows += InfoRow("Latest version", if (record.isLatestRevision) "Yes" else "No")
    rows += InfoRow("Original message id", record.originalMessageId?.id?.toString() ?: NOT_SET)
    return InfoSection("Edits", rows)
  }

  /**
   * Present only when the sender asked for this message to be deleted for everyone and this device kept it,
   * so the message on screen is one the other person believes they took back.
   */
  private fun retraction(record: MessageRecord): InfoSection? {
    val retractedAt = SignalDatabase.retractedMessages.retractedAt(record.id)
    if (retractedAt <= 0L) {
      return null
    }

    return InfoSection(
      "Retracted",
      listOf(
        InfoRow("They deleted this for everyone", formatTime(retractedAt)),
        InfoRow("Kept on this device", "Yes, they cannot tell")
      )
    )
  }

  /**
   * Device one is always the account's primary phone; anything higher is a linked device such as Desktop
   * or an iPad. This is the only hint the protocol gives about what they were typing on.
   */
  private fun describeDevice(deviceId: Int): String {
    return when {
      deviceId <= 0 -> NOT_SET
      deviceId == 1 -> "Primary phone"
      else -> "Linked device $deviceId, such as Desktop or iPad"
    }
  }

  private fun describeTransfer(state: Int): String {
    return when (state) {
      org.thoughtcrime.securesms.database.AttachmentTable.TRANSFER_PROGRESS_DONE -> "Downloaded"
      org.thoughtcrime.securesms.database.AttachmentTable.TRANSFER_PROGRESS_STARTED -> "Downloading"
      org.thoughtcrime.securesms.database.AttachmentTable.TRANSFER_PROGRESS_PENDING -> "Pending"
      org.thoughtcrime.securesms.database.AttachmentTable.TRANSFER_PROGRESS_FAILED -> "Failed"
      else -> "State $state"
    }
  }

  private fun formatTime(millis: Long): String {
    if (millis <= 0) {
      return NOT_SET
    }
    return SimpleDateFormat("MMM d, yyyy  HH:mm:ss.SSS", Locale.getDefault()).format(Date(millis))
  }

  private fun formatOffset(millis: Long): String {
    val sign = if (millis >= 0) "+" else "-"
    return sign + formatDuration(Math.abs(millis))
  }

  private fun formatDuration(millis: Long): String {
    if (millis < 1000) {
      return "$millis ms"
    }
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    if (seconds < 60) {
      return "$seconds s"
    }
    val minutes = TimeUnit.SECONDS.toMinutes(seconds)
    if (minutes < 60) {
      return "$minutes min"
    }
    val hours = TimeUnit.MINUTES.toHours(minutes)
    return if (hours < 24) "$hours h" else "${TimeUnit.HOURS.toDays(hours)} d"
  }

  private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) {
      return NOT_SET
    }
    if (bytes < 1024) {
      return "$bytes B"
    }
    val kb = bytes / 1024.0
    if (kb < 1024) {
      return String.format(Locale.US, "%.1f KB", kb)
    }
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
  }
}
