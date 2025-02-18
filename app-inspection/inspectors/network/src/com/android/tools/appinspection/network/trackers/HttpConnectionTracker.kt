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
package com.android.tools.appinspection.network.trackers

import com.android.tools.appinspection.network.rules.NetworkInterceptionMetrics
import java.io.InputStream
import java.io.OutputStream
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport

/**
 * HTTP stacks can use this interface to report the key states and data associated with individual
 * requests, to be consumed by the network inspector.
 *
 * The methods in this interface are expected to be called in the following order (the calls marked
 * with question mark are optional)
 *
 * trackRequest() ---> trackRequestBody()? ---> trackResponse() ---> trackResponseBody()? --->
 * disconnect()?
 *
 * Each method must be called on the thread that initiates the corresponding operation.
 */
interface HttpConnectionTracker {

  /** Reports an explicit disconnect request */
  fun disconnect()

  /**
   * Reports a fatal HTTP exchange failure
   *
   * @param message error message
   */
  fun error(message: String)

  /**
   * Tracks an optional request body (before the request is sent)
   *
   * @param stream the stream used to write the request body
   * @return an output stream which may wrap the original stream
   */
  fun trackRequestBody(stream: OutputStream): OutputStream

  /**
   * An HTTP request is about to be sent to the wire
   *
   * @param method HTTP method
   * @param headers HTTP request header fields
   */
  fun trackRequest(method: String, headers: Map<String, List<String>>, transport: HttpTransport)

  /**
   * Tracks the receiving of an HTTP response
   *
   * @param headers HTTP response header fields
   */
  fun trackResponseHeaders(responseCode: Int, headers: Map<String?, List<String>>)

  /**
   * Tracks an optional response body after the response is received
   *
   * @param stream the stream used to read the response body
   * @return an input stream which may wrap the original stream
   */
  fun trackResponseBody(stream: InputStream): InputStream

  /**
   * Tracks an optional response interception after the response is received and intercepted.
   *
   * @param interception metadata about how the response is intercepted.
   */
  fun trackResponseInterception(interception: NetworkInterceptionMetrics)
}
