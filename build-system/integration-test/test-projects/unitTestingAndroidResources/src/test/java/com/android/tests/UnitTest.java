/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;

public class UnitTest {

    @Test
    public void androidResources() throws Exception {
        InputStream inputStream =
                UnitTest.class
                        .getClassLoader()
                        .getResourceAsStream("com/android/tools/test_config.properties");
        Properties properties = new Properties();
        properties.load(inputStream);

        for (Object key : properties.keySet()) {
            String value = properties.get(key).toString();
            if (key.equals("android_custom_package")) {
                // Check R class exists.
                try {
                    getClass().getClassLoader().loadClass(value + ".R");
                } catch (ClassNotFoundException e) {
                    throw new AssertionError("Expected R class in package " + value, e);
                }
            } else {
                assertTrue(key + " = " + value + " doesn't exist",
                        new File(value).exists());
            }
        }
    }

    @Test
    public void idsAreConsistent() throws Exception {
        Map<String, Integer> seenIds = new HashMap<>();

        for (Field field : androidx.appcompat.R.id.class.getFields()) {
            String name = field.getName();
            int appCompatId = (Integer) field.get(null);
            seenIds.put(name, appCompatId);
        }

        for (Field field : androidx.constraintlayout.widget.R.id.class.getFields()) {
            String name = field.getName();
            int constraintId = (Integer) field.get(null);

            // Make sure we are using fresh ints for fresh ids but reusing the same ints
            // for ids with name clashing with main code (or other libs).
            if (seenIds.containsKey(name)) {
                assertEquals(name, (int) seenIds.get(name), constraintId);
            } else {
                assertFalse(String.format("%x", constraintId), seenIds.containsValue(constraintId));
            }
        }
    }

    @Test
    public void stylablesArePresent() throws Exception {
        int[] stylable = androidx.appcompat.R.styleable.AppCompatTheme;
        int child = androidx.appcompat.R.styleable.AppCompatTheme_listPreferredItemPaddingRight;
        assertTrue(stylable.length > 0);
        assertTrue(child >= 0);
    }
}
