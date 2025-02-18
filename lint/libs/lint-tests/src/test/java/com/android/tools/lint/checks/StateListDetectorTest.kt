/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Detector

class StateListDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return StateListDetector()
  }

  fun testStates() {
    lint()
      .files(
        xml(
            "res/drawable/states.xml",
            """
                <selector xmlns:android="http://schemas.android.com/apk/res/android">
                    <item  android:color="#ff000000"/> <!-- WRONG, SHOULD BE LAST -->
                    <item android:state_pressed="true"
                          android:color="#ffff0000"/> <!-- pressed -->
                    <item android:state_focused="true"
                          android:color="#ff0000ff"/> <!-- focused -->
                </selector>
                """,
          )
          .indented()
      )
      .run()
      .expect(
        """
            res/drawable/states.xml:3: Warning: This item is unreachable because a previous item (item #1) is a more general match than this one [StateListReachable]
                <item android:state_pressed="true"
                ^
                res/drawable/states.xml:2: Earlier item which masks item
                <item  android:color="#ff000000"/> <!-- WRONG, SHOULD BE LAST -->
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
  }

  fun testCustomStates() {
    //noinspection all // Sample code
    lint()
      .files(
        xml(
            "res/drawable/states2.xml",
            """

                <selector xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res/com.domain.pkg">
                <item
                    app:mystate_custom="false"
                    android:drawable="@drawable/item" />
                </selector>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testStates3() {
    lint()
      .files(
        xml(
            "res/drawable/states3.xml",
            """

                <!-- Copyright (C) 2008 The Android Open Source Project

                     Licensed under the Apache License, Version 2.0 (the "License");
                     you may not use this file except in compliance with the License.
                     You may obtain a copy of the License at

                          http://www.apache.org/licenses/LICENSE-2.0

                     Unless required by applicable law or agreed to in writing, software
                     distributed under the License is distributed on an "AS IS" BASIS,
                     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                     See the License for the specific language governing permissions and
                     limitations under the License.
                -->

                <selector xmlns:android="http://schemas.android.com/apk/res/android">
                    <item android:state_checked="false" android:state_window_focused="false"
                          android:drawable="@drawable/btn_star_big_off" />
                    <item android:state_checked="true" android:state_window_focused="false"
                          android:drawable="@drawable/btn_star_big_on" />
                    <item android:state_checked="true" android:state_window_focused="false"
                          android:state_enabled="false" android:drawable="@drawable/btn_star_big_on_disable" />
                    <item android:state_checked="false" android:state_window_focused="false"
                          android:state_enabled="false" android:drawable="@drawable/btn_star_big_off_disable" />

                    <item android:state_checked="true" android:state_pressed="true"
                          android:drawable="@drawable/btn_star_big_on_pressed" />
                    <item android:state_checked="false" android:state_pressed="true"
                          android:drawable="@drawable/btn_star_big_off_pressed" />

                    <item android:state_checked="true" android:state_focused="true"
                          android:drawable="@drawable/btn_star_big_on_selected" />
                    <item android:state_checked="false" android:state_focused="true"
                          android:drawable="@drawable/btn_star_big_off_selected" />

                    <item android:state_checked="true" android:state_focused="true" android:state_enabled="false"
                          android:drawable="@drawable/btn_star_big_on_disable_focused" />
                    <item android:state_checked="true" android:state_focused="false" android:state_enabled="false"
                          android:drawable="@drawable/btn_star_big_on_disable" />

                    <item android:state_checked="false" android:state_focused="true" android:state_enabled="false"
                          android:drawable="@drawable/btn_star_big_off_disable_focused" />
                    <item android:state_checked="false" android:state_focused="false" android:state_enabled="false"
                          android:drawable="@drawable/btn_star_big_off_disable" />

                    <item android:state_checked="false" android:drawable="@drawable/btn_star_big_off" />
                    <item android:state_checked="true" android:drawable="@drawable/btn_star_big_on" />
                </selector>
                """,
          )
          .indented()
      )
      .run()
      .expect(
        """
            res/drawable/states3.xml:24: Warning: This item is unreachable because a previous item (item #1) is a more general match than this one [StateListReachable]
                <item android:state_checked="false" android:state_window_focused="false"
                ^
                res/drawable/states3.xml:18: Earlier item which masks item
                <item android:state_checked="false" android:state_window_focused="false"
                ^
            0 errors, 1 warnings
            """
      )
  }
}
