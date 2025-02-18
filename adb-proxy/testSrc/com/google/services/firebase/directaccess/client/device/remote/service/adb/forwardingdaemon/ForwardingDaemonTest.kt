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
package com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon

import com.android.adblib.AdbChannel
import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbServerSocket
import com.android.adblib.DeviceAddress
import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.utils.createChildScope
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test

private val TEST_TIMEOUT = Duration.ofSeconds(30)

class ForwardingDaemonTest {
  // A series of commands that come from the remote server that are handled by ForwardingDaemon
  // The list is iterated and each command is written to the channel which is read by the
  // ForwardingDaemon.
  private val inputList =
    listOf(
      createByteBuffer(CNXN),
      createByteBuffer(OPEN, 1),
      createByteBuffer(WRTE, 1, 1, 4, "test"),
      createByteBuffer(CLSE, 1, 1),
    )

  private val fakeAdbSession = FakeAdbSession()
  private val throwErrorWhenClosing = MutableStateFlow(false)

  private var shouldStreamNotRespondOnClosing = false
  private var streamOnClosingWithoutResponse = false

  private val fakeStreamOpener =
    object : StreamOpener {
      private val job = Job()
      private val myStream =
        object : Stream {

          override fun sendWrite(command: WriteCommand) {
            assertThat(String(command.payload)).isEqualTo("test")
          }

          override fun sendClose() = delayUntilStreamOpenerClose()

          override suspend fun receiveCommand(command: StreamCommand) = Unit

          private fun delayUntilStreamOpenerClose() {
            if (shouldStreamNotRespondOnClosing) {
              runBlocking {
                withContext(job) {
                  streamOnClosingWithoutResponse = true
                  delay(TEST_TIMEOUT.toMillis())
                }
                streamOnClosingWithoutResponse = false
              }
            }
          }
        }

      override fun connect(forwardingDaemon: ForwardingDaemon) {
        fakeAdbSession.scope.launch {
          forwardingDaemon.onStateChanged(DeviceState.MISSING)
          forwardingDaemon.onStateChanged(DeviceState.DEVICE)
        }
      }

      override fun open(service: String, streamId: Int, adbOutputChannel: AdbOutputChannel) =
        myStream

      override fun close() {
        job.cancel()
        if (throwErrorWhenClosing.value) throw RuntimeException("expected closing exception")
      }
    }
  private lateinit var testSocket: AdbServerSocket
  private lateinit var forwardingDaemon: ForwardingDaemonImpl
  private val payloadAssertException = AtomicReference<Throwable>()
  private val exceptionHandler = CoroutineExceptionHandler { _, t -> payloadAssertException.set(t) }
  private var port = -1

