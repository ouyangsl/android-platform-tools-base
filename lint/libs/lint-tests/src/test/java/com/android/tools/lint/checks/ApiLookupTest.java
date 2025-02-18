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

import static com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS;
import static com.android.tools.lint.checks.ApiClass.STRIP_MEMBERS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.IAndroidTarget;
import com.android.testutils.TestUtils;
import com.android.tools.lint.MainTest;
import com.android.tools.lint.checks.infrastructure.LintTestUtils;
import com.android.tools.lint.checks.infrastructure.TestLintResult;
import com.android.tools.lint.checks.infrastructure.TestLintTask;
import com.android.tools.lint.checks.infrastructure.TestMode;
import com.android.tools.lint.client.api.PlatformLookup;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.Pair;

import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"ConstantConditions"})
public class ApiLookupTest extends AbstractCheckTest {
    @SuppressWarnings("deprecation")
    private final ApiLookup mDb = ApiLookup.get(createClient());

    private int getClassVersion(String owner) {
        return mDb.getClassVersions(owner).min();
    }

    private int getMethodVersion(String owner, String name, String desc) {
        return mDb.getMethodVersions(owner, name, desc).min();
    }

    private int getFieldVersion(String owner, String name) {
        return mDb.getFieldVersions(owner, name).min();
    }

    @SuppressWarnings("SameParameterValue")
    private int getFieldVersion(ApiLookup lookup, String owner, String name) {
        return lookup.getFieldVersions(owner, name).min();
    }

    private int getCastVersion(String source, String destination) {
        return mDb.getValidCastVersions(source, destination).min();
    }

    public int getFieldDeprecatedIn(@NonNull String owner, @NonNull String name) {
        return mDb.getFieldDeprecatedInVersions(owner, name).min();
    }

    public int getMethodDeprecatedIn(
            @NonNull String owner, @NonNull String name, @NonNull String desc) {
        return mDb.getMethodDeprecatedInVersions(owner, name, desc).min();
    }

    public int getClassDeprecatedIn(@NonNull String className) {
        return mDb.getClassDeprecatedInVersions(className).min();
    }

    public int getClassRemovedIn(@NonNull String className) {
        return mDb.getClassRemovedInVersions(className).min();
    }

    public int getFieldRemovedIn(@NonNull String owner, @NonNull String name) {
        return mDb.getFieldRemovedInVersions(owner, name).min();
    }

    public int getMethodRemovedIn(
            @NonNull String owner, @NonNull String name, @NonNull String desc) {
        return mDb.getMethodRemovedInVersions(owner, name, desc).min();
    }

