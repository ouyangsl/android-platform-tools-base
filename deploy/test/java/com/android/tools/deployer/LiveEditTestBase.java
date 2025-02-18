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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.rules.TemporaryFolder;

public class LiveEditTestBase extends AgentTestBase {

    protected static final String ACTIVITY_CLASS = "app.LiveEditActivity";

    // This is a hack.
    // The way bazel test set up the chroot jail does not work well with how the ART process
    // handle the agent's call to
    // addDex("/data/data/package.name/code_cache/.studio/live_edit.dex").
    //
    // To work around this, we'll just load the lambda super class dex as part of ART start up.
    protected static final String LIVE_EDIT_LAMBDA_DEX_LOCATION =
            ProcessRunner.getProcessPath("liveedit.app.dex.location");

    protected enum CompileClassLocation {
        JAVA_ORIGINAL_LOCATION("java.original.class.location"),
        JAVA_SWAPPED_LOCATION("java.swapped.class.location"),
        KOTLIN_ORIGINAL_LOCATION("kotlin.original.class.location"),
        KOTLIN_SWAPPED_LOCATION("kotlin.swapped.class.location");

        private final String PATH;

        private CompileClassLocation(String location) {
            PATH = ProcessRunner.getProcessPath(location);
        }
    }

    public LiveEditTestBase(String artFlag) {
        super(artFlag);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    protected Deploy.AgentLiveEditResponse sendUpdateRequest(Deploy.LiveEditRequest request) {
        try (LiveEditClient installer = new LiveEditClient(android, dexLocation)) {
            installer.startServer();
            installer.update(request);
            return installer.getLiveEditResponse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Deploy.AgentComposeStatusResponse sendPollRequest() {
        try (LiveEditClient installer = new LiveEditClient(android, dexLocation)) {
            installer.startServer();
            installer.poll(Deploy.ComposeStatusRequest.newBuilder().build());
            return installer.getComposeStatusResponse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static byte[] getClassBytes(String name, CompileClassLocation location)
            throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(location.PATH))) {
            for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                if (!entry.getName().equals(name)) {
                    continue;
                }

                byte[] buffer = new byte[1024];
                ByteArrayOutputStream content = new ByteArrayOutputStream();

                int len;
                while ((len = zis.read(buffer)) > 0) {
                    content.write(buffer, 0, len);
                }
                return content.toByteArray();
            }
            Assert.fail("Cannot find " + name + " in " + location.PATH);
            return null;
        }
    }

    protected static class LiveEditClient extends InstallServerTestClient implements AutoCloseable {
        protected LiveEditClient(FakeAndroidDriver android, TemporaryFolder messageDir) {
            super(android, messageDir);
        }

        protected void update(Deploy.LiveEditRequest request) {
            Deploy.SendAgentMessageRequest agentRequest =
                    Deploy.SendAgentMessageRequest.newBuilder()
                            .setAgentCount(1)
                            .setAgentRequest(
                                    Deploy.AgentRequest.newBuilder().setLeRequest(request).build())
                            .build();
            Deploy.InstallServerRequest serverRequest =
                    Deploy.InstallServerRequest.newBuilder().setSendRequest(agentRequest).build();
            callInstaller(serverRequest.toByteArray());
        }

        protected void poll(Deploy.ComposeStatusRequest request) {
            Deploy.SendAgentMessageRequest agentRequest =
                    Deploy.SendAgentMessageRequest.newBuilder()
                            .setAgentCount(1)
                            .setAgentRequest(
                                    Deploy.AgentRequest.newBuilder()
                                            .setComposeStatusRequest(request)
                                            .build())
                            .build();
            Deploy.InstallServerRequest serverRequest =
                    Deploy.InstallServerRequest.newBuilder().setSendRequest(agentRequest).build();
            callInstaller(serverRequest.toByteArray());
        }

        void callInstaller(byte[] message) {
            try {
                sendMessage(message);
                attachAgent();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        protected Deploy.AgentLiveEditResponse getLiveEditResponse() throws IOException {
            return getAgentResponse().getLeResponse();
        }

        protected Deploy.AgentComposeStatusResponse getComposeStatusResponse() throws IOException {
            return getAgentResponse().getComposeStatusResponse();
        }

        @Override
        public void close() {
            stopServer();
        }
    }
}
