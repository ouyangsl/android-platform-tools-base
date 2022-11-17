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

package com.android.build.api.variant.impl

import com.android.SdkConstants
import com.android.build.api.variant.SourceDirectories
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.BuilderConstants
import com.android.ide.common.resources.AssetSet
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable
import java.io.File

open class LayeredSourceDirectoriesImpl(
    _name: String,
    private val variantServices: VariantServices,
    variantDslFilters: PatternFilterable?
): SourceDirectoriesImpl(_name, variantServices, variantDslFilters),
    SourceDirectories.Layered {

    // For compatibility with the old variant API, we must allow reading the content of this list
    // before it is finalized.
    @Suppress("UNCHECKED_CAST")
    protected val  variantSources: ListProperty<DirectoryEntries> =
        variantServices.newListPropertyForInternalUse(DirectoryEntries::class.java)

    // this will contain all the directories
    @Suppress("UNCHECKED_CAST")
    private val directories: ListProperty<Collection<Directory>> =
        variantServices.newListPropertyForInternalUse(Collection::class.java) as ListProperty<Collection<Directory>>


    override val all: Provider<List<Collection<Directory>>> = directories.map {
            it.reversed()
        }


    //
    // Internal APIs
    //
    override fun addSource(directoryEntry: DirectoryEntry) {
        variantSources.add(DirectoryEntries(directoryEntry.name, listOf(directoryEntry)))
        variantServices.newListPropertyForInternalUse(Directory::class.java).also {
            it.addAll(
                directoryEntry.asFiles(
                    variantServices.provider {
                        variantServices.projectInfo.projectDirectory
                    }
                )
            )
            directories.add(it)
        }
    }

    internal fun addSources(sources: DirectoryEntries) {
        variantSources.add(sources)
        variantServices.newListPropertyForInternalUse(Directory::class.java).also {
            sources.directoryEntries.forEach { directoryEntry ->
                it.addAll(
                    directoryEntry.asFiles(
                        variantServices.provider {
                            variantServices.projectInfo.projectDirectory
                        }
                    )
                )
            }
            directories.add(it)
        }
    }

    fun getVariantSources(): Provider<List<DirectoryEntries>> = variantSources

    /**
     * Returns the list of local source files which filters out the user added folders as well as
     * any generated folders.
     */
    fun getLocalSourcesAsFileCollection(): Provider<Map<String, FileCollection>> =
        getVariantSources().map { allSources ->
            allSources.associate { directoryEntries ->
                directoryEntries.name to
                        variantServices.fileCollection(directoryEntries.directoryEntries
                            .filterNot { it.isUserAdded || it.isGenerated}
                            .map { it.asFiles(
                                variantServices.provider {
                                    variantServices.projectInfo.projectDirectory
                                })
                            }
                        )
            }
        }

    /*
     * Internal API that can only be used by the model.
     */
    override fun variantSourcesForModel(filter: (DirectoryEntry) -> Boolean ): List<File> {
        val files = mutableListOf<File>()
        variantSources.get()
            .map { it.directoryEntries}
            .flatten()
            .filter { filter.invoke(it) }
            .forEach {
                if (it is TaskProviderBasedDirectoryEntryImpl) {
                    files.add(it.directoryProvider.get().asFile)
                } else {
                    val asDirectoriesProperty = it.asFiles(
                        variantServices.provider {
                            variantServices.projectInfo.projectDirectory
                        }
                    )
                    if (asDirectoriesProperty.isPresent) {
                        files.addAll(asDirectoriesProperty.get().map { directory ->
                            directory.asFile
                        })
                    }
                }
            }
        return files
    }

    /**
     * Returns the dynamic list of [AssetSet] based on the current list of [DirectoryEntry]
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in an
     * asset merger
     *
     * @param aaptEnv the value of "ANDROID_AAPT_IGNORE" environment variable.
     * @return a [Provider] of a [List] of [Provider] of [AssetSet].
     */
    fun getAscendingOrderAssetSets(
        aaptEnv: Provider<String>
    ): Provider<List<Provider<AssetSet>>> {

        return variantSources.map { allDirectories ->
            allDirectories.map { directoryEntries ->
                val assetName = if (directoryEntries.name == SdkConstants.FD_MAIN)
                    BuilderConstants.MAIN else directoryEntries.name

                directoryEntries.directoryEntries.map { directoryEntry ->
                    directoryEntry.asFiles(
                        variantServices.provider {
                            variantServices.projectInfo.projectDirectory
                        }
                    ).map {
                        AssetSet(assetName, aaptEnv.orNull).also { assetSet ->
                            assetSet.addSources(it.map { it.asFile })
                        }
                    }
                }
            }.flatten()
        }
    }
}
