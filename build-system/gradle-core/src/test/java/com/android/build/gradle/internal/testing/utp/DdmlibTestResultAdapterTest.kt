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

package com.android.build.gradle.internal.testing.utp

import com.android.ddmlib.testrunner.TestIdentifier
import com.android.ddmlib.testrunner.XmlTestRunListener
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.google.protobuf.Any
import com.google.protobuf.TextFormat
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never

/**
 * Unit tests for [DdmlibTestResultAdapter].
 */
class DdmlibTestResultAdapterTest {
    private val mockDdmlibListener: XmlTestRunListener = mock()

    @Test
    fun testPassed() {
        val resultProto = createResultProto("""
            test_suite_meta_data {
              scheduled_test_case_count: 1
            }
            test_result {
              test_case {
                test_class: "ExampleInstrumentedTest"
                test_package: "com.example.application"
                test_method: "useAppContext"
              }
              test_status: PASSED
            }
        """.trimIndent())

        val adapter = DdmlibTestResultAdapter("runName", mockDdmlibListener)

        replayTestEvent(resultProto, adapter)

        inOrder(mockDdmlibListener).apply {
            verify(mockDdmlibListener).testRunStarted(eq("runName"), eq(1))
            verify(mockDdmlibListener).testStarted(eq(TestIdentifier(
                    "com.example.application.ExampleInstrumentedTest",
                    "useAppContext")))
            verify(mockDdmlibListener, never()).testFailed(any(), any())
            verify(mockDdmlibListener).testEnded(
                    eq(TestIdentifier(
                            "com.example.application.ExampleInstrumentedTest",
                            "useAppContext")),
                    eq(mapOf()))
            verify(mockDdmlibListener, never()).testRunFailed(any())
            verify(mockDdmlibListener).testRunEnded(any(), eq(mapOf()))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun testFailed() {
        val resultProto = createResultProto("""
            test_suite_meta_data {
              scheduled_test_case_count: 1
            }
            test_result {
              test_case {
                test_class: "ExampleInstrumentedTest"
                test_package: "com.example.application"
                test_method: "useAppContext"
              }
              test_status: FAILED
              error {
                stack_trace: "example error stacktrace"
              }
            }
        """.trimIndent())

        val adapter = DdmlibTestResultAdapter("runName", mockDdmlibListener)

        replayTestEvent(resultProto, adapter)

        inOrder(mockDdmlibListener).apply {
            verify(mockDdmlibListener).testRunStarted(eq("runName"), eq(1))
            verify(mockDdmlibListener).testStarted(eq(TestIdentifier(
                    "com.example.application.ExampleInstrumentedTest",
                    "useAppContext")))
            verify(mockDdmlibListener).testFailed(
                    eq(TestIdentifier(
                            "com.example.application.ExampleInstrumentedTest",
                            "useAppContext")),
                    eq("example error stacktrace"))
            verify(mockDdmlibListener).testEnded(
                    eq(TestIdentifier(
                            "com.example.application.ExampleInstrumentedTest",
                            "useAppContext")),
                    eq(mapOf()))
            verify(mockDdmlibListener).testRunFailed(eq("There was 1 failure(s)."))
            verify(mockDdmlibListener).testRunEnded(any(), eq(mapOf()))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun testFailedByPlatformError() {
        val resultProto = createResultProto("""
            test_status: ERROR
            platform_error {
              errors {
                summary {
                  namespace {
                    namespace: "com.google.testing.platform"
                  }
                  error_code: 3002
                  error_name: "DEVICE_PROVISION_FAILED"
                  error_classification: "UNDERLYING_TOOL"
                  error_message: "Failed trying to provide device controller."
                }
                cause {
                  summary {
                    error_message: "Gradle was unable to attach one or more devices to the adb server."
                  }
                }
              }
            }
        """)

        val adapter = DdmlibTestResultAdapter("runName", mockDdmlibListener)

        replayTestEvent(resultProto, adapter)

        inOrder(mockDdmlibListener).apply {
            verify(mockDdmlibListener).addSystemError(eq(
                "Failed trying to provide device controller.\n" +
                "Gradle was unable to attach one or more devices to the adb server.\n\n"))
            verify(mockDdmlibListener).testRunEnded(any(), eq(mapOf()))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun testFailedByProcessCrash() {
        val resultProto = createResultProto("""
            test_status: FAILED
            issue {
              namespace {
                namespace: "com.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver"
              }
              severity: SEVERE
              code: 1
              name: "INSTRUMENTATION_FAILED"
              message: "Test run failed to complete. Instrumentation run failed due to Process crashed."
            }
        """)

        val adapter = DdmlibTestResultAdapter("runName", mockDdmlibListener)

        replayTestEvent(resultProto, adapter)

        inOrder(mockDdmlibListener).apply {
            verify(mockDdmlibListener).addSystemError(eq(
                "Test run failed to complete. Instrumentation run failed due to Process crashed.\n"))
            verify(mockDdmlibListener).testRunEnded(any(), eq(mapOf()))
            verifyNoMoreInteractions()
        }
    }

    private fun replayTestEvent(
            testSuiteResult: TestSuiteResultProto.TestSuiteResult,
            utpListener: UtpTestResultListener) {
        utpListener.onTestResultEvent(TestResultEvent.newBuilder().apply {
            testSuiteStartedBuilder.apply {
                deviceId = "mockDeviceSerialNumber"
                testSuiteMetadata = Any.pack(testSuiteResult.testSuiteMetaData)
            }
        }.build())
        testSuiteResult.testResultList.forEach { testResult ->
            utpListener.onTestResultEvent(TestResultEvent.newBuilder().apply {
                testCaseStartedBuilder.apply {
                    deviceId = "mockDeviceSerialNumber"
                    testCase = Any.pack(testResult.testCase)
                }
            }.build())
            utpListener.onTestResultEvent(TestResultEvent.newBuilder().apply {
                testCaseFinishedBuilder.apply {
                    deviceId = "mockDeviceSerialNumber"
                    testCaseResult = Any.pack(testResult)
                }
            }.build())
        }
        utpListener.onTestResultEvent(TestResultEvent.newBuilder().apply {
            testSuiteFinishedBuilder.apply {
                deviceId = "mockDeviceSerialNumber"
                this.testSuiteResult = Any.pack(testSuiteResult)
            }
        }.build())
    }

    private fun createResultProto(asciiProto: String): TestSuiteResultProto.TestSuiteResult {
        return TextFormat.parse(asciiProto, TestSuiteResultProto.TestSuiteResult::class.java)
    }
}
