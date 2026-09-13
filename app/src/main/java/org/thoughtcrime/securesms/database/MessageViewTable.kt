/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.content.Context
import org.signal.core.util.readToSingleLong
import org.signal.core.util.select

/**
 * Records the first moment each message was marked read on this device.
 *
 * Signal itself has no such record. The one per-message timestamp it keeps is rewritten every time the
 * conversation is opened, so it reports the last time you opened the chat rather than when you first saw a
 * given message. This table fixes that by writing once and never updating: the unique constraint on
 * message_id means a later read is ignored rather than overwriting the original moment.
 *
 * Device-local. Nothing here is sent or synced, and only messages that arrive from now on are covered,
 * since there is no history to reconstruct.
 */
class MessageViewTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    const val TABLE_NAME = "message_view"

    private const val ID = "_id"
    const val MESSAGE_ID = "message_id"
    const val FIRST_VIEWED_AT = "first_viewed_at"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MESSAGE_ID INTEGER NOT NULL UNIQUE REFERENCES ${MessageTable.TABLE_NAME} (${MessageTable.ID}) ON DELETE CASCADE,
        $FIRST_VIEWED_AT INTEGER NOT NULL
      )
    """
  }

  /** Zero when this message was never recorded as viewed, which includes anything older than this feature. */
  fun firstViewedAt(messageId: Long): Long {
    return readableDatabase
      .select(FIRST_VIEWED_AT)
      .from(TABLE_NAME)
      .where("$MESSAGE_ID = ?", messageId)
      .run()
      .readToSingleLong(0L)
  }
}
