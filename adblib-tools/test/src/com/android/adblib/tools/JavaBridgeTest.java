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
package com.android.adblib.tools;

import static kotlinx.coroutines.DelayKt.delay;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.adblib.tools.testutils.AdbLibToolsTestBase;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import kotlin.Unit;
import kotlinx.coroutines.TimeoutCancellationException;
import org.junit.Test;

@SuppressWarnings({"resource", "CodeBlock2Expr"})
public class JavaBridgeTest extends AdbLibToolsTestBase {

    @Test
    public void runBlockingWorks() {
        // Prepare

        // Act
        int version =
                JavaBridge.runBlocking(
                        getSession(),
                        continuation -> {
                            return getSession().getHostServices().version(continuation);
                        },
                        getSession().getScope(),
                        30_000);

        // Assert
        assertEquals(40, version);
    }

    @Test
    public void runBlockingHandlesNonSuspendingCoroutines() {
        // Prepare

        // Act
        int result =
                JavaBridge.runBlocking(
                        getSession(),
                        cnt -> JavaBridgeTestUtils.immediateResultCoroutine(5, cnt),
                        getSession().getScope(),
                        30_000);

        // Assert
        assertEquals(10, result);
    }

    @Test
    public void runBlockingHandlesExceptionNonSuspendingCoroutines() {
        // Prepare

        // Act
        exceptionRule.expect(Exception.class);
        exceptionRule.expectMessage("test");
        JavaBridge.runBlocking(
                getSession(),
                cnt -> JavaBridgeTestUtils.immediateExceptionCoroutine("test", cnt),
                getSession().getScope(),
                30_000);

        // Assert
        fail("Should not reach");
    }

    @Test
    public void runBlockingIsTransparentToExceptions() {
        // Prepare

        // Act
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("My Message");
        JavaBridge.runBlocking(
                getSession(),
                continuation -> {
                    throw new RuntimeException("My Message");
                });

        // Assert
        fail("Should not reach");
    }

    @Test
    public void runBlockingHandlesTimeout() {
        // Prepare

        // Act
        exceptionRule.expect(TimeoutCancellationException.class);
        JavaBridge.runBlocking(
                getSession(),
                cnt -> {
                    return delay(5_000, cnt);
                },
                getSession().getScope(),
                10);

        // Assert
        fail("Should not reach");
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    public void runBlockingIsCancelledWhenScopeIsCancelled() throws Throwable {
        // Prepare

        // Act
        AtomicBoolean hasStarted = new AtomicBoolean(false);
        Thread t =
                new Thread(
                        () -> {
                            JavaBridge.runBlocking(
                                    getSession(),
                                    continuation -> {
                                        hasStarted.set(true);
                                        return delay(10_000, continuation);
                                    });
                        });
        AtomicReference<Throwable> uncaughtException = new AtomicReference<>();
        t.setUncaughtExceptionHandler((thread, throwable) -> uncaughtException.set(throwable));

        // Start thread, wait for runBlocking and "delay" to start, then cancel
        // coroutine scope
        t.start();
        while (!hasStarted.get()) {
            // Wait until thread has started
        }
        getSession().close();
        t.join(1_000);

        // Assert
        assertFalse(t.isAlive());
        assertNotNull(uncaughtException.get());
        assertTrue(uncaughtException.get() instanceof CancellationException);
    }

    @Test
    public void runBlockingThrowsWhenCalledOnEventDispatchThread() throws Throwable {
        // Prepare

        // Act
        exceptionRule.expect(InvocationTargetException.class);
        exceptionRule.expectCause(instanceOf(IllegalStateException.class));
        SwingUtilities.invokeAndWait(
                () -> {
                    JavaBridge.runBlocking(getSession(), continuation -> delay(10, continuation));
                });

        // Assert
        fail("Should not reach");
    }

    @Test
    public void invokeAsyncWorks() {
        // Prepare

        // Act
        JavaDeferred<Integer> deferredVersion =
                JavaBridge.invokeAsync(
                        getSession(),
                        continuation -> getSession().getHostServices().version(continuation));
        int version = deferredVersion.awaitBlocking();

        // Assert
        assertEquals(40, version);
    }

    @Test
    public void invokeAsyncIsTransparentToExceptions() {
        // Prepare

        // Act
        JavaDeferred<Object> deferred =
                JavaBridge.invokeAsync(
                        getSession(),
                        continuation -> {
                            throw new RuntimeException("My Message");
                        });

        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("My Message");
        deferred.awaitBlocking();

        // Assert
        fail("Should not reach");
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    public void invokeAsyncCanBeCancelled() throws Throwable {
        // Prepare

        // Act
        JavaDeferred<Unit> deferred =
                JavaBridge.invokeAsync(getSession(), continuation -> delay(10_000, continuation));

        // Wait until async has started running
        while (!deferred.isActive()) {
            // Wait until thread has started
        }
        getSession().close();

        // Assert
        assertTrue(deferred.isCancelled());
    }

    @Test
    public void invokeAsyncWorksOnEventDispatchThread() throws Throwable {
        // Prepare

        // Act
        AtomicReference<JavaDeferred<Integer>> deferredVersion = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    JavaDeferred<Integer> deferred =
                            JavaBridge.invokeAsync(
                                    getSession(),
                                    continuation ->
                                            getSession().getHostServices().version(continuation));
                    deferredVersion.set(deferred);
                });

        int version = deferredVersion.get().awaitBlocking();

        // Assert
        assertEquals(40, version);
    }

    @Test
    public void invokeAsyncAddCallbackWorks() throws Throwable {
        // Prepare

        // Act
        JavaDeferred<Integer> deferredVersion =
                JavaBridge.invokeAsync(
                        getSession(),
                        continuation -> getSession().getHostServices().version(continuation));
        AtomicInteger version = new AtomicInteger();
        deferredVersion.addCallback(
                (throwable, value) -> {
                    version.set(value);
                });

        deferredVersion.awaitBlocking();
        Thread.sleep(100); // Wait for callback to be invoked

        // Assert
        assertEquals(40, version.get());
    }

    @Test
    public void invokeAsyncAddCallbackIsTransparentToExceptions() throws Throwable {
        // Prepare

        // Act
        JavaDeferred<Integer> deferredVersion =
                JavaBridge.invokeAsync(
                        getSession(),
                        continuation -> {
                            throw new IOException("My Message");
                        });
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        AtomicReference<Integer> versionRef = new AtomicReference<>();
        deferredVersion.addCallback(
                (throwable, value) -> {
                    System.out.printf("t: %s, v:%s\n", throwable, value);
                    throwableRef.set(throwable);
                    versionRef.set(value);
                    System.out.printf("t: %s, v:%s\n", throwableRef.get(), versionRef.get());
                });

        // Wait 5 seconds max.
        long startTime = System.currentTimeMillis();
        while ((throwableRef.get() == null) && (System.currentTimeMillis() - startTime < 5_000)) {
            //noinspection BusyWait
            Thread.sleep(10); // Wait for callback to be invoked
        }

        // Assert
        assertTrue(deferredVersion.isCompleted());
        assertTrue(deferredVersion.isCancelled());
        assertFalse(deferredVersion.isActive());
        assertNull(versionRef.get());
        assertNotNull(throwableRef.get());
        assertTrue(throwableRef.get() instanceof IOException);
        assertEquals("My Message", throwableRef.get().getMessage());
    }
}
