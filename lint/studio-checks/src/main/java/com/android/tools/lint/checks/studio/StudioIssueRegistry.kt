/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.AssertDetector
import com.android.tools.lint.checks.CheckResultDetector
import com.android.tools.lint.checks.CommentDetector
import com.android.tools.lint.checks.DateFormatDetector
import com.android.tools.lint.checks.DefaultEncodingDetector
import com.android.tools.lint.checks.InteroperabilityDetector
import com.android.tools.lint.checks.KotlincFE10Detector
import com.android.tools.lint.checks.LintDetectorDetector
import com.android.tools.lint.checks.NoOpDetector
import com.android.tools.lint.checks.RestrictToDetector
import com.android.tools.lint.checks.SamDetector
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient.Companion.isStudio
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

class StudioIssueRegistry : IssueRegistry() {

  override val api = CURRENT_API

  override val vendor: Vendor = AOSP_VENDOR

  init {
    // Turn on some checks that are off by default but which we want run in Studio:
    LintDetectorDetector.UNEXPECTED_DOMAIN.setEnabledByDefault(true)
    LintDetectorDetector.MISSING_DOC_EXAMPLE.setEnabledByDefault(true)
    if (isStudio) {
      LintDetectorDetector.PSI_COMPARE.setEnabledByDefault(true)
    }

    // A few other standard lint checks disabled by default which we want enforced
    // in our codebase
    CommentDetector.EASTER_EGG.setEnabledByDefault(true)
    CommentDetector.STOP_SHIP.setEnabledByDefault(true)
    DateFormatDetector.WEEK_YEAR.setEnabledByDefault(true)
    NoOpDetector.ASSUME_PURE_GETTERS.defaultValue = true
    NoOpDetector.ISSUE.setEnabledByDefault(true)
    if (isStudio) { // not enforced in PSQ but give guidance in the IDE
      AssertDetector.EXPENSIVE.setEnabledByDefault(true)
      ByLazyDetector.ISSUE.setEnabledByDefault(true)
      InteroperabilityDetector.NO_HARD_KOTLIN_KEYWORDS.setEnabledByDefault(true)
      InteroperabilityDetector.LAMBDA_LAST.setEnabledByDefault(true)
      InteroperabilityDetector.KOTLIN_PROPERTY.setEnabledByDefault(true)
      DefaultEncodingDetector.ISSUE.setEnabledByDefault(true)
      ForbiddenStudioCallDetector.MOCKITO_WHEN.setEnabledByDefault(true)
      KotlincFE10Detector.ISSUE.setEnabledByDefault(true)
    } else {
      LintDetectorDetector.DOLLAR_STRINGS.setEnabledByDefault(false)
    }
  }

  override val issues =
    listOf(
      AdbDeviceServicesCommandDetector.ISSUE,
      ByLazyDetector.ISSUE,
      CheckResultDetector.CHECK_RESULT,
      ContextReceiverDetector.ISSUE,
      ExternalAnnotationsDetector.ISSUE,
      FileComparisonDetector.ISSUE,
      ForbiddenStudioCallDetector.ADD_TO_STDLIB_USAGE,
      ForbiddenStudioCallDetector.INTERN,
      ForbiddenStudioCallDetector.FILES_COPY,
      ForbiddenStudioCallDetector.MOCKITO_WHEN,
      ForbiddenStudioCallDetector.ADD_DEPENDENCY,
      ForkJoinPoolDetector.COMMON_FJ_POOL,
      ForkJoinPoolDetector.NEW_FJ_POOL,
      GradleApiUsageDetector.ISSUE,
      HdpiDetector.ISSUE,
      HtmlPaneDetector.ISSUE,
      ImplicitExecutorDetector.ISSUE,
      IntellijThreadDetector.ISSUE,
      LintDetectorDetector.CHECK_URL,
      LintDetectorDetector.DOLLAR_STRINGS,
      LintDetectorDetector.ID,
      LintDetectorDetector.MISSING_DOC_EXAMPLE,
      // We're not including this check here;
      // a vendor is not required for built-in checks
      // LintDetectorDetector.MISSING_VENDOR,
      LintDetectorDetector.PSI_COMPARE,
      LintDetectorDetector.TEXT_FORMAT,
      LintDetectorDetector.TRIM_INDENT,
      LintDetectorDetector.UNEXPECTED_DOMAIN,
      LintDetectorDetector.USE_KOTLIN,
      LintDetectorDetector.USE_UAST,
      PathAsIterableDetector.ISSUE,
      PluginXmlDetector.ISSUE,
      RegexpPathDetector.ISSUE,
      RestrictToDetector.TEST_VISIBILITY_INTELLIJ,
      ShortNameCacheDetector.ISSUE,
      SwingWorkerDetector.ISSUE,
      TerminologyDetector.ISSUE,
      InconsistentThreadingAnnotationDetector.ISSUE,
    )
}
