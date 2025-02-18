/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.appinspection.network.rules

import java.net.URL
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method

/** A criteria class that checks if a connection should be intercepted. */
class InterceptionCriteria(private val interceptCriteria: InterceptCriteria) {

  fun appliesTo(connection: NetworkConnection): Boolean {
    if (!interceptCriteria.method.appliesTo(connection.method)) {
      return false
    }
    val url = URL(connection.url)
    if (!interceptCriteria.protocol.appliesTo(url.protocol)) {
      return false
    }
    return wildCardMatches(interceptCriteria.port, url.port.toString()) &&
      wildCardMatches(interceptCriteria.host, url.host) &&
      wildCardMatches(interceptCriteria.path, url.path) &&
      wildCardMatches(interceptCriteria.query, url.query)
  }
}

private fun Method.appliesTo(connectionMethod: String): Boolean {
  val method =
    when (this) {
      Method.METHOD_UNSPECIFIED -> return true
      Method.METHOD_GET -> "GET"
      Method.METHOD_POST -> "POST"
      Method.METHOD_HEAD -> "HEAD"
      Method.METHOD_PUT -> "PUT"
      Method.METHOD_DELETE -> "DELETE"
      Method.METHOD_TRACE -> "TRACE"
      Method.METHOD_CONNECT -> "CONNECT"
      Method.METHOD_PATCH -> "PATCH"
      Method.METHOD_OPTIONS -> "OPTIONS"
      Method.UNRECOGNIZED -> return false
    }

  return method == connectionMethod
}

private fun InterceptCriteria.Protocol.appliesTo(connectionProtocol: String): Boolean {
  val protocol =
    when (this) {
      InterceptCriteria.Protocol.PROTOCOL_UNSPECIFIED -> return true
      InterceptCriteria.Protocol.PROTOCOL_HTTPS -> "https"
      InterceptCriteria.Protocol.PROTOCOL_HTTP -> "http"
      else -> return false
    }

  return protocol == connectionProtocol
}
