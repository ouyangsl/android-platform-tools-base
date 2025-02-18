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

package com.android.tools.appinspection.network

import android.os.Build
import com.android.tools.appinspection.common.testing.LogPrinterRule
import com.android.tools.appinspection.network.testing.NetworkInspectorRule
import com.android.tools.appinspection.network.testing.createFakeRuleAddedEvent
import com.android.tools.appinspection.network.testing.http.FakeHttpUrlConnection
import com.android.tools.appinspection.network.testing.receiveInterceptCommand
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.junit.rules.CloseGuardRule
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport
import studio.network.inspection.NetworkInspectorProtocol.InterceptCommand

private const val URL_PARAMS = "activity=http"
private val FAKE_URL = URL("https://www.google.com?$URL_PARAMS")
private val EXPECTED_RESPONSE =
  NetworkInspectorProtocol.HttpConnectionEvent.ResponseStarted.newBuilder()
    .setResponseCode(200)
    .addHeaders(
      NetworkInspectorProtocol.HttpConnectionEvent.Header.newBuilder()
        .setKey("null")
        .addValues("HTTP/1.0 200 OK")
    )
    .build()

@RunWith(RobolectricTestRunner::class)
@Config(
  manifest = Config.NONE,
  minSdk = Build.VERSION_CODES.O,
  maxSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)
internal class HttpUrlTest {
  private val inspectorRule = NetworkInspectorRule()

  @get:Rule
  val rule: RuleChain =
    RuleChain.outerRule(CloseGuardRule()).around(inspectorRule).around(LogPrinterRule())

