/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.test.inspectors.network.grpc

import com.google.test.inspectors.grpc.custom.CustomRequest
import com.google.test.inspectors.grpc.custom.CustomServiceCoroutineStub
import com.google.test.inspectors.grpc.json.JsonRequest
import com.google.test.inspectors.grpc.json.JsonServiceCoroutineStub
import com.google.test.inspectors.grpc.proto.ProtoRequest
import com.google.test.inspectors.grpc.proto.ProtoServiceGrpcKt
import com.google.test.inspectors.grpc.xml.XmlRequest
import com.google.test.inspectors.grpc.xml.XmlServiceCoroutineStub
import io.grpc.ManagedChannelBuilder
import io.grpc.android.AndroidChannelBuilder
import io.grpc.internal.GrpcUtil
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit.SECONDS

internal class GrpcClient(host: String, port: Int, channelBuilderType: ChannelBuilderType) :
  AutoCloseable {
  enum class ChannelBuilderType(val displayName: String) {
    MANAGED_FOR_ADDRESS("For Address") {
      override fun newBuilder(host: String, port: Int): ManagedChannelBuilder<*> =
        ManagedChannelBuilder.forAddress(host, port)
    },
    MANAGED_FOR_TARGET("For Target") {
      override fun newBuilder(host: String, port: Int): ManagedChannelBuilder<*> =
        ManagedChannelBuilder.forTarget(GrpcUtil.authorityFromHostAndPort(host, port))
    },
    ANDROID_FOR_ADDRESS("For Address (Android)") {
      override fun newBuilder(host: String, port: Int): ManagedChannelBuilder<*> =
        AndroidChannelBuilder.forAddress(host, port)
    },
    ANDROID_FOR_TARGET("For Target (Android)") {
      override fun newBuilder(host: String, port: Int): ManagedChannelBuilder<*> =
        AndroidChannelBuilder.forTarget(GrpcUtil.authorityFromHostAndPort(host, port))
    },
    OKHTTP_FOR_ADDRESS("For Address (OKHTTP)") {
      override fun newBuilder(host: String, port: Int): ManagedChannelBuilder<*> =
        OkHttpChannelBuilder.forAddress(host, port)
    },
    OKHTTP_FOR_TARGET("For Target (OKHTTP)") {
      override fun newBuilder(host: String, port: Int): ManagedChannelBuilder<*> =
        OkHttpChannelBuilder.forTarget(GrpcUtil.authorityFromHostAndPort(host, port))
    },
    ;

    abstract fun newBuilder(host: String, port: Int): ManagedChannelBuilder<*>
  }

  // TODO(aalbert): Add secure channel support
  private val channel =
    channelBuilderType
      .newBuilder(host, port)
      .usePlaintext()
      .intercept(ClientMetadataInterceptor(), LoggingGrpcInterceptor())
      .build()

  private val protoStub = ProtoServiceGrpcKt.ProtoServiceCoroutineStub(channel)

  private val jsonStub = JsonServiceCoroutineStub(channel)

  private val xmlStub = XmlServiceCoroutineStub(channel)

  private val customStub = CustomServiceCoroutineStub(channel)

  suspend fun doProtoGrpc(request: ProtoRequest) = protoStub.doProtoRpc(request)

  suspend fun doJsonGrpc(request: JsonRequest) = jsonStub.doJsonGrpc(request)

  suspend fun doXmlGrpc(request: XmlRequest) = xmlStub.doXmlGrpc(request)

  suspend fun doCustomGrpc(request: CustomRequest) = customStub.doCustomGrpc(request)

  override fun close() {
    channel.shutdown()
    channel.awaitTermination(2, SECONDS)
  }
}
