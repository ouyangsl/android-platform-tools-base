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

package com.android.tools.agent.appinspection.framework

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.android.tools.layoutinspector.BitmapType
import java.util.Stack
import kotlin.math.roundToInt

fun ViewGroup.getChildren(): List<View> {
    ThreadUtils.assertOnMainThread()
    return (0 until childCount).map { i -> getChildAt(i) }
}

/**
 * Return this node's text value, if it is a kind of node that has one.
 */
fun View.getTextValue(): String? {
    if (this !is TextView) return null
    return text?.toString()
}

/**
 * Return a list of this view and all its children in depth-first order
 */
fun View.flatten(): Sequence<View> {
    ThreadUtils.assertOnMainThread()

    return sequence {
        val toProcess = Stack<View>()
        toProcess.push(this@flatten)

        while (toProcess.isNotEmpty()) {
            val curr = toProcess.pop()
            yield(curr)
            if (curr is ViewGroup) {
                toProcess.addAll(curr.getChildren())
            }
        }
    }
}

/**
 * Convert this view into a bitmap.
 *
 * This method may return null if the app runs out of memory or has a reflection issue.
 */
fun View.takeScreenshot(scale: Float, bitmapType: BitmapType): Bitmap? {
    val scaledWidth = (width * scale).roundToInt()
    val scaledHeight = (height * scale).roundToInt()
    val surface = viewRootImpl?.mSurface ?: return null
    if (scaledWidth <= 0 || scaledHeight <= 0 || !surface.isValid) {
        return null
    }
    val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, bitmapType.toBitmapConfig())
    return try {
            val location = IntArray(2)
            getLocationInSurface(location)
            val bounds = Rect(location[0], location[1], width + location[0], height + location[1])
            val resultCode = SynchronousPixelCopy().request(surface, bounds, bitmap)
            if (resultCode == PixelCopy.SUCCESS) {
                bitmap
            } else {
                Log.w("ViewLayoutInspector", "PixelCopy got error code $resultCode")
                null
            }
    } catch (t: Throwable) {
        Log.w("ViewLayoutInspector", t)
        null
    }
}
