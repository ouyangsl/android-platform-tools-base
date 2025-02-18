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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class X509TrustManagerDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return X509TrustManagerDetector()
  }

  fun testTrustsAll() {
    lint()
      .files(
        manifest(
            """

                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <service
                            android:name=".InsecureTLSIntentService" >
                        </service>
                    </application>

                </manifest>

                """
          )
          .indented(),
        java(
            """
                package test.pkg;

                import android.app.IntentService;
                import android.content.Intent;

                import java.security.GeneralSecurityException;
                import java.security.cert.CertificateException;

                import javax.net.ssl.HttpsURLConnection;
                import javax.net.ssl.SSLContext;
                import javax.net.ssl.TrustManager;
                import javax.net.ssl.X509TrustManager;

                public class InsecureTLSIntentService extends IntentService {
                    TrustManager[] trustAllCerts = new TrustManager[]{ new X509TrustManager() {
                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
                        }
                    }};

                    public InsecureTLSIntentService() {
                        super("InsecureTLSIntentService");
                    }

                    @Override
                    protected void onHandleIntent(Intent intent) {
                        try {
                            SSLContext sc = SSLContext.getInstance("TLSv1.2");
                            sc.init(null, trustAllCerts, new java.security.SecureRandom());
                            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                        } catch (GeneralSecurityException e) {
                            System.out.println(e.getStackTrace());
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/InsecureTLSIntentService.java:22: Warning: checkClientTrusted is empty, which could cause insecure network traffic due to trusting arbitrary TLS/SSL certificates presented by peers [TrustAllX509TrustManager]
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                                ~~~~~~~~~~~~~~~~~~
            src/test/pkg/InsecureTLSIntentService.java:26: Warning: checkServerTrusted is empty, which could cause insecure network traffic due to trusting arbitrary TLS/SSL certificates presented by peers [TrustAllX509TrustManager]
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
                                ~~~~~~~~~~~~~~~~~~
            src/test/pkg/InsecureTLSIntentService.java:15: Warning: Implementing a custom X509TrustManager is error-prone and likely to be insecure. It is likely to disable certificate validation altogether, and is non-trivial to implement correctly without calling Android's default implementation. [CustomX509TrustManager]
                TrustManager[] trustAllCerts = new TrustManager[]{ new X509TrustManager() {
                                                                       ~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
      )

    // TODO: Test bytecode check via library jar?
    // "bytecode/InsecureTLSIntentService.java.txt=>src/test/pkg/InsecureTLSIntentService.java",
    // "bytecode/InsecureTLSIntentService.class.data=>bin/classes/test/pkg/InsecureTLSIntentService.class",
    // "bytecode/InsecureTLSIntentService$1.class.data=>bin/classes/test/pkg/InsecureTLSIntentService$1.class"));
  }

  fun testCustom() {
    lint()
      .files(
        manifest(
            """

                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <service
                            android:name=".ExampleTLSIntentService" >
                        </service>
                    </application>

                </manifest>
                """
          )
          .indented(),
        java(
            """
                package test.pkg;

                import android.app.IntentService;
                import android.content.Intent;

                import java.io.BufferedInputStream;
                import java.io.FileInputStream;
                import java.security.GeneralSecurityException;
                import java.security.cert.CertificateException;
                import java.security.cert.CertificateFactory;
                import java.security.cert.X509Certificate;

                import javax.net.ssl.HttpsURLConnection;
                import javax.net.ssl.SSLContext;
                import javax.net.ssl.TrustManager;
                import javax.net.ssl.X509TrustManager;

                public class ExampleTLSIntentService extends IntentService {
                    TrustManager[] trustManagerExample;

                    {
                        trustManagerExample = new TrustManager[]{new X509TrustManager() {
                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                try {
                                    FileInputStream fis = new FileInputStream("testcert.pem");
                                    BufferedInputStream bis = new BufferedInputStream(fis);
                                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                                    X509Certificate cert = (X509Certificate) cf.generateCertificate(bis);
                                    return new X509Certificate[]{cert};
                                } catch (Exception e) {
                                    throw new RuntimeException("Could not load cert");
                                }
                            }

                            @Override
                            public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                                throw new CertificateException("Not trusted");
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                                throw new CertificateException("Not trusted");
                            }
                        }};
                    }

                    public ExampleTLSIntentService() {
                        super("ExampleTLSIntentService");
                    }

                    @Override
                    protected void onHandleIntent(Intent intent) {
                        try {
                            SSLContext sc = SSLContext.getInstance("TLSv1.2");
                            sc.init(null, trustManagerExample, new java.security.SecureRandom());
                            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                        } catch (GeneralSecurityException e) {
                            System.out.println(e.getStackTrace());
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/ExampleTLSIntentService.java:22: Warning: Implementing a custom X509TrustManager is error-prone and likely to be insecure. It is likely to disable certificate validation altogether, and is non-trivial to implement correctly without calling Android's default implementation. [CustomX509TrustManager]
                    trustManagerExample = new TrustManager[]{new X509TrustManager() {
                                                                 ~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
  }

  @Suppress("removal")
  fun testInterfaceMethods() {
    // Regression test for
    // https://issuetracker.google.com/270065082
    lint()
      .files(
        compiled(
          "libs/library.jar",
          java(
              """
            package test.pkg;

            import java.util.List;

            import javax.net.ssl.X509TrustManager;
            import javax.security.cert.CertificateException;
            import javax.security.cert.X509Certificate;

            public interface ExtendedX509TrustManager extends X509TrustManager {
                List<X509Certificate> checkServerTrusted(X509Certificate[] chain, String authType, String host)
                        throws CertificateException;
            }
            """
            )
            .indented(),
          0x2a622729,
          """
            test/pkg/ExtendedX509TrustManager.class:
            H4sIAAAAAAAA/7WRP0sDQRDF38SYM/FfsFewi41rIyKRNBKriMVZCNqsm/Hc
            5NiE3blw1n4rCz+AH0rcS0AFI9hYDLx5vJn9Mfv2/vIK4ATtBBsJNhNsEXbM
            I5txyn7G/toXQXhIuOvcDkZ6pksV2BTeypMy7EXdHB+dnkdhH6zRwt15SOXa
            ZSoVb122xDlYWIXYXA1skC6h1S8NT8VOXEiwTWimNnNaCs+E5398+uxPm3sV
            YTopvOELm0ek3X4p7IY8rJLzG11qpzP2h9U6wr5wEDUdZ+q3IKH9BXd1P2Ij
            hL0FjGNRIeTq50xnGe030s8rNgiEWqyV+L1UJ9SxGvtG7OrRTbAWVQ3NqFtz
            tf4BCHqzYQsCAAA=
            """,
        )
      )
      .issues(X509TrustManagerDetector.TRUSTS_ALL)
      .run()
      .expectClean()
  }
}
