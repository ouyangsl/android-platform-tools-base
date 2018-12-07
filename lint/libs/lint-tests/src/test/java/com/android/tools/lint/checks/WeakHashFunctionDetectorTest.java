/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import lint.WeakHashFunctionDetector;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class WeakHashFunctionDetectorTest extends LintDetectorTest {
	@Test
    public void testMessageDigestCallWithWeakHashFunction() {
		lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import java.security.MessageDigest;\n" +
                        "import java.security.NoSuchAlgorithmException;\n" +
                        "public class TestClass1 {\n" +
                            "public static void main(String[] args){\n" +
                                "String password = \"15\";\n" +
                                "MessageDigest md5Digest = null;\n" +
                                "try {\n" +
                                    "md5Digest = MessageDigest.getInstance(\"MD5\");\n" +
                                "} catch (NoSuchAlgorithmException e) {\n" +
                                    "e.printStackTrace();\n" +
                                "}\n" +
                                "md5Digest.update(password.getBytes());\n" +
                                "byte[] hashValue = md5Digest.digest();\n" +
                                "}\n" +
                        "}"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches("MD5 is considered a weak hash function.");
    }

	@Test
    public void testQualifiedMessageDigestCallWithWeakHashFunction() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import java.security.MessageDigest;\n" +
                        "import java.security.NoSuchAlgorithmException;\n" +
                        "public class TestClass1 {\n" +
                        "public static void main(String[] args){\n" +
                        "String password = \"15\";\n" +
                        "java.security.MessageDigest md5Digest = null;\n" +
                        "try {\n" +
                        "md5Digest = java.security.MessageDigest.getInstance(\"MD5\");\n" +
                        "} catch (NoSuchAlgorithmException e) {\n" +
                        "e.printStackTrace();\n" +
                        "}\n" +
                        "md5Digest.update(password.getBytes());\n" +
                        "byte[] hashValue = md5Digest.digest();\n" +
                        "}\n" +
                        "}"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches("MD5 is considered a weak hash function.");
    }

	@Test
    public void testMessageDigestCallWithStrongHashFunction() {
		lint().files(
            java("" +
                    "package test.pkg;\n" +
                    "import java.security.MessageDigest;\n" +
                    "import java.security.NoSuchAlgorithmException;\n" +
                    "public class TestClass1 {\n" +
                    "public static void main(String[] args){\n" +
                    "String password = \"15\";\n" +
                    "java.security.MessageDigest md5Digest = null;\n" +
                    "try {\n" +
                    "md5Digest = java.security.MessageDigest.getInstance(\"SHA256\");\n" +
                    "} catch (NoSuchAlgorithmException e) {\n" +
                    "e.printStackTrace();\n" +
                    "}\n" +
                    "md5Digest.update(password.getBytes());\n" +
                    "byte[] hashValue = md5Digest.digest();\n" +
                    "}\n" +
                    "}"))
            .run()
            .expectCount(0);
    }

	@Test
    public void testMessageDigestCallWithWeakHashFunctionAsConstant() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import java.security.MessageDigest;\n" +
                        "import java.security.NoSuchAlgorithmException;\n" +
                        "public class TestClass1 {\n" +
                        "private static final String MD5 = \"MD5\";" +
                        "public static void main(String[] args){\n" +
                        "String password = \"15\";\n" +
                        "java.security.MessageDigest md5Digest = null;\n" +
                        "try {\n" +
                        "md5Digest = java.security.MessageDigest.getInstance(MD5);\n" +
                        "} catch (NoSuchAlgorithmException e) {\n" +
                        "e.printStackTrace();\n" +
                        "}\n" +
                        "md5Digest.update(password.getBytes());\n" +
                        "byte[] hashValue = md5Digest.digest();\n" +
                        "}\n" +
                        "}"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches("MD5 is considered a weak hash function.");
        ;
    }

    @Override
    protected Detector getDetector() {
        return new WeakHashFunctionDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(WeakHashFunctionDetector.ISSUE);
    }
}
