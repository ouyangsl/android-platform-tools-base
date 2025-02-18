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

package com.android.utils.cxx.ninja
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

class WriteCompileCommandsJsonBinTest {
    @Test
    fun `check deconflict sources`() {
        assertThat(deconflictSourceFiles(listOf("source.cpp"))).isEqualTo("source.cpp")
        assertThat(deconflictSourceFiles(listOf("source.cpp", "pch.pch"))).isEqualTo("source.cpp")
        assertThat(deconflictSourceFiles(listOf("pch.pch", "source.cpp"))).isEqualTo("source.cpp")
        assertThat(deconflictSourceFiles(listOf("pch.pch", "pch.pch"))).isEqualTo("pch.pch")
    }

    // This (disabled) test was originally used to optimize Ninja parsing and conversion to
    // compile_commands.json.bin. It used the build.ninja that was generated by aidegen for Android
    // Studio platform. That resulting build.ninja was 9GB.
    //
    // Initial time to read ninja/write compile_commands.json.bin: 40 minutes (Macbook Pro)
    // After optimizations: 6 minutes
    //
    // Size of final compile_commands.json.bin is 91MB.
    // @Test
    fun `read massive`() {
        val buildNinja = File("/Users/jomof/projects/android-platform/master/out/soong/build.ninja")
        val sourcesRoot = File("/Users/jomof/projects/android-platform/master")
        val compileCommandsJsonBin = File("/Users/jomof/projects/android-platform/compile_commands.json.bin")
        val start = System.currentTimeMillis()
        var lastBytes = 0L
        var last = start
        val samples = mutableListOf<Double>()
        writeCompileCommandsJsonBin(
            ninjaBuildFile = buildNinja,
            sourcesRoot,
            compileCommandsJsonBin
        ) { ninja, fileSize, bytesRead ->
            val current = System.currentTimeMillis()
            val bytesPerSecond = ((bytesRead - lastBytes) + 0.0) / (current - last) / 1000.0
            samples.add(bytesPerSecond)
            samples.sort()
            val tenPercentile = samples[(samples.size * 0.1).toInt()].roundToInt()
            val median = samples[(samples.size * 0.5).toInt()].roundToInt()
            val ninetyPercentile = samples[(samples.size * 0.9).toInt()].roundToInt()

            println("$bytesRead of $fileSize bytes ($tenPercentile/$median/$ninetyPercentile MB/s)")
            last = current
            lastBytes = bytesRead
        }
        val end = System.currentTimeMillis()
        println("Size of compile_commands.json.bin: ${compileCommandsJsonBin.length()}")
        println("Time to convert build.ninja to compile_commands.json.bin ${end - start}")
    }
}
