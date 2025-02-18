/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.zipflinger.ZipRepo
import java.nio.file.Files
import java.nio.file.Paths
import kexter.Dex
import kexter.DexBytecode
import kexter.DexClass
import kexter.DexDumper
import kexter.Logger

open class DexArchiveBase(private val archiveName: String) {

  val dex = getTestResourceDex(archiveName)

  private fun getTestResourceDex(filename: String): Dex {
    val path = "tools/base/kexter/$filename"
    var resourcesPath = Paths.get(path)
    if (!Files.exists(resourcesPath)) {
      resourcesPath = Paths.get("bazel-bin/$path")
    }
    val repo = ZipRepo(resourcesPath)
    repo.use {
      val logger = Logger()
      val dex = Dex.fromBytes(repo.getContent("classes.dex").array(), logger)
      DexDumper.dump(dex, logger)
      return dex
    }
  }

  internal fun getRawBytecode(className: String, methodName: String): ByteArray {
    return getByteCode(className, methodName).bytes
  }

  internal fun getByteCode(className: String, methodName: String): DexBytecode {
    if (!dex.classes.containsKey(className)) {
      throw IllegalStateException(
        "Unable to find class $className, found:${dex.classes.keys.joinToString(",\n")}"
      )
    }
    val clazz = dex.classes[className]!!
    if (!clazz.methods.containsKey(methodName)) {
      throw IllegalStateException(
        "Unable to find method $methodName in class $className. Found:\n ${clazz.methods.keys.joinToString(",\n" )}"
      )
    }
    val method = clazz.methods[methodName]!!
    return method.byteCode
  }

  internal fun allClasses() = dex.classes

  internal fun retrieveClass(internalClassName: String): DexClass {
    if (!dex.classes.containsKey(internalClassName)) {
      throw IllegalStateException(
        "No class $internalClassName in dex $archiveName. Only found:\n${
                    dex.classes.keys.joinToString(
                        "\n"
                    )
                }"
      )
    }
    return dex.classes[internalClassName]!!
  }
}
