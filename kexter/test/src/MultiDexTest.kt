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

import kexter.Logger
import org.junit.Assert
import org.junit.Test

class MultiDexTest {

  // Issue can arise if anything remains cache between dexes
  @Test
  fun testClasses() {
    val logger = Logger()
    val dex = OtherDexArchive.dex
    val expectedClasses = listOf("LTestClassFromOtherDex;")
    logger.info("Classes: ${dex.classes.keys.joinToString()}")
    for (clazz in expectedClasses) {
      Assert.assertTrue("$clazz is not present", clazz in dex.classes)
    }
  }
}
