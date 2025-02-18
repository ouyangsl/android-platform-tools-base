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

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout

class FakeCall(
  private val client: FakeOkHttp3Client,
  private val request: Request,
  private val response: Response,
) : Call {

  override fun clone(): Call {
    throw NotImplementedError("Not yet implemented")
  }

  override fun request(): Request {
    return request
  }

  override fun execute(): Response {
    return client.triggerInterceptor(request, response)
  }

  override fun enqueue(responseCallback: Callback) {}

  override fun cancel() {}

  override fun isExecuted(): Boolean {
    // does not matter
    return false
  }

  override fun isCanceled(): Boolean {
    // does not matter
    return false
  }

  override fun timeout(): Timeout {
    throw NotImplementedError("Not yet implemented")
  }

  fun executeThenBlowUp(): Response {
    return client.triggerInterceptor(request, response, true)
  }
}
