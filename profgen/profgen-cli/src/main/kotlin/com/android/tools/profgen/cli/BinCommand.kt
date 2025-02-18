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

package com.android.tools.profgen.cli

import com.android.tools.profgen.Apk
import com.android.tools.profgen.ArtProfile
import com.android.tools.profgen.ArtProfileSerializer
import com.android.tools.profgen.Diagnostics
import com.android.tools.profgen.HumanReadableProfile
import com.android.tools.profgen.ObfuscationMap
import com.android.tools.profgen.dumpProfile
import com.android.tools.profgen.extractProfileAsDm
import com.android.tools.profgen.readHumanReadableProfileOrExit
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.cli.required
import kotlin.io.path.Path

@Suppress("unused") // Values are referenced by name as shell args
@ExperimentalCli
enum class ArtProfileFormat(internal val serializer: ArtProfileSerializer) {
    V0_1_5_S(ArtProfileSerializer.V0_1_5_S), // targets S+
    V0_1_0_P(ArtProfileSerializer.V0_1_0_P), // targets P -> R
    V0_0_9_OMR1(ArtProfileSerializer.V0_0_9_OMR1), // targets Android O MR1
    V0_0_5_O(ArtProfileSerializer.V0_0_5_O), // targets O
    V0_0_1_N(ArtProfileSerializer.V0_0_1_N) // targets N
}

@ExperimentalCli
class BinCommand : Subcommand("bin", "Generate Binary Profile") {
    val hrpPath by argument(ArgType.String, "profile", "File path to Human Readable profile")
    val apkPath by option(ArgType.String, "apk", "a", "File path to apk").required()
    val outPath by option(ArgType.String, "output", "o", "File path to generated binary profile").required()
    val obfPath by option(ArgType.String, "map", "m", "File path to name obfuscation map")
    val metaPath by option(ArgType.String, "output-meta", "om", "File path to generated metadata output")
    val artProfileFormat by option(ArgType.Choice<ArtProfileFormat>(), "profile-format", "pf", "The ART profile format version").default(ArtProfileFormat.V0_1_0_P)

    override fun execute() {
        val hrpFile = Path(hrpPath).toFile()
        require(hrpFile.exists()) { "File not found: $hrpPath" }

        val apkFile = Path(apkPath).toFile()
        require(apkFile.exists()) { "File not found: $apkPath" }

        val obfFile = obfPath?.let { Path(it).toFile() }
        require(obfFile?.exists() != false) { "File not found: $obfPath" }

        val metaFile = metaPath?.let { Path(it).toFile() }
        if (metaFile != null) {
            require(metaFile.parentFile.exists()) {
                "Directory does not exist: ${metaFile.parent}"
            }
        }

        val outFile = Path(outPath).toFile()
        require(outFile.parentFile.exists()) { "Directory does not exist: ${outFile.parent}" }

        val hrp = readHumanReadableProfileOrExit(hrpFile, StdErrorDiagnostics)
        val apk = Apk(apkFile)
        val obf = if (obfFile != null) ObfuscationMap(obfFile) else ObfuscationMap.Empty
        val profile = ArtProfile(hrp, obf, apk)
        profile.save(outFile.outputStream(), artProfileFormat.serializer)
        if (metaFile != null) {
            profile.save(metaFile.outputStream(), ArtProfileSerializer.METADATA_0_0_2)
        }
    }
}

@ExperimentalCli
class ExtractProfileCommand : Subcommand("extractProfile", "Extract Binary Profile as versioned dex metadata") {
    private val apkPath by option(ArgType.String, "apk", "a", "File path to apk").required()
    private val outPath by option(ArgType.String, "output-dex-metadata", "odm", "File path to generated dex metadata output").required()
    private val artProfileFormat by option(ArgType.Choice<ArtProfileFormat>(), "profile-format", "pf", "The ART profile format version").default(ArtProfileFormat.V0_1_0_P)

    override fun execute() {
        val apkFile = Path(apkPath).toFile()
        require(apkFile.exists()) { "File not found: $apkPath" }

        val outFile = Path(outPath).toFile()
        require(outFile.parentFile.exists()) { "Directory does not exist: ${outFile.parent}" }

        extractProfileAsDm(
            apkFile = apkFile,
            profileSerializer = artProfileFormat.serializer,
            metadataSerializer = ArtProfileSerializer.METADATA_0_0_2,
            outputStream = outFile.outputStream()
        )
    }
}

@ExperimentalCli
class ValidateCommand : Subcommand("validate", "Validate Profile") {
    val hrpPath by argument(ArgType.String, "profile", "File path to Human Readable profile")
    override fun execute() {
        val hrpFile = Path(hrpPath).toFile()
        require(hrpFile.exists()) { "File not found: $hrpPath" }
        HumanReadableProfile(hrpFile, StdErrorDiagnostics)
    }
}

@ExperimentalCli
class PrintCommand : Subcommand("print", "Print methods matching profile") {
    val hrpPath by argument(ArgType.String, "profile", "File path to Human Readable profile")
    val apkPath by option(ArgType.String, "apk", "a", "File path to apk").required()
    val outPath by option(ArgType.String, "output", "o", "File path to generated binary profile").required()
    val obfPath by option(ArgType.String, "map", "m", "File path to name obfuscation map")
    override fun execute() {
        val hrpFile = Path(hrpPath).toFile()
        require(hrpFile.exists()) { "File not found: $hrpPath" }

        val apkFile = Path(apkPath).toFile()
        require(apkFile.exists()) { "File not found: $apkPath" }

        val obfFile = obfPath?.let { Path(it).toFile() }
        require(obfFile?.exists() != false) { "File not found: $obfPath" }

        val outFile = Path(outPath).toFile()
        require(outFile.parentFile.exists()) { "Directory does not exist: ${outFile.parent}" }

        val hrp = readHumanReadableProfileOrExit(hrpFile, StdErrorDiagnostics)
        val apk = Apk(apkFile)
        val obf = if (obfFile != null) ObfuscationMap(obfFile) else ObfuscationMap.Empty
        val profile = ArtProfile(hrp, obf, apk)
        profile.print(System.out, obf)
    }
}
@ExperimentalCli
class ProfileDumpCommand: Subcommand("dumpProfile", "Dump a binary profile to a HRF") {
    val binPath by option(ArgType.String, "profile", "p", "File path to the binary profile").required()
    val apkPath by option(ArgType.String, "apk", "a", "File path to apk").required()
    val obfPath by option(ArgType.String, "map", "m", "File path to name obfuscation map")
    val strictMode by option(ArgType.Boolean, "strict", "s", "Strict mode").default(value = true)
    val outPath by option(ArgType.String, "output", "o", "File path for the HRF").required()
    override fun execute() {
        val binFile = Path(binPath).toFile()
        require(binFile.exists()) { "File not found: $binPath" }

        val apkFile = Path(apkPath).toFile()
        require(apkFile.exists()) { "File not found: $apkPath" }

        val obfFile = obfPath?.let { Path(it).toFile() }
        require(obfFile?.exists() != false) { "File not found: $obfPath" }

        val outFile = Path(outPath).toFile()
        require(outFile.parentFile.exists()) { "Directory does not exist: ${outFile.parent}" }

        val profile = ArtProfile(binFile)!!
        val apk = Apk(apkFile)
        val obf = if (obfFile != null) ObfuscationMap(obfFile) else ObfuscationMap.Empty
        dumpProfile(outFile, profile, apk, obf, strict = strictMode)
    }
}

val StdErrorDiagnostics = Diagnostics { System.err.println(it) }
