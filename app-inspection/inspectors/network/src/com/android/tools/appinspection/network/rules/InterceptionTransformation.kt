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

import com.android.tools.appinspection.network.utils.Logger
import java.io.IOException
import java.io.InputStream
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.Transformation.BodyModified
import studio.network.inspection.NetworkInspectorProtocol.Transformation.BodyReplaced
import studio.network.inspection.NetworkInspectorProtocol.Transformation.HeaderAdded
import studio.network.inspection.NetworkInspectorProtocol.Transformation.HeaderReplaced
import studio.network.inspection.NetworkInspectorProtocol.Transformation.StatusCodeReplaced

interface InterceptionTransformation {

  fun transform(response: NetworkResponse): NetworkResponse
}

/** A transformation class that changes the status code from response headers. */
internal class StatusCodeReplacedTransformation(
  private val statusCodeReplaced: StatusCodeReplaced
) : InterceptionTransformation {

  override fun transform(response: NetworkResponse): NetworkResponse {
    val targetCodeProto = statusCodeReplaced.targetCode
    val replacingCode = statusCodeReplaced.newCode.toIntOrNull()
    if (replacingCode == null) {
      Logger.debug(
        "Ignoring interception rule because of an invalid newCode: ${statusCodeReplaced.newCode}"
      )
      return response
    }
    return transformWithNullHeader(response, targetCodeProto, replacingCode)
      ?: transformWithStatusCodeHeader(response, targetCodeProto, replacingCode)
  }

  private fun transformWithNullHeader(
    response: NetworkResponse,
    targetCodeProto: NetworkInspectorProtocol.MatchingText,
    replacingCode: Int,
  ): NetworkResponse? {
    val statusHeader = response.responseHeaders[null] ?: return null
    val statusLine = statusHeader.getOrNull(0) ?: return null
    if (statusLine.startsWith("HTTP/1.")) {
      val codePos = statusLine.indexOf(' ')
      if (codePos > 0) {
        var phrasePos = statusLine.indexOf(' ', codePos + 1)
        if (phrasePos < 0) {
          phrasePos = statusLine.length
        }
        val code = statusLine.substring(codePos + 1, phrasePos)
        if (targetCodeProto.matches(code)) {
          val prefix = statusLine.substring(0, codePos)
          val suffix = statusLine.substring(phrasePos)
          val newHeaders = response.responseHeaders.toMutableMap()
          newHeaders[null] = listOf("$prefix $replacingCode$suffix")
          if (newHeaders.containsKey(FIELD_RESPONSE_STATUS_CODE)) {
            newHeaders[FIELD_RESPONSE_STATUS_CODE] = listOf(replacingCode.toString())
          }
          return response.copy(
            responseCode = replacingCode,
            responseHeaders = newHeaders,
            interception = response.interception.copy(statusCode = true),
          )
        }
      }
    }
    return null
  }

  private fun transformWithStatusCodeHeader(
    response: NetworkResponse,
    targetCodeProto: NetworkInspectorProtocol.MatchingText,
    replacingCode: Int,
  ): NetworkResponse {
    val statusCodeHeaderValue = response.responseHeaders[FIELD_RESPONSE_STATUS_CODE]
    if (statusCodeHeaderValue?.isNotEmpty() == true) {
      val statusCode = statusCodeHeaderValue[0]
      if (targetCodeProto.matches(statusCode)) {
        val newHeaders = response.responseHeaders.toMutableMap()
        newHeaders[FIELD_RESPONSE_STATUS_CODE] = listOf(replacingCode.toString())
        return response.copy(
          responseCode = replacingCode,
          responseHeaders = newHeaders,
          interception = response.interception.copy(statusCode = true),
        )
      }
    }
    return response
  }
}

/** A transformation class that adds a pair of header and value to the response. */
class HeaderAddedTransformation(private val headerAdded: HeaderAdded) : InterceptionTransformation {

  override fun transform(response: NetworkResponse): NetworkResponse {
    val headers = response.responseHeaders.toMutableMap()
    val values = headers.getOrPut(headerAdded.name) { listOf() }.toMutableList()
    values.add(headerAdded.value)
    headers[headerAdded.name] = values
    return response.copy(
      responseHeaders = headers,
      interception = response.interception.copy(headerAdded = true),
    )
  }
}

/**
 * A transformation class that finds the all target name-value pairs and replaces them with a new
 * pair.
 */
