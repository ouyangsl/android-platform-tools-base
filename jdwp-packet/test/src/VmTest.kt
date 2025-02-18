import com.android.jdwppacket.vm.AllClassesCmd
import com.android.jdwppacket.vm.AllClassesReply
import com.android.jdwppacket.vm.AllClassesWithGenericsCmd
import com.android.jdwppacket.vm.AllClassesWithGenericsReply
import com.android.jdwppacket.vm.ClassesBySignatureReply
import com.android.jdwppacket.vm.ClassesBySignatureReply.Class
import com.android.jdwppacket.vm.IDSizesReply
import com.android.jdwppacket.vm.ResumeCmd
import com.android.jdwppacket.vm.ResumeReply
import org.junit.Test

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
class VmTest {

  @Test
  fun testIDSizesReply() {
    val packet = IDSizesReply(1, 2, 3, 4, 5)
    assertJDWPObjectAndWireEquals(packet, IDSizesReply::parse)
  }

  @Test
  fun testClassBySignatureReply() {
    val classes = listOf(Class(Byte.MAX_VALUE, Long.MAX_VALUE, Int.MAX_VALUE))
    val packet = ClassesBySignatureReply(classes)
    assertJDWPObjectAndWireEquals(packet, ClassesBySignatureReply::parse)
  }

  @Test
  fun testAllClassesWithGenericCmd() {
    val packet = AllClassesWithGenericsCmd()
    assertJDWPObjectAndWireEquals(packet, AllClassesWithGenericsCmd::parse)
  }

  @Test
  fun testAllClassWithGenericReply() {
    val classes =
      listOf(
        AllClassesWithGenericsReply.Class(
          Byte.MAX_VALUE,
          Long.MAX_VALUE,
          "foo",
          "bar",
          Int.MAX_VALUE,
        )
      )
    val packet = AllClassesWithGenericsReply(classes)
    assertJDWPObjectAndWireEquals(packet, AllClassesWithGenericsReply::parse)
  }

  @Test
  fun testAllClassesCmd() {
    val packet = AllClassesCmd()
    assertJDWPObjectAndWireEquals(packet, AllClassesCmd::parse)
  }

  @Test
  fun testAllClassesReply() {
    val classes =
      listOf(AllClassesReply.Class(Byte.MAX_VALUE, Long.MAX_VALUE, "foo", Int.MAX_VALUE))
    val packet = AllClassesReply(classes)
    assertJDWPObjectAndWireEquals(packet, AllClassesReply::parse)
  }

  @Test
  fun testResumeCmd() {
    val packet = ResumeCmd()
    assertJDWPObjectAndWireEquals(packet, ResumeCmd::parse)
  }

  @Test
  fun testResumeReply() {
    val packet = ResumeReply()
    assertJDWPObjectAndWireEquals(packet, ResumeReply::parse)
  }
}
