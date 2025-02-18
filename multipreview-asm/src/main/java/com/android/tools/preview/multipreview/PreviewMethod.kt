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

package com.android.tools.preview.multipreview

/**
 * Represents a preview method.
 *
 * @param method contains information about preview method and its parameters
 * @param previewAnnotations contains information about preview annotation and its parameters
 */
data class PreviewMethod(
    val method: MethodRepresentation,
    val previewAnnotations: Set<BaseAnnotationRepresentation>,
) {
    init {
        require(previewAnnotations.isNotEmpty()) { "previewAnnotations must not be empty" }
    }
}