class HeaderReplacedTransformation(private val headerReplaced: HeaderReplaced) :
  InterceptionTransformation {

  override fun transform(response: NetworkResponse): NetworkResponse {
    // Remove all matched header values.
    val defaultKey = if (headerReplaced.hasNewName()) headerReplaced.newName else null
    val defaultValue = if (headerReplaced.hasNewValue()) headerReplaced.newValue else null
    if (defaultKey == null && defaultValue == null) {
      return response
    }
    val newHeaders = mutableMapOf<String?, MutableSet<String>>()
    val headers =
      response.responseHeaders
        .mapValues { (headerKey, headerValues) ->
          if (headerReplaced.targetName.matches(headerKey, true)) {
            headerValues.filter { headerValue ->
              val matched = headerReplaced.targetValue.matches(headerValue)
              if (matched) {
                newHeaders
                  .computeIfAbsent(defaultKey ?: headerKey) { mutableSetOf() }
                  .add(defaultValue ?: headerValue)
              }
              !matched
            }
          } else headerValues
        }
        .filter { it.value.isNotEmpty() }
        .toMutableMap()

    // Add the replaced header and value.
    newHeaders.entries.forEach { (key, value) ->
      val valueSet = mutableSetOf<String>()
      valueSet.addAll(headers.getOrDefault(key, listOf()))
      valueSet.addAll(value)
      headers[key] = valueSet.toList()
    }
    return response.copy(
      responseHeaders = headers,
      interception = response.interception.copy(headerReplaced = newHeaders.isNotEmpty()),
    )
  }
}

/** A transformation class that replaces the response body. */
class BodyReplacedTransformation(private val bodyReplaced: BodyReplaced) :
  InterceptionTransformation {

  private val body: InputStream
    get() = bodyReplaced.body.toByteArray().inputStream()

  private val gzipBody: InputStream
    get() = bodyReplaced.body.toByteArray().gzip().inputStream()

  override fun transform(response: NetworkResponse): NetworkResponse {
    return response.copy(
      responseBody =
        InterceptedResponseBody.SuccessfulResponseBody(
          if (isContentCompressed(response)) gzipBody else body
        ),
      interception = response.interception.copy(bodyReplaced = true),
    )
  }
}

private val TEXT_TYPES = setOf("csv", "html", "json", "xml")

/** A transformation class that replaces the target text segments from response body. */
class BodyModifiedTransformation(private val bodyModified: BodyModified) :
  InterceptionTransformation {

  override fun transform(response: NetworkResponse): NetworkResponse {
    if (!isSupportedTextType(response)) {
      return response
    }

    val isCompressed = isContentCompressed(response)
    try {
      val inputStream = if (isCompressed) GZIPInputStream(response.body) else response.body
      val body = inputStream.bufferedReader().use { it.readText() }
      val newBody = bodyModified.targetText.toRegex().replace(body, bodyModified.newText)
      val isBodyModified = body != newBody
      val newBodyBytes = newBody.toByteArray()
      return response.copy(
        responseBody =
          InterceptedResponseBody.SuccessfulResponseBody(
            (if (isCompressed) newBodyBytes.gzip() else newBodyBytes).inputStream()
          ),
        interception = response.interception.copy(bodyModified = isBodyModified),
      )
    } catch (ignored: IOException) {
      // If we got here, it means we failed to unzip data that was supposedly zipped.
      return response
    } catch (ignored: ZipException) {
      return response
    }
  }

  /** @return true if its type is "text" or its subtype is a known text subtype. */
  private fun isSupportedTextType(response: NetworkResponse): Boolean {
    val contentHeaderValues = response.responseHeaders[FIELD_CONTENT_TYPE] ?: return false
    val mimeType = contentHeaderValues.getOrNull(0) ?: return false
    val typeAndSubType = mimeType.split(Pattern.compile("/"), 2)
    if (typeAndSubType.isEmpty()) {
      return false
    }
    if (typeAndSubType[0].equals("text", ignoreCase = true)) {
      return true
    }
    if (typeAndSubType.size == 2) {
      // Without suffix: json, xml, html, etc.
      // With suffix: vnd.api+json, svg+xml, etc.
      // With charset: json; charset=utf-8 etc.
      // See also: https://en.wikipedia.org/wiki/Media_type#Suffix
      val type = typeAndSubType[1].substringAfter('+').substringBefore(';')
      return TEXT_TYPES.contains(type)
    }
    return false
  }
}
