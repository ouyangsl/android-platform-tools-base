/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.Severity.WARNING
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import java.util.EnumSet

/**
 * It checks that there is a reason defined when using the @Ignored annotation from JUnit.
 */
class IgnoreWithoutReasonDetector : Detector(), Detector.UastScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "IgnoreWithoutReason",
            briefDescription = "Test is ignored without given any explanation.",
            explanation = """
            Ignoring a test without a reason makes it difficult to figure out the problem later.
            Please define an explicit reason why it is ignored, and when it can be resolved.""",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                IgnoreWithoutReasonDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java, UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = IgnoreAnnotationVisitor(context)

    internal class IgnoreAnnotationVisitor(private val context: JavaContext) : UElementHandler() {
        override fun visitMethod(node: UMethod) = processAnnotations(node, node)

        override fun visitClass(node: UClass) = processAnnotations(node, node)

        private fun processAnnotations(element: UElement, modifierListOwner: PsiModifierListOwner) {
            val annotations = context.evaluator.getAllAnnotations(modifierListOwner, false)

            annotations.firstOrNull { it.qualifiedName?.split(".")?.lastOrNull() == "Ignore" }?.let {
              if (it.findDeclaredAttributeValue("value")?.text.isNullOrBlank()) {
                context.report(ISSUE, element, context.getLocation(it), "Test is ignored without given any explanation.")
              }
            }
        }
    }
}
