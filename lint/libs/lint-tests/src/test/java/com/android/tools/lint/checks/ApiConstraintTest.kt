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

package com.android.tools.lint.checks

import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.ApiConstraint.Companion.ALL
import com.android.tools.lint.detector.api.ApiConstraint.Companion.NONE
import com.android.tools.lint.detector.api.ApiConstraint.Companion.above
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atLeast
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atMost
import com.android.tools.lint.detector.api.ApiConstraint.Companion.below
import com.android.tools.lint.detector.api.ApiConstraint.Companion.deserialize
import com.android.tools.lint.detector.api.ApiConstraint.Companion.exactly
import com.android.tools.lint.detector.api.ApiConstraint.Companion.max
import com.android.tools.lint.detector.api.ApiConstraint.Companion.not
import com.android.tools.lint.detector.api.ApiConstraint.Companion.range
import com.android.tools.lint.detector.api.ApiConstraint.Companion.serialize
import com.android.tools.lint.detector.api.ApiLevel
import com.android.tools.lint.detector.api.ExtensionSdk
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID
import com.android.tools.lint.detector.api.ExtensionSdkRegistry
import com.android.utils.XmlUtils
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ApiConstraintTest {
  private fun atLeast(api: Int) = atLeast(ApiLevel.get(api), ANDROID_SDK_ID)

  private fun below(api: Int) = below(ApiLevel.get(api), ANDROID_SDK_ID)

  private fun range(from: Int, to: Int) =
    range(ApiLevel.get(from), ApiLevel.get(to), ANDROID_SDK_ID)

  @Test
  fun testToString() {
    assertEquals(
      "API level = 5 or API level = 9 or API level ≥ 11 and API level < 15 or API level = 25",
      (exactly(5) or exactly(9) or range(11, 15) or exactly(25)).toString(),
    )
    assertEquals("API level ≥ 15", atLeast(15).toString())
    assertEquals("API level ≥ 16", above(15).toString())
    assertEquals("API level < 15", below(15).toString())
    assertEquals("API level ≥ 15 and API level < 23", range(15, 23).toString())
    assertEquals("API level ≠ 15", not(15).toString())
    assertEquals("API level ≥ 4 and API level < 6", range(4, 6).toString())
    assertEquals("No API levels", (above(24) and below(22)).toString())
    assertEquals("All API levels", (atLeast(11) or atMost(23)).toString())

    assertEquals(
      "API level ≥ 30 and SDK 31: version ≥ 2 and SDK 33: version ≥ 2",
      multiSdkAllOf("0:30,31:2,33:2").toString(),
    )
    assertEquals("API level ≥ 31 or SDK 34: version ≥ 3", multiSdkAnyOf("0:31,34:3").toString())
    assertEquals(
      "API level ≥ 30 and SDK 31: version ≥ 2 and SDK 33: version ≥ 2 and any of (API level ≥ 31 or SDK 34: version ≥ 3)",
      max(multiSdkAllOf("0:30,31:2,33:2"), multiSdkAnyOf("0:31,34:3")).toString(),
    )
  }

  @Suppress("LocalVariableName")
  @Test
  fun testMinorVersions() {
    val atLeast15 = atLeast(15, 0, ANDROID_SDK_ID)
    val atLeast15_1 = atLeast(15, 1, ANDROID_SDK_ID)
    val atLeast15_2 = atLeast(15, 2, ANDROID_SDK_ID)
    val below16_2 = below(16, 2, ANDROID_SDK_ID)
    val between15_2_and_16_2 = atLeast15_2 and below16_2

    assertEquals(15, atLeast15_1.fromInclusive())
    assertEquals(1, atLeast15_1.fromInclusiveMinor())
    assertEquals("15.1", atLeast15_1.minString())

    assertEquals("API level ≥ 15", atLeast15.toString())
    assertEquals("API level ≥ 15.1", atLeast15_1.toString())
    assertEquals("API level ≥ 15.2", atLeast15_2.toString())
    assertEquals("API level < 16.2", below16_2.toString())
    assertEquals("API level ≥ 15.2 and API level < 16.2", between15_2_and_16_2.toString())
    assertTrue(atLeast15_1.isAtLeast(atLeast15))
    assertTrue(atLeast15_2.isAtLeast(atLeast15_1))
    assertFalse(atLeast15.isAtLeast(atLeast15_1))
    assertFalse(atLeast15_1.isAtLeast(atLeast15_2))
  }

  @Test
  fun testIsAtLeast() {
    val manifest = atLeast(31)
    val call = atLeast(28)
    assertTrue(manifest.isAtLeast(call))
    assertFalse(call.isAtLeast(manifest))

    assertTrue(atLeast(31).isAtLeast(28))

    assertTrue(exactly(31).isAtLeast(atLeast(29)))
    assertTrue(atLeast(21).isAtLeast(atLeast(20)))
    assertTrue(atLeast(21).isAtLeast(atLeast(21)))
    assertFalse(atLeast(21).isAtLeast(atLeast(22)))

    // This method has unclear semantics when you have complex ranges
    // in the range requirements (e.g. method requires exactly 21).
    // But that's not something that should happen; the complexity
    // is on the "what we have" side.
    assertTrue(atLeast(29).isAtLeast(exactly(18)))

    val sdk15 = atLeast(15, 0)
    val sdk30 = atLeast(2, 30)
    val sdk15or30 = multiSdkAnyOf("0:15,30:2")
    val sdk15and30 = multiSdkAllOf("0:15,30:2")

    assertTrue(sdk15.isAtLeast(sdk15))
    assertTrue(sdk30.isAtLeast(sdk30))
    assertTrue(sdk15or30.isAtLeast(sdk15or30))
    assertTrue(sdk15and30.isAtLeast(sdk15and30))

    assertTrue(sdk15and30.isAtLeast(sdk15))
    assertTrue(sdk15and30.isAtLeast(sdk30))
    assertTrue(sdk15and30.isAtLeast(sdk15or30))
    assertTrue(sdk15and30.isAtLeast(sdk15and30))
    assertTrue(sdk15.isAtLeast(sdk15or30))
    assertTrue(sdk30.isAtLeast(sdk15or30))

    assertFalse(sdk15.isAtLeast(sdk15and30))
    assertFalse(sdk30.isAtLeast(sdk15and30))
    assertFalse(sdk15or30.isAtLeast(sdk15and30))
  }

  @Test
  fun testAtLeast() {
    assertFalse(atLeast(10).includes(8))
    assertFalse(atLeast(10).includes(9))
    assertTrue(atLeast(10).includes(10))
    assertTrue(atLeast(10).includes(11))
  }

  @Test
  fun testAbove() {
    assertFalse(above(10).includes(9))
    assertFalse(above(10).includes(10))
    assertTrue(above(10).includes(11))
    assertTrue(above(10).includes(12))
  }

  @Test
  fun testBelow() {
    assertTrue(below(10).includes(8))
    assertTrue(below(10).includes(9))
    assertFalse(below(10).includes(10))
    assertFalse(below(10).includes(11))
  }

  @Test
  fun testAtMost() {
    assertTrue(atMost(10).includes(8))
    assertTrue(atMost(10).includes(9))
    assertTrue(atMost(10).includes(10))
    assertFalse(atMost(10).includes(11))
  }

  @Test
  fun testRange() {
    assertFalse(range(10, 15).includes(8))
    assertFalse(range(10, 15).includes(9))
    assertTrue(range(10, 15).includes(10))
    assertTrue(range(10, 15).includes(11))
    assertTrue(range(10, 15).includes(14))
    assertFalse(range(10, 15).includes(15))
  }

  @Test
  fun testExactly() {
    assertFalse(exactly(11).includes(9))
    assertFalse(exactly(11).includes(10))
    assertTrue(exactly(11).includes(11))
    assertFalse(exactly(11).includes(12))
    assertFalse(exactly(11).includes(13))
  }

  @Test
  fun testNot() {
    assertTrue(not(11).includes(10))
    assertTrue(not(11).includes(12))
    assertFalse(not(11).includes(11))

    assertEquals("API level = 11", exactly((11)).toString())
    assertEquals("API level ≠ 11", exactly((11)).not().toString())
    assertEquals("API level = 11", exactly((11)).not().not().toString())

    assertEquals("API level ≥ 24", (!below(24)).toString())
    assertEquals("API level ≥ 24", atLeast(24).toString())
    assertEquals("API level < 24", (!atLeast(24)).toString())
    assertEquals("API level ≥ 24", (!(!atLeast(24))).toString())
  }

  @Test
  fun testAlwaysAtLeast() {
    assertTrue(atLeast(4).alwaysAtLeast(4))
    assertTrue(atLeast(21).alwaysAtLeast(21))
    assertTrue(atLeast(21).alwaysAtLeast(23))
    assertFalse(atLeast(21).alwaysAtLeast(20))
    assertFalse(atLeast(21).alwaysAtLeast(20))
    assertFalse(range(15, 21).alwaysAtLeast(23))
    assertFalse(range(15, 21).alwaysAtLeast(19))
    assertFalse(below(11).alwaysAtLeast(9))
  }

  @Test
  fun testNeverAtMost() {
    @Suppress("DEPRECATION")
    fun ApiConstraint.neverAtMost(level: Int): Boolean {
      return not().alwaysAtLeast(level)
    }

    assertFalse(below(21).neverAtMost(20))
    assertTrue(below(21).neverAtMost(21))
    assertTrue(below(21).neverAtMost(22))
    assertFalse(atLeast(21).neverAtMost(23))
    assertFalse(atLeast(25).neverAtMost(23))
  }

  @Test
  fun testEverHigher() {
    assertTrue(below(21).everHigher(19)) // includes 20
    assertFalse(below(21).everHigher(20))
    assertFalse(atMost(21).everHigher(21))
    assertTrue(below(22).everHigher(20))
    assertTrue(atLeast(5).everHigher(21))
    assertTrue(exactly(50).everHigher(21))
    assertFalse(exactly(21).everHigher(21))
    assertTrue(exactly(21).everHigher(20)) // includes 21
  }

  @Test
  fun testAllNone() {
    assertTrue(NONE.isAtLeast(NONE))
    assertTrue(ALL.isAtLeast(ALL))
    assertFalse(ALL.isAtLeast(NONE))
    assertFalse(ALL.isAtLeast(exactly(5)))
  }

  @Test
  fun testCurDevelopment() {
    assertEquals("API level < 10000", below(10000).toString())
    assertEquals("API level < 10001", below(10001).toString())
    assertEquals("API level = 33 or API level = 10000", (exactly(10000) or exactly(33)).toString())
  }

  @Test
  fun testNegatable() {
    assertTrue(atLeast(26).negatable())
    assertTrue(exactly(26).negatable())
    assertFalse(exactly(26).asNonNegatable().negatable())
    assertEquals("API level < 26", atLeast(26).not().toString())
    assertEquals("No API levels", atLeast(26).asNonNegatable().not().toString())
    assertTrue(multiSdkAnyOf("0:15,30:2").negatable())
    assertFalse(multiSdkAnyOf("0:15,30:2").asNonNegatable().negatable())
    assertEquals(
      "API level < 15 and SDK 30: version = 1",
      multiSdkAnyOf("0:15,30:2").not().toString(),
    )
    assertEquals(
      "No API levels and SDK 30: No versions",
      multiSdkAnyOf("0:15,30:2").asNonNegatable().not().toString(),
    )
  }

  @Test
  fun testSerialization() {
    assertEquals("26-29", serialize(atLeast(26) and atMost(28)))
    assertEquals("API level ≥ 26 and API level < 29", deserialize("26-29").toString())
    assertEquals("26-∞", serialize(atLeast(26)))
    assertEquals("API level ≥ 26", deserialize("26-∞").toString())

    val sdkConstraint = atLeast(26, 10)
    val deserialized = deserialize(sdkConstraint.serialize())
    assertEquals(sdkConstraint.toString(), deserialized.toString())
    assertEquals(26, deserialized.min())
    assertEquals(10, deserialized.getSdk())

    assertEquals("{:15-∞,:2-∞;30}", serialize(multiSdkAnyOf("0:15,30:2")))
    assertEquals("{15-∞:,2-∞;30:}", serialize(multiSdkAllOf("0:15,30:2")))
    assertEquals(
      multiSdkAnyOf("0:15,30:2").toString(),
      deserialize(multiSdkAnyOf("0:15,30:2").serialize()).toString(),
    )
    assertEquals(
      multiSdkAllOf("0:15,30:2").toString(),
      deserialize(multiSdkAllOf("0:15,30:2").serialize()).toString(),
    )

    val combined = max(multiSdkAllOf("0:15,30:2"), multiSdkAnyOf("0:17,30:4"))
    assertEquals("{15-∞:17-∞,2-∞;30:4-∞;30}", combined.serialize())
    assertEquals(combined.toString(), deserialize(combined.serialize()).toString())

    assertEquals("{15-∞:,2-∞;30:}", serialize(multiSdkAllOf("0:15,30:2")))
    val deserialize = deserialize(multiSdkAnyOf("0:15,30:2").serialize())
    assertEquals(multiSdkAnyOf("0:15,30:2").toString(), deserialize.toString())

    assertEquals("0;-1", NONE.serialize())
    assertEquals(NONE.toString(), deserialize("0;-1").toString())
  }

  @Test
  fun testAnd() {
    assertEquals("API level ≥ 23", (atLeast(11) and atLeast(23)).toString())
    assertEquals("API level < 12", (atMost(11) and atMost(23)).toString())
    assertEquals("API level ≥ 11 and API level < 24", (atLeast(11) and atMost(23)).toString())
    assertEquals("API level ≥ 26", (atLeast(24) and atLeast(26)).toString())
    assertEquals("API level ≥ 24", (atLeast(24) and atLeast(22)).toString())
    assertEquals("API level < 22", (below(24) and below(22)).toString())
    assertEquals("No API levels", (above(24) and below(22)).toString())

    assertEquals("No versions", (atLeast(11) and NONE).toString())
    assertEquals("No versions", (NONE and atLeast(11)).toString())
    assertEquals("API level ≥ 11", (atLeast(11) and ALL).toString())
    assertEquals("API level ≥ 11", (ALL and atLeast(11)).toString())

    assertEquals("version ≥ 15", (atLeast(11, 2) and atLeast(15, 2)).toString())

    assertEquals("No versions", (atLeast(11, 2) and atLeast(11, 3)).toString())

    assertEquals("API level ≥ 15", (atLeast(11) and multiSdkAnyOf("0:15,30:2")).toString())

    assertEquals("API level ≥ 15", (multiSdkAnyOf("0:15,30:2") and atLeast(11)).toString())

    assertEquals(
      "version ≥ 3",
      (multiSdkAnyOf("0:15,30:2") and multiSdkAnyOf("30:3,31:4")).toString(),
    )
  }

  @Test
  fun testOr() {
    assertEquals("API level ≥ 11", (atLeast(11) or atLeast(23)).toString())
    assertEquals("API level < 24", (atMost(11) or atMost(23)).toString())
    assertEquals("All API levels", (atLeast(11) or atMost(23)).toString())

    assertEquals("API level ≥ 11", (atLeast(11) or NONE).toString())
    assertEquals("API level ≥ 11", (NONE or atLeast(11)).toString())

    assertEquals("All API levels", (atLeast(11) or ALL).toString())
    assertEquals("All API levels", (ALL or atLeast(11)).toString())

    assertEquals("version ≥ 11", (atLeast(11, 2) or atLeast(15, 2)).toString())

    assertEquals(
      "SDK 2: version ≥ 11 or SDK 3: version ≥ 11",
      (atLeast(11, 2) or atLeast(11, 3)).toString(),
    )

    assertEquals(
      "API level ≥ 11 or SDK 30: version ≥ 2",
      (atLeast(11) or multiSdkAnyOf("0:15,30:2")).toString(),
    )

    assertEquals(
      "API level ≥ 11 or SDK 30: version ≥ 2",
      (multiSdkAnyOf("0:15,30:2") or atLeast(11)).toString(),
    )

    assertEquals(
      "API level ≥ 15 or SDK 30: version ≥ 2 or SDK 31: version ≥ 4",
      (multiSdkAnyOf("0:15,30:2") or multiSdkAnyOf("30:3,31:4")).toString(),
    )

    assertEquals(
      "SDK 1000000: version < 5 or API level < 29",
      (atMost(4, 1000000) or atMost(28, 0)).toString(),
    )

    assertEquals(
      "API level < 29 or SDK 1000000: version < 5",
      (atMost(28, 0) or atMost(4, 1000000)).toString(),
    )
  }

  @Test
  fun testMax() {
    assertEquals("API level ≥ 23", max(atLeast(11), atLeast(23)).toString())
    assertEquals("API level < 12", max(atMost(11), atMost(23)).toString())
    assertEquals("API level ≥ 11 and API level < 24", max(atLeast(11), atMost(23)).toString())
    assertEquals("API level ≥ 26", max(atLeast(24), atLeast(26)).toString())
    assertEquals("API level ≥ 24", max(atLeast(24), atLeast(22)).toString())
    assertEquals("API level < 22", max(below(24), below(22)).toString())
    assertEquals("No API levels", max(above(24), below(22)).toString())

    assertEquals("No versions", max(atLeast(11), NONE).toString())
    assertEquals("No versions", max(NONE, atLeast(11)).toString())
    assertEquals("API level ≥ 11", max(atLeast(11), ALL).toString())
    assertEquals("API level ≥ 11", max(ALL, atLeast(11)).toString())
    assertEquals("API level ≥ 15", max(atLeast(15), null).toString())
    assertEquals("No versions", max(atLeast(15), NONE).toString())
    assertEquals("No versions", max(NONE, atLeast(15)).toString())
    assertEquals("API level ≥ 15", max(atLeast(11), atLeast(15)).toString())

    assertEquals(
      "SDK 2: version ≥ 11 and SDK 3: version ≥ 11",
      max(atLeast(11, 2), atLeast(11, 3)).toString(),
    )

    assertEquals(
      "API level ≥ 15 and SDK 30: version ≥ 2",
      max(atLeast(11), multiSdkAllOf("0:15,30:2")).toString(),
    )

    assertEquals(
      "API level ≥ 11 and any of (API level ≥ 15 or SDK 30: version ≥ 2)",
      max(atLeast(11), multiSdkAnyOf("0:15,30:2")).toString(),
    )

    assertEquals(
      "API level ≥ 17 and optionally SDK 30: version ≥ 2",
      max(atLeast(17), multiSdkAnyOf("0:15,30:2")).toString(),
    )

    assertEquals(
      "API level ≥ 17 and any of (API level ≥ 19 or SDK 30: version ≥ 2)",
      max(atLeast(17), multiSdkAnyOf("0:19,30:2")).toString(),
    )

    assertEquals(
      "API level ≥ 11 and any of (API level ≥ 15 or SDK 30: version ≥ 2)",
      max(multiSdkAnyOf("0:15,30:2"), atLeast(11)).toString(),
    )

    assertEquals(
      "API level ≥ 15 and SDK 30: version ≥ 2",
      max(multiSdkAllOf("0:15,30:2"), atLeast(11)).toString(),
    )

    assertEquals(
      "API level ≥ 15 and SDK 30: version ≥ 2",
      max(multiSdkAllOf("0:15,30:2"), atLeast(11), false).toString(),
    )

    assertEquals(
      "API level ≥ 15 or SDK 30: version ≥ 2",
      max(multiSdkAnyOf("0:15,30:2"), atLeast(11), true).toString(),
    )

    assertEquals(
      "API level ≥ 33 or SDK 30: version ≥ 3 or SDK 31: version ≥ 2 or SDK 100: version ≥ 5",
      max(multiSdkAnyOf("0:33,30:2,31:2"), multiSdkAnyOf("0:31,30:3,100:5")).toString(),
    )

    assertEquals(
      "API level = 14 or API level ≥ 16",
      max(below(10) or exactly(14) or atLeast(16), atLeast(10)).toString(),
    )

    assertEquals(
      "API level = 14 or API level ≥ 16 or SDK 2: version ≥ 13",
      max(below(10) or exactly(14) or atLeast(16), atLeast(10) or atLeast(13, 2), true).toString(),
    )

    val some = multiSdkAnyOf("30:2,31:2")
    val always = multiSdkAllOf("0:33,30:4")
    assertEquals(
      "SDK 30: version ≥ 4 and API level ≥ 33 and optionally SDK 31: version ≥ 2",
      max(some, always).toString(),
    )
    assertEquals(
      "API level ≥ 33 and SDK 30: version ≥ 4 and optionally SDK 31: version ≥ 2",
      max(always, some).toString(),
    )
  }

  val registry =
    ExtensionSdkRegistry(
      listOf(
        ExtensionSdk.ANDROID_SDK,
        ExtensionSdk("R Extensions", "R-ext", 30, "android.os.Build\$VERSION_CODES.R"),
        ExtensionSdk("S Extensions", "S-ext", 31, "android.os.Build\$VERSION_CODES.S"),
        ExtensionSdk("T Extensions", "T-ext", 33, "android.os.Build\$VERSION_CODES.T"),
        ExtensionSdk(
          "Ad Services Extensions",
          "AD_SERVICES-ext",
          1000000,
          "android.os.ext.SdkExtensions.AD_SERVICES",
        ),
      )
    )

  @Test
  fun testSdkExtensions() {
    val allConstraint = multiSdkAllOf("0:33,30:2,31:2,33:2")
    val anyConstraint = multiSdkAnyOf("0:33,30:2,31:2,33:2")

    assertEquals("0:33,30:2,31:2,33:2", ApiConstraint.MultiSdkApiConstraint.describe(allConstraint))

    assertEquals(
      "API level ≥ 33 and SDK 30: version ≥ 2 and SDK 31: version ≥ 2 and SDK 33: version ≥ 2",
      allConstraint.toString(),
    )

    assertEquals(
      "API level ≥ 33 and R Extensions: version ≥ 2 and S Extensions: version ≥ 2 and T Extensions: version ≥ 2",
      allConstraint.toString(registry),
    )

    val constraint2 = multiSdk("0:34,1000000:4,33:4")
    assertEquals(
      "API level ≥ 34 or Ad Services Extensions: version ≥ 4 or T Extensions: version ≥ 4",
      constraint2.toString(registry),
    )

    val requires31 = atLeast(31)
    val requires34 = atLeast(34)
    assertTrue(allConstraint.isAtLeast(requires31)) // because platform SDK is 33
    assertFalse(allConstraint.isAtLeast(requires34))

    assertFalse(
      allConstraint.isAtLeast(constraint2)
    ) // we have 33 and require 34 of the platform; not a match
    assertFalse(constraint2.isAtLeast(allConstraint))
    assertFalse(constraint2.isAtLeast(anyConstraint))

    val rb = multiSdk("1000000:4")
    assertTrue(rb.isAtLeast(constraint2)) // because we have Rubidium
    assertFalse(
      rb.isAtLeast(allConstraint)
    ) // we don't have any of the platforms in constraint (which didn't include rubidium)

    assertTrue(NONE.isAtLeast(allConstraint))
    assertTrue(allConstraint.isAtLeast(NONE))
    assertTrue(allConstraint.isAtLeast(ALL))
  }

  @Test
  fun testGetRequirementFromManifest1() {
    @Language("xml")
    val xml =
      """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="31">
                  <extension-sdk android:sdkVersion="30" android:minExtensionVersion="12" />
                  <extension-sdk android:sdkVersion="31" android:minExtensionVersion="8" />
                </uses-sdk>
            </manifest>
            """

    val document = XmlUtils.parseDocumentSilently(xml, true)!!
    val usesSdkTag = document.getElementsByTagName("uses-sdk").item(0)!!
    assertEquals(
      "SDK 30: version ≥ 12 and SDK 31: version ≥ 8",
      ApiConstraint.getFromUsesSdk(usesSdkTag as Element).toString(),
    )
  }

  @Test
  fun testGetRequirementFromManifest2() {
    // Tests that if we specify a higher minSdkVersion than any of the extension sdks, we implicitly
    // add one for the minSdkVersion too
    @Language("xml")
    val xml =
      """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="31">
                  <extension-sdk android:sdkVersion="29" android:minExtensionVersion="5" />
                  <extension-sdk android:sdkVersion="30" android:minExtensionVersion="12" />
                </uses-sdk>
            </manifest>
            """

    val document = XmlUtils.parseDocumentSilently(xml, true)!!
    val usesSdkTag = document.getElementsByTagName("uses-sdk").item(0) as Element
    assertEquals(
      "API level ≥ 31 and SDK 29: version ≥ 5 and SDK 30: version ≥ 12",
      ApiConstraint.getFromUsesSdk(usesSdkTag).toString(),
    )
  }

  @Test
  fun testGetRequirementFromManifestWithoutMinSdk() {
    // Like testGetRequirementFromManifest1, but we don't include a minSdkVersion/targetSdkVersion
    // on the <uses-sdk>
    @Language("xml")
    val xml =
      """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk>
                  <extension-sdk android:sdkVersion="30" android:minExtensionVersion="12" />
                  <extension-sdk android:sdkVersion="31" android:minExtensionVersion="8" />
                </uses-sdk>
            </manifest>
            """

    val document = XmlUtils.parseDocumentSilently(xml, true)!!
    val usesSdkTag = document.getElementsByTagName("uses-sdk").item(0)!!
    assertEquals(
      "SDK 30: version ≥ 12 and SDK 31: version ≥ 8",
      ApiConstraint.getFromUsesSdk(usesSdkTag as Element).toString(),
    )
  }

  @Test
  fun testMinMaxMultiConstraint() {
    // (See 282932318 for more.)

    // Use Android SDK if present
    assertEquals(34, multiSdk("1000000:4,0:34,33:4").min())
    assertEquals(34, multiSdk("1000000:4,0:34,33:4").fromInclusive())
    assertEquals(29, (atMost(4, 1000000) or atMost(28, 0)).toExclusive())

    // Otherwise, just use the first item
    assertEquals(-1, multiSdk("1000000:4,33:5").fromInclusive())
    assertEquals(-1, (atMost(4, 1000000) or atMost(28, 34)).toExclusive())
  }

  @Suppress("LocalVariableName")
  @Test
  fun testFirstMissing() {
    fun checkFirstMissing(expected: String, have: ApiConstraint, required: ApiConstraint) {
      assertFalse(have.isAtLeast(required))
      assertEquals(expected, have.firstMissing(required)?.toString(registry))
    }

    val atLeast36 = atLeast(36)
    val atLeast30 = atLeast(30)
    val atLeast34 = atLeast(34)
    val atLeastSdk30_4 = atLeast(4, 30)
    val atLeastSdk37_4 = atLeast(4, 37)
    val atLeastAdsSdk_4 = atLeast(4, 1000000)

    checkFirstMissing("API level ≥ 36", atLeast30, atLeast36)
    checkFirstMissing("API level ≥ 36", atLeast30, atLeast36 or atLeast(4, 3))
    checkFirstMissing("API level ≥ 36", atLeast30 or atLeastSdk37_4, atLeast36)
    checkFirstMissing(
      "Ad Services Extensions: version ≥ 4",
      atLeast34 or atLeastSdk30_4,
      atLeastAdsSdk_4 or atLeastSdk30_4,
    )
    // It might seem more natural for this to list
    // Ad Services Extensions: version ≥ 4
    // as the first missing, since we *may* have R, but we
    // definitely don't have the Ads SDK, but our policy is to
    // always list the *first* requirement in the required vector
    // first; it's an ordered list.
    checkFirstMissing(
      "R Extensions: version ≥ 4",
      atLeast34 or atLeastSdk30_4,
      atLeastSdk30_4 or atLeastAdsSdk_4,
    )
    checkFirstMissing(
      "R Extensions: version ≥ 4",
      atLeast34 or atLeastSdk30_4,
      atLeastSdk30_4 or atLeastAdsSdk_4,
    )

    checkFirstMissing(
      "R Extensions: version ≥ 4",
      multiSdk("0:30,30:4"),
      multiSdk("30:4,1000000:4"),
    )
  }

  @Test
  fun testGetRequirementFromManifestWithCodename() {
    // Tests that we can handle a codename in the minSdkVersion name as well

    // Unknown code name will use API level HIGHEST_KNOWN_API + 1;
    // pick the other numbers here to make sure we have a contiguous region
    // from it
    val future = SdkVersionInfo.HIGHEST_KNOWN_API + 1
    val current = SdkVersionInfo.HIGHEST_KNOWN_API
    val prev = current - 1

    @Language("xml")
    val xml =
      """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-sdk android:minSdkVersion="ZZ" android:targetSdkVersion="ZZ">
                      <extension-sdk android:sdkVersion="$prev" android:minExtensionVersion="5" />
                      <extension-sdk android:sdkVersion="$current" android:minExtensionVersion="12" />
                    </uses-sdk>
                </manifest>
                """

    val document = XmlUtils.parseDocumentSilently(xml, true)!!
    val usesSdkTag = document.getElementsByTagName("uses-sdk").item(0) as Element
    val fromUsesSdk = ApiConstraint.getFromUsesSdk(usesSdkTag)
    assertEquals(
      "API level ≥ $future and SDK $prev: version ≥ 5 and SDK $current: version ≥ 12",
      fromUsesSdk?.toString(),
    )
  }

  private fun multiSdk(desc: String, anyOf: Boolean = true): ApiConstraint =
    ApiConstraint.MultiSdkApiConstraint.create(desc, anyOf)

  private fun multiSdkAnyOf(desc: String): ApiConstraint = multiSdk(desc, true)

  private fun multiSdkAllOf(desc: String): ApiConstraint = multiSdk(desc, false)
}
