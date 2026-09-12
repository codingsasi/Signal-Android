package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a device-local table of user-defined message tags. Nothing in it is ever synced or sent.
 */
@Suppress("ClassName")
object V328_AddMessageTagTable : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE message_tag (
        _id INTEGER PRIMARY KEY,
        message_id INTEGER NOT NULL REFERENCES message (_id) ON DELETE CASCADE,
        thread_id INTEGER NOT NULL,
        tag TEXT NOT NULL,
        created_at INTEGER NOT NULL
      )
      """
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS message_tag_message_tag_index ON message_tag (message_id, tag)")
    db.execSQL("CREATE INDEX IF NOT EXISTS message_tag_thread_tag_index ON message_tag (thread_id, tag)")
  }
}
