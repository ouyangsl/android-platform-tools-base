/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.rpclib.binary;

import com.android.tools.rpclib.rpccore.ObjectFactory;
import com.android.tools.rpclib.rpccore.RpcError;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class EncoderTest extends TestCase {
  public void testEncodeBool() {
    final boolean[] input = new boolean[]{true, false};
    final byte[] expected = new byte[]{(byte)0x01, (byte)0x00};

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (boolean bool : input) {
        e.bool(bool);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeInt8() {
    final byte[] input = new byte[]{0, 127, -128, -1};
    final byte[] expected = new byte[]{(byte)0x00, (byte)0x7f, (byte)0x80, (byte)0xff};

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (byte s8 : input) {
        e.int8(s8);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeUint8() {
    final short[] input = new short[]{0x00, 0x7f, 0x80, 0xff};
    final byte[] expected = new byte[]{(byte)0x00, (byte)0x7f, (byte)0x80, (byte)0xff};

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (short u8 : input) {
        e.uint8(u8);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeInt16() {
    final short[] input = new short[]{0, 32767, -32768, -1};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00,
      (byte)0xff, (byte)0x7f,
      (byte)0x00, (byte)0x80,
      (byte)0xff, (byte)0xff
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (short s16 : input) {
        e.int16(s16);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeUint16() {
    final int[] input = new int[]{0, 0xbeef, 0xc0de};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00,
      (byte)0xef, (byte)0xbe,
      (byte)0xde, (byte)0xc0
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (int u16 : input) {
        e.uint16(u16);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeInt32() {
    final int[] input = new int[]{0, 2147483647, -2147483648, -1};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x7f,
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x80,
      (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (int s32 : input) {
        e.int32(s32);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeUint32() {
    final long[] input = new long[]{0, 0x01234567, 0x10abcdef};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x67, (byte)0x45, (byte)0x23, (byte)0x01,
      (byte)0xef, (byte)0xcd, (byte)0xab, (byte)0x10
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (long u32 : input) {
        e.uint32(u32);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeInt64() {
    final long[] input = new long[]{0L, 9223372036854775807L, -9223372036854775808L, -1L};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x7f,
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x80,
      (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (long s64 : input) {
        e.int64(s64);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeUint64() {
    final long[] input = new long[]{0L, 0x0123456789abcdefL, 0xfedcba9876543210L};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0xef, (byte)0xcd, (byte)0xab, (byte)0x89, (byte)0x67, (byte)0x45, (byte)0x23, (byte)0x01,
      (byte)0x10, (byte)0x32, (byte)0x54, (byte)0x76, (byte)0x98, (byte)0xba, (byte)0xdc, (byte)0xfe
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (long u64 : input) {
        e.uint64(u64);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeFloat32() {
    final float[] input = new float[]{0.F, 1.F, 64.5F};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x3f,
      (byte)0x00, (byte)0x00, (byte)0x81, (byte)0x42,
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (float f32 : input) {
        e.float32(f32);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeFloat64() {
    final double[] input = new double[]{0.D, 1.D, 64.5D};
    final byte[] expected = new byte[]{
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x3f,
      (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x20, (byte)0x50, (byte)0x40
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (double f64 : input) {
        e.float64(f64);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeString() {
    final String[] input = new String[]{"Hello", "", "World", "こんにちは世界"};
    final byte[] expected = new byte[]{
      0x05, 0x00, 0x00, 0x00, 'H', 'e', 'l', 'l', 'o',
      0x00, 0x00, 0x00, 0x00,
      0x05, 0x00, 0x00, 0x00, 'W', 'o', 'r', 'l', 'd',
      0x15, 0x00, 0x00, 0x00,
      (byte)0xe3, (byte)0x81, (byte)0x93, (byte)0xe3, (byte)0x82, (byte)0x93, (byte)0xe3,
      (byte)0x81, (byte)0xab, (byte)0xe3, (byte)0x81, (byte)0xa1, (byte)0xe3, (byte)0x81,
      (byte)0xaf, (byte)0xe4, (byte)0xb8, (byte)0x96, (byte)0xe7, (byte)0x95, (byte)0x8c
    };

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (String str : input) {
        e.string(str);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }

  public void testEncodeObject() {
    final RpcError rpcError = new RpcError();
    final BinaryObject[] input = new BinaryObject[]{null, rpcError, rpcError};
    byte[] expected = null;

    ByteArrayOutputStream expectedStream = new ByteArrayOutputStream();
    try {
      // null BinaryObject:
      expectedStream.write(new byte[]{(byte)0xff, (byte)0xff}); // BinaryObject.NULL_ID

      // rpcError BinaryObject:
      expectedStream.write(new byte[]{0x00, 0x00}); // rpcError reference
      expectedStream.write(ObjectFactory.RpcErrorIDBytes); // rpcError.type()
      expectedStream.write(new byte[]{0x00, 0x00, 0x00, 0x00}); // rpcError.mMessage.length

      // rpcError BinaryObject again, only by reference this time:
      expectedStream.write(new byte[]{0x00, 0x00}); // rpcError reference
      expected = expectedStream.toByteArray();
    }
    catch (IOException ex) {
      assertNull(ex);
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length);
    Encoder e = new Encoder(output);

    try {
      for (BinaryObject obj : input) {
        e.object(obj);
      }
    }
    catch (IOException ex) {
      assertNull(ex);
    }
    Assert.assertArrayEquals(expected, output.toByteArray());
  }
}
