package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.database.SqlUtil
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Fork-only. This fork once shipped its own migrations as V328 to V331, which collide with upstream's
 * V328 to V331. A database that reached version 331 on the fork never ran upstream's four, so this
 * re-applies them when their columns are missing. On a database that ran upstream's, it does nothing.
 * The fork's own tables moved to V333 to V336 and use CREATE TABLE IF NOT EXISTS for the same reason.
 */
@Suppress("ClassName")
object V332_FixForkMigrationOverlap : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (!SqlUtil.columnExists(db, "recipient", "shared_given_name")) {
      V328_AddSharedNameToRecipientTable.migrate(context, db, oldVersion, newVersion)
    }
    if (!SqlUtil.columnExists(db, "sticker", "favorited_at")) {
      V329_AddStickerFavoriteColumns.migrate(context, db, oldVersion, newVersion)
    }
    if (!SqlUtil.columnExists(db, "sticker_pack", "hidden")) {
      V330_AddHiddenPackColumns.migrate(context, db, oldVersion, newVersion)
    }
    V331_AddMessageThreadDateSentIndex.migrate(context, db, oldVersion, newVersion)
  }
}
