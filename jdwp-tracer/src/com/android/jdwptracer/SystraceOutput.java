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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// The renderer which takes a set of events and turn them into systrace format.
class SystraceOutput {

    static void genOutput(@NonNull Session session, @NonNull Path path) {
        List<Event> events = session.events();
        try {
            FileWriter out = new FileWriter(path.toAbsolutePath().toString());
            JsonArray root = new JsonArray();
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                if (event instanceof Transmission) {
                    processTransmission(root, (Transmission) event);
                } else if (event instanceof NamedEvent) {
                    processNamedEvent(root, (NamedEvent) event);
                } else {
                    throw new IllegalStateException("Unknown type of event");
                }
            }

            // On Android U and above, oj-libjdwp sends how long it took for ART to process a cmd
            // and send a reply. We augment our traces with it.
            addARTTimings(session, root);

            // The Json output we generate is meant to be opened vi perfetto UI. The render usually
            // deals with Processes and Threads with result in an awkward title "Process 0". We name
            // the process 0 in order to get a nicer title "JDWP packets, session 0".
            nameProcess(root, "JDWP packets, session");

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            out.write(gson.toJson(root));
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addARTTimings(@NonNull Session session, JsonArray root) throws IOException {
        Map<Integer, DdmJDWPTiming> idToArtTimings = session.timings();
        List<Transmission> transmissions = event2Transmission(session.events());

        // To integrate the ART timings with the local JDWP packet timings, we need to synchronize
        // the clocks. We achieve this by finding the tightest fit of an ART timing within a
        // Transaction, center it and use this delta for all ART Timings.
        long minDiff_ns = Long.MAX_VALUE;
        long clockDelta_ns = 0;

        for (Transmission t : transmissions) {
            if (!t.reply().isPresent()) {
                continue;
            }

            if (!idToArtTimings.containsKey(t.jdpwId())) {
                continue;
            }

            DdmJDWPTiming timing = idToArtTimings.get(t.jdpwId());
            long duration_ns = timing.duration_ns();
            long transmDuration_ns = t.reply().get().time_ns() - t.cmd().time_ns();

            long diff_ns = transmDuration_ns - duration_ns;
            if (diff_ns < minDiff_ns) {
                minDiff_ns = diff_ns;
                clockDelta_ns =
                        t.cmd().time_ns()
                                - timing.start_ns()
                                + (transmDuration_ns - duration_ns) / 2;
            }
        }

        for (Transmission t : transmissions) {
            if (!t.reply().isPresent()) {
                continue;
            }

            if (!idToArtTimings.containsKey(t.jdpwId())) {
                continue;
            }

            DdmJDWPTiming timing = idToArtTimings.get(t.jdpwId());

            // Use the delta previously calculated to sync the start time with the JDWP packet
            // times.
            long startTime = timing.start_ns() + clockDelta_ns;
            emitCompleteEvent(root, ns2us(startTime), ns2us(timing.duration_ns()), t.line(), "art");
        }
    }

    private static List<Transmission> event2Transmission(List<Event> events) {
        List<Transmission> transmissions = new ArrayList<>();
        for (Event event : events) {
            if (event instanceof Transmission) {
                transmissions.add((Transmission) event);
            }
        }
        return transmissions;
    }

    private static void processNamedEvent(@NonNull JsonArray root, @NonNull NamedEvent event)
            throws IOException {
        String name = event.name();
        emitInstantEvent(root, ns2us(event.time_ns()), event.line(), name);
        emitThreadMetadata(root, event.line(), name);
    }

    private static void processTransmission(
            @NonNull JsonArray root, @NonNull Transmission transmission) throws IOException {
        Command command = transmission.cmd();

        CmdSet cmdset = CmdSets.get(transmission.cmd().cmdSetID());
        String name = cmdset.name() + ":" + cmdset.cmdName(transmission.cmd().cmdID());
        if (!command.message().name().isEmpty()) {
            name += ":" + command.message().name();
        }

        JsonObject args = new JsonObject();

        // Display id using hex to make it easier to recogize the fingerprint of a debug participant
        // ddmlib uses 0x80000000, ddm Android OS uses 0x40000000, scache 0x90000000
        args.addProperty("id", "0x" + Integer.toHexString(transmission.jdpwId()));

        if (transmission.reply().isPresent()) {
            Reply reply = transmission.reply().get();
            long duration = reply.time_ns() - command.time_ns();

            args.add("cmd", makeMessagePayload(command.message()));
            args.add("reply", makeMessagePayload(reply.message()));
            emitCompleteEvent(
                    root,
                    ns2us(command.time_ns()),
                    ns2us(duration),
                    transmission.line(),
                    name,
                    args);
        } else {
            args.add("event", makeMessagePayload(command.message()));
            emitInstantEvent(root, ns2us(command.time_ns()), transmission.line(), name, args);
        }

        // Write the thread dictionary via MetaData Event
        emitThreadMetadata(root, transmission.line(), name);
    }

    private static JsonObject makeMessagePayload(Message message) {
        JsonObject payloadObject = new JsonObject();
        payloadObject.addProperty("length", message.payloadLength());

        JsonObject arguments = message.args();
        if (arguments.size() > 0) {
            payloadObject.add("payload", arguments);
        }

        return payloadObject;
    }

    private static void emitCompleteEvent(
            @NonNull JsonArray root,
            long startTime_us,
            long duration_us,
            int line,
            @NonNull String name)
            throws IOException {
        emitCompleteEvent(root, startTime_us, duration_us, line, name, new JsonObject());
    }

    private static void emitCompleteEvent(
            @NonNull JsonArray root,
            long startTime,
            long duration_us,
            int i,
            @NonNull String name,
            @NonNull JsonObject args)
            throws IOException {
        JsonObject part = new JsonObject();
        part.addProperty("name", name);
        part.addProperty("cat", "foo");
        part.addProperty("ph", "X");
        part.addProperty("ts", startTime);
        part.addProperty("dur", duration_us);
        part.addProperty("pid", 0);
        part.addProperty("tid", i);
        part.add("args", args);

        root.add(part);
    }

    private static void emitInstantEvent(
            @NonNull JsonArray root, long time_us, int lineID, @NonNull String name)
            throws IOException {
        emitInstantEvent(root, time_us, lineID, name, new JsonObject());
    }

    private static void emitInstantEvent(
            @NonNull JsonArray root,
            long time_us,
            int threadID,
            @NonNull String name,
            @NonNull JsonObject args)
            throws IOException {
        JsonObject part = new JsonObject();
        part.addProperty("name", name);
        part.addProperty("ph", "i");
        part.addProperty("ts", time_us);
        part.addProperty("pid", 0);
        part.addProperty("tid", threadID);
        part.addProperty("s", "t");
        if (args != null) {
            part.add("args", args);
        }

        root.add(part);
    }

    private static void emitThreadMetadata(@NonNull JsonArray root, int threadID, @NonNull String name) {
        // 1. Issue thread name
        JsonObject threadNameMeta = new JsonObject();
        threadNameMeta.addProperty("name", "thread_name");
        threadNameMeta.addProperty("ph", "M");
        threadNameMeta.addProperty("pid", 0);
        threadNameMeta.addProperty("tid", threadID);

        JsonObject argsName = new JsonObject();
        argsName.addProperty("name", name);
        threadNameMeta.add("args", argsName);

        root.add(threadNameMeta);

        // 2. Issue thread drawing order
        JsonObject part = new JsonObject();
        part.addProperty("name", "thread_sort_index");
        part.addProperty("ph", "M");
        part.addProperty("pid", 0);
        part.addProperty("tid", threadID);

        JsonObject argsOrder = new JsonObject();
        argsOrder.addProperty("sort_index", threadID);
        part.add("args", argsOrder);

        root.add(part);

    }

    private static void nameProcess(@NonNull JsonArray root, @NonNull String name)
            throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("name", name);

        JsonObject part = new JsonObject();
        part.addProperty("name", "process_name");
        part.addProperty("ph", "M");
        part.addProperty("pid", 0);
        part.add("args", args);

        root.add(part);
    }

    private static long ns2us(long time_ns) {
        return time_ns / 1000;
    }
}
