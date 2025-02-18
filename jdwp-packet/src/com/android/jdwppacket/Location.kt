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
package com.android.jdwppacket

data class Location(val typeTag: Byte, val classID: Long, val methodID: Long, val index: Long) {

  fun write(writer: Writer) {
    writer.putTypeTag(typeTag)
    writer.putClassID(classID)
    writer.putMethodID(methodID)
    writer.putLong(index)
  }

  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): Location {
      return Location(
        reader.getTypeTag(),
        reader.getClassID(),
        reader.getMethodID(),
        reader.getLong(),
      )
    }
  }
}
