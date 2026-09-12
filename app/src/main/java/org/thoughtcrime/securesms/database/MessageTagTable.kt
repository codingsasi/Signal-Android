/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.content.Context
import org.signal.core.util.delete
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.withinTransaction

/**
 * Private, device-local labels the user can attach to any message.
 *
 * Nothing in this table is ever sent, synced, or otherwise shared. It exists only in this database, so the
 * other side of a conversation cannot see that a message was tagged, or that tags exist at all. Rows are
 * removed automatically when the underlying message is deleted.
 */
class MessageTagTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(MessageTagTable::class.java)

    const val TABLE_NAME = "message_tag"

    private const val ID = "_id"
    private const val MESSAGE_ID = "message_id"
    private const val THREAD_ID = "thread_id"
    private const val TAG_NAME = "tag"
    private const val CREATED_AT = "created_at"

    /** Keeps a tag readable and short enough to render as a chip. */
    const val MAX_TAG_LENGTH = 24

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MESSAGE_ID INTEGER NOT NULL REFERENCES ${MessageTable.TABLE_NAME} (${MessageTable.ID}) ON DELETE CASCADE,
        $THREAD_ID INTEGER NOT NULL,
        $TAG_NAME TEXT NOT NULL,
        $CREATED_AT INTEGER NOT NULL
      )
    """

    const val DEFINITION_TABLE_NAME = "tag_definition"
    private const val DEFINITION_ID = "_id"
    private const val DEFINITION_NAME = "name"
    private const val DEFINITION_CREATED_AT = "created_at"

    /** The canonical list of tags the user has created, independent of whether any message uses them. */
    const val CREATE_DEFINITION_TABLE = """
      CREATE TABLE $DEFINITION_TABLE_NAME (
        $DEFINITION_ID INTEGER PRIMARY KEY,
        $DEFINITION_NAME TEXT NOT NULL UNIQUE,
        $DEFINITION_CREATED_AT INTEGER NOT NULL
      )
    """

    val CREATE_INDEXES = arrayOf(
      "CREATE UNIQUE INDEX IF NOT EXISTS message_tag_message_tag_index ON $TABLE_NAME ($MESSAGE_ID, $TAG_NAME)",
      "CREATE INDEX IF NOT EXISTS message_tag_thread_tag_index ON $TABLE_NAME ($THREAD_ID, $TAG_NAME)"
    )

    /** Trims and collapses whitespace so "  Events " and "Events" are the same tag. */
    fun normalize(raw: String): String {
      return raw.trim().replace(Regex("\\s+"), " ").take(MAX_TAG_LENGTH)
    }
  }

  fun addTag(messageId: Long, threadId: Long, rawTag: String) {
    val tag = normalize(rawTag)
    if (tag.isEmpty()) {
      return
    }

    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        MESSAGE_ID to messageId,
        THREAD_ID to threadId,
        TAG_NAME to tag,
        CREATED_AT to System.currentTimeMillis()
      )
      .run(conflictStrategy = SQLiteDatabase.CONFLICT_IGNORE)

    defineTag(tag)
  }

  fun removeTag(messageId: Long, rawTag: String) {
    writableDatabase
      .delete(TABLE_NAME)
      .where("$MESSAGE_ID = ? AND $TAG_NAME = ?", messageId, normalize(rawTag))
      .run()
  }

  fun setTags(messageId: Long, threadId: Long, tags: Collection<String>) {
    writableDatabase.withinTransaction {
      it.delete(TABLE_NAME, "$MESSAGE_ID = ?", arrayOf(messageId.toString()))
      tags.forEach { tag -> addTag(messageId, threadId, tag) }
    }
  }

  fun getTagsForMessage(messageId: Long): List<String> {
    return readableDatabase
      .select(TAG_NAME)
      .from(TABLE_NAME)
      .where("$MESSAGE_ID = ?", messageId)
      .run()
      .readToList { it.requireNonNullString(TAG_NAME) }
      .sorted()
  }

  /** Every tag available to the picker. Defined tags plus anything already attached to a message. */
  fun getAllTags(): List<String> {
    val used = readableDatabase
      .select(TAG_NAME)
      .from(TABLE_NAME)
      .run()
      .readToList { it.requireNonNullString(TAG_NAME) }

    return (getDefinedTags() + used).distinct().sorted()
  }

  /** Tags used in one conversation, each with how many messages carry it. */
  fun getTagsWithCounts(threadId: Long): List<TagCount> {
    return readableDatabase
      .select(TAG_NAME, "COUNT(*) AS count")
      .from(TABLE_NAME)
      .where("$THREAD_ID = ?", threadId)
      .groupBy(TAG_NAME)
      .run()
      .readToList { TagCount(it.requireNonNullString(TAG_NAME), it.requireLong("count").toInt()) }
      .sortedBy { it.tag }
  }

  fun getMessageIdsForTag(threadId: Long, rawTag: String): List<Long> {
    return readableDatabase
      .select(MESSAGE_ID)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $TAG_NAME = ?", threadId, normalize(rawTag))
      .run()
      .readToList { it.requireLong(MESSAGE_ID) }
  }

  fun countForTag(threadId: Long, rawTag: String): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $TAG_NAME = ?", threadId, normalize(rawTag))
      .run()
      .readToSingleInt()
  }

  /** Creates a tag that exists even before any message uses it. Returns false if the name is empty or taken. */
  fun defineTag(rawTag: String): Boolean {
    val tag = normalize(rawTag)
    if (tag.isEmpty()) {
      return false
    }

    return writableDatabase
      .insertInto(DEFINITION_TABLE_NAME)
      .values(DEFINITION_NAME to tag, DEFINITION_CREATED_AT to System.currentTimeMillis())
      .run(conflictStrategy = SQLiteDatabase.CONFLICT_IGNORE) > 0
  }

  /** Every tag the user has created, whether or not a message currently carries it. */
  fun getDefinedTags(): List<String> {
    return readableDatabase
      .select(DEFINITION_NAME)
      .from(DEFINITION_TABLE_NAME)
      .run()
      .readToList { it.requireNonNullString(DEFINITION_NAME) }
      .sorted()
  }

  /** How many messages carry each defined tag, across every conversation. */
  fun getGlobalUsageCounts(): Map<String, Int> {
    // The select builder only offers groupBy after a where clause, and this query wants every row,
    // so the tally happens in Kotlin instead. The table holds one short row per tagged message.
    return readableDatabase
      .select(TAG_NAME)
      .from(TABLE_NAME)
      .run()
      .readToList { it.requireNonNullString(TAG_NAME) }
      .groupingBy { it }
      .eachCount()
  }

  fun renameTag(oldTag: String, newTag: String) {
    val normalized = normalize(newTag)
    val previous = normalize(oldTag)
    if (normalized.isEmpty() || normalized == previous) {
      return
    }

    writableDatabase.withinTransaction { db ->
      db.execSQL("UPDATE OR IGNORE $TABLE_NAME SET $TAG_NAME = ? WHERE $TAG_NAME = ?", arrayOf(normalized, previous))
      // Any row that survived the rename collided with an existing tag on the same message, so drop it.
      db.execSQL("DELETE FROM $TABLE_NAME WHERE $TAG_NAME = ?", arrayOf(previous))
      db.execSQL("UPDATE OR IGNORE $DEFINITION_TABLE_NAME SET $DEFINITION_NAME = ? WHERE $DEFINITION_NAME = ?", arrayOf(normalized, previous))
      db.execSQL("DELETE FROM $DEFINITION_TABLE_NAME WHERE $DEFINITION_NAME = ?", arrayOf(previous))
    }
  }

  /** Removes a tag from every message and forgets the tag itself. */
  fun deleteTagEverywhere(rawTag: String) {
    val tag = normalize(rawTag)
    writableDatabase.withinTransaction { db ->
      db.delete(TABLE_NAME, "$TAG_NAME = ?", arrayOf(tag))
      db.delete(DEFINITION_TABLE_NAME, "$DEFINITION_NAME = ?", arrayOf(tag))
    }
  }

  data class TagCount(val tag: String, val count: Int)
}
