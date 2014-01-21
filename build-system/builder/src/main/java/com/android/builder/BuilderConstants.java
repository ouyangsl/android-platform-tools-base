/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder;

/**
 * Generic constants.
 */
public class BuilderConstants {

    /**
     * Extension for library packages.
     */
    public static final String EXT_LIB_ARCHIVE = "aar";

    /**
     * The name of the default config.
     */
    public static final String MAIN = "main";

    public static final String DEBUG = "debug";
    public static final String RELEASE = "release";

    public static final String LINT = "lint";

    public static final String FD_REPORTS = "reports";

    public static final String CONNECTED = "connected";
    public static final String DEVICE = "device";

    public static final String INSTRUMENT_TEST = "instrumentTest";
    public static final String FD_INSTRUMENT_TESTS = "instrumentTests";
    public static final String FD_INSTRUMENT_RESULTS = INSTRUMENT_TEST + "-results";

    public static final String UI_TEST = "uiTest";
    public static final String FD_UI_TESTS = "uiTests";
    public static final String FD_UI_RESULTS = UI_TEST + "-results";

    public static final String FD_FLAVORS = "flavors";
    public static final String FD_FLAVORS_ALL = "all";
}
