/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.ddmlib.logcat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import junit.framework.TestCase;

/**
 * Unit tests for {@link LogCatMessageParser}.
 */
public final class LogCatMessageParserTest extends TestCase {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS");

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Yerevan");

    private List<LogCatMessage> mParsedMessages;

    /**
     * A list of messages generated with the following code:
     *
     * <pre>{@code
     * Log.d("dtag", "debug message");
     * Log.e("etag", "error message");
     * Log.i("itag", "info message");
     * Log.v("vtag", "verbose message");
     * Log.w("wtag", "warning message");
     * Log.wtf("wtftag", "wtf message");
     * Log.d("dtag", "debug message");
     * }</pre>
     *
     * Note: On Android 2.3, Log.wtf doesn't really generate the message. It only produces the
     * message header, but swallows the message tag. This string has been modified to include the
     * message.
     */
    private static final String[] MESSAGES =
            new String[] {
                "[ 08-11 19:11:07.132   495:0x1ef D/dtag     ]", //$NON-NLS-1$
                "debug message", //$NON-NLS-1$
                "[ 08-11 19:11:07.132   495:  234 E/etag     ]", //$NON-NLS-1$
                "error message", //$NON-NLS-1$
                "[ 08-11 19:11:07.132   495:0x1ef I/itag     ]", //$NON-NLS-1$
                "info message", //$NON-NLS-1$
                "[ 08-11 19:11:07.132   495:0x1ef V/vtag     ]", //$NON-NLS-1$
                "verbose message", //$NON-NLS-1$
                "[ 08-11 19:11:07.132   495:0x1ef W/wtag     ]", //$NON-NLS-1$
                "warning message", //$NON-NLS-1$
                "[ 08-11 19:11:07.132   495:0x1ef F/wtftag   ]", //$NON-NLS-1$
                "wtf message", //$NON-NLS-1$
                "[ 08-11 21:15:35.754   540:0x21c D/dtag     ]", //$NON-NLS-1$
                "debug message", //$NON-NLS-1$
                "[ 09-11 14:18:30.992   524:  524 E/         ]", //$NON-NLS-1$
                "failed to retrieved process context for pid 0", //$NON-NLS-1$
                "[ 09-11 14:18:30.992   524:  524 E/my tag   ]", //$NON-NLS-1$
                "my tag message", //$NON-NLS-1$
                "[ 09-11 14:18:30.992   524:  524 E/my tag with spaces  ]", //$NON-NLS-1$
                "my tag with spaces message", //$NON-NLS-1$
            };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LogCatMessageParser parser = new LogCatMessageParser(2014, ZONE_ID);

        IDevice d = mock(IDevice.class);
        when(d.getClientName(anyInt()))
                .thenAnswer(
                        (invocation) ->
                                invocation.getArguments()[0].equals(495) ? "com.example.name" : "");

        mParsedMessages = parser.processLogLines(MESSAGES, d);
    }

    /** Check that the correct number of messages are received. */
    public void testMessageCount() {
        assertEquals(10, mParsedMessages.size());
    }

    /** Check the log level in a few of the parsed messages. */
    public void testLogLevel() {
        assertEquals(LogLevel.DEBUG, mParsedMessages.get(0).getHeader().getLogLevel());
        assertEquals(LogLevel.ASSERT, mParsedMessages.get(5).getHeader().getLogLevel());
    }

    /** Check the parsed tag. */
    @SuppressWarnings("NewMethodNamingConvention")
    public void testTag() {
        assertEquals("etag", mParsedMessages.get(1).getHeader().getTag());
    }

    /** Check for empty tag */
    public void testEmptyTag() {
        // Technically, the "Log" API allows the "tag" parameter to be an empty string
        assertEquals("", mParsedMessages.get(7).getHeader().getTag());
    }

    /** Check for empty tag */
    public void testTagWithSpace() {
        // Technically, the "Log" API allows the "tag" parameter to be an empty string
        assertEquals("my tag", mParsedMessages.get(8).getHeader().getTag());
    }

    /** Check for empty tag */
    public void testTagWithSpaces() {
        // Technically, the "Log" API allows the "tag" parameter to be an empty string
        assertEquals("my tag with spaces", mParsedMessages.get(9).getHeader().getTag());
    }

    /** Check the time field. */
    public void testTime() {
        Instant timestamp = mParsedMessages.get(6).getHeader().getTimestamp();
        assertEquals("08-11 21:15:35.754", formatTimestamp(timestamp));
    }

    /** Check the message field. */
    public void testMessage() {
        assertEquals(mParsedMessages.get(2).getMessage(), MESSAGES[5]);
    }

    @SuppressWarnings("NewMethodNamingConvention")
    public void testTid() {
        assertEquals(0x1ef, mParsedMessages.get(0).getHeader().getTid());
        assertEquals(234, mParsedMessages.get(1).getHeader().getTid());
    }

    public void testTimeAsDate() {
        LogCatHeader header = mParsedMessages.get(0).getHeader();
        // Test date against "08-11 19:11:07.132"
        assertEquals("08-11 19:11:07.132", formatTimestamp(header.getTimestamp()));
    }

    public void testPackageName() {
        assertEquals("com.example.name", mParsedMessages.get(0).getHeader().getAppName());
        assertEquals("?", mParsedMessages.get(6).getHeader().getAppName());
    }

    public void testLinesWithoutHeadersAreIgnored() {
        String[] TRUNCATED_LOGS =
                new String[] {
                    "Log[0] logline2", //$NON-NLS-1$
                    "Log[0] logline3", //$NON-NLS-1$
                    "", //$NON-NLS-1$
                    "[ 08-11 19:11:07.132   495:0x1ef D/dtag     ]", //$NON-NLS-1$
                    "Log[1] logline1", //$NON-NLS-1$
                    "Log[1] logline2", //$NON-NLS-1$
                    "Log[1] logline3", //$NON-NLS-1$
                };

        LogCatMessageParser parser = new LogCatMessageParser();
        mParsedMessages = parser.processLogLines(TRUNCATED_LOGS, null);
        assertEquals(3, mParsedMessages.size());
        assertEquals("Log[1] logline1", mParsedMessages.get(0).getMessage());
    }

    @NonNull
    private static String formatTimestamp(@NonNull Instant timestamp) {
        return DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(timestamp, ZONE_ID));
    }
}
