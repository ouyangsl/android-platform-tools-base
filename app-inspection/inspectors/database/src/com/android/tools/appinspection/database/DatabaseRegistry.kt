/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.appinspection.database

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger

private const val SCORE_READ_ONLY = 0
private const val SCORE_FORCED = 1
private const val SCORE_BEST = 2

/**
 * The class keeps track of databases under inspection, and can keep database connections open if
 * such option is enabled.
 *
 * Signals expected to be provided to the class:
 * * [.notifyDatabaseOpened] - should be called when the inspection code detects a database open
 *   operation.
 * * [.notifyAllDatabaseReferencesReleased] - should be called when the inspection code detects that
 *   the last database connection reference has been released (effectively a connection closed
 *   event).
 * * [.notifyKeepOpenToggle] - should be called when the inspection code detects a request to change
 *   the keep-database-connection-open setting (enabled|disabled).
 *
 * Callbacks exposed by the class:
 * * Detected a database that is now open, and previously was either closed or not tracked.
 * * Detected a database that is now closed, and previously was reported as open.
 *
 * @param onOpenedCallback called when tracking state changes (notTracked|closed)->open
 * @param onClosedCallback called when tracking state changes open->closed
 */
internal class DatabaseRegistry(
  private val onOpenedCallback: OnDatabaseOpenedCallback,
  private val onClosedCallback: OnDatabaseClosedCallback,
  private val testMode: Boolean = false,
) {
  // True if keep-database-connection-open functionality is enabled.
  private var keepDatabasesOpen = false
  private var forceOpen = false

  private val lock = Any()

  @GuardedBy("lock") private val nextId = AtomicInteger(1)

  @GuardedBy("lock") private var isForceOpenInProgress = false

  // TODO: decide if use weak-references to database objects
  /**
   * Database connection id -> a list of database references pointing to the same database. The
   * collection is meant to only contain open connections (eventually consistent after all callbacks
   * queued behind [lock] are processed).
   */
  @GuardedBy("lock") private val databases = mutableMapOf<Int, MutableSet<SQLiteDatabase>>()

  // Database connection id -> extra database reference used to facilitate the
  // keep-database-connection-open functionality.
  @VisibleForTesting
  @GuardedBy("lock")
  internal val keepOpenReferences = mutableMapOf<Int, KeepOpenReference>()

  // Database path -> database connection id - allowing to report a consistent id for all
  // references pointing to the same path.
  @GuardedBy("lock") private val pathToId = mutableMapOf<String, Int>()

  @GuardedBy("lock") private val forcedOpen = mutableSetOf<SQLiteDatabase>()

  /**
   * Should be called when the inspection code detects a database being open operation.
   *
   * Note that the method should be called before any code has a chance to close the database, so
   * e.g. in an [androidx.inspection.ArtTooling.ExitHook.onExit] before the return value is
   * released. Thread-safe.
   */
  fun notifyDatabaseOpened(database: SQLiteDatabase) {
    handleDatabaseSignal(database)
  }

  fun notifyReleaseReference(database: SQLiteDatabase) {
    synchronized(lock) {
      /**
       * Prevent all other methods from releasing a reference if a [KeepOpenReference] is present
       *
       * The below will always succeed as [keepOpenReferences] only contains active references:
       * - we only insert active references into [keepOpenReferences]
       * - [KeepOpenReference.releaseAllReferences] is the only place where we allow references to
       *   be released
       * - [KeepOpenReference.releaseAllReferences] is private and can only be called from this
       *   class; and before it is called, it must be removed from [keepOpenReferences]
       */
      findKeepOpenReference(database)?.acquireReference()
    }
  }

  /**
   * Should be called when the inspection code detects that the last database connection reference
   * has been released (effectively a connection closed event). Thread-safe.
   */
  fun notifyAllDatabaseReferencesReleased(database: SQLiteDatabase) {
    handleDatabaseSignal(database)
  }

  /**
   * Should be called when the inspection code detects a request to change the
   * keep-database-connection-open setting (enabled|disabled). Thread-safe.
   */
  fun notifyKeepOpenToggle(setEnabled: Boolean) {
    synchronized(lock) {
      if (keepDatabasesOpen == setEnabled || forceOpen) {
        // forceOpen implies keepDatabasesOpen so ignore toggle commands. They will be disabled in
        // the UI anyway.
        return // no change
      }
      keepDatabasesOpen = setEnabled
      if (setEnabled) {
        // Changed from allowClose -> keepOpen
        databases.keys.forEach(::secureKeepOpenReference)
      } else {
        val openReferences = keepOpenReferences.values.toList()
        keepOpenReferences.clear()
        openReferences.forEach { it.releaseAllReferences() }
      }
    }
  }

  /**
   * Should be called at the start of inspection to pre-populate the list of databases with ones on
   * disk.
   */
  fun notifyOnDiskDatabase(path: String) {
    Log.v(HIDDEN_TAG, "notifyOnDiskDatabase: $path")
    synchronized(lock) {
      if (pathToId.containsKey(path)) {
        Log.v(HIDDEN_TAG, "Database is already open: $path")
        return
      }
      if (forceOpen) {
        Log.v(HIDDEN_TAG, "Force opening: $path")
        // We just need to open the database. Our hook will call notifyDatabaseOpened()
        isForceOpenInProgress = true
        try {
          val db = SQLiteDatabase.openDatabase(path, null, OPEN_READWRITE)
          if (testMode) {
            // During tests, ART Tooling hooks are not activated so this, so we need to trigger it
            // manually.
            notifyDatabaseOpened(db)
          }
        } finally {
          isForceOpenInProgress = false
        }
      } else {
        Log.v(HIDDEN_TAG, "Registering as offline: $path")
        val id = nextId.getAndIncrement()
        pathToId[path] = id
        onClosedCallback.onDatabaseClosed(id, path)
      }
    }
  }

  fun isForcedConnection(database: SQLiteDatabase) =
    synchronized(lock) { forcedOpen.contains(database) }

  /**
   * Handles database-opened and database-closed signals from the API
   *
   * Thread-safe
   */
  private fun handleDatabaseSignal(database: SQLiteDatabase) {
    synchronized(lock) {
      val isOpen = database.isOpen
      if (isForceOpenInProgress && isOpen) {
        forcedOpen.add(database)
      }
      val id = getIdForDatabase(database)
      val before = getConnection(id)
      registerReference(id, database)
      val after = getConnection(id)

      when {
        after == null -> onClosedCallback.onDatabaseClosed(id, database.pathForDatabase())
        after.getScore() != before?.getScore() -> onOpenedCallback.onDatabaseOpened(id, after)
      }

      secureKeepOpenReference(id)
      logDatabaseStatus(database.path)
    }
  }

  /**
   * Returns a currently active database reference if one is available. Null otherwise. Thread-safe
   */
  fun getConnection(id: Int, filter: (SQLiteDatabase) -> Boolean = { true }): SQLiteDatabase? {
    synchronized(lock) {
      return databases[id]?.filter(filter)?.findBestConnection()
    }
  }

  fun enableForceOpen() {
    forceOpen = true
  }

  @VisibleForTesting fun getDatabases(id: Int): Set<SQLiteDatabase> = databases.getValue(id)

  fun dispose() {
    // TODO(161081452): release database locks and keep-open references
    synchronized(lock) {
      for (database in forcedOpen) {
        database.close()
      }
    }
  }

  @GuardedBy("lock")
  private fun registerReference(id: Int, database: SQLiteDatabase) {
    val references =
      databases.getOrPut(id) {
        if (!database.isInMemoryDatabase()) {
          pathToId[database.pathForDatabase()] = id
        }
        mutableSetOf()
      }

    when (database.isOpen) {
      true -> references.add(database)
      false -> references.remove(database)
    }
  }

  @GuardedBy("lock")
  private fun secureKeepOpenReference(id: Int) {
    val shouldKeepOpen = keepDatabasesOpen || forceOpen
    if (!(shouldKeepOpen)) {
      return
    }

    val best = getConnection(id) { !isForcedConnection(it) } ?: return
    val kept = keepOpenReferences[id]
    if (kept == null || best.getScore() > kept.database.getScore()) {
      keepOpenReferences[id] = KeepOpenReference(best)
      kept?.releaseAllReferences()
    }
  }

  @GuardedBy("lock")
  private fun getIdForDatabase(database: SQLiteDatabase): Int {
    val id =
      when (database.isInMemoryDatabase()) {
        true -> findInMemoryReferenceKey(database)
        false -> pathToId[database.pathForDatabase()]
      }
    return id ?: nextId.getAndIncrement()
  }

  internal fun interface OnDatabaseOpenedCallback {
    fun onDatabaseOpened(databaseId: Int, path: String, isForced: Boolean, isReadOnly: Boolean)
  }

  internal fun interface OnDatabaseClosedCallback {
    fun onDatabaseClosed(databaseId: Int, path: String)
  }

  @VisibleForTesting
  internal inner class KeepOpenReference(val database: SQLiteDatabase) {
    private val lock = Any()

    @GuardedBy("lock") private var acquiredReferenceCount = 0

    fun acquireReference() {
      synchronized(lock) {
        if (database.tryAcquireReference()) {
          acquiredReferenceCount++
        }
      }
    }

    /**
     * This should only be called after removing the object from
     * [DatabaseRegistry.keepOpenReferences]. Otherwise, the object will get in its own way or
     * releasing its references.
     */
    fun releaseAllReferences() {
      synchronized(lock) {
        while (acquiredReferenceCount > 0) {
          database.releaseReference()
          acquiredReferenceCount--
        }
        if (testMode && !database.isOpen) {
          // Simulate hook call if operation resulted in database getting actually closed
          notifyAllDatabaseReferencesReleased(database)
        }
      }
    }
  }

  private fun findInMemoryReferenceKey(database: SQLiteDatabase): Int? =
    databases.entries.find { (_, items) -> items.contains(database) }?.key

  private fun logDatabaseStatus(path: String) {
    if (!Log.isLoggable(HIDDEN_TAG, Log.VERBOSE)) {
      return
    }
    synchronized(lock) {
      val id = pathToId[path] ?: return
      Log.v(HIDDEN_TAG, "Database: $path (id=$id):")
      val statusList = databases[id]?.map { it.getStatus() } ?: return
      Log.v(HIDDEN_TAG, "  instances: [${statusList.joinToString { it }}]")
    }
  }

  @GuardedBy("lock")
  private fun SQLiteDatabase.getStatus(): String {
    val id = pathToId[path] ?: -1
    val suffix = if (keepOpenReferences[id]?.database == this) "*" else ""
    return when {
      isReadOnly -> "ReadOnly"
      isForcedConnection(this) -> "Forced"
      else -> "ReadWrite"
    } + suffix
  }

  fun getIdForPath(path: String): Int? {
    synchronized(lock) {
      return pathToId[path]
    }
  }

  private class DbScore(val db: SQLiteDatabase, val score: Int)

  private fun Collection<SQLiteDatabase>.findBestConnection(): SQLiteDatabase? {
    // Assign a score to each candidate.
    // - Read-only instance has the lowest score.
    // - Non forced read-write instance has the max score.
    val scores = map {
      val score = it.getScore()
      if (score == SCORE_BEST) {
        return@map DbScore(it, 2)
      }
      DbScore(it, score)
    }
    if (Log.isLoggable(HIDDEN_TAG, Log.VERBOSE)) {
      Log.v(HIDDEN_TAG, "scores: ${scores.joinToString { "${it.db.hashCode()} -> ${it.score}" }}")
    }
    return scores.maxByOrNull { it.score }?.db
  }

  private fun OnDatabaseOpenedCallback.onDatabaseOpened(id: Int, database: SQLiteDatabase) {
    onDatabaseOpened(
      id,
      database.pathForDatabase(),
      isForcedConnection(database),
      database.isReadOnly,
    )
  }

  private fun findKeepOpenReference(database: SQLiteDatabase): KeepOpenReference? {
    return keepOpenReferences.values.find { it.database == database }
  }

  private fun SQLiteDatabase.getScore() =
    when {
      isReadOnly -> SCORE_READ_ONLY
      isForcedConnection(this) -> SCORE_FORCED
      else -> SCORE_BEST
    }
}
