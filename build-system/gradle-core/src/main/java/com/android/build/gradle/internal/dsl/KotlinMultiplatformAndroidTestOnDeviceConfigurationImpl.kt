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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.Installation
import com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnDeviceConfiguration
import com.android.build.api.dsl.MultiDexConfig
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.getNamePrefixedWithTarget
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.core.BuilderConstants
import com.android.builder.model.TestOptions
import com.android.utils.HelpfulEnumConverter
import com.google.common.base.Preconditions
import com.google.common.base.Verify
import org.gradle.api.Action
import javax.inject.Inject

abstract class KotlinMultiplatformAndroidTestOnDeviceConfigurationImpl @Inject constructor(
    compilationName: String,
    val dslServices: DslServices,
): KotlinMultiplatformAndroidTestOnDeviceConfiguration, KotlinMultiplatformAndroidTestConfigurationImpl(compilationName) {

    private val executionConverter = HelpfulEnumConverter(TestOptions.Execution::class.java)
    private var _execution = TestOptions.Execution.HOST

    override var applicationId: String? = null
    override var functionalTest: Boolean? = null
    override var handleProfiling: Boolean? = null
    override var defaultSourceSetName: String = compilationName.getNamePrefixedWithTarget()
    override var sourceSetTree: String? = null

    override var animationsDisabled: Boolean = false

    override var execution: String
        get() = Verify.verifyNotNull(
            executionConverter.reverse().convert(_execution),
            "No string representation for enum."
        )
        set(value) {
            _execution = Preconditions.checkNotNull(
                executionConverter.convert(value),
                "The value of `execution` cannot be null."
            )
        }

    override val installation: Installation = dslServices.newDecoratedInstance(
        AdbOptions::class.java,
        dslServices
    )

    override fun installation(action: Installation.() -> Unit) {
        action.invoke(installation)
    }

    fun installation(action: Action<Installation>) {
        action.execute(installation)
    }

    override val managedDevices: ManagedDevices =
        dslServices.newInstance(ManagedDevices::class.java, dslServices)

    override fun managedDevices(action: com.android.build.api.dsl.ManagedDevices.() -> Unit) {
        action.invoke(managedDevices)
    }

    fun managedDevices(action: Action<com.android.build.api.dsl.ManagedDevices>) {
        action.execute(managedDevices)
    }

    override val emulatorControl: com.android.build.api.dsl.EmulatorControl  =
        dslServices.newDecoratedInstance(EmulatorControl::class.java, dslServices)

    override fun emulatorControl(action: com.android.build.api.dsl.EmulatorControl.() -> Unit) {
        action.invoke(emulatorControl)
    }

    fun emulatorControl(action: Action<com.android.build.api.dsl.EmulatorControl>) {
        action.execute(emulatorControl)
    }

    override val emulatorSnapshots: com.android.build.api.dsl.EmulatorSnapshots =
        dslServices.newInstance(EmulatorSnapshots::class.java, dslServices)

    override fun emulatorSnapshots(action: com.android.build.api.dsl.EmulatorSnapshots.() -> Unit) {
        action.invoke(emulatorSnapshots)
    }

    fun emulatorSnapshots(action: Action<com.android.build.api.dsl.EmulatorSnapshots>) {
        action.execute(emulatorSnapshots)
    }

    var signingConfig = dslServices.newDecoratedInstance(
        SigningConfig::class.java, BuilderConstants.DEBUG, dslServices
    )

    override fun signing(action: ApkSigningConfig.() -> Unit) {
        action.invoke(signingConfig)
    }

    fun signing(action: Action<ApkSigningConfig>) {
        action.execute(signingConfig)
    }

    override val multidex: MultiDexConfig = dslServices.newDecoratedInstance(
        MultiDexConfigImpl::class.java,
        dslServices
    )

    override fun multidex(action: MultiDexConfig.() -> Unit) {
        action.invoke(multidex)
    }

    fun multidex(action: Action<MultiDexConfig>) {
        action.execute(multidex)
    }
}
