/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.testprojects.prebuilts

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.AndroidProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject

/**
 * Creates a [GradleTestProject] and initialize it with a HelloWorld App module
 */
fun createHelloWorldAppGradleProject(): GradleTestProject {
    return createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    }
}


fun AndroidProjectBuilder.setUpHelloWorld(setupDefaultCompileSdk: Boolean = true) {
    if (setupDefaultCompileSdk) {
        defaultCompileSdk()
    }
    addFile(
        "src/main/java/${namespace.replace('.','/')}/HelloWorld.java",
        // language=java
        """
                package $namespace;

                import android.app.Activity;
                import android.os.Bundle;

                public class HelloWorld extends Activity {
                    /** Called when the activity is first created. */
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.main);
                    }
                }
            """.trimIndent()
    )

    addFile(
        "src/main/res/values/strings.xml",
        // language=xml
        """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name">HelloWorld</string>
                </resources>
            """.trimIndent()
    )

    addFile(
        "src/main/res/layout/main.xml",
        // language=xml
        """
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:orientation="vertical"
                    android:layout_width="fill_parent"
                    android:layout_height="fill_parent"
                    >
                <TextView
                    android:layout_width="fill_parent"
                    android:layout_height="wrap_content"
                    android:text="hello world!"
                    android:id="@+id/text"
                    />
                </LinearLayout>
            """.trimIndent()
    )

    addFile(
        "src/main/AndroidManifest.xml",
        // language=xml
        """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                      android:versionCode="1"
                      android:versionName="1.0">

                    <application android:label="@string/app_name">
                        <activity android:name=".HelloWorld"
                                  android:exported="true"
                                  android:label="@string/app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent()
    )
}
