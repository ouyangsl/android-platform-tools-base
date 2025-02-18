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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class PrivateKeyDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return PrivateKeyDetector()
  }

  fun testPrivateKey() {
    //noinspection all // Sample code
    lint()
      .files(
        // Not a private key file
        source(
            "res/values/strings.xml",
            """
                """,
          )
          .indented(),
        // Private key file
        source(
            "res/private_key.pem",
            """
                -----BEGIN RSA PRIVATE KEY-----
                Proc-Type: 4,ENCRYPTED
                DEK-Info: DES-EDE3-CBC,77F426A58B274623

                FH1NdgJgrX1OGKM0WfzwWUWmLTmfawdaUPeFNJbz1+WJ5DEt1DmC6o0QkXoxIPC
                Te/+FS80gNruYgYIWu4WXWtCSdvSfGI8LP1JZ7hmMCW055J2mLVKT4o6HqAQnHrb
                hTATVG6CB/GdHTFPG3J65qIyTlG50jyzfwZtliMCCAWi+AaAlo5xzUe0DgedytB2
                sFkLq5EiD6066P/LXPH/Z5WJKiMCFOl0Gjwd3M9ohZufnWJPJT5ap2fm7OSJSfa6
                jPREY+UwhPyKkYOc2cWgojj6HrsSQlXPl176b1+31c19hhhRAtDfJBIU2OrOFVk/
                V88/Dm0I+ROyLme0rYfFg8uHz2aIymWEMds5ZKEFTFbBhaWbVYKIX7+82tftnd+P
                2kT15JAK9V27F0p4SRiWQ5RsDkT3rBWWjtk9Rptkrgec9WKoTaO2fT8bPaWFR/M1
                6X7kjMqhLw1sHmsSeDKx0YCWfS+gWh7RPWGQ2EfH2pxoZkWAR5R3cZCEn3Ia1BeV
                UTFWy+DwjEeSrNkO96E0WH1r8204cJAKK8cWS4HSAPMsQPf5cZjIrrAak/9Wupkq
                fnrB0Ae6GFO2gWHYQfbSWdEq6w5+S6XZyTauVyaJAjjIFWmegfaKWHzNvqCWJ4T
                YPsiptUrKz6WWYyhiUrNJQKcyGWHWrwMNIblWqSBNCa8OIVoaZiRibgO1SIafAGAS
                9MDXXVaY6rqx1yfZYDcWVgKGXTJhBXALCeGMWF43bvAmPq3M13QJA0rlO7lAUUF2
                5INqBUeJxZWYxn6tRr9WMty/UcYnPR3YHgt0RDZycvbcqPsU5tHk9Q==
                -----END RSA PRIVATE KEY-----
                """,
          )
          .indented(),
      )
      .run()
      .expect(
        """
            res/private_key.pem: Error: The res/private_key.pem file seems to be a private key file. Please make sure not to embed this in your APK file. [PackagedPrivateKey]
            1 errors, 0 warnings
            """
      )
  }
}
