package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a device-local record of when each message was first marked read. Write-once by design: the unique
 * constraint makes later reads no-ops rather than overwrites. Nothing in it is sent or synced.
 */
@Suppress("ClassName")
object V331_AddMessageViewTable : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE message_view (
        _id INTEGER PRIMARY KEY,
        message_id INTEGER NOT NULL UNIQUE REFERENCES message (_id) ON DELETE CASCADE,
        first_viewed_at INTEGER NOT NULL
      )
      """
    )
  }
}
