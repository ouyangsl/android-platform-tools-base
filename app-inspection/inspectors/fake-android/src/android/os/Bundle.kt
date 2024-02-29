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

package android.os

class Bundle(val name: String = "bundle") {

  private val data = mutableMapOf<String, Any>()

  fun keySet(): Set<String> = data.keys

  @Deprecated(
    """This method is deprecated in Android. There is public alternative for getting a
          value without knowing its type."""
  )
  fun get(key: String): Any? = data[key]

  /** This is not an Android Bundle method. It's just for convenience in tests. */
  fun put(key: String, value: Any): Bundle {
    data[key] = value
    return this
  }

  override fun toString() = name
}
