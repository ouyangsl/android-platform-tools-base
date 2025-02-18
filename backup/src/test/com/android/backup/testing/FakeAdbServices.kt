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

package com.android.backup.testing

import ai.grazie.utils.dropPrefix
import com.android.backup.AbstractAdbServices
import com.android.backup.AdbServices.AdbOutput
import com.android.backup.BackupException
import com.android.backup.ErrorCode
import com.android.tools.environment.log.NoopLogger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.EmptyCoroutineContext

private const val BMGR_ENABLE = "bmgr enable "
private const val SET_TEST_MODE = "settings put secure backup_enable_android_studio_mode "
private const val SET_BACKUP_TYPE = "settings put secure backup_android_studio_mode_backup_type "
private const val SET_TEST_APP = "settings put secure backup_android_studio_test_package_name "
private const val CLEAR_TEST_APP = "settings delete secure backup_android_studio_test_package_name"
private const val SET_TRANSPORT = "bmgr transport "
private const val LIST_TRANSPORT = "bmgr list transports"
private const val INIT_TRANSPORT = "bmgr init "
private const val BACKUP_NOW = "bmgr backupnow "
private const val RESTORE = "bmgr restore "
private const val DUMPSYS_GMSCORE = "dumpsys package com.google.android.gms"
private const val LAUNCH_PLAY_STORE = "am start market://details?id=com.google.android.gms"
private const val DUMPSYS_ACTIVITY = "dumpsys activity"
private const val LIST_PACKAGES = "pm list packages"

