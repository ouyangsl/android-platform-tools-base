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
package com.android.adblib.tools.debugging.packets.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class JdwpErrorCodeTest {

    @Test
    fun testErrorNameWorks() {
        assertEquals("INVALID_THREAD", JdwpErrorCode.errorName(10))
        assertEquals("2000", JdwpErrorCode.errorName(2000))
        // We check each entry to make sure the internal binary search works and that entries
        // are sorted by error code.
        JdwpErrorCode.values().forEach {
            assertEquals(it.name, JdwpErrorCode.errorName(it.errorCode))
        }
    }

    @Test
    fun testErrorMessageWorks() {
        assertEquals(
            "Passed thread is null, is not a valid thread or has exited.",
            JdwpErrorCode.errorMessage(10)
        )
        assertEquals("[n/a]", JdwpErrorCode.errorMessage(2_000))
        JdwpErrorCode.values().forEach {
            assertEquals(it.errorMessage, JdwpErrorCode.errorMessage(it.errorCode))
        }
    }
}
