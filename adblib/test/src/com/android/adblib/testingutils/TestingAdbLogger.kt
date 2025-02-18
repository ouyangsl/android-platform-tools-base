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
package com.android.adblib.testingutils

import com.android.adblib.AdbLogger
import com.android.adblib.AdbLoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

class TestingAdbLoggerFactory : AdbLoggerFactory {
    private var previousInstant: Instant? = null

    private val threadNameWidth = 35

    var minLevel: AdbLogger.Level = AdbLogger.Level.INFO

    /**
     * Whether to log with "delta time" from previous log entry. `true` by default, but can be
     * disabled manually in test code.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var logDeltaTime: Boolean = true

    override val logger: AdbLogger by lazy {
        TestingAdbLogger(this)
    }

    override fun createLogger(cls: Class<*>): AdbLogger {
        return createLogger(cls.simpleName)
    }

    override fun createLogger(category: String): AdbLogger {
        return TestingAdbLoggerWithPrefix(this, category)
    }

    fun logWithPrefix(prefix: String, level: AdbLogger.Level, message: String) {
        if (logDeltaTime) {
            val newInstant = Instant.now()
            synchronized(this) {
                val prevInstant = previousInstant ?: newInstant
                previousInstant = newInstant
                println(
                    String.format(
                        "[%s%s] [%-${threadNameWidth}s] %7s - %30s - %s",
                        formatInstant(newInstant),
                        deltaInstant(newInstant, prevInstant),
                        Thread.currentThread().name.takeLast(threadNameWidth),
                        level.toString().takeLast(7),
                        prefix.takeLast(30),
                        message
                    )
                )
            }
        } else {
            println(
                String.format(
                    "[%-${threadNameWidth}s] %7s - %30s - %s",
                    Thread.currentThread().name.takeLast(threadNameWidth),
                    level.toString().takeLast(7),
                    prefix.takeLast(30),
                    message
                )
            )
        }

    }

    private fun deltaInstant(newInstant: Instant, prevInstant: Instant): String {
        // Depending on the amount of time between newInstant and prevInstance, we want
        // to log either # of milliseconds or seconds, with one decimal.
        var unitString: String? = null
        var unitValue: Long? = null

        val millis = ChronoUnit.MILLIS.between(prevInstant, newInstant)
        if (millis >= 1000) {
            // We have at least one second
            unitString = "s "
            unitValue = millis
        } else {
            val micros = ChronoUnit.MICROS.between(prevInstant, newInstant)
            if (micros >= 100) {
                // We have at least one tenth of a milli
                unitString = "ms"
                unitValue = micros
            }
        }
        return if (unitValue != null) {
            assert(unitString != null)
            String.format(Locale.ROOT, "(+%5.1f%s)", unitValue.toDouble() / 1000, unitString)
        } else {
            "          "
        }
    }

    private fun formatInstant(newInstant: Instant) =
        newInstant.toString().replace('T', ' ').dropLast(4)
}

open class TestingAdbLogger(
    private val factory: TestingAdbLoggerFactory
) : AdbLogger() {

    open val prefix: String = "adblib"

    override val minLevel: Level
        get() = factory.minLevel

    override fun log(level: Level, message: String) {
        factory.logWithPrefix(prefix, level, message)
    }

    override fun log(level: Level, exception: Throwable?, message: String) {
        if (level >= minLevel) {
            log(level, message)
            exception?.printStackTrace()
        }
    }
}

class TestingAdbLoggerWithPrefix(
    factory: TestingAdbLoggerFactory,
    override val prefix: String
) : TestingAdbLogger(factory)
