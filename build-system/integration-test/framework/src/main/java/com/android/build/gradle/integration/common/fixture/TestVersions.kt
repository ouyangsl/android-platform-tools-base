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

@file:JvmName("TestVersions")
package com.android.build.gradle.integration.common.fixture

/**
 * When updating the DEFAULT_COMPILE_SDK_VERSION, the bazel filegroup
 * //tools/base/build-system/integration-test:android_platform_for_tests will also need to be
 * updated to match
*/
const val DEFAULT_COMPILE_SDK_VERSION = 35
const val DEFAULT_MIN_SDK_VERSION = 14
const val SUPPORT_LIB_MIN_SDK = 14
const val NDK_19_SUPPORT_LIB_MIN_SDK = 21

const val ANDROIDX_VERSION = "1.0.0"
const val ANDROIDX_CONSTRAINT_LAYOUT_VERSION = "1.1.3"
const val ANDROIDX_APPCOMPAT_APPCOMPAT_VERSION = "1.6.1"
const val ANDROIDX_TEST_ESPRESSO_ESPRESSO_CORE_VERSION = "3.5.1"

const val SUPPORT_LIB_VERSION = "28.0.0"
const val SUPPORT_LIB_CONSTRAINT_LAYOUT_VERSION = "1.0.2"
const val ANDROID_ARCH_VERSION = "1.1.1"
const val TEST_SUPPORT_LIB_VERSION = "1.0.2"

const val PLAY_SERVICES_VERSION = "15.0.1"

const val DESUGAR_DEPENDENCY_VERSION = "1.1.5"
const val DESUGAR_NIO_DEPENDENCY_VERSION = "2.0.2"

const val COM_GOOGLE_ANDROID_MATERIAL_MATERIAL_VERSION = "1.9.0"
