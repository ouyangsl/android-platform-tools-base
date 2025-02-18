/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.utils.function

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RunOnceTest {
  @Test
  fun runsOnce_kotlinFunctionalUsage() {
    var invocations = 0
    val runOnce: () -> Unit = RunOnce { ++invocations }
    assertThat(invocations).isEqualTo(0)
    runOnce()
    assertThat(invocations).isEqualTo(1)
    runOnce()
    assertThat(invocations).isEqualTo(1)
  }

  @Test
  fun runsOnce_runnableUsage() {
    var invocations = 0
    val runnable = Runnable { ++invocations }
    val runOnce: Runnable = RunOnce.of(runnable)
    assertThat(invocations).isEqualTo(0)
    runOnce.run()
    assertThat(invocations).isEqualTo(1)
    runOnce.run()
    assertThat(invocations).isEqualTo(1)
  }
}