  @Test
  fun httpGet() {
    with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
      val inputStream = inputStream
      inputStream.use { it.readBytes() }
    }
    assertThat(inspectorRule.connection.httpData).hasSize(6)
    val httpRequestStarted =
      inspectorRule.connection.findHttpEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_STARTED
      )!!
    assertThat(httpRequestStarted.httpRequestStarted.url).contains(URL_PARAMS)
    assertThat(httpRequestStarted.httpRequestStarted.method).isEqualTo("GET")
    assertThat(httpRequestStarted.httpRequestStarted.transport).isEqualTo(HttpTransport.JAVA_NET)

    val httpResponseStarted =
      inspectorRule.connection.findHttpEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED
      )
    assertThat(httpResponseStarted!!.httpResponseStarted).isEqualTo(EXPECTED_RESPONSE)

    assertThat(
        inspectorRule.connection
          .findHttpEvent(NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD)!!
          .responsePayload
          .payload
          .toStringUtf8()
      )
      .isEqualTo("Test")

    assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()
  }

  @Test
  fun httpIntercept() {
    // Step1: add a new body rule.
    val ruleAdded = createFakeRuleAddedEvent(FAKE_URL)

    inspectorRule.inspector.receiveInterceptCommand(
      InterceptCommand.newBuilder().apply { interceptRuleAdded = ruleAdded }.build()
    )

    with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
      val inputStream = inputStream
      inputStream.use { it.readBytes() }
    }
    assertThat(
        inspectorRule.connection
          .findHttpEvent(NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD)!!
          .responsePayload
          .payload
          .toStringUtf8()
      )
      .isEqualTo("InterceptedBody1")

    assertThat(
        inspectorRule.connection
          .findHttpEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_INTERCEPTED
          )!!
          .httpResponseIntercepted
          .bodyReplaced
      )
      .isTrue()
    assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()

    // Step2: add another body rule with different content.
    inspectorRule.inspector.receiveInterceptCommand(
      InterceptCommand.newBuilder()
        .apply {
          interceptRuleAdded =
            ruleAdded
              .toBuilder()
              .apply {
                ruleId = 2
                ruleBuilder.transformationBuilderList[0].bodyReplacedBuilder.body =
                  ByteString.copyFrom("InterceptedBody2".toByteArray())
              }
              .build()
        }
        .build()
    )
    with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
      val inputStream = inputStream
      inputStream.use { it.readBytes() }
    }
    assertThat(
        inspectorRule.connection
          .findLastHttpEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
          )!!
          .responsePayload
          .payload
          .toStringUtf8()
      )
      .isEqualTo("InterceptedBody2")

    // Step3: reorder two body rules.
    inspectorRule.inspector.receiveInterceptCommand(
      InterceptCommand.newBuilder()
        .apply { reorderInterceptRulesBuilder.apply { addAllRuleId(listOf(2, 1)) }.build() }
        .build()
    )
    with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
      val inputStream = inputStream
      inputStream.use { it.readBytes() }
    }
    assertThat(
        inspectorRule.connection
          .findLastHttpEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
          )!!
          .responsePayload
          .payload
          .toStringUtf8()
      )
      .isEqualTo("InterceptedBody1")

    // Step4: remove the last body rule.
    inspectorRule.inspector.receiveInterceptCommand(
      InterceptCommand.newBuilder()
        .apply { interceptRuleRemovedBuilder.apply { ruleId = 1 }.build() }
        .build()
    )
    with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
      val inputStream = inputStream
      inputStream.use { it.readBytes() }
    }
    assertThat(
        inspectorRule.connection
          .findLastHttpEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
          )!!
          .responsePayload
          .payload
          .toStringUtf8()
      )
      .isEqualTo("InterceptedBody2")
  }

  @Test
  fun httpPost() {
    with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "POST").triggerHttpExitHook()) {
      doOutput = true
      outputStream.use { it.write("TestRequestBody".toByteArray()) }
      inputStream.use { it.readBytes() }
    }

    assertThat(inspectorRule.connection.httpData).hasSize(8)
    val httpRequestStarted =
      inspectorRule.connection.findHttpEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_STARTED
      )!!
    assertThat(httpRequestStarted.httpRequestStarted.url).contains(URL_PARAMS)
    assertThat(httpRequestStarted.httpRequestStarted.method).isEqualTo("POST")
    assertThat(httpRequestStarted.httpRequestStarted.transport).isEqualTo(HttpTransport.JAVA_NET)

    assertThat(
        inspectorRule.connection.findHttpEvent(
          NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_COMPLETED
        )
      )
      .isNotNull()

    assertThat(
        inspectorRule.connection
          .findHttpEvent(NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.REQUEST_PAYLOAD)!!
          .requestPayload
          .payload
          .toStringUtf8()
      )
      .isEqualTo("TestRequestBody")

    val httpResponseStarted =
      inspectorRule.connection.findHttpEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED
      )!!
    assertThat(httpResponseStarted.httpResponseStarted).isEqualTo(EXPECTED_RESPONSE)

    assertThat(
        inspectorRule.connection
          .findHttpEvent(NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD)!!
          .responsePayload
          .payload
          .toStringUtf8()
      )
      .isEqualTo("Test")

    assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()
  }

  /**
   * HttpURLConnection has many functions to query response values which cause a connection to be
   * made if one wasn't already established. This test helps makes sure our tracking code handles
   * such functions before we call 'connect' or 'getInputStream'.
   */
  @Test
  fun getResponseCodeBeforeConnect() {
    with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
      // Calling getResponseCode() implicitly calls connect()
      responseCode
      inputStream.use { it.readBytes() }
    }
    assertThat(inspectorRule.connection.httpData).hasSize(6)
    val httpRequestStarted =
      inspectorRule.connection.findHttpEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_STARTED
      )!!
    assertThat(httpRequestStarted.httpRequestStarted.method).isEqualTo("GET")

    val httpResponseStarted =
      inspectorRule.connection.findHttpEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED
      )!!
    assertThat(httpResponseStarted.httpResponseStarted).isEqualTo(EXPECTED_RESPONSE)

    assertThat(
        inspectorRule.connection
          .findHttpEvent(NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD)!!
          .responsePayload
          .payload
          .toStringUtf8()
      )
      .isEqualTo("Test")

    assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()
  }

  @Test
  fun `failed connection should not affect getting headerFields`() {
    val url = URL("http://google1231541431.com")
    val connection = (url.openConnection() as HttpURLConnection).triggerHttpExitHook()
    connection.requestMethod = "GET"
    assertThat(connection.headerFields).isEmpty()

    try {
      connection.connect()
    } catch (e: Exception) {
      // ignore
    }

    assertThat(connection.headerFields).isEmpty()
  }

  private fun HttpURLConnection.triggerHttpExitHook(): HttpURLConnection {
    return inspectorRule.environment.fakeArtTooling.triggerExitHook(
      URL::class.java,
      "openConnection()Ljava/net/URLConnection;",
      this,
    )
  }
}
