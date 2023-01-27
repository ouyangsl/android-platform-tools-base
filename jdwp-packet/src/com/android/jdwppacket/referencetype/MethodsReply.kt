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
package com.android.jdwppacket.referencetype

import com.android.jdwppacket.MessageReader

class MethodsReply(val methods: List<Method>) {
  class Method(val methodID: Long, val name: String, val signature: String, val modBits: Int)

  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): MethodsReply {
      val declared = reader.getInt()
      val methods =
        List(declared) {
          Method(reader.getMethodID(), reader.getString(), reader.getString(), reader.getInt())
        }
      return MethodsReply(methods)
    }
  }
}
