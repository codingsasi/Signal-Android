/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.messageinfo

import android.content.Context
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.database.GroupReceiptTable
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
 * Collects what this client actually knows about a single message.
 *
 * Every row here is omitted rather than shown empty. A field that is absent, zero, off, or false is simply
 * not listed, so the sheet only ever contains facts. Sections with nothing left in them disappear too.
 *
 * Note on what is permanently absent: nothing describes the sender's network, carrier, battery, IP address,
 * app version, or location, and there is no record of when you last viewed a message. Signal neither
 * transmits nor stores any of that, so none of it can appear here.
 */
object MessageInfo {

  @WorkerThread
  fun collect(context: Context, record: MessageRecord): List<InfoSection> {
    return listOfNotNull(
      timing(record),
      origin(context, record),
      delivery(context, record),
      state(record),
      content(record),
      reactions(context, record),
      edits(record),
      retraction(record)
    ) + attachments(record)
  }

  // region Builders that drop anything without a value

  /** Builds a section, keeping only rows that carry real data, and returns null if none do. */
  private fun section(title: String, build: RowBuilder.() -> Unit): InfoSection? {
    val builder = RowBuilder().apply(build)
    return if (builder.rows.isEmpty()) null else InfoSection(title, builder.rows)
  }

  private class RowBuilder {
    val rows = mutableListOf<InfoRow>()

    /** Adds a row only when the value is meaningful. */
    fun add(label: String, value: String?) {
      if (!value.isNullOrBlank()) {
        rows += InfoRow(label, value)
      }
    }

    /** Adds a timestamp row only when the timestamp is actually set. */
    fun time(label: String, millis: Long) {
      if (millis > 0) {
        rows += InfoRow(label, formatTime(millis))
      }
    }

    /** Adds a flag row only when the flag is true, since a list of "No" tells you nothing. */
    fun flag(label: String, condition: Boolean, value: String = "Yes") {
      if (condition) {
        rows += InfoRow(label, value)
      }
    }
  }

  // endregion

  private fun timing(record: MessageRecord): InfoSection? = section("Timing") {
    if (record.isOutgoing) {
      // For a message we sent, every clock here is ours, so there is no cross-device latency to report.
      time("Sent from this device", record.dateSent)
      return@section
    }

    time("Sent by their device", record.dateSent)
    time("Received by server", record.serverTimestamp)
    time("Arrived on this phone", record.dateReceived)

    // The three clocks come from three different machines, so the gaps are the only latency signal there is.
    if (record.serverTimestamp > 0 && record.dateSent > 0) {
      add("Their clock vs server", formatOffset(record.dateSent - record.serverTimestamp))
    }
    if (record.serverTimestamp > 0 && record.dateReceived > record.serverTimestamp) {
      add("Server to this phone", formatDuration(record.dateReceived - record.serverTimestamp))
    }
    time("You were notified", record.notifiedTimestamp)

    // Only meaningful for messages they sent us, and only recorded for messages seen since this was added.
    if (!record.isOutgoing) {
      time("First seen by you", SignalDatabase.messageViews.firstViewedAt(record.id))
    }
  }

  private fun origin(context: Context, record: MessageRecord): InfoSection? = section("Origin") {
    if (record.isOutgoing) {
      add("To", record.toRecipient.getDisplayName(context))
      return@section
    }
    add("From", record.fromRecipient.getDisplayName(context))
    add("Their device", describeDevice(record.fromDeviceId))
    flag("Sealed sender", record.isUnidentified, "Yes, the server could not see the sender")
    flag("Safety number issue", record.isIdentityMismatchFailure)
  }

  private fun state(record: MessageRecord): InfoSection? = section("State") {
    // The read column only means something for incoming messages; outgoing rows are always stored as read.
    // What the recipient did with an outgoing message is in the Delivery section instead.
    val mms = record as? MmsMessageRecord
    if (mms != null && !record.isOutgoing) {
      add("Read by you", if (mms.isRead) "Yes" else "Not yet")
    }
    if (record.expiresIn > 0) {
      add("Disappearing timer", formatDuration(record.expiresIn))
      if (record.expireStarted > 0) {
        time("Disappears at", record.expireStarted + record.expiresIn)
      }
    }
    flag("View once", record.isViewOnce)
    flag("Starred", record.isStarred)
    flag("Deleted", record.isRemoteDelete, "Yes, the content is gone")
  }

  /**
   * Outgoing only: what the recipients' devices have reported back. In a 1:1 chat the message row holds a
   * single receipt timestamp, which is the time of the latest receipt of any type, so it cannot say when
   * delivery happened separately from reading. Groups keep one row per member with its own timestamp.
   * A missing read receipt does not prove the message is unread, since recipients can turn receipts off.
   */
  private fun delivery(context: Context, record: MessageRecord): InfoSection? {
    if (!record.isOutgoing) {
      return null
    }

    val to = record.toRecipient
    if (to.isGroup || to.isDistributionList) {
      val receipts = SignalDatabase.groupReceipts.getGroupReceiptInfo(record.id)
      return section("Delivery") {
        receipts.forEach { info ->
          val name = Recipient.resolved(info.recipientId).getDisplayName(context)
          val status = describeGroupStatus(info.status)
          add(name, if (info.timestamp > 0 && info.status >= GroupReceiptTable.STATUS_DELIVERED) "$status at ${formatTime(info.timestamp)}" else status)
        }
      }
    }

    return section("Delivery") {
      add(
        "Status",
        when {
          record.isFailed -> "Failed to send"
          record.isPending -> "Sending"
          record.isViewed -> "Viewed by them"
          record.hasReadReceipt() -> "Read by them"
          record.isDelivered -> "Delivered, no read receipt"
          else -> "Sent, not yet delivered"
        }
      )
      if (record.isDelivered || record.hasReadReceipt() || record.isViewed) {
        time("Latest receipt", record.receiptTimestamp)
      }
    }
  }

