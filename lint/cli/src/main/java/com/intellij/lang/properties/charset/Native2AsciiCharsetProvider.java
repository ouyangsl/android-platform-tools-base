/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.lang.properties.charset;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.charset.spi.CharsetProvider;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// TEMPORARY COPY; referenced from intellij-core via
//   platform/boot/src/META-INF/services/java.nio.charset.spi.CharsetProvider
// but not included; should be.

/**
 * @author Alexey
 */
public class Native2AsciiCharsetProvider extends CharsetProvider {
    public Native2AsciiCharsetProvider() {
    }

    @Override
    public Charset charsetForName(String charsetName) {
        return Native2AsciiCharset.forName(charsetName);
    }

    @Override
    public Iterator<Charset> charsets() {
        return Collections.<Charset>emptyList().iterator();
    }

    public static class Native2AsciiCharset extends Charset {
        private static final String[] ALIASES = new String[0];
        private final Charset myBaseCharset;
        @SuppressWarnings({"HardCodedStringLiteral"}) private static final String NAME_PREFIX = "NATIVE_TO_ASCII_";
        @SuppressWarnings({"HardCodedStringLiteral"}) private static final String DEFAULT_ENCODING_NAME = "ISO-8859-1";

        private Native2AsciiCharset(String canonicalName) {
            super(canonicalName, ALIASES);
            String baseCharsetName = canonicalName.substring(NAME_PREFIX.length());
            Charset baseCharset = null;
            try {
                baseCharset = Charset.forName(baseCharsetName);
            }
            catch (IllegalCharsetNameException e) {
                //ignore
            }
            catch(UnsupportedCharsetException e){
                //ignore
            }
            myBaseCharset = baseCharset == null ? Charset.forName(DEFAULT_ENCODING_NAME) : baseCharset;
        }

        @Override
        public String displayName() {
            return getBaseCharset().displayName();
        }

        @Override
        public boolean contains(Charset cs) {
            return false;
        }

        @Override
        public CharsetDecoder newDecoder() {
            return new Native2AsciiCharsetDecoder(this);
        }

        @Override
        public CharsetEncoder newEncoder() {
            return new Native2AsciiCharsetEncoder(this);
        }

        public Charset getBaseCharset() {
            return myBaseCharset;
        }
        public static String makeNative2AsciiEncodingName(String baseCharsetName) {
            if (baseCharsetName == null) baseCharsetName = DEFAULT_ENCODING_NAME;
            return NAME_PREFIX + baseCharsetName;
        }

        public static Charset forName(String charsetName) {
            if (charsetName.startsWith(NAME_PREFIX)) {
                Native2AsciiCharset cached = cache.get(charsetName);
                if (cached == null) {
                    cached = new Native2AsciiCharset(charsetName);
                    Native2AsciiCharset prev = cache.putIfAbsent(charsetName, cached);
                    if (prev != null) cached = prev;
                }
                return cached;
            }
            return null;
        }
        public static Charset wrap(Charset baseCharset) {
            return forName(NAME_PREFIX + baseCharset.name());
        }

        public static Charset nativeToBaseCharset(Charset charset) {
            if (charset instanceof Native2AsciiCharset) {
                return ((Native2AsciiCharset)charset).getBaseCharset();
            }
            return charset;
        }

        private static final ConcurrentMap<String, Native2AsciiCharset>
                cache = new ConcurrentHashMap<String, Native2AsciiCharset>();
    }

    static class Native2AsciiCharsetDecoder extends CharsetDecoder {

        private static final char INVALID_CHAR = (char) -1;
        private StringBuilder myOutBuffer = new StringBuilder();
        private final Charset myBaseCharset;

        Native2AsciiCharsetDecoder(final Native2AsciiCharset charset) {
            super(charset, 1, 6);
            myBaseCharset = charset.getBaseCharset();
        }

        @Override
        protected void implReset() {
            super.implReset();
            myOutBuffer = new StringBuilder();
        }

        @Override
        protected CoderResult implFlush(CharBuffer out) {
            return doFlush(out);
        }

        private CoderResult doFlush(final CharBuffer out) {
            if (myOutBuffer.length() != 0) {
                int remaining = out.remaining();
                int outLen = Math.min(remaining, myOutBuffer.length());
                out.append(myOutBuffer, 0, outLen);
                myOutBuffer.delete(0, outLen);
                if (myOutBuffer.length() != 0)
                    return CoderResult.OVERFLOW;
            }
            return CoderResult.UNDERFLOW;
        }

