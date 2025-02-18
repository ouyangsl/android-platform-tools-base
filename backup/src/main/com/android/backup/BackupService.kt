/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.backup

import com.android.adblib.AdbSession
import com.android.backup.ErrorCode.INVALID_BACKUP_FILE
import com.android.tools.environment.Logger
import java.math.BigInteger
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.pathString

interface BackupService {

  suspend fun backup(
    serialNumber: String,
    applicationId: String,
    type: BackupType,
    backupFile: Path,
    listener: BackupProgressListener?,
  ): BackupResult

  suspend fun restore(
    serialNumber: String,
    backupFile: Path,
    listener: BackupProgressListener?,
  ): BackupResult

  suspend fun sendUpdateGmsIntent(serialNumber: String): BackupResult

  suspend fun getForegroundApplicationId(serialNumber: String): String

  suspend fun isInstalled(serialNumber: String, applicationId: String): Boolean

  companion object {
    const val TOKEN_FILE = "restore_token_file"
    const val PM_DATA_FILE = "pm_backup_data"
    const val APP_DATA_FILE = "app_backup_data"
    const val APP_ID = "app_id"
    val BACKUP_FILES = setOf(PM_DATA_FILE, TOKEN_FILE, APP_DATA_FILE)

    fun getInstance(adbSession: AdbSession, logger: Logger, minGmsVersion: Int): BackupService =
      BackupServiceImpl(AdbServicesFactoryImpl(adbSession, logger, minGmsVersion))

    /**
     * Verifies a backup file is valid and returns the application id of the associated app
     *
     * @param backupFile The path of a backup file to validate
     * @return The application id of the associated app
     * @throws Exception `backupFile` is not valid
     */
    fun validateBackupFile(backupFile: Path): String {
      ZipFile(backupFile.pathString).use { zip ->
        val applicationId = zip.getApplicationId()
        zip.getRestoreToken()
        val filenames = zip.entries().asSequence().mapTo(mutableSetOf()) { it.name }
        if (!filenames.containsAll(BACKUP_FILES)) {
          throw BackupException(
            INVALID_BACKUP_FILE,
            "File is not a valid backup file: ${backupFile.pathString} ($filenames)",
          )
        }
        return applicationId
      }
    }

    internal fun ZipFile.getRestoreToken(): String {
      return try {
        BigInteger(getInputStream(getEntry(TOKEN_FILE)).reader().readText()).toString(16)
      } catch (e: Exception) {
        throw BackupException(
          INVALID_BACKUP_FILE,
          "Backup file does not contain a valid token: $name",
          e,
        )
      }
    }

    internal fun ZipFile.getApplicationId(): String {
      return try {
        getInputStream(getEntry(APP_ID)).reader().readText()
      } catch (e: Exception) {
        throw BackupException(
          INVALID_BACKUP_FILE,
          "Backup file does not contain a valid application id: $name",
          e,
        )
      }
    }

    fun getApplicationId(backupFile: Path): String {
      val zipFile =
        try {
          ZipFile(backupFile.toFile())
        } catch (e: Exception) {
          throw BackupException(
            INVALID_BACKUP_FILE,
            "File is not a valid backup file: $backupFile",
            e,
          )
        }
      return zipFile.getApplicationId()
    }
  }
}
