/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.sdklib.deviceprovisioner

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

class PeriodicActionTest {
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun run() = runBlockingTest {
    var i = 0
    val action = PeriodicAction(this, Duration.ofHours(1)) { i++ }
    testScheduler.apply {
      advanceTimeBy(Duration.ofHours(5).toMillis() + 1000)
      runCurrent()
    }
    assertThat(i).isEqualTo(5)
    action.cancel()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun runNow() = runBlockingTest {
    var i = 0
    val action = PeriodicAction(this, Duration.ofHours(1)) { i++ }
    testScheduler.apply {
      advanceTimeBy(Duration.ofMinutes(59).toMillis())
      runCurrent()
    }
    action.runNow()
    assertThat(i).isEqualTo(1)
    testScheduler.apply {
      advanceTimeBy(Duration.ofMinutes(3).toMillis())
      runCurrent()
    }
    assertThat(i).isEqualTo(1)
    testScheduler.apply {
      advanceTimeBy(Duration.ofMinutes(59).toMillis())
      runCurrent()
    }
    assertThat(i).isEqualTo(2)
    action.cancel()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun runNowCancellation() = runBlockingTest {
    var i = 0
    val action =
      PeriodicAction(this, Duration.ofSeconds(1)) {
        delay(Duration.ofHours(1).toMillis())
        i++
      }
    action.runNow()
    testScheduler.apply {
      advanceTimeBy(Duration.ofMinutes(59).toMillis())
      runCurrent()
    }
    action.runNow()
    testScheduler.apply {
      advanceTimeBy(Duration.ofMinutes(59).toMillis())
      runCurrent()
    }
    action.runNow()
    // Cancellations prevent the action from completing.
    assertThat(i).isEqualTo(0)

    testScheduler.apply {
      advanceTimeBy(Duration.ofMinutes(61).toMillis())
      runCurrent()
    }
    assertThat(i).isEqualTo(1)

    val job = action.runNow()
    testScheduler.apply {
      advanceTimeBy(Duration.ofMinutes(40).toMillis())
      runCurrent()
    }
    job.cancel()
    testScheduler.apply {
      advanceTimeBy(Duration.ofMinutes(40).toMillis())
      runCurrent()
    }
    assertThat(i).isEqualTo(1)

    action.cancel()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun actionThrows() = runBlockingTest {
    var i = 0
    var exceptions = 0
    val exceptionHandler = CoroutineExceptionHandler { ctx, t -> exceptions++ }
    val action =
      PeriodicAction(this + exceptionHandler, Duration.ofHours(1)) {
        if (i % 2 == 1) {
          throw Exception()
        }
        i++
      }
    testScheduler.apply {
      advanceTimeBy(Duration.ofHours(5).toMillis() + 1000)
      runCurrent()
    }
    assertThat(i).isEqualTo(1)
    assertThat(exceptions).isEqualTo(1)
    action.cancel()
  }
}
