/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.content.Context
import org.signal.core.database.exists
import org.signal.core.database.insertInto
import org.signal.core.util.readToSingleLong
import org.signal.core.database.select

/**
 * Records that the sender asked for a message to be deleted for everyone while this device was configured
 * to keep it. The message itself stays intact; this table is only the marker that a retraction arrived, so
 * the UI can say so rather than silently pretending the message was never taken back.
 *
 * Device-local. Nothing here is sent or synced.
 */
class RetractedMessageTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    const val TABLE_NAME = "retracted_message"

    private const val ID = "_id"
    private const val MESSAGE_ID = "message_id"
    private const val RETRACTED_AT = "retracted_at"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MESSAGE_ID INTEGER NOT NULL UNIQUE REFERENCES ${MessageTable.TABLE_NAME} (${MessageTable.ID}) ON DELETE CASCADE,
        $RETRACTED_AT INTEGER NOT NULL
      )
    """
  }

  fun markRetracted(messageId: Long, retractedAt: Long) {
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(MESSAGE_ID to messageId, RETRACTED_AT to retractedAt)
      .run(conflictStrategy = SQLiteDatabase.CONFLICT_IGNORE)
  }

  fun isRetracted(messageId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$MESSAGE_ID = ?", messageId)
      .run()
  }

  /** Zero when the message was never retracted. */
  fun retractedAt(messageId: Long): Long {
    return readableDatabase
      .select(RETRACTED_AT)
      .from(TABLE_NAME)
      .where("$MESSAGE_ID = ?", messageId)
      .run()
      .readToSingleLong(0L)
  }
}
