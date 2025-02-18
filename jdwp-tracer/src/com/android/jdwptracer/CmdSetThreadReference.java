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
package com.android.jdwptracer;

import com.android.annotations.NonNull;
import com.android.jdwppacket.MessageReader;
import com.android.jdwppacket.threadreference.FramesCmd;
import com.android.jdwppacket.threadreference.FramesCountCmd;
import com.android.jdwppacket.threadreference.FramesCountReply;
import com.android.jdwppacket.threadreference.FramesReply;
import com.android.jdwppacket.threadreference.NameCmd;
import com.android.jdwppacket.threadreference.NameReply;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class CmdSetThreadReference extends CmdSet {

    protected CmdSetThreadReference() {
        super(11, "THREAD_REF");

        add(1, "Name", CmdSetThreadReference::parseNameCmd, CmdSetThreadReference::parseNameReply);
        add(2, "Suspend");
        add(3, "Resume");
        add(4, "Status");
        add(5, "ThreadGroup");
        add(
                6,
                "Frames",
                CmdSetThreadReference::parseFramesCmd,
                CmdSetThreadReference::parseFramesReply);
        add(
                7,
                "FrameCount",
                CmdSetThreadReference::parseFramesCountCmd,
                CmdSetThreadReference::parseFramesCountReply);
        add(8, "OwnerMonitors");
        add(9, "CurrentContendedMonitor");
        add(10, "Stop");
        add(11, "Interrupt");
        add(12, "SuspendCount");
        add(13, "OwnerMonitorStackDepthInfo");
        add(14, "ForceEarlyReturn");
    }

    private static Message parseFramesCountReply(MessageReader reader, Session session) {
        FramesCountReply reply = FramesCountReply.parse(reader);
        Message message = new Message(reader);
        message.addArg("count", reply.getCount());
        return message;
    }

    private static Message parseFramesCountCmd(MessageReader reader, Session session) {
        FramesCountCmd cmd = FramesCountCmd.parse(reader);
        Message message = new Message(reader);
        message.addArg("threadID", cmd.getThreadID());
        return message;
    }

    private static Message parseNameReply(MessageReader reader, Session session) {
        NameReply reply = NameReply.parse(reader);
        Message message = new Message(reader);
        message.addArg("name", reply.getName());
        return message;
    }

    private static Message parseNameCmd(MessageReader reader, Session session) {
        NameCmd cmd = NameCmd.parse(reader);
        Message message = new Message(reader);
        message.addArg("threadID", cmd.getThreadID());
        return message;
    }

    private static Message parseFramesCmd(@NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);
        FramesCmd cmd = FramesCmd.parse(reader);
        message.addArg("threadID", cmd.getThreadID());
        message.addArg("startFrame", cmd.getStartFrame());
        message.addArg("length", cmd.getLength());
        return message;
    }

    private static Message parseFramesReply(
            @NonNull MessageReader reader, @NonNull Session session) {
        Message message = new Message(reader);

        FramesReply frames = FramesReply.parse(reader);

        JsonArray framesArray = new JsonArray();
        for (FramesReply.Frame frame : frames.getFrames()) {
            JsonObject jsonFrame = new JsonObject();
            jsonFrame.addProperty("frameID", frame.getId());
            jsonFrame.add("location", JsonLocation.get(frame.getLocation()));
            framesArray.add(jsonFrame);
        }

        message.addArg("frames", framesArray);

        return message;
    }
}