  private fun describeGroupStatus(status: Int): String {
    return when (status) {
      GroupReceiptTable.STATUS_VIEWED -> "Viewed"
      GroupReceiptTable.STATUS_READ -> "Read"
      GroupReceiptTable.STATUS_DELIVERED -> "Delivered"
      GroupReceiptTable.STATUS_UNDELIVERED -> "Not yet delivered"
      GroupReceiptTable.STATUS_SKIPPED -> "Skipped"
      else -> "Unknown"
    }
  }

  private fun content(record: MessageRecord): InfoSection? = section("Content") {
    val body = record.body.trim()
    val mms = record as? MmsMessageRecord

    if (body.isNotEmpty()) {
      add("Characters", body.length.toString())
      add("Words", body.split(Regex("\\s+")).size.toString())
    }

    val styles = record.messageRanges?.ranges.orEmpty().mapNotNull { it.style?.name }
    if (styles.isNotEmpty()) {
      add("Formatting", styles.joinToString(", ") { it.lowercase(Locale.US) })
    }

    flag("Replying to another message", mms?.quote != null)
    add("Link preview", mms?.linkPreviews?.firstOrNull()?.url)
    if ((mms?.sharedContacts?.size ?: 0) > 0) {
      add("Shared contacts", mms!!.sharedContacts.size.toString())
    }
    flag("Mentions you", mms?.hasSelfMention() == true)
  }

  private fun attachments(record: MessageRecord): List<InfoSection> {
    val slides: List<Slide> = (record as? MmsMessageRecord)?.slideDeck?.slides ?: return emptyList()

    return slides.mapIndexedNotNull { index, slide ->
      val attachment = slide.asAttachment()
      val title = if (slides.size == 1) "Attachment" else "Attachment ${index + 1}"

      section(title) {
        add("Type", slide.contentType)
        if (slide.fileSize > 0) {
          add("Size", formatBytes(slide.fileSize))
        }
        add("Filename", slide.fileName.orElse(null))
        if (attachment.width > 0 && attachment.height > 0) {
          add("Dimensions", "${attachment.width} x ${attachment.height}")
        }
        add("Caption", slide.caption.orElse(null))
        flag("Looping GIF", slide.isVideoGif)
        flag("Voice note", attachment.voiceNote)
        flag("Borderless", slide.isBorderless)
        add("Transfer state", describeTransfer(slide.transferState))
      }
    }
  }

  private fun reactions(context: Context, record: MessageRecord): InfoSection? = section("Reactions") {
    record.reactions.forEach { reaction ->
      add(reaction.emoji, "${Recipient.resolved(reaction.author).getDisplayName(context)} at ${formatTime(reaction.dateReceived)}")
    }
  }

  private fun edits(record: MessageRecord): InfoSection? {
    if (!record.isEditMessage) {
      return null
    }
    return section("Edits") {
      add("Times edited", record.revisionNumber.toString())
      add("Latest version", if (record.isLatestRevision) "Yes" else "No, a newer one exists")
    }
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
    return section("Retracted") {
      time("They deleted this for everyone", retractedAt)
      add("Kept on this device", "Yes, they cannot tell")
    }
  }

  /**
   * Device one is always the account's primary phone; anything higher is a linked device such as Desktop
   * or an iPad. This is the only hint the protocol gives about what they were typing on.
   */
  private fun describeDevice(deviceId: Int): String? {
    return when {
      deviceId <= 0 -> null
      deviceId == 1 -> "Primary phone"
      else -> "Linked device $deviceId, such as Desktop or iPad"
    }
  }

  /** Only worth saying when the file is not simply sitting downloaded on the device. */
  private fun describeTransfer(state: Int): String? {
    return when (state) {
      org.thoughtcrime.securesms.database.AttachmentTable.TRANSFER_PROGRESS_DONE -> null
      org.thoughtcrime.securesms.database.AttachmentTable.TRANSFER_PROGRESS_STARTED -> "Downloading"
      org.thoughtcrime.securesms.database.AttachmentTable.TRANSFER_PROGRESS_PENDING -> "Pending"
      org.thoughtcrime.securesms.database.AttachmentTable.TRANSFER_PROGRESS_FAILED -> "Failed"
      else -> "State $state"
    }
  }

}

private fun formatTime(millis: Long): String {
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
  if (bytes < 1024) {
    return "$bytes B"
  }
  val kb = bytes / 1024.0
  if (kb < 1024) {
    return String.format(Locale.US, "%.1f KB", kb)
  }
  return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}
