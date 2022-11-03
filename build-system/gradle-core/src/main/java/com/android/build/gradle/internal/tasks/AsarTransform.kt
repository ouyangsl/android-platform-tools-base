/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.build.gradle.internal.privaysandboxsdk.tagAllElementsAsRequiredByPrivacySandboxSdk
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.tasks.PrivacySandboxSdkGenerateJarStubsTask
import com.android.bundle.SdkMetadataOuterClass
import com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.file.Files
import java.util.zip.ZipFile

@CacheableTransform
abstract class AsarTransform : TransformAction<AsarTransform.Parameters> {

    interface Parameters : GenericTransformParameters {
        @get:Input
        val targetType: Property<ArtifactType>
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val asar: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val asarFile = asar.get().asFile
        ZipFile(asarFile).use {
            when (val targetType = parameters.targetType.get()) {
                ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_USES_SDK_LIBRARY_MANIFEST_SNIPPET -> {
                    val outputFile = outputs.file(asar.get().asFile.nameWithoutExtension
                            + SdkConstants.PRIVACY_SANDBOX_SDK_DEPENDENCY_MANIFEST_SNIPPET_NAME_SUFFIX)
                            .toPath()
                    it.getInputStream(it.getEntry("SdkMetadata.pb")).use { protoBytes ->
                        val metadata = SdkMetadataOuterClass.SdkMetadata.parseFrom(protoBytes)
                        val encodedVersion =
                                RuntimeEnabledSdkVersionEncoder.encodeSdkMajorAndMinorVersion(
                                        metadata.sdkVersion.major,
                                        metadata.sdkVersion.minor
                                )
                        outputFile.toFile().writeText("""
                    <manifest
                        xmlns:android="http://schemas.android.com/apk/res/android">
                        <application>
                            <uses-sdk-library
                                android:name="${metadata.packageName}"
                                android:certDigest="${metadata.certificateDigest}"
                                android:versionMajor="$encodedVersion" />
                        </application>
                    </manifest>
                """.trimIndent())
                    }
                }
                ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_METADATA_PROTO -> {
                    val outputFile =
                            outputs.file(asarFile.nameWithoutExtension + "_SdkMetadata.pb").toPath()
                    it.getInputStream(it.getEntry("SdkMetadata.pb")).use { protoBytes ->
                        Files.copy(protoBytes, outputFile)
                    }
                }
                ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR -> {
                    val sdkInterfaceDescriptor = outputs.file(
                            PrivacySandboxSdkGenerateJarStubsTask.privacySandboxSdkStubJarFilename)
                    val entry =
                            it.getEntry(PrivacySandboxSdkGenerateJarStubsTask.privacySandboxSdkStubJarFilename)
                    it.getInputStream(entry).use { jar ->
                        sdkInterfaceDescriptor.writeBytes(jar.readAllBytes())
                    }
                }
                ArtifactType.MANIFEST -> {
                    val manifest = outputs.file(asarFile.nameWithoutExtension + "_AndroidManifest.xml").toPath()
                    it.getInputStream(it.getEntry("AndroidManifest.xml")).use { asarManifest ->
                        val newManifestString = tagAllElementsAsRequiredByPrivacySandboxSdk(asarManifest)
                        Files.writeString(manifest, newManifestString)
                    }
                }
                else -> {
                    error("There is not yet support from transforming the asar format to $targetType")
                }
            }
        }
    }

    companion object {

        val supportedAsarTransformTypes = listOf(
                ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_USES_SDK_LIBRARY_MANIFEST_SNIPPET,
                ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_METADATA_PROTO,
                ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR,
                // The ASAR contributes to the main manifest potentially permissions,
                // which are marked with tools:requiredByPrivacySandboxSdk="true"
                // Bundle tool will then rem
                ArtifactType.MANIFEST,
        )
    }
}
