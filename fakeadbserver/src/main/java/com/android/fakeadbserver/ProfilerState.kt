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
package com.android.fakeadbserver

class ProfilerState {
    var status = Status.Off

    val samplingData = SamplingData()

    val instrumentationData = InstrumentationData()

    enum class Status(val ddmsChunkValue: Byte) {
        Off(0),
        Instrumentation(1),
        Sampling(2)
    }

    class SamplingData {
        var bufferSize = 0
        var flags = 0
        var intervalMicros = 0
        var bytes = ByteArray(0)
    }

    class InstrumentationData {
        var bufferSize = 0
        var bytes = ByteArray(0)
    }
}
