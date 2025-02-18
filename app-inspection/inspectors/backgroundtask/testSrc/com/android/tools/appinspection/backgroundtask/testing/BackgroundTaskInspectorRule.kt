/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.appinspection.backgroundtask.testing

import androidx.inspection.Inspector
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.Command
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.TrackBackgroundTaskCommand
import com.android.tools.appinspection.backgroundtask.BackgroundTaskInspectorFactory
import com.android.tools.appinspection.backgroundtask.BackgroundTaskUtil
import java.util.concurrent.Executor
import org.junit.rules.ExternalResource

class BackgroundTaskInspectorRule : ExternalResource() {

  val connection = FakeConnection()
  private val environment = FakeEnvironment()
  val inspector = BackgroundTaskInspectorFactory().createInspector(connection, environment)

  override fun before() {
    inspector.onReceiveCommand(
      Command.newBuilder()
        .setTrackBackgroundTask(TrackBackgroundTaskCommand.getDefaultInstance())
        .build()
        .toByteArray(),
      object : Inspector.CommandCallback {
        override fun reply(response: ByteArray) {}

        override fun addCancellationListener(executor: Executor, runnable: Runnable) {}
      },
    )
  }

  override fun after() {
    inspector.onDispose()
    BackgroundTaskUtil.atomicLong.set(0)
  }
}
