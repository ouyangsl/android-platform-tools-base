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

import com.android.backup.BackupResult.Error
import com.android.backup.ErrorCode.UNEXPECTED_ERROR

/** The result of a backup/restore operation */
sealed class BackupResult {
  data object Success : BackupResult()

  data class Error(val errorCode: ErrorCode, val throwable: Throwable) : BackupResult()
}

internal fun Throwable.toBackupResult() =
  if (this is BackupException) Error(errorCode, this) else Error(UNEXPECTED_ERROR, this)