        @Override
        protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
            try {
                CoderResult coderResult = doFlush(out);
                if (coderResult == CoderResult.OVERFLOW)
                    return CoderResult.OVERFLOW;

                int start = in.position();
                byte[] buf = new byte[4];
                while (in.position() < in.limit()) {
                    in.mark();
                    final byte b = in.get();
                    if (b == '\\') {
                        decodeArray(in.array(), start, in.position() - 1);
                        byte next = in.get();
                        if (next == 'u') {
                            buf[0] = in.get();
                            buf[1] = in.get();
                            buf[2] = in.get();
                            buf[3] = in.get();
                            char decoded = unicode(buf);
                            if (decoded == INVALID_CHAR) {
                                myOutBuffer.append("\\u");
                                myOutBuffer.append((char) buf[0]);
                                myOutBuffer.append((char) buf[1]);
                                myOutBuffer.append((char) buf[2]);
                                myOutBuffer.append((char) buf[3]);
                            } else {
                                myOutBuffer.append(decoded);
                            }
                        } else {
                            myOutBuffer.append("\\");
                            myOutBuffer.append((char) next);
                        }
                        start = in.position();
                    }
                }
                decodeArray(in.array(), start, in.position());
            } catch (BufferUnderflowException e) {
                in.reset();
            }
            return doFlush(out);
        }

        private void decodeArray(final byte[] buf, int start, int end) {
            if (end <= start)
                return;
            ByteBuffer byteBuffer = ByteBuffer.wrap(buf, start, end - start);
            CharBuffer charBuffer = myBaseCharset.decode(byteBuffer);
            myOutBuffer.append(charBuffer.toString());
        }

        private static char unicode(byte[] ord) {
            int d1 = Character.digit((char) ord[0], 16);
            if (d1 == -1)
                return INVALID_CHAR;
            int d2 = Character.digit((char) ord[1], 16);
            if (d2 == -1)
                return INVALID_CHAR;
            int d3 = Character.digit((char) ord[2], 16);
            if (d3 == -1)
                return INVALID_CHAR;
            int d4 = Character.digit((char) ord[3], 16);
            if (d4 == -1)
                return INVALID_CHAR;
            int b1 = (d1 << 12) & 0xF000;
            int b2 = (d2 << 8) & 0x0F00;
            int b3 = (d3 << 4) & 0x00F0;
            int b4 = (d4 << 0) & 0x000F;
            int code = b1 | b2 | b3 | b4;
            if (Character.isWhitespace((char) code))
                return INVALID_CHAR;
            return (char) code;
        }
    }

    static class Native2AsciiCharsetEncoder extends CharsetEncoder {

        @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
        private static final char ANCHOR = Boolean.getBoolean("idea.native2ascii.lowercase") ? 'a' : 'A';

        private final Charset myBaseCharset;

        public Native2AsciiCharsetEncoder(Native2AsciiCharset charset) {
            super(charset, 1, 6);
            myBaseCharset = charset.getBaseCharset();
        }

        @Override
        protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
            while (in.position() < in.limit()) {
                in.mark();
                try {
                    char c = in.get();
                    if (c < '\u0080') {
                        ByteBuffer byteBuffer = myBaseCharset.encode(Character.toString(c));
                        out.put(byteBuffer);
                    }
                    else {
                        if (out.remaining() < 6) throw new BufferOverflowException();
                        out.put((byte)'\\');
                        out.put((byte)'u');
                        out.put(toHexChar(c >> 12));
                        out.put(toHexChar((c >> 8) & 0xf));
                        out.put(toHexChar((c >> 4) & 0xf));
                        out.put(toHexChar(c & 0xf));
                    }
                }
                catch (BufferUnderflowException e) {
                    in.reset();
                }
                catch (BufferOverflowException e) {
                    in.reset();
                    return CoderResult.OVERFLOW;
                }
            }
            return CoderResult.UNDERFLOW;
        }

        private static byte toHexChar(int digit) {
            if (digit < 10) {
                return (byte)('0' + digit);
            }
            return (byte)(ANCHOR - 10 + digit);
        }
    }
}