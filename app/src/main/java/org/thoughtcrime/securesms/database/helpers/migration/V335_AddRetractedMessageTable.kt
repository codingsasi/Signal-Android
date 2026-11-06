package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a device-local marker for messages whose sender asked for a delete-for-everyone that this device
 * chose to keep. Nothing in it is sent or synced.
 */
@Suppress("ClassName")
object V335_AddRetractedMessageTable : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS retracted_message (
        _id INTEGER PRIMARY KEY,
        message_id INTEGER NOT NULL UNIQUE REFERENCES message (_id) ON DELETE CASCADE,
        retracted_at INTEGER NOT NULL
      )
      """
    )
  }
}
