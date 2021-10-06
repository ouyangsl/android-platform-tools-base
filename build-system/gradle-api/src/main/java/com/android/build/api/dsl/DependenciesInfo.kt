/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.dsl

import org.gradle.api.Incubating

<<<<<<< HEAD   (f8bfc4 Merge cherrypicks of [15392998, 15392999, 15393000, 15392965)
/** DSL object to specify whether to include SDK dependency information in APKs and Bundles. */
@Incubating
=======
/** DSL object to specify whether to include SDK dependency information in APKs and Bundles.
 * Including dependency information in your APK or Bundle allows Google Play to ensure that
 * any third-party software your app uses complies with
 * <a href="https://support.google.com/googleplay/android-developer/topic/9858052">Google Play's Developer Program Policies</a>.
 * For more information, see the Play Console support page
 * <a href="https://support.google.com/googleplay/android-developer/answer/10358880">Using third-party SDKs in your app</a>. */
>>>>>>> CHANGE (f9e876 Add link to explanation about how dependency info file is us)
interface DependenciesInfo {

  /** If false, information about SDK dependencies of an APK will not be added to its signature
   * block. */
  var includeInApk: Boolean

  /** If false, information about SDK dependencies of an App Bundle will not be added to it. */
  var includeInBundle: Boolean
}