/** A fake [com.android.backup.AdbServices] */
internal class FakeAdbServices(
  serialNumber: String = "serial",
  totalSteps: Int = 10,
  minGmsVersion: Int = 100,
) :
  AbstractAdbServices(
    serialNumber,
    NoopLogger(),
    FakeProgressListener(),
    totalSteps,
    minGmsVersion,
  ) {

  override val ioContext = EmptyCoroutineContext

  sealed class CommandOverride(val command: String) {
    class Output(command: String, private val stdout: String, private val stderr: String = "") :
      CommandOverride(command) {

      override fun handle(errorCode: ErrorCode) = AdbOutput(stdout, stderr)
    }

    class Throw(command: String) : CommandOverride(command) {

      override fun handle(errorCode: ErrorCode): AdbOutput {
        throw BackupException(errorCode, "Fake failure")
      }
    }

    abstract fun handle(errorCode: ErrorCode): AdbOutput
  }

  private val commandOverrides = mutableMapOf<String, CommandOverride>()

  var bmgrEnabled = false
  var failReadWriteContent = false
  var testMode = 0
  var transports =
    listOf(
      "com.android.localtransport/.LocalTransport",
      "com.google.android.gms/.backup.migrate.service.D2dTransport",
      "com.google.android.gms/.backup.BackupTransportService",
    )
  var activeTransport = "com.google.android.gms/.backup.BackupTransportService"
  private val commands = mutableListOf<String>()
  private val pushedFiles = mutableListOf<String>()

  fun getCommands(): List<String> = commands

  fun getProgress() = (progressListener as FakeProgressListener).getSteps()

  override suspend fun executeCommand(command: String, errorCode: ErrorCode): AdbOutput {
    commands.add(command)
    val result = commandOverrides[command]?.handle(errorCode)
    if (result != null) {
      return result
    }
    val out =
      when {
        command == "bmgr enabled" -> handleBmgrEnabled()
        command.startsWith(BMGR_ENABLE) -> handleEnableBmgr(command)
        command.startsWith(SET_TEST_MODE) -> handleSetTestMode(command)
        command.startsWith(SET_TEST_APP) -> "".asStdout()
        command == CLEAR_TEST_APP -> "".asStdout()
        command.startsWith(SET_TRANSPORT) -> handleSetTransport(command)
        command == LIST_TRANSPORT -> handleListTransports()
        command.startsWith(INIT_TRANSPORT) -> handleInitTransport(command)
        command.startsWith(SET_BACKUP_TYPE) -> handleBackupType()
        command.startsWith(BACKUP_NOW) -> handleBackupNow(command)
        command.startsWith(RESTORE) -> handleRestore()
        command.startsWith(LIST_PACKAGES) -> handleListPackages()
        command == DUMPSYS_GMSCORE -> handleDumpsysGmsCore()
        command == LAUNCH_PLAY_STORE -> handleLaunchPlayStore()
        command == DUMPSYS_ACTIVITY -> handleDumpsysActivity()
        else -> throw NotImplementedError("Command '$command' is not implemented")
      }
    return out
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  override suspend fun readContent(outputStream: OutputStream, uri: String) {
    if (failReadWriteContent) {
      throw IOException()
    }
    outputStream.write(uri.toByteArray())
  }

  override suspend fun writeContent(inputStream: InputStream, uri: String) {
    if (failReadWriteContent) {
      throw IOException()
    }
    pushedFiles.add(uri)
  }

  fun addCommandOverride(override: CommandOverride): FakeAdbServices {
    commandOverrides[override.command] = override
    return this
  }

  private fun handleBmgrEnabled(): AdbOutput {
    return when (bmgrEnabled) {
      true -> "Backup Manager currently enabled"
      false -> "Backup Manager currently disabled"
    }.asStdout()
  }

  private fun handleEnableBmgr(command: String): AdbOutput {
    bmgrEnabled = command.dropPrefix(BMGR_ENABLE).toBoolean()
    return "".asStdout()
  }

  private fun handleSetTestMode(command: String): AdbOutput {
    testMode = command.dropPrefix(SET_TEST_MODE).toInt()
    return "".asStdout()
  }

  private fun handleBackupType(): AdbOutput {
    return "".asStdout()
  }

  private fun handleSetTransport(command: String): AdbOutput {
    val oldTransport = activeTransport
    activeTransport = command.dropPrefix(SET_TRANSPORT)
    return "Selected transport $activeTransport (formerly $oldTransport)".asStdout()
  }

  private fun handleListTransports(): AdbOutput {
    return transports
      .joinToString("\n") {
        val active = if (it == activeTransport) "*" else " "
        "  $active $it"
      }
      .asStdout()
  }

  private fun handleInitTransport(command: String): AdbOutput {
    val transport = command.dropPrefix(INIT_TRANSPORT)
    // In production, even if the transport doesn't exist, the commends succeeds. We just use this
    // condition as an easy way to trigger an error.
    return when (transport in transports) {
      true -> "Initialization result: 0"
      false -> "Error: $transport not supported"
    }.asStdout()
  }

  private fun handleDumpsysGmsCore(): AdbOutput {
    // Small extract of actual command
    return """
      Packages:
        Package [com.google.android.gms] (19e117e):
          userId=10105
          versionCode=242335038 minSdk=31 targetSdk=34
          minExtensionVersions=[]
          versionName=24.23.35 (190400-646585959)
    """
      .trimIndent()
      .asStdout()
  }

  private fun handleBackupNow(command: String): AdbOutput {
    val applicationId = command.split(' ')[3]
    return """
    Package $applicationId with result: Success
    Backup finished with result: Success
  """
      .trimIndent()
      .asStdout()
  }

  private fun handleLaunchPlayStore(): AdbOutput {
    return "Starting: Intent { act=android.intent.action.VIEW dat=market://details/... }".asStdout()
  }

  private fun handleDumpsysActivity(): AdbOutput {
    return """
      ACTIVITY MANAGER SETTINGS (dumpsys activity settings) activity_manager_constants:
      ...
        mFocusedApp=ActivityRecord{b47d1f u0 com.app/.MainActivity t224}
      ...
    """
      .trimIndent()
      .asStdout()
  }

  private fun handleListPackages(): AdbOutput {
    return "".asStdout()
  }

  private fun handleRestore(): AdbOutput {
    return "restoreFinished: 0\n".asStdout()
  }
}

private fun String.asStdout() = AdbOutput(this, "")
