package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a device-local list of user-defined tag names, so a tag can exist before any message uses it.
 */
@Suppress("ClassName")
object V334_AddTagDefinitionTable : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS tag_definition (
        _id INTEGER PRIMARY KEY,
        name TEXT NOT NULL UNIQUE,
        created_at INTEGER NOT NULL
      )
      """
    )
    // Seed it with anything already applied to a message so existing tags stay visible.
    db.execSQL("INSERT OR IGNORE INTO tag_definition (name, created_at) SELECT DISTINCT tag, 0 FROM message_tag")
  }
}
