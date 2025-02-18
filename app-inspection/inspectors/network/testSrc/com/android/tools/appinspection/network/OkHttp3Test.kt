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
import com.android.tools.appinspection.network.testing.okhttp3.FakeOkHttp3Client
import com.android.tools.appinspection.network.testing.receiveInterceptCommand
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.lang.IllegalStateException
import java.net.URL
import kotlin.test.fail
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSink
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.junit.rules.CloseGuardRule
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Header
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.ResponseStarted
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.REQUEST_PAYLOAD
import studio.network.inspection.NetworkInspectorProtocol.InterceptCommand

private const val URL_PARAMS = "activity=OkHttp3Test"
private val FAKE_URL = URL("https://www.google.com?$URL_PARAMS")
private val EXPECTED_RESPONSE =
  ResponseStarted.newBuilder()
    .setResponseCode(200)
    .addHeaders(Header.newBuilder().setKey("response-status-code").addValues("200"))
    .build()

@RunWith(RobolectricTestRunner::class)
@Config(
  manifest = Config.NONE,
  minSdk = Build.VERSION_CODES.O,
  maxSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)
internal class OkHttp3Test {
  private val inspectorRule = NetworkInspectorRule()

  @get:Rule
  val rule: RuleChain =
    RuleChain.outerRule(CloseGuardRule()).around(inspectorRule).around(LogPrinterRule())

  @Test
  fun get() {
    val client = createFakeOkHttp3Client()

    val request = Request.Builder().url(FAKE_URL).build()
    val fakeResponse = createFakeResponse(request)

    val response = client.newCall(request, fakeResponse).execute()
    response.body!!.byteStream().use { it.readBytes() }

    assertThat(inspectorRule.connection.httpData).hasSize(6)
    val httpRequestStarted = inspectorRule.connection.httpData.first().httpRequestStarted
    assertThat(httpRequestStarted.url).contains(URL_PARAMS)
    assertThat(httpRequestStarted.method).isEqualTo("GET")
    assertThat(httpRequestStarted.transport).isEqualTo(HttpTransport.OKHTTP3)

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
  fun post() {
    val client = createFakeOkHttp3Client()
    val requestBody =
      object : RequestBody() {
        override fun contentType(): MediaType {
          return "text/text".toMediaType()
        }

        override fun writeTo(sink: BufferedSink) {
          val requestBody = "request body"
          sink.write(requestBody.toByteArray())
        }
      }
    val request = Request.Builder().url(FAKE_URL).post(requestBody).build()
    val fakeResponse = createFakeResponse(request)

    val response = client.newCall(request, fakeResponse).execute()
    response.body!!.byteStream().use { it.readBytes() }

    assertThat(inspectorRule.connection.httpData).hasSize(8)
    val httpRequestStarted = inspectorRule.connection.httpData.first().httpRequestStarted
    assertThat(httpRequestStarted.url).contains(URL_PARAMS)
    assertThat(httpRequestStarted.method).isEqualTo("POST")
    assertThat(httpRequestStarted.transport).isEqualTo(HttpTransport.OKHTTP3)

    val httpRequestCompleted =
      inspectorRule.connection.findHttpEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_COMPLETED
      )
    assertThat(httpRequestCompleted).isNotNull()

    val requestPayload = inspectorRule.connection.findHttpEvent(REQUEST_PAYLOAD)
    assertThat(requestPayload!!.requestPayload.payload.toStringUtf8()).isEqualTo("request body")
  }

  @Test
  fun intercept() {
    val ruleAdded = createFakeRuleAddedEvent(FAKE_URL)

    inspectorRule.inspector.receiveInterceptCommand(
      InterceptCommand.newBuilder().apply { interceptRuleAdded = ruleAdded }.build()
    )

    val client = createFakeOkHttp3Client()
    val request = Request.Builder().url(FAKE_URL).build()
    val fakeResponse = createFakeResponse(request)
    val response = client.newCall(request, fakeResponse).execute()

    assertThat(response.code).isEqualTo(404)
    assertThat(response.headers["Name"]).isEqualTo("Value")

    response.body!!.byteStream().use { it.readBytes() }
    assertThat(
        inspectorRule.connection
          .findHttpEvent(NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD)!!
          .responsePayload
          .payload
          .toStringUtf8()
      )
      .isEqualTo("InterceptedBody1")

    assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()
  }