  @Before
  fun setUp(): Unit = runBlockingWithTimeout {
    shouldStreamNotRespondOnClosing = false
    streamOnClosingWithoutResponse = false
    throwErrorWhenClosing.value = false
    port = -1
    val socket = fakeAdbSession.channelFactory.createServerSocket()
    testSocket =
      object : AdbServerSocket by socket {
        override suspend fun bind(local: InetSocketAddress?, backLog: Int): InetSocketAddress {
          val address = socket.bind(local, backLog)
          // Setup commands with socket bind.
          if (port != address.port) {
            port = address.port
            fakeAdbSession.deviceServices.configureShellV2Command(
              DeviceSelector.fromSerialNumber("localhost:$port"),
              "cat",
              "Foo",
            )
          }
          return address
        }

        /**
         * Returns an [AdbChannel] that never writes the whole buffer with its writeBuffer method to
         * expose issues of not writing the whole buffer from the caller.
         */
        override suspend fun accept(): AdbChannel {
          val adbChannel = socket.accept()
          return object : AdbChannel by adbChannel {
            override suspend fun writeBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
              // Do not write the last byte from the buffer unless the buffer only has 1 byte.
              if (buffer.remaining() == 1) {
                adbChannel.writeBuffer(buffer, timeout, unit)
              } else {
                buffer.limit(buffer.limit() - 1)
                adbChannel.writeBuffer(buffer, timeout, unit)
                buffer.limit(buffer.limit() + 1)
              }
            }
          }
        }
      }
  }

  @After
  fun tearDown() {
    forwardingDaemon.close()
    fakeAdbSession.close()
  }

  @Test
  fun testForwardingDaemon() = runBlockingWithTimeout {
    val childScope = fakeAdbSession.scope.createChildScope(context = exceptionHandler)
    forwardingDaemon =
      ForwardingDaemonImpl(fakeStreamOpener, childScope, fakeAdbSession) { testSocket }
    assertThat(forwardingDaemon.devicePort).isEqualTo(-1)
    forwardingDaemon.start()
    assertThat(forwardingDaemon.devicePort).isEqualTo(testSocket.localAddress()?.port)
    yieldUntil { isAdbDeviceConnected() }
    fakeAdbSession.channelFactory.connectSocket(testSocket.localAddress()!!).use { channel ->
      inputList.forEach { channel.writeExactly(it) }
      // CNXN response
      channel.assertCommand(CNXN)
      // OPEN response
      channel.assertCommand(OKAY, 1, 1)
      // WRTE response
      channel.assertCommand(OKAY, 1, 1)
    }
    assertThat(payloadAssertException.get()).isEqualTo(null)
  }

  @Test
  fun testLatency() = runBlockingWithTimeout {
    val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    forwardingDaemon = ForwardingDaemonImpl(fakeStreamOpener, scope, fakeAdbSession) { testSocket }
    assertThat(forwardingDaemon.devicePort).isEqualTo(-1)
    forwardingDaemon.start()
    // Check if roundTripLatencyMsFlow emits a value.
    assertThat(forwardingDaemon.roundTripLatencyMsFlow.first()).isNotNull()
    yieldUntil { scope.children.isNotEmpty() }
    forwardingDaemon.close()

    yieldUntil { scope.children.isEmpty() }
  }

  @Test
  fun testStreamWithoutResponseClosing() = runBlockingWithTimeout {
    shouldStreamNotRespondOnClosing = true
    val childScope = fakeAdbSession.scope.createChildScope(context = exceptionHandler)
    forwardingDaemon =
      ForwardingDaemonImpl(fakeStreamOpener, childScope, fakeAdbSession) { testSocket }
    assertThat(forwardingDaemon.devicePort).isEqualTo(-1)
    forwardingDaemon.start()
    assertThat(forwardingDaemon.devicePort).isEqualTo(testSocket.localAddress()?.port)
    yieldUntil { isAdbDeviceConnected() }
    fakeAdbSession.channelFactory.connectSocket(testSocket.localAddress()!!).use { channel ->
      inputList.forEach { channel.writeExactly(it) }
      // CNXN response
      channel.assertCommand(CNXN)
      // OPEN response
      channel.assertCommand(OKAY, 1, 1)
      // WRTE response
      channel.assertCommand(OKAY, 1, 1)
    }
    // The stream should not respond on sendClose().
    yieldUntil { streamOnClosingWithoutResponse }
    // The stream opener should close with forwardingDaemon.
    forwardingDaemon.close()
    // Wait until sendClose() ends.
    childScope.coroutineContext.job.join()
  }

  @Test
  fun testOnStateChangeCalledWithCancelledScope() = runBlockingWithTimeout {
    val childScope = fakeAdbSession.scope.createChildScope(context = exceptionHandler)
    forwardingDaemon =
      ForwardingDaemonImpl(fakeStreamOpener, childScope, fakeAdbSession) { testSocket }
    forwardingDaemon.start()
    assertThat(forwardingDaemon.devicePort).isEqualTo(testSocket.localAddress()?.port)

    childScope.cancel()
    // Implicit assert - Test will fail with JobCancellationException if this call fails.
    forwardingDaemon.close()
  }

  @Test
  fun testConcurrentClose() = runBlockingWithTimeout {
    val childScope = fakeAdbSession.scope.createChildScope(context = exceptionHandler)
    forwardingDaemon =
      ForwardingDaemonImpl(fakeStreamOpener, childScope, fakeAdbSession) { testSocket }
    forwardingDaemon.start()
    forwardingDaemon.close()

    // Connect to a device.
    fakeAdbSession.hostServices.connect(DeviceAddress("localhost:${forwardingDaemon.devicePort}"))
    assertThat(isAdbDeviceConnected()).isTrue()
    // The second call to close will be ignored.
    forwardingDaemon.close()
    // Device should still be connected.
    assertThat(isAdbDeviceConnected()).isTrue()
  }

  @Test
  fun testStreamConcurrencySupport() = runBlockingWithTimeout {
    val childScope = fakeAdbSession.scope.createChildScope(context = exceptionHandler)
    forwardingDaemon =
      ForwardingDaemonImpl(fakeStreamOpener, childScope, fakeAdbSession) { testSocket }
    assertThat(forwardingDaemon.devicePort).isEqualTo(-1)
    forwardingDaemon.start()
    assertThat(forwardingDaemon.devicePort).isEqualTo(testSocket.localAddress()?.port)
    yieldUntil { isAdbDeviceConnected() }
    var streamId = 1
    // Make sure the stream list if big enough to iterate.
    val streamCount = 2000
    val countDownLatch = CountDownLatch(streamCount)
    childScope.launch {
      fakeAdbSession.channelFactory.connectSocket(testSocket.localAddress()!!).use { channel ->
        inputList.forEach { channel.writeExactly(it) }
        // CNXN response
        channel.assertCommand(CNXN)
        // OPEN response
        channel.assertCommand(OKAY, 1, 1)
        // WRTE response
        channel.assertCommand(OKAY, 1, 1)
        while (true) {
          try {
            channel.writeExactly(createByteBuffer(OPEN, ++streamId))
            countDownLatch.countDown()
            channel.assertCommand(OKAY, streamId, streamId)
          } catch (_: IOException) {
            break
          }
        }
      }
    }
    countDownLatch.await()
    forwardingDaemon.close()
  }

  @Test
  fun testExceptionsOnClosing() = runBlockingWithTimeout {
    throwErrorWhenClosing.value = true
    val childScope = fakeAdbSession.scope.createChildScope(context = exceptionHandler)
    forwardingDaemon =
      ForwardingDaemonImpl(fakeStreamOpener, childScope, fakeAdbSession) { testSocket }
    forwardingDaemon.start()
    yieldUntil { isAdbDeviceConnected() }
    throwErrorWhenClosing.value = false
    forwardingDaemon.close()
    // Device should still be disconnected.
    assertThat(isAdbDeviceConnected()).isFalse()
  }

  @Test
  fun testDeviceStateOnLatencyDisconnect() = runBlockingWithTimeout {
    val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    forwardingDaemon = ForwardingDaemonImpl(fakeStreamOpener, scope, fakeAdbSession) { testSocket }
    assertThat(forwardingDaemon.devicePort).isEqualTo(-1)
    // Since we don't send/receive anything, the latency collector throws an EOF error
    // which results in emitting ROUND_TRIP_LATENCY_LIMIT thrice.
    forwardingDaemon.start()

    yieldUntil { forwardingDaemon.deviceState.value == DeviceState.LATENCY_DISCONNECT }
  }

  private fun isAdbDeviceConnected() =
    fakeAdbSession.hostServices.devices.entries.any {
      it.serialNumber == "localhost:${forwardingDaemon.devicePort}"
    }
}
