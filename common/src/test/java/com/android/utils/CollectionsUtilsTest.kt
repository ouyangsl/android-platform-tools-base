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
package com.android.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CollectionsUtilsTest {
  @Test
  fun mapValuesNotNull() {
    val map = mapOf(1 to 2, 2 to 4, 3 to 6, 4 to 8)
    // Explicitly typed here to make sure we get the right type back.
    val result: Map<Int, Int> = map.mapValuesNotNull { if (it.key % 2 == 0) it.value * 2 else null }
    assertThat(result).isEqualTo(mapOf(2 to 8, 4 to 16))
  }

  @Test
  fun associateWithNotNull() {
    val list = listOf(1, 2, 3, 4, 5)
    val result = list.associateWithNotNull { it.takeIf { n -> n % 2 == 0 }?.let { n -> 2 * n } }
    assertThat(result).containsExactly(2, 4, 4, 8)
  }

  @Test
  fun associateByNotNull() {
    val list = listOf(1, 2, 3, 4, 5)
    val result = list.associateByNotNull { it.takeIf { n -> n % 2 == 0 }?.let { n -> 2 * n } }
    assertThat(result).containsExactly(4, 2, 8, 4)
  }

  @Test
  fun associateNotNull() {
    val list = listOf(1, 2, 3, 4, 5)
    val result =
      list.associateNotNull { it.takeIf { n -> n % 2 == 0 }?.let { n -> 2 * n to 3 * n } }
    assertThat(result).containsExactly(4, 6, 8, 12)
  }
}
