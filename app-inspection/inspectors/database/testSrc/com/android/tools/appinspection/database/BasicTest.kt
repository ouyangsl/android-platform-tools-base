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

import android.os.Build
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command
import androidx.sqlite.inspection.SqliteInspectorProtocol.ErrorContent.ErrorCode.ERROR_UNRECOGNISED_COMMAND_VALUE
import com.android.tools.appinspection.database.testing.MessageFactory
import com.android.tools.appinspection.database.testing.SqliteInspectorTestEnvironment
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.junit.rules.CloseGuardRule

@RunWith(RobolectricTestRunner::class)
@Config(
  manifest = Config.NONE,
  minSdk = Build.VERSION_CODES.O,
  maxSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BasicTest {
  private val testEnvironment = SqliteInspectorTestEnvironment()
  @get:Rule val rule: RuleChain = RuleChain.outerRule(CloseGuardRule()).around(testEnvironment)

  @Test
  fun test_basic_proto() {
    val command = MessageFactory.createTrackDatabasesCommand()

    val commandBytes = command.toByteArray()
    assertThat(commandBytes).isNotEmpty()

    val commandBack = Command.parseFrom(commandBytes)
    assertThat(commandBack).isEqualTo(command)
  }

  @Test
  fun test_basic_inject() {
    // no crash means the inspector was successfully injected
    testEnvironment.assertNoQueuedEvents()
  }

  @Test
  fun test_unset_command() = runBlocking {
    testEnvironment.sendCommand(Command.getDefaultInstance()).let { response ->
      assertThat(response.hasErrorOccurred()).isEqualTo(true)
      assertThat(response.errorOccurred.content.message)
        .contains("Unrecognised command type: ONEOF_NOT_SET")
      assertThat(response.errorOccurred.content.errorCodeValue)
        .isEqualTo(ERROR_UNRECOGNISED_COMMAND_VALUE)
    }
  }
}