  @Test
  fun intercept_duplexRequest() {
    val ruleAdded = createFakeRuleAddedEvent(FAKE_URL)
    inspectorRule.inspector.receiveInterceptCommand(
      InterceptCommand.newBuilder().apply { interceptRuleAdded = ruleAdded }.build()
    )
    val client = createFakeOkHttp3Client()

    val request = Request.Builder().url(FAKE_URL).post(DuplexRequestBody()).build()
    client.newCall(request, createFakeResponse(request)).execute()

    val event = inspectorRule.connection.findHttpEvent(REQUEST_PAYLOAD) ?: fail("Payload not found")
    assertThat(event.requestPayload.payload.toStringUtf8()).isEqualTo("Duplex body omitted")
  }

  @Test
  fun intercept_oneShotRequest() {
    val ruleAdded = createFakeRuleAddedEvent(FAKE_URL)
    inspectorRule.inspector.receiveInterceptCommand(
      InterceptCommand.newBuilder().apply { interceptRuleAdded = ruleAdded }.build()
    )
    val client = createFakeOkHttp3Client()

    val request = Request.Builder().url(FAKE_URL).post(OneShotRequestBody()).build()
    client.newCall(request, createFakeResponse(request)).execute()

    val event = inspectorRule.connection.findHttpEvent(REQUEST_PAYLOAD) ?: fail("Payload not found")
    assertThat(event.requestPayload.payload.toStringUtf8()).isEqualTo("One-shot body omitted")
  }

  @Test
  fun abort() {
    val client = createFakeOkHttp3Client()
    val request = Request.Builder().url(FAKE_URL).build()
    val fakeResponse = createFakeResponse(request)

    val call = client.newCall(request, fakeResponse)
    try {
      call.executeThenBlowUp()
    } catch (e: IOException) {
      // This exception comes from blowing up the interceptor chain and is expected.
    }

    val response = client.newCall(request, fakeResponse).execute()
    response.body!!.byteStream().use { it.readBytes() }

    val events = inspectorRule.connection.httpData.groupBy { it.connectionId }

    run {
      // events for the aborted call
      val abortedEvents = events[0]!!
      assertThat(abortedEvents).hasSize(3)
      assertThat(abortedEvents[2].hasHttpClosed()).isTrue()
      assertThat(abortedEvents[2].httpClosed.completed).isFalse()
      assertThat(abortedEvents[1].hasHttpThread()).isTrue()
    }

    run {
      // events for the follow-up GET call that is successful
      val successEvents = events[1]!!
      assertThat(successEvents).hasSize(6)
      assertThat(successEvents[2].hasHttpResponseStarted()).isTrue()
      val httpResponseStarted =
        inspectorRule.connection.findHttpEvent(
          NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED
        )
      assertThat(httpResponseStarted!!.httpResponseStarted).isEqualTo(EXPECTED_RESPONSE)
      assertThat(successEvents[5].hasHttpClosed()).isTrue()
      assertThat(successEvents[5].httpClosed.completed).isTrue()
    }
  }

  private fun createFakeOkHttp3Client(): FakeOkHttp3Client {
    return FakeOkHttp3Client(
      inspectorRule.environment.fakeArtTooling.triggerExitHook(
        okhttp3.OkHttpClient::class.java,
        "networkInterceptors()Ljava/util/List;",
        emptyList(),
      )
    )
  }

  private abstract class FakeRequestBody : RequestBody() {

    override fun contentType() = "text".toMediaType()

    override fun contentLength() = 100L

    override fun writeTo(sink: BufferedSink) {
      throw IllegalStateException()
    }
  }

  private class OneShotRequestBody : FakeRequestBody() {

    override fun isOneShot() = true
  }

  private class DuplexRequestBody : FakeRequestBody() {

    override fun isDuplex() = true
  }
}

private fun createFakeResponse(request: Request): Response {
  return Response.Builder()
    .request(request)
    .protocol(Protocol.HTTP_2)
    .code(200)
    .message("")
    .body("Test".toResponseBody("text/text; charset=utf-8".toMediaType()))
    .build()
}
