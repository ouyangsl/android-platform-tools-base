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

import com.android.jdwppacket.MessageReader;
import com.android.jdwppacket.SuperClassCmd;
import com.android.jdwppacket.SuperClassReply;

class CmdSetClassType extends CmdSet {

    protected CmdSetClassType() {
        super(3, "CLASS_TYPE");
        add(
                1,
                "Superclass",
                CmdSetClassType::parseSuperclassCmd,
                CmdSetClassType::parseSuperclassReply);
        add(2, "SetValues");
        add(3, "InvokeMethod");
        add(4, "NewInstance");
    }

    private static Message parseSuperclassCmd(MessageReader reader, Session session) {
        Message message = new Message(reader);
        SuperClassCmd cmd = SuperClassCmd.parse(reader);
        message.addArg("classID", cmd.getClazz());
        return message;
    }

    private static Message parseSuperclassReply(MessageReader reader, Session session) {
        Message message = new Message(reader);
        SuperClassReply reply = SuperClassReply.parse(reader);
        message.addArg("superClassID", reply.getSuperclass());
        return message;
    }
}
