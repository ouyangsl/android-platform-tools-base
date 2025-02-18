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
import com.android.jdwppacket.Reply
import com.android.jdwppacket.Writer

data class SourceDebugExtensionReply(val extension: String) : Reply() {
  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): SourceDebugExtensionReply {
      // See
      // https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html#JDWP_ReferenceType_SourceDebugExtension.
      // There is supposed to be an extension string argument; but at times I've found that the
      // message just ends. So we need to test for any remaining bytes before trying to read the
      // extension argument.
      val extension = if (reader.hasRemaining()) reader.getString() else ""
      return SourceDebugExtensionReply(extension)
    }
  }

  override fun writePayload(writer: Writer) {
    writer.putString(extension)
  }
}
