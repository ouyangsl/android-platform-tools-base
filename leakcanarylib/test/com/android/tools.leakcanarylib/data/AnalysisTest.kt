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
package com.android.tools.leakcanarylib.data

import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnalysisTest {

    @Test
    fun `heap analysis success - verify application and library leak`() {
        val heapAnalysisText = """
        ====================================
        HEAP ANALYSIS RESULT
        ====================================
        2 APPLICATION LEAKS

        References underlined with "~~~" are likely causes.
        Learn more at https://squ.re/leaks.

        2228 bytes retained by leaking objects
        Signature: 41c3c2258578581a1b0c9f78b59966266ed118b9
        ┬───
        │ GC Root: Input or output parameters in native code
        │
        ├─ dalvik.system.PathClassLoader instance
        │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
        │    ↓ ClassLoader.runtimeInternalObjects
        ├─ java.lang.Object[] array
        │    Leaking: NO (InternalLeakCanary↓ is not leaking)
        │    ↓ Object[747]
        ├─ leakcanary.internal.InternalLeakCanary class
        │    Leaking: NO (MainActivity↓ is not leaking and a class is never leaking)
        │    ↓ static InternalLeakCanary.resumedActivity
        ├─ com.amaze.filemanager.ui.activities.MainActivity instance
        │    Leaking: NO (AppBarLayout↓ is not leaking and Activity#mDestroyed is false)
        │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        │    mApplication instance of com.amaze.filemanager.application.AppConfig
        │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
        │    ↓ MainActivity.appBarLayout
        ├─ com.google.android.material.appbar.AppBarLayout instance
        │    Leaking: NO (AppsListFragment↓ is not leaking and View attached)
        │    View is part of a window view hierarchy
        │    View.mAttachInfo is not null (view attached)
        │    View.mID = R.id.lin
        │    View.mWindowAttachCount = 1
        │    mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        │    ↓ AppBarLayout.listeners
        ├─ java.util.ArrayList instance
        │    Leaking: NO (AppsListFragment↓ is not leaking)
        │    ↓ ArrayList[2]
        ├─ com.amaze.filemanager.ui.fragments.AppsListFragment${'$'}ExternalSyntheticLambda0 instance
        │    Leaking: NO (AppsListFragment↓ is not leaking)
        │    ↓ AppsListFragment${'$'}ExternalSyntheticLambda0.f${'$'}0
        ├─ com.amaze.filemanager.ui.fragments.AppsListFragment instance
        │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
        │    ↓ AppsListFragment.rootView
        │                       ~~~~~~~~
        ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
        ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.AppsListFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
        ​     Retaining 2.2 kB in 43 objects
        ​     key = 63dee33b-9463-414f-89e0-4cb58974e0db
        ​     watchDurationMillis = 234158
        ​     retainedDurationMillis = 229157
        ​     View not part of a window view hierarchy
        ​     View.mAttachInfo is null (view detached)
        ​     View.mWindowAttachCount = 1
        ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false

        8928 bytes retained by leaking objects
        Displaying only 1 leak trace out of 4 with the same signature
        Signature: 2d8918c3076020f19fa7ab4b6ca5cb4423772b20
        ┬───
        │ GC Root: Input or output parameters in native code
        │
        ├─ dalvik.system.PathClassLoader instance
        │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
        │    ↓ ClassLoader.runtimeInternalObjects
        ├─ java.lang.Object[] array
        │    Leaking: NO (InternalLeakCanary↓ is not leaking)
        │    ↓ Object[747]
        ├─ leakcanary.internal.InternalLeakCanary class
        │    Leaking: NO (MainActivity↓ is not leaking and a class is never leaking)
        │    ↓ static InternalLeakCanary.resumedActivity
        ├─ com.amaze.filemanager.ui.activities.MainActivity instance
        │    Leaking: NO (TabFragment↓ is not leaking and Activity#mDestroyed is false)
        │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        │    mApplication instance of com.amaze.filemanager.application.AppConfig
        │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
        │    ↓ ComponentActivity.mOnConfigurationChangedListeners
        ├─ java.util.concurrent.CopyOnWriteArrayList instance
        │    Leaking: NO (TabFragment↓ is not leaking)
        │    ↓ CopyOnWriteArrayList[2]
        ├─ androidx.fragment.app.FragmentManager${'$'}ExternalSyntheticLambda0 instance
        │    Leaking: NO (TabFragment↓ is not leaking)
        │    ↓ FragmentManager${'$'}ExternalSyntheticLambda0.f${'$'}0
        ├─ androidx.fragment.app.FragmentManagerImpl instance
        │    Leaking: NO (TabFragment↓ is not leaking)
        │    ↓ FragmentManager.mParent
        ├─ com.amaze.filemanager.ui.fragments.TabFragment instance
        │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
        │    ↓ TabFragment.rootView
        │                  ~~~~~~~~
        ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
        ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.TabFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
        ​     Retaining 2.2 kB in 43 objects
        ​     key = d8a25ea4-cdd7-4a2a-b459-afe3956b109b
        ​     watchDurationMillis = 242280
        ​     retainedDurationMillis = 237276
        ​     View not part of a window view hierarchy
        ​     View.mAttachInfo is null (view detached)
        ​     View.mWindowAttachCount = 1
        ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        ====================================
        2 LIBRARY LEAKS

        A Library Leak is a leak caused by a known bug in 3rd party code that you do not have control over.
        See https://square.github.io/leakcanary/fundamentals-how-leakcanary-works/#4-categorizing-leaks

        Leak pattern: native global variable referencing com.example.library.LeakingClass
        Description: LeakingClass holds a static reference to LeakedObject
        2300 bytes retained by leaking objects
        Signature: 41c3c2258578581a1b0c9f78b59966266ed118b9
        ┬───
        │ GC Root: Input or output parameters in native code
        │
        ├─ dalvik.system.PathClassLoader instance
        │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
        │    ↓ ClassLoader.runtimeInternalObjects
        ├─ java.lang.Object[] array
        │    Leaking: NO (InternalLeakCanary↓ is not leaking)
        │    ↓ Object[747]
        ├─ leakcanary.internal.InternalLeakCanary class
        │    Leaking: NO (MainActivity↓ is not leaking and a class is never leaking)
        │    ↓ static InternalLeakCanary.resumedActivity
        ├─ com.amaze.filemanager.ui.activities.MainActivity instance
        │    Leaking: NO (AppBarLayout↓ is not leaking and Activity#mDestroyed is false)
        │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        │    mApplication instance of com.amaze.filemanager.application.AppConfig
        │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
        │    ↓ MainActivity.appBarLayout
        ├─ com.google.android.material.appbar.AppBarLayout instance
        │    Leaking: NO (AppsListFragment↓ is not leaking and View attached)
        │    View is part of a window view hierarchy
        │    View.mAttachInfo is not null (view attached)
        │    View.mID = R.id.lin
        │    View.mWindowAttachCount = 1
        │    mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        │    ↓ AppBarLayout.listeners
        ├─ java.util.ArrayList instance
        │    Leaking: NO (AppsListFragment↓ is not leaking)
        │    ↓ ArrayList[2]
        ├─ com.amaze.filemanager.ui.fragments.AppsListFragment${'$'}ExternalSyntheticLambda0 instance
        │    Leaking: NO (AppsListFragment↓ is not leaking)
        │    ↓ AppsListFragment${'$'}ExternalSyntheticLambda0.f${'$'}0
        ├─ com.amaze.filemanager.ui.fragments.AppsListFragment instance
        │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
        │    ↓ AppsListFragment.rootView
        │                       ~~~~~~~~
        ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
        ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.AppsListFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
        ​     Retaining 2.2 kB in 43 objects
        ​     key = 63dee33b-9463-414f-89e0-4cb58974e0db
        ​     watchDurationMillis = 234158
        ​     retainedDurationMillis = 229157
        ​     View not part of a window view hierarchy
        ​     View.mAttachInfo is null (view detached)
        ​     View.mWindowAttachCount = 1
        ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false

        Leak pattern: native global variable referencing com.example.library.LeakingClass
        Description: LeakingClass holds a static reference to LeakedObject
        8928 bytes retained by leaking objects
        Displaying only 1 leak trace out of 4 with the same signature
        Signature: 2d8918c3076020f19fa7ab4b6ca5cb4423772b20
        ┬───
        │ GC Root: Input or output parameters in native code
        │
        ├─ dalvik.system.PathClassLoader instance
        │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
        │    ↓ ClassLoader.runtimeInternalObjects
        ├─ java.lang.Object[] array
        │    Leaking: NO (InternalLeakCanary↓ is not leaking)
        │    ↓ Object[747]
        ├─ leakcanary.internal.InternalLeakCanary class
        │    Leaking: NO (MainActivity↓ is not leaking and a class is never leaking)
        │    ↓ static InternalLeakCanary.resumedActivity
        ├─ com.amaze.filemanager.ui.activities.MainActivity instance
        │    Leaking: NO (TabFragment↓ is not leaking and Activity#mDestroyed is false)
        │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        │    mApplication instance of com.amaze.filemanager.application.AppConfig
        │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
        │    ↓ ComponentActivity.mOnConfigurationChangedListeners
        ├─ java.util.concurrent.CopyOnWriteArrayList instance
        │    Leaking: NO (TabFragment↓ is not leaking)
        │    ↓ CopyOnWriteArrayList[2]
        ├─ androidx.fragment.app.FragmentManager${'$'}ExternalSyntheticLambda0 instance
        │    Leaking: NO (TabFragment↓ is not leaking)
        │    ↓ FragmentManager${'$'}ExternalSyntheticLambda0.f${'$'}0
        ├─ androidx.fragment.app.FragmentManagerImpl instance
        │    Leaking: NO (TabFragment↓ is not leaking)
        │    ↓ FragmentManager.mParent
        ├─ com.amaze.filemanager.ui.fragments.TabFragment instance
        │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
        │    ↓ TabFragment.rootView
        │                  ~~~~~~~~
        ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
        ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.TabFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
        ​     Retaining 2.2 kB in 43 objects
        ​     key = d8a25ea4-cdd7-4a2a-b459-afe3956b109b
        ​     watchDurationMillis = 242280
        ​     retainedDurationMillis = 237276
        ​     View not part of a window view hierarchy
        ​     View.mAttachInfo is null (view detached)
        ​     View.mWindowAttachCount = 1
        ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        ====================================
        0 UNREACHABLE OBJECTS

        An unreachable object is still in memory but LeakCanary could not find a strong reference path
        from GC roots.
        ====================================
        METADATA

        Please include this in bug reports and Stack Overflow questions.

        Build.VERSION.SDK_INT: 34
        Build.MANUFACTURER: Google
        LeakCanary version: 2.11
        App process name: com.amaze.filemanager.debug
        Class count: 31515
        Instance count: 285274
        Primitive array count: 175482
        Object array count: 36824
        Thread count: 46
        Heap total bytes: 35738009
        Bitmap count: 43
        Bitmap total bytes: 5698726
        Large bitmap count: 0
        Large bitmap total bytes: 0
        Db 1: open /data/user/0/com.amaze.filemanager.debug/databases/explorer.db
        Db 2: open /data/user/0/com.amaze.filemanager.debug/databases/utilities.db
        Stats: LruCache[maxSize=3000,hits=150410,misses=271371,hitRate=35%] RandomAccess[bytes=16226939,reads=271371,travel=108916827463,range=43478898,size=52638494]
        Analysis duration: 2779 ms
        Heap dump file path: /Users/addivya/bin/leakcanary/shark/shark-cli/src/main/java/shark/data/3.hprof
        Heap dump timestamp: 1710721509247
        Heap dump duration: Unknown
        ====================================
    """.trimIndent()

        val actualAnalysis = Analysis.fromString(heapAnalysisText)
        val actualToString = actualAnalysis.toString()

        // Filter out the lines to ignore in the expected output
        val expectedLeakString = heapAnalysisText.lines()
            .filterNot { it.contains("Leak pattern:") || it.contains("Description:") }
            .joinToString("\n").trim()

        // Filter out the lines to ignore in the actual output
        val actualLeakString = actualToString.lines()
            .filterNot { it.contains("Leak pattern:") || it.contains("Description:") }
            .joinToString("\n").trim()

        assertEquals(expectedLeakString, actualLeakString)

        // Convert back to analysis object to verify
        val convertedAnalysis = Analysis.fromString(actualToString)
        assertEquals(actualAnalysis, convertedAnalysis)
    }

    @Test
    fun `heap analysis failure - verify metadata`() {
        val failureText = """
            ====================================
            METADATA
            Heap dump file path: /path/to/heapdump.hprof
            Heap dump timestamp: 1687311070556
            Analysis duration: 17730 ms
            ====================================
            STACKTRACE

            java.lang.OutOfMemoryError: Out of memory
                at com.example.app.MyClass.doSomething(MyClass.kt:123)
            ====================================
        """.trimIndent()

        val expectedFile = File("/path/to/heapdump.hprof")
        val expectedTimestamp = 1687311070556L
        val expectedAnalysisDuration = 17730L

        val result = Analysis.fromString(failureText) as AnalysisFailure
        assertEquals(expectedFile, result.heapDumpFile)
        assertEquals(expectedTimestamp, result.createdAtTimeMillis)
        assertEquals(expectedAnalysisDuration, result.analysisDurationMillis)
    }

    @Test
    fun `heap analysis failure - verify exception message`() {
        val failureText = """
            ====================================
            METADATA
            Heap dump file path: /path/to/heapdump.hprof
            Heap dump timestamp: 1690122505918
            Analysis duration: 3280 ms
            ====================================
            STACKTRACE
            com.example.app.MyCustomException: Something went wrong!
                at com.example.app.AnotherClass.doSomethingElse(AnotherClass.kt:45)
            ====================================
        """.trimIndent()

        val result = Analysis.fromString(failureText) as AnalysisFailure
        assertTrue(result.exception.message.toString().contains("Something went wrong!"))
    }

    @Test
    fun `test analysis from file`() {
        val currentDir = System.getProperty("user.dir")

        val listOfFiles = ImmutableList.of(
            "SingleApplicationLeak.txt",
            "SingleApplicationLeak2.txt",
            "SingleApplicationLeak3.txt",
            "SingleApplicationLeakAnalyzeCmd.txt",
            "MultiApplicationLeak.txt",
            "MultiApplicationLeak2.txt",
            "NoLeak.txt"
        )

        listOfFiles.forEach { fileName ->
            run {
                val file = File("$currentDir/tools/base/leakcanarylib/test/data/$fileName")
                val fileContent = file.readText()
                val result = Analysis.fromString(fileContent)
                val resultString = result.toString()
                if (fileName != "SingleApplicationLeak3.txt") {
                    // File SingleApplicationLeak3 is neglected because of LeakingStatusReason being in multi-line. It will parse
                    // successfully but toString() will convert them to single line causing the comparison to fail.
                    compareLeaks(fileContent.trim(), resultString.trim())
                }
                val convertBack = Analysis.fromString(resultString)
                assertEquals(result, convertBack)
            }
        }
    }

    private fun compareLeaks(expected: String, actual: String) {
        // Filter out the lines to ignore in the expected output
        val expectedLibraryLeakString = expected.lines()
            .filterNot { it.contains("RandomAccess[bytes=") }
            .joinToString("\n").trim()

        // Filter out the lines to ignore in the actual output
        val actualLibraryLeakString = actual.lines()
            .filterNot { it.contains("RandomAccess[bytes=") }
            .joinToString("\n").trim()

        assertEquals(expectedLibraryLeakString, actualLibraryLeakString)
    }
}
