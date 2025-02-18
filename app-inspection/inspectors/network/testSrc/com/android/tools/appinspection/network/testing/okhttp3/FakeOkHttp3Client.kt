/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.appinspection.network.testing.okhttp3

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class FakeOkHttp3Client(private val networkInterceptorz: List<Interceptor>) : OkHttpClient() {

  fun newCall(request: Request, fakeResponse: Response): FakeCall {
    return FakeCall(this, request, fakeResponse)
  }

  fun triggerInterceptor(request: Request, response: Response, blowUp: Boolean = false): Response {
    return networkInterceptorz
      .first()
      .intercept(
        object : Interceptor.Chain {
          override fun request(): Request {
            return request
          }

          override fun proceed(request: Request): Response {
            if (blowUp) {
              throw IOException("BLOWING UP")
            }
            return response
          }

          override fun connection(): Connection {
            throw NotImplementedError()
          }

          override fun call(): Call {
            throw NotImplementedError("Not yet implemented")
          }

          override fun connectTimeoutMillis(): Int {
            throw NotImplementedError("Not yet implemented")
          }

          override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
            throw NotImplementedError("Not yet implemented")
          }

          override fun readTimeoutMillis(): Int {
            throw NotImplementedError("Not yet implemented")
          }

          override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
            throw NotImplementedError("Not yet implemented")
          }

          override fun writeTimeoutMillis(): Int {
            throw NotImplementedError("Not yet implemented")
          }

          override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
            throw NotImplementedError("Not yet implemented")
          }
        }
      )
  }
}
