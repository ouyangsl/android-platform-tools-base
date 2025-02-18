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

package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.SystemPermissionsDetector.SYSTEM_PERMISSIONS;

import com.android.tools.lint.detector.api.Detector;
import java.util.List;

public class SystemPermissionsDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new SystemPermissionsDetector();
    }

  public void testDocumentationExample() {
    lint().files(
            xml(
                "AndroidManifest.xml",
                ""
                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                    + "    package=\"foo.bar2\"\n"
                    + "    android:versionCode=\"1\"\n"
                    + "    android:versionName=\"1.0\" >\n"
                    + "\n"
                    + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                    + "\n"
                    + "    <!-- Ok: -->\n"
                    + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
                    + "\n"
                    + "    <!-- Error -->\n"
                    + "    <uses-permission android:name=\"android.permission.BACKUP\" />\n"
                    + "\n"
                    + "</manifest>\n"))
        .run()
        .expect(""
            + "AndroidManifest.xml:13: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
            + "    <uses-permission android:name=\"android.permission.BACKUP\" />\n"
            + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "1 errors, 0 warnings");
  }

    public void testBrokenOrder() {
        //noinspection all // Sample code
        String expected =
                ""
                        + "AndroidManifest.xml:15: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.intent.category.MASTER_CLEAR.permission.C2D_MESSAGE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:16: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ACCESS_CACHE_FILESYSTEM\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:17: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ACCESS_CHECKIN_PROPERTIES\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:18: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ACCESS_MTP\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:19: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ACCESS_SURFACE_FLINGER\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:20: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ACCOUNT_MANAGER\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:21: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ALLOW_ANY_CODEC_FOR_PLAYBACK\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:22: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ASEC_ACCESS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:23: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ASEC_CREATE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:24: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ASEC_DESTROY\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:25: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ASEC_MOUNT_UNMOUNT\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:26: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.ASEC_RENAME\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:27: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BACKUP\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:28: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BIND_APPWIDGET\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:29: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BIND_DEVICE_ADMIN\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:30: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BIND_INPUT_METHOD\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:31: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BIND_PACKAGE_VERIFIER\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:32: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BIND_REMOTEVIEWS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:33: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BIND_TEXT_SERVICE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:34: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BIND_VPN_SERVICE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:35: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BIND_WALLPAPER\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:36: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BRICK\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:37: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BROADCAST_PACKAGE_REMOVED\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:38: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BROADCAST_SMS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:39: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BROADCAST_WAP_PUSH\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:40: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.CALL_PRIVILEGED\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:41: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.CHANGE_BACKGROUND_DATA_SETTING\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:42: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.CHANGE_COMPONENT_ENABLED_STATE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:43: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.CLEAR_APP_USER_DATA\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:44: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.CONFIRM_FULL_BACKUP\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:45: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.CONNECTIVITY_INTERNAL\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:46: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.CONTROL_LOCATION_UPDATES\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:47: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.COPY_PROTECTED_DATA\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:48: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.CRYPT_KEEPER\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:49: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.DELETE_CACHE_FILES\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:50: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.DELETE_PACKAGES\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:51: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.DEVICE_POWER\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:52: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.DIAGNOSTIC\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:53: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.DUMP\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:54: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.FACTORY_TEST\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:55: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.FORCE_BACK\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:56: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.FORCE_STOP_PACKAGES\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:57: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.GLOBAL_SEARCH\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:58: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.GLOBAL_SEARCH_CONTROL\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:59: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.HARDWARE_TEST\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:60: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.INJECT_EVENTS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:61: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.INSTALL_LOCATION_PROVIDER\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:62: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.INSTALL_PACKAGES\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:63: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.INTERNAL_SYSTEM_WINDOW\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:64: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.MANAGE_APP_TOKENS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:65: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.MANAGE_NETWORK_POLICY\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:66: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.MANAGE_USB\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:67: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.MASTER_CLEAR\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:68: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.MODIFY_NETWORK_ACCOUNTING\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:69: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.MODIFY_PHONE_STATE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:70: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.MOVE_PACKAGE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:71: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.NET_ADMIN\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:72: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.MODIFY_PHONE_STATE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:73: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.PACKAGE_USAGE_STATS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:74: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.PACKAGE_VERIFICATION_AGENT\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:75: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.PERFORM_CDMA_PROVISIONING\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:76: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.READ_FRAME_BUFFER\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:77: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.READ_INPUT_STATE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:78: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.READ_NETWORK_USAGE_HISTORY\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:79: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.READ_PRIVILEGED_PHONE_STATE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:80: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.REBOOT\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:81: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.RECEIVE_EMERGENCY_BROADCAST\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:82: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.REMOVE_TASKS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:83: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.RETRIEVE_WINDOW_CONTENT\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:84: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.SEND_SMS_NO_CONFIRMATION\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:85: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.SET_ACTIVITY_WATCHER\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:86: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.SET_ORIENTATION\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:87: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.SET_POINTER_SPEED\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:88: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.SET_PREFERRED_APPLICATIONS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:89: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.SET_SCREEN_COMPATIBILITY\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:90: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.SET_TIME\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:91: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.SET_WALLPAPER_COMPONENT\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:92: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.SHUTDOWN\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:93: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.STATUS_BAR\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:94: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.STATUS_BAR_SERVICE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:95: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.STOP_APP_SWITCHES\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:96: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.UPDATE_DEVICE_STATS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:97: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.WRITE_APN_SETTINGS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:98: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.WRITE_GSERVICES\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:99: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.WRITE_MEDIA_STORAGE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:100: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.WRITE_SECURE_SETTINGS\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:101: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                        + "    <uses-permission android:name=\"android.permission.BIND_CALL_STREAMING_SERVICE\" />\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "87 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"foo.bar2\"\n"
                                        + "    android:versionCode=\"1\"\n"
                                        + "    android:versionName=\"1.0\" >\n"
                                        + "\n"
                                        + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                                        + "\n"
                                        + "    <!-- No warnings for those -->\n"
                                        + "    <uses-permission android:name=\"android.permission.GET_ACCOUNTS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SEND_SMS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
                                        + "\n"
                                        + "    <!-- Warnings for those -->\n"
                                        + "    <uses-permission android:name=\"android.intent.category.MASTER_CLEAR.permission.C2D_MESSAGE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ACCESS_CACHE_FILESYSTEM\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ACCESS_CHECKIN_PROPERTIES\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ACCESS_MTP\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ACCESS_SURFACE_FLINGER\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ACCOUNT_MANAGER\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ALLOW_ANY_CODEC_FOR_PLAYBACK\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ASEC_ACCESS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ASEC_CREATE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ASEC_DESTROY\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ASEC_MOUNT_UNMOUNT\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.ASEC_RENAME\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BACKUP\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BIND_APPWIDGET\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BIND_DEVICE_ADMIN\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BIND_INPUT_METHOD\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BIND_PACKAGE_VERIFIER\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BIND_REMOTEVIEWS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BIND_TEXT_SERVICE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BIND_VPN_SERVICE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BIND_WALLPAPER\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BRICK\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BROADCAST_PACKAGE_REMOVED\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BROADCAST_SMS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BROADCAST_WAP_PUSH\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.CALL_PRIVILEGED\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.CHANGE_BACKGROUND_DATA_SETTING\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.CHANGE_COMPONENT_ENABLED_STATE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.CLEAR_APP_USER_DATA\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.CONFIRM_FULL_BACKUP\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.CONNECTIVITY_INTERNAL\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.CONTROL_LOCATION_UPDATES\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.COPY_PROTECTED_DATA\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.CRYPT_KEEPER\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.DELETE_CACHE_FILES\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.DELETE_PACKAGES\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.DEVICE_POWER\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.DIAGNOSTIC\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.DUMP\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.FACTORY_TEST\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.FORCE_BACK\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.FORCE_STOP_PACKAGES\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.GLOBAL_SEARCH\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.GLOBAL_SEARCH_CONTROL\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.HARDWARE_TEST\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.INJECT_EVENTS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.INSTALL_LOCATION_PROVIDER\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.INSTALL_PACKAGES\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.INTERNAL_SYSTEM_WINDOW\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.MANAGE_APP_TOKENS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.MANAGE_NETWORK_POLICY\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.MANAGE_USB\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.MASTER_CLEAR\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.MODIFY_NETWORK_ACCOUNTING\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.MODIFY_PHONE_STATE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.MOVE_PACKAGE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.NET_ADMIN\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.MODIFY_PHONE_STATE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.PACKAGE_USAGE_STATS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.PACKAGE_VERIFICATION_AGENT\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.PERFORM_CDMA_PROVISIONING\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.READ_FRAME_BUFFER\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.READ_INPUT_STATE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.READ_NETWORK_USAGE_HISTORY\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.READ_PRIVILEGED_PHONE_STATE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.REBOOT\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.RECEIVE_EMERGENCY_BROADCAST\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.REMOVE_TASKS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.RETRIEVE_WINDOW_CONTENT\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SEND_SMS_NO_CONFIRMATION\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SET_ACTIVITY_WATCHER\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SET_ORIENTATION\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SET_POINTER_SPEED\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SET_PREFERRED_APPLICATIONS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SET_SCREEN_COMPATIBILITY\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SET_TIME\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SET_WALLPAPER_COMPONENT\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SHUTDOWN\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.STATUS_BAR\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.STATUS_BAR_SERVICE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.STOP_APP_SWITCHES\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.UPDATE_DEVICE_STATS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.WRITE_APN_SETTINGS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.WRITE_GSERVICES\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.WRITE_MEDIA_STORAGE\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.WRITE_SECURE_SETTINGS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.BIND_CALL_STREAMING_SERVICE\" />\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                                        + "        android:label=\"@string/app_name\" >\n"
                                        + "        <activity\n"
                                        + "            android:label=\"@string/app_name\"\n"
                                        + "            android:name=\".Foo2Activity\" >\n"
                                        + "            <intent-filter >\n"
                                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                                        + "\n"
                                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .run()
                .expect(expected);
    }

    public void testSuppressed() {
        //noinspection all // Sample code
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    package=\"foo.bar2\"\n"
                                        + "    android:versionCode=\"1\"\n"
                                        + "    android:versionName=\"1.0\" >\n"
                                        + "\n"
                                        + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                                        + "\n"
                                        + "    <!-- No warnings for these: -->\n"
                                        + "    <uses-permission android:name=\"android.permission.GET_ACCOUNTS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.SEND_SMS\" />\n"
                                        + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
                                        + "\n"
                                        + "    <!-- Warnings for these: -->\n"
                                        + "    <uses-permission android:name=\"android.intent.category.MASTER_CLEAR.permission.C2D_MESSAGE\" tools:ignore=\"ProtectedPermissions\" />\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                                        + "        android:label=\"@string/app_name\" >\n"
                                        + "        <activity\n"
                                        + "            android:label=\"@string/app_name\"\n"
                                        + "            android:name=\".Foo2Activity\" >\n"
                                        + "            <intent-filter >\n"
                                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                                        + "\n"
                                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .run()
                .expectClean();
    }

    public void testMaxVersion() {
        // Regression test for 71701793: android.permission.SYSTEM_ALERT_WINDOW causing lint failure
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.lintbugs\">\n"
                                        + "    <uses-permission android:name=\"android.permission.MOUNT_UNMOUNT_FILESYSTEMS\" android:maxSdkVersion=\"15\"/><!-- OK -->\n"
                                        + "    <uses-permission android:name=\"android.permission.MOUNT_UNMOUNT_FILESYSTEMS\" android:maxSdkVersion=\"16\"/><!-- OK -->\n"
                                        + "    <uses-permission android:name=\"android.permission.MOUNT_UNMOUNT_FILESYSTEMS\" android:maxSdkVersion=\"17\"/><!-- ERROR -->\n"
                                        + "</manifest>"))
                .run()
                .expect(
                        ""
                                + "AndroidManifest.xml:6: Error: Permission is only granted to system apps [ProtectedPermissions]\n"
                                + "    <uses-permission android:name=\"android.permission.MOUNT_UNMOUNT_FILESYSTEMS\" android:maxSdkVersion=\"17\"/><!-- ERROR -->\n"
                                + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void testMaxNoLongerNeeded() {
        // MOUNT_UNMOUNT_FILESYSTEMS is only protected up until (and including) API level 16. Don't
        // need
        // to set maxSdkVersion if minSdkVersion is higher.
        lint().files(
                        manifest(
                                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<manifest"
                                    + " xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                    + "    package=\"com.example.lintbugs\">\n"
                                    + "    <uses-sdk android:minSdkVersion=\"17\""
                                    + " android:targetSdkVersion=\"35\" />\n"
                                    + "    <uses-permission"
                                    + " android:name=\"android.permission.MOUNT_UNMOUNT_FILESYSTEMS\"/><!--"
                                    + " OK -->\n"
                                    + "</manifest>"))
                .run()
                .expectClean();
    }

    public void testRequestInstallPackages() {
        // Regression test for 73857733
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"com.example.lintbugs\">\n"
                                        + "    <uses-permission android:name=\"android.permission.REQUEST_INSTALL_PACKAGES\"/><!-- OK -->\n"
                                        + "    <uses-permission android:name=\"android.permission.SYSTEM_ALERT_WINDOW\"/><!-- OK -->\n"
                                        + "</manifest>"))
                .run()
                .expectClean();
    }

    public void testDbUpToDate() {
        PermissionDataGenerator generator = new PermissionDataGenerator();
        List<Permission> permissions = generator.getSignaturePermissions(false);
        if (permissions.isEmpty()) {
            return;
        }
        String[] names = generator.getPermissionNames(permissions);
        generator.assertSamePermissions(SYSTEM_PERMISSIONS, names);

        /*
        When updating, also check the following -- that the first generated switch
        below matches SystemPermissionsDetector#getLastNonSignatureApiLevel,
        and that the second switch cases continue to have just one case,
        which is currently checked in visitElement -- if there are additional cases,
        insert actual switch.

        System.out.println("Switch statement for handling signature permissions that " +
                "weren't signature permissions until a given API level:");
        System.out.println(generator.getLastNonSignatureApiLevelSwitch());

        System.out.println("Switch statement for handling signature permissions that " +
                "were signature permissions up until a given version:");
        System.out.println(generator.getRemovedSignaturePermissionSwitch());
        */
    }
}
