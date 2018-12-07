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
import java.util.Collections;
import java.util.List;
import lint.InsufficientRSAKeySizeDetector;

public class InsufficientRSAKeySizeDetectorTest extends LintDetectorTest {
    public void testRSAWithInsufficentBits() {
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import java.security.KeyPair;"
                                        + "import java.security.KeyPairGenerator;\n"
                                        + "import java.security.NoSuchAlgorithmException;\n"
                                        + "import java.security.NoSuchProviderException;\n"
                                        + "public class TestClass1 {\n"
                                        + "KeyPair generateKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException {\n"
                                        + "KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(\"RSA\", \"BC\");\n"
                                        + "keyPairGenerator.initialize(1024);\n"
                                        + "return keyPairGenerator.generateKeyPair();\n"
                                        + "}\n"
                                        + "}"))
                .run()
                .expectCount(1, Severity.WARNING)
                .expectMatches(InsufficientRSAKeySizeDetector.MESSAGE);
    }

    public void testRSAWithEnoughBits() {
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import java.security.KeyPair;"
                                        + "import java.security.KeyPairGenerator;\n"
                                        + "import java.security.NoSuchAlgorithmException;\n"
                                        + "import java.security.NoSuchProviderException;\n"
                                        + "public class TestClass1 {\n"
                                        + "KeyPair generateKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException {\n"
                                        + "KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(\"RSA\");\n"
                                        + "keyPairGenerator.initialize(2048);\n"
                                        + "return keyPairGenerator.generateKeyPair();\n"
                                        + "}\n"
                                        + "}"))
                .run()
                .expectCount(0);
    }

    public void testDifferentAlgorithm() {
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import java.security.KeyPair;"
                                        + "import java.security.KeyPairGenerator;\n"
                                        + "import java.security.NoSuchAlgorithmException;\n"
                                        + "import java.security.NoSuchProviderException;\n"
                                        + "public class TestClass1 {\n"
                                        + "KeyPair generateKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException {\n"
                                        + "KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(\"DiffieHellman\");\n"
                                        + "keyPairGenerator.initialize(1024);\n"
                                        + "return keyPairGenerator.generateKeyPair();\n"
                                        + "}\n"
                                        + "}"))
                .run()
                .expectCount(0);
    }

    public void testRSAWithInsufficentBitsWithConstant() {
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import java.security.KeyPair;"
                                        + "import java.security.KeyPairGenerator;\n"
                                        + "import java.security.NoSuchAlgorithmException;\n"
                                        + "import java.security.NoSuchProviderException;\n"
                                        + "public class TestClass1 {\n"
                                        + "private static final String RSA = \"RSA\";\n"
                                        + "private static final int KEY_SIZE = 1024;\n"
                                        + "KeyPair generateKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException {\n"
                                        + "KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA, \"BC\");\n"
                                        + "keyPairGenerator.initialize(KEY_SIZE);\n"
                                        + "return keyPairGenerator.generateKeyPair();\n"
                                        + "}\n"
                                        + "}"))
                .run()
                .expectCount(1, Severity.WARNING)
                .expectMatches(InsufficientRSAKeySizeDetector.MESSAGE);
    }

    @Override
    protected Detector getDetector() {
        return new InsufficientRSAKeySizeDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(InsufficientRSAKeySizeDetector.ISSUE);
    }
}
