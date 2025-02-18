/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.stripping

import com.android.build.gradle.internal.core.Abi
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class SymbolStripExecutableFinderTest {
    @Test
    fun testBasicFileLocation() {
        val so = File("my.so")
        val finder = SymbolStripExecutableFinder(hashMapOf(
                Abi.X86.tag to File("my-strip.exe")))
        val stripExePath = finder.stripToolExecutableFile(so, Abi.X86.tag) { null }
        assertThat(stripExePath).isNotNull()
        assertThat(stripExePath).isEqualTo(File("my-strip.exe"))
    }

    @Test
    fun testUnrecognizedAbi() {
        val so = File("my.so")
        val finder = SymbolStripExecutableFinder(hashMapOf(
                Abi.X86.tag to File("my-strip.exe")))
        val sb = StringBuilder()
        val stripExePath = finder.stripToolExecutableFile(so, Abi.ARM64_V8A.tag) {
            sb.append(it)
            null
        }
        assertThat(stripExePath).isNull()
        assertThat(sb.toString())
                .isEqualTo("Unable to strip library '${so.absolutePath}' due to missing " +
                        "strip tool for ABI 'arm64-v8a'.")
    }

    @Test
    fun testNullAbi() {
        val so = File("my.so")
        val finder = SymbolStripExecutableFinder(hashMapOf(
                Abi.X86.tag to File("my-strip.exe")))
        val sb = StringBuilder()
        val stripExePath = finder.stripToolExecutableFile(so, null) {
            sb.append(it)
            null
        }
        assertThat(stripExePath).isNull()
        assertThat(sb.toString())
                .isEqualTo("Unable to strip library '${so.absolutePath}' " +
                        "due to unknown ABI.")
    }
}
