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

package com.android.compose.screenshot.report

import org.gradle.api.Action



/**
 * Action adapter/implementation for action code that may throw exceptions.
 *
 * Implementations implement doExecute() (instead of execute()) which is allowed to throw checked
 * exceptions.
 * Any checked exceptions thrown will be wrapped as unchecked exceptions and re-thrown.
 *
 * @param <T> The type of object which this action accepts.
</T> */
abstract class ErroringAction<T> : Action<T> {

    override fun execute(objectToExecute: T) {
        try {
            doExecute(objectToExecute)
        } catch (e: Exception) {
            if (e is RuntimeException) {
                throw e
            }
            throw RuntimeException(e)
        }
    }

    @Throws(Exception::class)
    protected abstract fun doExecute(objectToExecute: T)
}
