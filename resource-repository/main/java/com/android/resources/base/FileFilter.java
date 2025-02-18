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
package com.android.resources.base;

import com.android.annotations.NonNull;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** A filter used to select files when traversing the file system. */
interface FileFilter {
  /** Returns true to skip the file or directory, or false to accept it. */
  boolean isIgnored(@NonNull Path fileOrDirectory, @NonNull BasicFileAttributes attrs);
}