    public void testBasic() {
        assertEquals(5, getFieldVersion("android.Manifest$permission", "AUTHENTICATE_ACCOUNTS"));
        assertEquals(5, getFieldVersion("android.Manifest.permission", "AUTHENTICATE_ACCOUNTS"));
        assertEquals(5, getFieldVersion("android/Manifest$permission", "AUTHENTICATE_ACCOUNTS"));
        assertTrue(getFieldVersion("android/R$attr", "absListViewStyle") <= 1);
        assertEquals(11, getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertEquals(
                5,
                getMethodVersion(
                        "android.graphics.drawable.BitmapDrawable",
                        "<init>",
                        "(Landroid.content.res.Resources;Ljava.lang.String;)V"));
        assertEquals(
                5,
                getMethodVersion(
                        "android/graphics/drawable/BitmapDrawable",
                        "<init>",
                        "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        assertEquals(
                4,
                getMethodVersion(
                        "android/graphics/drawable/BitmapDrawable",
                        "setTargetDensity",
                        "(Landroid/util/DisplayMetrics;)V"));
        assertEquals(7, getClassVersion("android/app/WallpaperInfo"));
        assertEquals(11, getClassVersion("android/widget/StackView"));
        assertTrue(getClassVersion("ava/text/ChoiceFormat") <= 1);

        // Class lookup: Unknown class
        assertEquals(-1, getClassVersion("foo/Bar"));
        // Field lookup: Unknown class
        assertEquals(-1, getFieldVersion("foo/Bar", "FOOBAR"));
        // Field lookup: Unknown field
        assertEquals(
                STRIP_MEMBERS ? 1 : -1, getFieldVersion("android/Manifest$permission", "FOOBAR"));
        // Method lookup: Unknown class
        assertEquals(
                -1,
                getMethodVersion(
                        "foo/Bar",
                        "<init>",
                        "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        // Method lookup: Unknown name
        assertEquals(
                STRIP_MEMBERS ? 1 : -1,
                getMethodVersion(
                        "android/graphics/drawable/BitmapDrawable",
                        "foo",
                        "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        // Method lookup: Unknown argument list
        assertEquals(
                STRIP_MEMBERS ? 1 : -1,
                getMethodVersion("android/graphics/drawable/BitmapDrawable", "<init>", "(I)V"));
    }

    public void testWildcardSyntax() {
        // Regression test:
        // This used to return 11 because of some wildcard syntax in the signature
        assertTrue(getMethodVersion("java/lang/Object", "getClass", "()") <= 1);
    }

    public void testIssue26467() {
        // We no longer support negative lookup; this isn't something we've needed in
        // actual detectors, so we can save space and time by omitting this info.
        //   assertTrue(getMethodVersion("java/nio/ByteBuffer", "array", "()") <= 1);
        assertEquals(9, getMethodVersion("java/nio/Buffer", "array", "()"));
    }

    public void testNoInheritedConstructors() {
        assertTrue(getMethodVersion("java/util/zip/ZipOutputStream", "<init>", "()") <= 1);
        assertTrue(
                getMethodVersion(
                                "android/app/AliasActivity",
                                "<init>",
                                "(Landroid/content/Context;I)")
                        <= 1);
    }

    public void testIssue35190() {
        assertEquals(
                9, getMethodVersion("java/io/IOException", "<init>", "(Ljava/lang/Throwable;)V"));
    }

    public void testSyntheticConstructorParameter() {
        assertEquals(
                11,
                getMethodVersion(
                        "android/content/Loader$ForceLoadContentObserver",
                        "<init>",
                        "(Landroid/content/Loader;)"));
    }

    public void testDeprecatedFields() {
        // Not deprecated:
        assertEquals(-1, getFieldDeprecatedIn("android/Manifest$permission", "GET_PACKAGE_SIZE"));
        // Field only has since > 1, no deprecation
        assertEquals(9, getFieldVersion("android/Manifest$permission", "NFC"));

        // Deprecated
        assertEquals(21, getFieldDeprecatedIn("android/Manifest$permission", "GET_TASKS"));
        // Field both deprecated and since > 1
        assertEquals(21, getFieldDeprecatedIn("android/Manifest$permission", "READ_SOCIAL_STREAM"));
        assertEquals(15, getFieldVersion("android/Manifest$permission", "READ_SOCIAL_STREAM"));
    }

    public void testDeprecatedMethods() {
        assertEquals(
                24,
                getMethodDeprecatedIn(
                        "android/app/Activity", "setProgressBarIndeterminate", "(Z)V"));
        String apiVersionString =
                StringsKt.substringAfterLast(
                        mDb.xmlFile.getParentFile().getParentFile().getPath(), "-", "");
        int apiDatabaseLevel = Integer.parseInt(apiVersionString);
        assertEquals(
                // Deprecated in API level 35, which we know once the API database is 35 or later
                apiDatabaseLevel >= 35 ? 35 : -1,
                getMethodDeprecatedIn(
                        "android/app/Activity", "getParent", "()Landroid/app/Activity;"));
        assertEquals(-1, getMethodDeprecatedIn("android/app/Activity", "getTaskId", "()I"));
        // Deprecated
        assertEquals(
                17,
                getMethodDeprecatedIn(
                        "android/content/IntentSender",
                        "getTargetPackage",
                        "()Ljava/lang/String;"));
        assertEquals(
                23,
                getMethodDeprecatedIn(
                        "android/app/Fragment",
                        "onInflate",
                        "(Landroid/app/Activity;Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
    }

    public void testDeprecatedClasses() {
        // Not deprecated:
        assertEquals(-1, getClassDeprecatedIn("android/app/Activity"));
        // Deprecated
        assertEquals(9, getClassDeprecatedIn("org/xml/sax/Parser"));
    }

    public void testRemovedFields() {
        // Not removed
        assertEquals(-1, getFieldRemovedIn("android/Manifest$permission", "GET_PACKAGE_SIZE"));
        // Field only has since > 1, no removal
        assertEquals(9, getFieldVersion("android/Manifest$permission", "NFC"));

        // Removed
        assertEquals(23, getFieldRemovedIn("android/Manifest$permission", "ACCESS_MOCK_LOCATION"));
        // Field both removed and since > 1
        assertEquals(23, getFieldRemovedIn("android/Manifest$permission", "AUTHENTICATE_ACCOUNTS"));
    }

    public void testRemovedMethods() {
        // Not removed
        assertEquals(
                -1,
                getMethodRemovedIn(
                        "android/app/Activity",
                        "enterPictureInPictureMode",
                        "(Landroid/app/PictureInPictureArgs;)Z"));
        // Moved to an interface
        assertEquals(
                -1, getMethodRemovedIn("android/database/sqlite/SQLiteDatabase", "close", "()V"));
        // Removed
        assertEquals(11, getMethodRemovedIn("android/app/Activity", "setPersistent", "(Z)V"));
    }

    public void testGetRemovedFields() {
        Collection<ApiMember> removedFields = mDb.getRemovedFields("android/Manifest$permission");
        assertTrue(removedFields.contains(new ApiMember("ACCESS_MOCK_LOCATION", 1, 0, 23)));
        assertTrue(removedFields.contains(new ApiMember("FLASHLIGHT", 1, 0, 24)));
        assertTrue(removedFields.contains(new ApiMember("READ_SOCIAL_STREAM", 15, 21, 23)));
        assertTrue(removedFields.stream().noneMatch(member -> member.getSignature().equals("NFC")));
    }

    public void testRemovedClasses() {
        // Not removed
        assertEquals(-1, getClassRemovedIn("android/app/Fragment"));
        // Removed
        assertEquals(24, getClassRemovedIn("android/graphics/AvoidXfermode"));
    }

    public void testInheritInterfaces() {
        // The onPreferenceStartFragment is inherited via the
        // android/preference/PreferenceFragment$OnPreferenceStartFragmentCallback
        // interface
        assertEquals(
                11,
                getMethodVersion(
                        "android/preference/PreferenceActivity",
                        "onPreferenceStartFragment",
                        "(Landroid/preference/PreferenceFragment;Landroid/preference/Preference;)"));
    }

    public void testInterfaceApi() {
        assertEquals(21, getClassVersion("android/animation/StateListAnimator"));
        assertEquals(
                11,
                getCastVersion(
                        "android/animation/AnimatorListenerAdapter",
                        "android/animation/Animator$AnimatorListener"));
        assertEquals(
                19,
                getCastVersion(
                        "android/animation/AnimatorListenerAdapter",
                        "android/animation/Animator$AnimatorPauseListener"));

        assertEquals(11, getCastVersion("android/animation/Animator", "java/lang/Cloneable"));
        assertEquals(
                22, getCastVersion("android/animation/StateListAnimator", "java/lang/Cloneable"));

        // Inherited interfaces

        assertEquals(
                3,
                getCastVersion(
                        "android/opengl/GLSurfaceView", "android/content/ComponentCallbacks"));
        assertEquals(
                3,
                getCastVersion(
                        "android/opengl/GLSurfaceView", "android/view/SurfaceHolder$Callback"));
        assertEquals(
                11,
                getCastVersion("android/app/DialogFragment", "android/content/ComponentCallbacks"));
        assertEquals(1, getCastVersion("android/widget/ArrayAdapter", "android/widget/Adapter"));
        assertEquals(
                23,
                getCastVersion(
                        "android/widget/ArrayAdapter", "android/widget/ThemedSpinnerAdapter"));
        assertEquals(
                1, getCastVersion("android/widget/ArrayAdapter", "android/widget/SpinnerAdapter"));
        assertEquals(
                24,
                getCastVersion("android/content/ContentProviderClient", "java/lang/AutoCloseable"));
        assertEquals(
                5,
                getCastVersion(
                        "android/content/ContentProviderClient", "java.io.Closeable")); // CHECK
        assertEquals(
                28, getCastVersion("android.net.LocalServerSocket", "java.lang.AutoCloseable"));
        assertEquals(28, getCastVersion("android.net.LocalServerSocket", "java.io.Closeable"));
    }

    public void testSuperClassCast() {
        assertEquals(
                22,
                getCastVersion(
                        "android/view/animation/AccelerateDecelerateInterpolator",
                        "android/view/animation/BaseInterpolator"));
    }

    public void testIsValidPackage() {
        assertTrue(isValidJavaPackage("java/lang/Integer"));
        assertTrue(isValidJavaPackage("java/util/Map$Entry"));
        assertTrue(isValidJavaPackage("javax/crypto/Cipher"));
        assertTrue(isValidJavaPackage("java/awt/font/NumericShaper"));

        assertFalse(isValidJavaPackage("javax/swing/JButton"));
        assertFalse(isValidJavaPackage("java/rmi/Naming"));
        assertFalse(isValidJavaPackage("java/lang/instrument/Instrumentation"));
    }

    private boolean isValidJavaPackage(String className) {
        return mDb.isValidJavaPackage(className, className.lastIndexOf('/'));
    }

    @Override
    protected Detector getDetector() {
        fail("This is not used in the ApiDatabase test");
        return null;
    }

    private File mCacheDir;

    @SuppressWarnings("StringBufferField")
    private final StringBuilder mLogBuffer = new StringBuilder();

    @SuppressWarnings({
        "ConstantConditions",
        "IOResourceOpenedButNotSafelyClosed",
        "ResultOfMethodCallIgnored"
    })
    @Override
    protected LookupTestClient createClient() {
        mCacheDir = new File(getTempDir(), "lint-test-cache");
        mCacheDir.mkdirs();

        return new LookupTestClient(mCacheDir, mLogBuffer);
    }

    @SuppressWarnings({
        "ConstantConditions",
        "IOResourceOpenedButNotSafelyClosed",
        "ResultOfMethodCallIgnored"
    })
    public void testCorruptedCacheHandling() throws Exception {
        if (ApiLookup.DEBUG_FORCE_REGENERATE_BINARY) {
            System.err.println("Skipping " + getName() + ": not valid while regenerating indices");
            return;
        }

        ApiLookup lookup;

        // Real cache:
        mCacheDir = createClient().getCacheDir(null, true);
        mLogBuffer.setLength(0);
        lookup = ApiLookup.get(createClient());
        assertNotNull(lookup);
        assertEquals(11, getFieldVersion(lookup, "android/R$attr", "actionMenuTextAppearance"));
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();

        // Custom cache dir: should also work
        mCacheDir = new File(getTempDir(), "testcache");
        mCacheDir.mkdirs();
        mLogBuffer.setLength(0);
        lookup = ApiLookup.get(createClient());
        assertNotNull(lookup);
        assertEquals(11, getFieldVersion(lookup, "android/R$attr", "actionMenuTextAppearance"));
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();

        // Now truncate cache file
        IAndroidTarget target = createClient().getLatestSdkTarget(1, true);
        Assert.assertNotNull(target);
        String key = target.getVersion().getApiString();
        int revision = target.getRevision();
        if (revision != 1) {
            key = key + "rev" + revision;
        }
        File cacheFile = new File(mCacheDir, ApiLookup.getCacheFileName("api-versions.xml", key));
        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        RandomAccessFile raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file in half
        raf.setLength(100); // Broken header
        raf.close();
        ApiLookup.get(createClient());
        String message = mLogBuffer.toString();
        // NOTE: This test is incompatible with the DEBUG_FORCE_REGENERATE_BINARY and WRITE_STATS
        // flags in the ApiLookup class, so if the test fails during development and those are
        // set, clear them.
        assertTrue(message.contains("Please delete the file and restart the IDE/lint:"));
        assertTrue(message.contains(mCacheDir.getPath()));
        ApiLookup.dispose();

        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file in half in the data portion
        raf.setLength(raf.length() / 2);
        raf.close();
        lookup = ApiLookup.get(createClient());
        // This data is now truncated: lookup returns the wrong size.
        assertNotNull(lookup);
        getFieldVersion(lookup, "android/R$attr", "actionMenuTextAppearance");
        assertTrue(message.contains("Please delete the file and restart the IDE/lint:"));
        assertTrue(message.contains(mCacheDir.getPath()));
        ApiLookup.dispose();

        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file to 0 bytes
        raf.setLength(0);
        raf.close();
        lookup = ApiLookup.get(createClient());
        assertNotNull(lookup);
        assertEquals(11, getFieldVersion(lookup, "android/R$attr", "actionMenuTextAppearance"));
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();
    }

    private static void assertSameApi(String desc, int expected, int actual) {
        assertSameApi(desc, expected, actual, false);
    }

    private static void assertSameApi(String desc, int expected, int actual, boolean allowMissing) {
        if (allowMissing && actual == -1) {
            return;
        }
        assertEquals(desc, expected, actual);
    }

    public void testDeprecatedIn() {
        assertEquals(9, getClassDeprecatedIn("org/xml/sax/Parser"));
        assertEquals(
                26,
                getFieldDeprecatedIn(
                        "android/accounts/AccountManager", "LOGIN_ACCOUNTS_CHANGED_ACTION"));

        assertEquals(
                20,
                getMethodDeprecatedIn(
                        "android/view/View", "fitSystemWindows", "(Landroid/graphics/Rect;)"));
        assertEquals(
                16, getMethodVersion("android/widget/CalendarView", "getWeekNumberColor", "()"));
        assertEquals(
                23,
                getMethodDeprecatedIn("android/widget/CalendarView", "getWeekNumberColor", "()"));
        assertEquals(
                19, getMethodVersion("android/webkit/WebView", "createPrintDocumentAdapter", "()"));
        // Regression test for 65376457: CreatePrintDocumentAdapter() was deprecated in api 21,
        // not api 3 as lint reports.
        // (The root bug was that for deprecation we also lowered it if superclasses were
        // deprecated (such as AbsoluteLayout, a superclass of WebView) - this is necessary when
        // computing version-requirements but not deprecation versions.)
        assertEquals(
                21,
                getMethodDeprecatedIn(
                        "android/webkit/WebView", "createPrintDocumentAdapter", "()"));
    }

    public void testClassLookupInnerClasses() {
        assertEquals(24, getClassVersion("java/util/Locale$Category"));
        assertEquals(24, getClassVersion("java.util.Locale.Category"));
        assertEquals(1, getClassVersion("android/view/WindowManager$BadTokenException"));
        assertEquals(1, getClassVersion("android.view.WindowManager.BadTokenException"));
    }

    public void testClassDeprecation() {
        assertEquals(5, getClassDeprecatedIn("android/webkit/PluginData"));
        assertEquals(1, getClassVersion("java/io/LineNumberInputStream"));
        assertEquals(1, getClassDeprecatedIn("java/io/LineNumberInputStream"));
    }

    public void testFindEverything() {
        // Load the API versions file and look up every single method/field/class in there
        // (provided since != 1) and also check the deprecated calls.
        File file = mDb.xmlFile;
        Api<ApiClass> info = Api.parseApi(file);
        for (ApiClass cls : info.getClasses().values()) {
            int classSince = ApiParser.getMajorVersion(cls.getSince());
            String className = cls.getName();
            assertSameApi(className, classSince, getClassVersion(className));

            for (String method : cls.getAllMethods(info)) {
                int since = ApiParser.getMajorVersion(cls.getMethodSince(method, info));
                int index = method.indexOf('(');
                String name = method.substring(0, index);
                String desc = method.substring(index);
                assertSameApi(method, since, getMethodVersion(className, name, desc));
            }
            for (String method : cls.getAllFields(info)) {
                int since = ApiParser.getMajorVersion(cls.getFieldSince(method, info));
                assertSameApi(method, since, getFieldVersion(className, method));
            }

            for (Pair<String, Integer> pair : cls.getAllInterfaces(info)) {
                String interfaceName = pair.getFirst();
                int api = ApiParser.getMajorVersion(pair.getSecond());
                assertSameApi(interfaceName, api, getCastVersion(className, interfaceName));
            }
        }

        // Check Deprecated In
        for (ApiClass cls : info.getClasses().values()) {
            int classDeprecatedIn = ApiParser.getMajorVersion(cls.getDeprecatedIn());
            String className = cls.getName();
            if (classDeprecatedIn >= 1) {
                assertSameApi(className, classDeprecatedIn, getClassDeprecatedIn(className));
            } else {
                assertSameApi(className, -1, getClassDeprecatedIn(className));
            }

            for (String method : cls.getAllMethods(info)) {
                int deprecatedIn =
                        ApiParser.getMajorVersion(cls.getMemberDeprecatedIn(method, info));
                if (deprecatedIn == 0) {
                    deprecatedIn = -1;
                }
                int index = method.indexOf('(');
                String name = method.substring(0, index);
                String desc = method.substring(index);
                assertSameApi(
                        method + " in " + className,
                        deprecatedIn,
                        getMethodDeprecatedIn(className, name, desc),
                        STRIP_MEMBERS);
            }
            for (String method : cls.getAllFields(info)) {
                int deprecatedIn =
                        ApiParser.getMajorVersion(cls.getMemberDeprecatedIn(method, info));
                if (deprecatedIn == 0) {
                    deprecatedIn = -1;
                }
                assertSameApi(
                        method,
                        deprecatedIn,
                        getFieldDeprecatedIn(className, method),
                        STRIP_MEMBERS);
            }
        }

        // Check Removed In
        for (ApiClass cls : info.getClasses().values()) {
            int classRemovedIn = ApiParser.getMajorVersion(cls.getRemovedIn());
            String className = cls.getName();
            if (classRemovedIn >= 1) {
                assertSameApi(className, classRemovedIn, getClassRemovedIn(className));
            } else {
                assertSameApi(className, -1, getClassRemovedIn(className));
            }

            for (String method : cls.getAllMethods(info)) {
                int removedIn = ApiParser.getMajorVersion(cls.getMemberRemovedIn(method, info));
                if (removedIn == 0) {
                    removedIn = -1;
                }
                int index = method.indexOf('(');
                String name = method.substring(0, index);
                String desc = method.substring(index);
                assertSameApi(
                        method + " in " + className,
                        removedIn,
                        getMethodRemovedIn(className, name, desc),
                        STRIP_MEMBERS);
            }
            for (String method : cls.getAllFields(info)) {
                int removedIn = ApiParser.getMajorVersion(cls.getMemberRemovedIn(method, info));
                if (removedIn == 0) {
                    removedIn = -1;
                }
                assertSameApi(
                        method, removedIn, getFieldRemovedIn(className, method), STRIP_MEMBERS);
            }
        }
    }

    public void testApi27() {
        // Regression test for 73514594; the following two attributes were added
        // *after* the prebuilt android.jar was checked into the source tree, which
        // is how the api-since data is computed. We're manually correcting for this
        // in the XML-to-binary database computation instead (and I plan to fix
        // metalava to also correct for this in the XML generation code.)
        assertEquals(27, getFieldVersion("android.R$attr", "navigationBarDividerColor"));
        assertEquals(27, getFieldVersion("android.R$attr", "windowLightNavigationBar"));
    }

    public void testLookUpContractSettings() {
        assertEquals(14, getFieldVersion("android/provider/ContactsContract$Settings", "DATA_SET"));
    }

    public void testPreCreateDatabase() {
        int apiLevel = 22;
        String codename = "stable";

        File root = new File(getTempDir(), getName());
        root.mkdirs();
        File outputFile = new File(root, "bin/api_database.bin");

        // Stub SDK
        File sdkHome = new File(root, "sdk");
        File platformDir = new File(sdkHome, "platforms/" + codename);
        File apiFile = new File(platformDir, "data/api-versions.xml");
        File sourceProp = new File(platformDir, "source.properties");

        //noinspection ResultOfMethodCallIgnored
        sourceProp.getParentFile().mkdirs();
        FilesKt.writeText(
                sourceProp,
                "Pkg.Desc=Android SDK Platform "
                        + codename
                        + "\n"
                        + "Pkg.UserSrc=false\n"
                        + "Platform.Version=13\n"
                        + "AndroidVersion.CodeName="
                        + codename
                        + "\n"
                        + "Pkg.Revision=2\n"
                        + "AndroidVersion.ApiLevel="
                        + apiLevel
                        + "\n"
                        + "AndroidVersion.ExtensionLevel=3\n"
                        + "AndroidVersion.IsBaseSdk=true\n"
                        + "Layoutlib.Api=15\n"
                        + "Layoutlib.Revision=1\n"
                        + "Platform.MinToolsRev=22",
                Charsets.UTF_8);

        //noinspection ResultOfMethodCallIgnored
        apiFile.getParentFile().mkdirs();
        FilesKt.writeText(
                apiFile,
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<api version=\"3\">\n"
                        + "        <class name=\"java/lang/Object\" since=\"1\">\n"
                        + "                <method name=\"&lt;init>()V\"/>\n"
                        + "                <method name=\"clone()Ljava/lang/Object;\"/>\n"
                        + "                <method name=\"equals(Ljava/lang/Object;)Z\"/>\n"
                        + "                <method name=\"finalize()V\"/>\n"
                        + "        </class>\n"
                        + "        <class name=\"android/Manifest\" since=\"1\">\n"
                        + "                <extends name=\"java/lang/Object\"/>\n"
                        + "                <method name=\"&lt;init>()V\"/>\n"
                        + "        </class>\n"
                        // Not real API; here so we can make sure we're really using this database
                        + "        <class name=\"android/MyTest\" since=\"14\">\n"
                        + "        </class>\n"
                        + "</api>\n",
                Charsets.UTF_8);

        MainTest.checkDriver(
                "",
                "Created API database file ROOT/bin/api_database.bin",
                ERRNO_SUCCESS,
                new String[] {"--XgenerateApiLookup", apiFile.getPath(), outputFile.getPath()},
                s -> {
                    s = s.replace(root.getPath(), "ROOT");
                    try {
                        s = s.replace(root.getCanonicalPath(), "ROOT");
                    } catch (IOException ignore) {
                    }
                    return LintTestUtils.dos2unix(s);
                },
                null);
        com.android.tools.lint.checks.infrastructure.TestLintClient client =
                new com.android.tools.lint.checks.infrastructure.TestLintClient() {
                    @Override
                    public File getSdkHome() {
                        return sdkHome;
                    }
                };
        List<IAndroidTarget> targets = client.getPlatformLookup().getTargets(false);
        assertEquals(1, targets.size());
        IAndroidTarget target = targets.get(0);
        assertEquals(codename, target.getVersion().getCodename());

        // Change API contents to not contain my custom class (android.MyTest)
        // to make sure we're really using the binary we point to, not a newly
        // recreated version of the database:
        FilesKt.writeText(
                apiFile,
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<api version=\"3\">\n"
                        + "        <class name=\"java/lang/Object\" since=\"1\">\n"
                        + "                <method name=\"&lt;init>()V\"/>\n"
                        + "                <method name=\"clone()Ljava/lang/Object;\"/>\n"
                        + "                <method name=\"equals(Ljava/lang/Object;)Z\"/>\n"
                        + "                <method name=\"finalize()V\"/>\n"
                        + "        </class>\n"
                        + "</api>\n",
                Charsets.UTF_8);

        // Make sure the output isn't older than the input (the API lookup code looks for that)
        //noinspection ResultOfMethodCallIgnored
        outputFile.setLastModified(apiFile.lastModified());
        try {
            ApiLookup.overrideDbBinaryPath = outputFile.getPath();
            ApiLookup lookup = ApiLookup.get(client, target);
            assertNotNull(lookup);
            assertEquals(14, lookup.getClassVersions("android.MyTest").min());
        } finally {
            ApiLookup.overrideDbBinaryPath = null;
        }
    }

    public void testFutureApiDatabaseFormat() throws IOException {
        int apiLevel = 100;
        String codename = "future";

        File root = new File(getTempDir(), getName());
        root.mkdirs();
        File outputFile = new File(root, "bin/api_database.bin");

        // Stub SDK
        File sdkHome = new File(root, "sdk2");
        File platformDir = new File(sdkHome, "platforms/" + codename);
        File apiFile = new File(platformDir, "data/api-versions.xml");
        File sourceProp = new File(platformDir, "source.properties");

        //noinspection ResultOfMethodCallIgnored
        sourceProp.getParentFile().mkdirs();
        FilesKt.writeText(
                sourceProp,
                "Pkg.Desc=Android SDK Platform "
                        + codename
                        + "\n"
                        + "Pkg.UserSrc=false\n"
                        + "Platform.Version=13\n"
                        + "AndroidVersion.CodeName="
                        + codename
                        + "\n"
                        + "Pkg.Revision=2\n"
                        + "AndroidVersion.ApiLevel="
                        + apiLevel
                        + "\n"
                        + "AndroidVersion.ExtensionLevel=3\n"
                        + "AndroidVersion.IsBaseSdk=true\n"
                        + "Layoutlib.Api=15\n"
                        + "Layoutlib.Revision=1\n"
                        + "Platform.MinToolsRev=22",
                Charsets.UTF_8);

        //noinspection ResultOfMethodCallIgnored
        apiFile.getParentFile().mkdirs();
        FilesKt.writeText(
                apiFile,
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<api version=\"100\">\n"
                        // Not real API; here so we can make sure we're really using this database
                        + "        <class name=\"android/MyTest\" since=\"14\">\n"
                        + "        </class>\n"
                        + "</api>\n",
                Charsets.UTF_8);

        StringBuilder logger = new StringBuilder();
        String canonicalRoot = root.getCanonicalPath();
        com.android.tools.lint.checks.infrastructure.TestLintClient client =
                new com.android.tools.lint.checks.infrastructure.TestLintClient() {
                    @Override
                    public File getSdkHome() {
                        return sdkHome;
                    }

                    @Override
                    public void log(Throwable exception, String format, @NotNull Object... args) {
                        logger.append(
                                String.format(format, args)
                                        .replace(root.getPath(), "ROOT")
                                        .replace(canonicalRoot, "ROOT"));
                    }
                };

        List<IAndroidTarget> targets = client.getPlatformLookup().getTargets(false);
        assertEquals(1, targets.size());
        IAndroidTarget target = targets.get(0);
        assertEquals(codename, target.getVersion().getCodename());

        // Make sure the output isn't older than the input (the API lookup code looks for that)
        //noinspection ResultOfMethodCallIgnored
        outputFile.setLastModified(apiFile.lastModified());
        try {
            ApiLookup.get(client, target);
            fail("Lookup for future version should have thrown an unsupported version exception");
        } catch (ApiLookup.UnsupportedVersionException e) {
            assertEquals(
                    "Android API 100, future preview (Preview) requires a newer version of Lint"
                            + " Unit Tests than unittest",
                    e.getDisplayMessage(client));
            // Make sure second attempt doesn't throw an exception
            assertNull(ApiLookup.get(client, target));
        }
    }

    public void testFrom() throws IOException {
        getTempDir();
        // Note: We're *not* using mDb as lookup here (the real API database); we're using a
        // customized database which contains a handful of APIs using the new API vector
        // (sdks=) format to test it before it lands in an official SDK.
        ApiLookup lookup = ApiLookupTest.createMultiSdkLookup(true, false);
        assertEquals("All API levels", lookup.getClassVersions("android.Manifest").toString());
        assertEquals(
                "API level ≥ 11",
                lookup.getClassVersions("android.animation.AnimatorSet").toString());
        assertEquals(
                "API level ≥ 34 or SDK 1000000: version ≥ 4 or SDK 33: version ≥ 4",
                lookup.getClassVersions("android.adservices.adid.AdIdManager").toString());
        assertEquals(
                "API level ≥ 33 or SDK 1000000: version ≥ 3 or SDK 33: version ≥ 3",
                lookup.getClassVersions("android.adservices.AdServicesVersion").toString());
        assertEquals(
                "API level ≥ 33 or SDK 30: version ≥ 2 or SDK 31: version ≥ 2 or SDK 33: version ≥"
                    + " 2",
                lookup.getFieldVersions(
                                "android.provider.MediaStore$PickerMediaColumns", "MIME_TYPE")
                        .toString());
        assertEquals(
                "API level ≥ 34 or SDK 1000000: version ≥ 4 or SDK 33: version ≥ 4",
                lookup.getMethodVersions("android.adservices.adid.AdId", "getAdId", "()")
                        .toString());

        assertEquals("Ad Services Extensions", lookup.getSdkName(1000000));
        assertEquals("Ad Services Extensions", lookup.getSdkName(1000000, false));
        assertEquals("AD_SERVICES-ext", lookup.getSdkName(1000000, true));
        assertEquals("AD_SERVICES", lookup.getSdkExtensionField(1000000, false));
        assertEquals(
                "android.os.ext.SdkExtensions.AD_SERVICES",
                lookup.getSdkExtensionField(1000000, true));
    }

    public void testComputeAllInterfaces() throws IOException {
        getTempDir();
        ApiLookup lookup = ApiLookupTest.createMultiSdkLookup(true, false);
        /*
          A implements B since 10
          A implements C since 3
          B implements D since 1
          C implements B since 5
          Computing interfaces from A:
          B:10, D:10, C:3, ... when we get to B from C we need to correct it from B:10 to B:5 and from D:10 to D:5
        */
        assertEquals(5, lookup.getValidCastVersions("A", "D").min());
        assertEquals(3, lookup.getValidCastVersions("A", "C").min());
        assertEquals(5, lookup.getValidCastVersions("A", "B").min());
    }

    @FunctionalInterface
    public interface CreateLintTask {
        TestLintTask create();
    }

    /**
     * Runs a Lint API check with a custom database using API vectors; see {@link
     * #createMultiSdkLookup}. Once we have API vectors in the platform, switch tests over to using
     * the *real* database instead.
     */
    public static TestLintResult runApiCheckWithCustomLookup(@NonNull CreateLintTask createTask) {
        return runApiCheckWithCustomLookup(false, createTask);
    }

    public static TestLintResult runApiCheckWithCustomLookup(
            @Language("XML") @NonNull String apiXml, @NonNull CreateLintTask createTask) {
        return runApiCheckWithCustomLookup(apiXml, false, createTask);
    }

    public static TestLintResult runApiCheckWithCustomLookup(
            boolean force2ByteFormat, @NonNull CreateLintTask createTask) {
        return runApiCheckWithCustomLookup(null, force2ByteFormat, createTask);
    }

    public static TestLintResult runApiCheckWithCustomLookup(
            @Language("XML") @NonNull String apiXml,
            boolean force2ByteFormat,
            @NonNull CreateLintTask createTask) {
        // this is here to prevent the SoftReference in the ApiLookup's
        // instance table from getting gc'ed
        @SuppressWarnings("WriteOnlyObject")
        AtomicReference<ApiLookup> lookup = new AtomicReference<>();
        try {
            TestLintTask lint = createTask.create();
            return lint.clientFactory(
                            () -> {
                                // This method has a side effect (because we pass in
                                // disposeAfter=false) will leave a custom ApiLookup
                                // around for when lint.run() loads the ApiDetector
                                // and performs the check.
                                try {
                                    lookup.set(
                                            ApiLookupTest.createMultiSdkLookup(
                                                    false, force2ByteFormat, apiXml));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                return new com.android.tools.lint.checks.infrastructure
                                        .TestLintClient() {
                                    @Nullable
                                    @Override
                                    public File getSdkHome() {
                                        return TestUtils.getSdk().toFile();
                                    }
                                };
                            })
                    .issues(
                            ApiDetector.UNSUPPORTED,
                            ApiDetector.INLINED,
                            ApiDetector.OBSOLETE_SDK,
                            ApiDetector.UNUSED)
                    .testModes(TestMode.DEFAULT)
                    .run();
        } finally {
            ApiLookup.dispose();
        }
    }

    @Nullable
    public static ApiLookup createMultiSdkLookup(boolean disposeAfter, boolean force2ByteFormat)
            throws IOException {
        return createMultiSdkLookup(disposeAfter, force2ByteFormat, null);
    }

    @Nullable
    public static ApiLookup createMultiSdkLookup(
            boolean disposeAfter,
            boolean force2ByteFormat,
            @Language("XML") @Nullable String apiVersionsOverride)
            throws IOException {
        TemporaryFolder temporaryFolder = new TemporaryFolder();
        temporaryFolder.create();
        LookupTestClient client =
                new ApiLookupTest()
                .new LookupTestClient(temporaryFolder.newFolder(), new StringBuilder());
        @Language("XML")
        String defaultApiXml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<api version=\"3\">\n"
                    + "        <sdk id=\"30\" shortname=\"R-ext\" name=\"R Extensions\""
                    + " reference=\"android/os/Build$VERSION_CODES$R\"/>\n"
                    + "        <sdk id=\"31\" shortname=\"S-ext\" name=\"S Extensions\""
                    + " reference=\"android/os/Build$VERSION_CODES$S\"/>\n"
                    + "        <sdk id=\"33\" shortname=\"T-ext\" name=\"T Extensions\""
                    + " reference=\"android/os/Build$VERSION_CODES$TIRAMISU\"/>\n"
                    + "        <sdk id=\"34\" shortname=\"U-ext\" name=\"U Extensions\""
                    + " reference=\"android/os/Build$VERSION_CODES$UPSIDE_DOWN_CAKE\"/>\n"
                    + "        <sdk id=\"35\" shortname=\"V-ext\" name=\"V Extensions\""
                    + " reference=\"android/os/Build$VERSION_CODES$VANILLA_ICE_CREAM\"/>\n"
                    + "        <sdk id=\"1000000\" shortname=\"AD_SERVICES-ext\" name=\"Ad Services"
                    + " Extensions\" reference=\"android/os/ext/SdkExtensions$AD_SERVICES\"/>\n"
                    + "        <class name=\"java/lang/Object\" since=\"1\">\n"
                    + "                <method name=\"&lt;init>()V\"/>\n"
                    + "                <method name=\"clone()Ljava/lang/Object;\"/>\n"
                    + "                <method name=\"equals(Ljava/lang/Object;)Z\"/>\n"
                    + "                <method name=\"finalize()V\"/>\n"
                    + "                <method name=\"getClass()Ljava/lang/Class;\"/>\n"
                    + "                <method name=\"hashCode()I\"/>\n"
                    + "                <method name=\"notify()V\"/>\n"
                    + "                <method name=\"notifyAll()V\"/>\n"
                    + "                <method name=\"toString()Ljava/lang/String;\"/>\n"
                    + "                <method name=\"wait()V\"/>\n"
                    + "                <method name=\"wait(J)V\"/>\n"
                    + "                <method name=\"wait(JI)V\"/>\n"
                    + "        </class>\n"
                    + "        <class name=\"android/Manifest\" since=\"1\">\n"
                    + "                <extends name=\"java/lang/Object\"/>\n"
                    + "                <method name=\"&lt;init>()V\"/>\n"
                    + "        </class>\n"
                    + "        <class name=\"android/animation/AnimatorSet\" since=\"11\"/>\n"
                    + "        <class name=\"android/adservices/AdServicesState\""
                    + " module=\"framework-adservices\" since=\"34\""
                    + " sdks=\"0:34,1000000:4,33:4\">\n"
                    + "                <extends name=\"java/lang/Object\"/>\n"
                    + "                <method name=\"isAdServicesStateEnabled()Z\"/>\n"
                    + "        </class>\n"
                    + "        <class name=\"android/adservices/AdServicesVersion\""
                    + " module=\"framework-adservices\" since=\"33\""
                    + " sdks=\"0:33,1000000:3,33:3\">\n"
                    + "                <extends name=\"java/lang/Object\"/>\n"
                    + "                <field name=\"API_VERSION\"/>\n"
                    + "        </class>\n"
                    + "        <class name=\"android/adservices/adid/AdId\""
                    + " module=\"framework-adservices\" since=\"34\""
                    + " sdks=\"0:34,1000000:4,33:4\">\n"
                    + "                <extends name=\"java/lang/Object\"/>\n"
                    + "                <method name=\"&lt;init>(Ljava/lang/String;Z)V\"/>\n"
                    + "                <method name=\"getAdId()Ljava/lang/String;\"/>\n"
                    + "                <method name=\"isLimitAdTrackingEnabled()Z\"/>\n"
                    + "                <field name=\"ZERO_OUT\"/>\n"
                    + "        </class>\n"
                    + "        <class name=\"android/adservices/adid/AdIdManager\""
                    + " module=\"framework-adservices\" since=\"34\""
                    + " sdks=\"0:34,1000000:4,33:4\">\n"
                    + "                <extends name=\"java/lang/Object\"/>\n"
                    + "                <method"
                    + " name=\"getAdId(Ljava/util/concurrent/Executor;Landroid/os/OutcomeReceiver;)V\"/>\n"
                    + "                <method name=\"hasOutcome()Z\""
                    + " sdks=\"1000000:2147483647,33:2147483647\"/>\n" // Simulate b/260515648
                        + "        </class>\n"
                        + "        <class name=\"android/provider/MediaStore\" since=\"1\">\n"
                        + "                <extends name=\"java/lang/Object\"/>\n"
                        + "                <method name=\"&lt;init>()V\"/>\n"
                        + "                <method"
                        + " name=\"canManageMedia(Landroid/content/Context;)Z\" since=\"31\"/>\n"
                        + "                <method"
                        + " name=\"createDeleteRequest(Landroid/content/ContentResolver;Ljava/util/Collection;)Landroid/app/PendingIntent;\""
                        + " since=\"30\"/>\n"
                        + "                <method"
                        + " name=\"createFavoriteRequest(Landroid/content/ContentResolver;Ljava/util/Collection;Z)Landroid/app/PendingIntent;\""
                        + " since=\"30\"/>\n"
                        + "                <method"
                        + " name=\"createTrashRequest(Landroid/content/ContentResolver;Ljava/util/Collection;Z)Landroid/app/PendingIntent;\""
                        + " since=\"30\"/>\n"
                        + "                <method"
                        + " name=\"createWriteRequest(Landroid/content/ContentResolver;Ljava/util/Collection;)Landroid/app/PendingIntent;\""
                        + " since=\"30\"/>\n"
                        + "                <method"
                        + " name=\"getDocumentUri(Landroid/content/Context;Landroid/net/Uri;)Landroid/net/Uri;\""
                        + " since=\"26\"/>\n"
                        + "                <method"
                        + " name=\"getExternalVolumeNames(Landroid/content/Context;)Ljava/util/Set;\""
                        + " since=\"29\"/>\n"
                        + "                <method"
                        + " name=\"getGeneration(Landroid/content/Context;Ljava/lang/String;)J\""
                        + " since=\"30\"/>\n"
                        + "                <method"
                        + " name=\"getMediaScannerUri()Landroid/net/Uri;\"/>\n"
                        + "                <method"
                        + " name=\"getMediaUri(Landroid/content/Context;Landroid/net/Uri;)Landroid/net/Uri;\""
                        + " since=\"29\"/>\n"
                        + "                <method"
                        + " name=\"getOriginalMediaFormatFileDescriptor(Landroid/content/Context;Landroid/os/ParcelFileDescriptor;)Landroid/os/ParcelFileDescriptor;\""
                        + " since=\"31\"/>\n"
                        + "                <method name=\"getPickImagesMaxLimit()I\" since=\"33\""
                        + " sdks=\"0:33,30:2,31:2,33:2\"/>\n"
                        + "                <method"
                        + " name=\"getRecentExternalVolumeNames(Landroid/content/Context;)Ljava/util/Set;\""
                        + " since=\"30\"/>\n"
                        + "        </class>\n"
                        // This is supposed to be 31, but here we're testing codename handling
                        // (10_000)
                        + "        <class name=\"android/app/GameManager\" since=\"10000\">\n"
                        + "                <extends name=\"java/lang/Object\"/>\n"
                        + "                <method name=\"getGameMode()I\"/>\n"
                        // Made up extra numbers (for testing purposes in
                        // ApiDetectorTest#testSdkExtensionOrder3 where
                        // we need a particular order)
                        + "                <field name=\"GAME_MODE_BATTERY\" since=\"34\""
                        + " sdks=\"1000000:4,0:34,33:4\"/>\n"
                        + "        </class>\n"
                        // Like GameManager, but using compat-format with the 10000 payload in sdks=
                        // and a low version for old-lint compat
                        + "        <class name=\"android/app/GameState\" since=\"34\""
                        + " sdks=\"0:10000\">\n"
                        + "                <extends name=\"java/lang/Object\"/>\n"
                        + "                <method name=\"getLabel()I\"/>\n"
                        + "        </class>\n"
                        + "        <class name=\"android/provider/MediaStore$PickerMediaColumns\""
                        + " module=\"framework-mediaprovider\" since=\"33\""
                        + " sdks=\"0:33,30:2,31:2,33:2\">\n"
                        + "                <extends name=\"java/lang/Object\"/>\n"
                        + "                <field name=\"DATA\"/>\n"
                        + "                <field name=\"DATE_TAKEN\"/>\n"
                        + "                <field name=\"DISPLAY_NAME\"/>\n"
                        + "                <field name=\"DURATION_MILLIS\"/>\n"
                        + "                <field name=\"MIME_TYPE\"/>\n"
                        + "                <field name=\"SIZE\"/>\n"
                        + "        </class>\n"
                        // Special test case for ApiDetectorTest#testSyntheticConstructorParameter
                        // with a synthetic
                        // constructor parameter to make sure we include those when computing
                        // signatures
                        + "        <class name=\"android/test/api/Outer\" since=\"28\">\n"
                        + "                <extends name=\"java/lang/Object\"/>\n"
                        + "                <method name=\"&lt;init>()V\"/>\n"
                        + "        </class>\n"
                        + "        <class name=\"android/test/api/Outer$Inner\" since=\"29\">\n"
                        + "                <extends name=\"android/test/api/Outer\"/>\n"
                        + "                <method name=\"&lt;init>(Landroid/test/api/Outer;F)V\""
                        + " since=\"32\"/>\n"
                        + "        </class>\n"
                        // Test scenario used by #testComputeAllInterfaces() to make sure we
                        // properly handle "diamond shaped" interface hierarchies.
                        // A implements B and C, C implements B, B implements D
                        + "        <class name=\"A\" since=\"1\">\n"
                        + "                <extends name=\"java/lang/Object\"/>\n"
                        + "                <implements name=\"B\" since=\"10\"/>"
                        + "                <implements name=\"C\" since=\"3\"/>"
                        + "                <method name=\"&lt;init>()V\"/>\n"
                        + "        </class>\n"
                        + "        <class name=\"B\" since=\"1\">\n"
                        + "                <extends name=\"java/lang/Object\"/>\n"
                        + "                <implements name=\"D\" since=\"1\"/>"
                        + "                <method name=\"&lt;init>()V\"/>\n"
                        + "        </class>\n"
                        + "        <class name=\"C\" since=\"1\">\n"
                        + "                <extends name=\"java/lang/Object\"/>\n"
                        + "                <implements name=\"B\" since=\"5\"/>"
                        + "                <method name=\"&lt;init>()V\"/>\n"
                        + "        </class>\n"
                        + "        <class name=\"D\" since=\"1\">\n"
                        + "                <extends name=\"java/lang/Object\"/>\n"
                        + "                <method name=\"&lt;init>()V\"/>\n"
                        + "        </class>\n"
                        + "</api>\n";
        File xml = File.createTempFile("api-versions", "xml");
        FilesKt.writeText(
                xml,
                apiVersionsOverride != null ? apiVersionsOverride : defaultApiXml,
                Charsets.UTF_8);
        ApiLookup.dispose();

        // Anticipate the same AndroidVersion (used for key lookup) that the lint detector test will
        // use:
        PlatformLookup platformLookup = client.getPlatformLookup();
        IAndroidTarget target = platformLookup.getLatestSdkTarget(1, false, false);

        String key = "LINT_API_DATABASE";
        String old = System.getProperty(key);
        System.setProperty(key, xml.getPath());
        ApiLookup lookup;
        try {
            Api.TEST_TWO_BYTE_APIS = force2ByteFormat;
            lookup = ApiLookup.get(client, target);
            if (disposeAfter) {
                ApiLookup.dispose();
            }
        } finally {
            Api.TEST_TWO_BYTE_APIS = false;
            if (old == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, old);
            }
        }
        temporaryFolder.delete();
        return lookup;
    }

    public void testMinorVersions() throws IOException {
        getTempDir();
        // Note: We're *not* using mDb as lookup here (the real API database); we're using a
        // customized database which contains a handful of APIs using the new API vector
        // (sdks=) format to test it before it lands in an official SDK.
        ApiLookup lookup = ApiLookupTest.createMultiSdkLookupWithMinorVersions(true);
        // <class name="android/os/PlatformOnly" since="36.4">
        assertEquals(
                "API level ≥ 36.4", lookup.getClassVersions("android.os.PlatformOnly").toString());
        // <class name="android/test/MyClass" since="30.0">
        assertEquals("API level ≥ 30", lookup.getClassVersions("android.test.MyClass").toString());
        // <method name="myMethod()String;" since="31.2" deprecated="34.2" removed="34.6"/>
        assertEquals(
                "API level ≥ 31.2",
                lookup.getMethodVersions("android.test.MyClass", "myMethod", "()").toString());
        assertEquals(
                "API level ≥ 34.2",
                lookup.getMethodDeprecatedInVersions("android.test.MyClass", "myMethod", "()")
                        .toString());
        assertEquals(
                "API level ≥ 34.6",
                lookup.getMethodRemovedInVersions("android.test.MyClass", "myMethod", "()")
                        .toString());
        // <method name="getAllExtensionVersions()Ljava/util/Map;" since="31.1"
        // sdks="30:2,31:2,33:4,35:12,36:16,0:31.1"/>
        assertEquals(
                "SDK 30: version ≥ 2 or SDK 31: version ≥ 2.1 or SDK 33: version ≥ 4 or SDK 35:"
                    + " version ≥ 12 or SDK 36: version ≥ 16 or API level ≥ 31.1",
                lookup.getMethodVersions("android.test.MyClass", "getAllExtensionVersions", "()")
                        .toString());
        // <field name="AD_SERVICES" since="34.2" sdks="30:4,31:4,33:4,34:4,35:12,36:16,0:34.2"/>
        assertEquals(
                "SDK 30: version ≥ 4 or SDK 31: version ≥ 4 or SDK 33: version ≥ 4 or SDK 34:"
                    + " version ≥ 4 or SDK 35: version ≥ 12 or SDK 36: version ≥ 16 or API level ≥"
                    + " 34.2",
                lookup.getFieldVersions("android.test.MyClass", "AD_SERVICES").toString());

        // Special test for b/20699600: Verify workaround in ApiClass to patch in the correct
        // API requirements for SdkExtensions on API level 33 only.
        assertEquals(
                "API level ≥ 30",
                lookup.getClassVersions("android.os.ext.SdkExtensions").toString());
    }

    @Nullable
    public static ApiLookup createMultiSdkLookupWithMinorVersions(boolean disposeAfter)
            throws IOException {
        TemporaryFolder temporaryFolder = new TemporaryFolder();
        temporaryFolder.create();
        LookupTestClient client =
                new ApiLookupTest()
                .new LookupTestClient(temporaryFolder.newFolder(), new StringBuilder());
        @Language("XML")
        String apiVersionsOverride =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<api version=\"4\">\n"
                    + "        <sdk id=\"36\" shortname=\"B-ext\" name=\"VANILLA_ICE_CREAM Extensions\"\n"
                    + "             reference=\"android/os/Build$VERSION_CODES$VANILLA_ICE_CREAM\"/>\n"
                    + "\n"
                    + "        <class name=\"java/lang/Object\" since=\"1\">\n"
                    + "                <method name=\"&lt;init>()V\"/>\n"
                    + "        </class>\n"
                    + "        <class name=\"android/test/MyClass\" since=\"30.0\">\n"
                    + "                <extends name=\"java/lang/Object\"/>\n"
                    + "                <method name=\"myMethod()String;\" since=\"31.2\""
                    + " deprecated=\"34.2\" removed=\"34.6\"/>\n"
                    + "                <method name=\"getAllExtensionVersions()Ljava/util/Map;\""
                    + " since=\"31.1\"\n"
                    + " sdks=\"30:2,31:2.1,33:4,35:12,36:16,0:31.1\"/>\n"
                    + "                <field name=\"AD_SERVICES\" since=\"34.2\""
                    + " sdks=\"30:4,31:4,33:4,34:4,35:12,36:16,0:34.2\"/>\n"
                    + "        </class>\n"
                    + "        <class name=\"android/os/PlatformOnly\" since=\"36.4\">\n"
                    + "                <extends name=\"java/lang/Object\"/>\n"
                    + "                <method name=\"foo(I)V\" />\n"
                    + "        </class>\n"
                    + "\n"
                    + "        <!-- Here to verify ApiClass workaround for b/20699600: -->\n"
                    + "        <class name=\"android/os/ext/SdkExtensions\" since=\"33\">\n"
                    + "                <extends name=\"java/lang/Object\"/>\n"
                    + "                <method"
                    + " name=\"getAllExtensionVersions()Ljava/util/Map;\"/>\n"
                    + "                <method name=\"getExtensionVersion(I)I\"/>\n"
                    + "        </class>\n"
                    + "</api>\n";
        File xml = File.createTempFile("api-versions", "xml");
        FilesKt.writeText(xml, apiVersionsOverride, Charsets.UTF_8);
        ApiLookup.dispose();

        // Anticipate the same AndroidVersion (used for key lookup) that the lint detector test will
        // use:
        PlatformLookup platformLookup = client.getPlatformLookup();
        IAndroidTarget target = platformLookup.getLatestSdkTarget(1, false, false);

        String key = "LINT_API_DATABASE";
        String old = System.getProperty(key);
        System.setProperty(key, xml.getPath());
        ApiLookup lookup;
        try {
            lookup = ApiLookup.get(client, target);
            if (disposeAfter) {
                ApiLookup.dispose();
            }
        } finally {
            if (old == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, old);
            }
        }
        temporaryFolder.delete();
        return lookup;
    }

    private final class LookupTestClient extends ToolsBaseTestLintClient {
        private final File mCacheDir;

        @SuppressWarnings("StringBufferField")
        private final StringBuilder mLogBuffer;

        public LookupTestClient(File cacheDir, StringBuilder logBuffer) {
            this.mCacheDir = cacheDir;
            this.mLogBuffer = logBuffer;
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Nullable
        @Override
        public File getCacheDir(@Nullable String name, boolean create) {
            assertNotNull(mCacheDir);
            if (create && !mCacheDir.exists()) {
                mCacheDir.mkdirs();
            }
            return mCacheDir;
        }

        @Override
        public void log(
                @NonNull Severity severity,
                @Nullable Throwable exception,
                @Nullable String format,
                @Nullable Object... args) {
            if (format != null) {
                mLogBuffer.append(String.format(format, args));
                mLogBuffer.append('\n');
            }
            if (exception != null) {
                StringWriter writer = new StringWriter();
                exception.printStackTrace(new PrintWriter(writer));
                mLogBuffer.append(writer);
                mLogBuffer.append('\n');
            }
        }

        @Override
        public void log(Throwable exception, String format, Object... args) {
            log(Severity.WARNING, exception, format, args);
        }
    }
}
