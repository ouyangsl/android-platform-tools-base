/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.perflib.heap.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MemoryMappedFileBuffer implements HprofBuffer {

    // Default chunk size is 1 << 30, or 1,073,741,824 bytes.
    private static final int DEFAULT_WIDTH = 30;

    // Eliminate wrapped, multi-byte reads across chunks in most cases.
    private static final int DEFAULT_PADDING = 1024;

    private final int mBufferWidth;

    private final int mBufferSize;

    private final int mPadding;

    private final ByteBuffer[] mByteBuffers;

    private final long mLength;

    private long mCurrentPosition;

    public MemoryMappedFileBuffer(File f, int width, int padding) throws IOException {
        mBufferWidth = width;
        mBufferSize = 1 << width;
        mPadding = padding;
        mLength = f.length();
        int shards = (int) (mLength >> mBufferWidth) + 1;
        mByteBuffers = new ByteBuffer[shards];

        FileInputStream inputStream = new FileInputStream(f);
        try {
            long offset = 0;
            for (int i = 0; i < shards; i++) {
                long size = Math.min(mLength - offset, mBufferSize + mPadding);
                mByteBuffers[i] = inputStream.getChannel()
                        .map(FileChannel.MapMode.READ_ONLY, offset, size);
                mByteBuffers[i].order(ByteOrder.BIG_ENDIAN);
                offset += mBufferSize;
            }
            mCurrentPosition = 0;
        } finally {
            inputStream.close();
        }
    }

    public MemoryMappedFileBuffer(File f) throws IOException {
        this(f, DEFAULT_WIDTH, DEFAULT_PADDING);
    }

    @Override
    public byte readByte() {
        byte result = mByteBuffers[getIndex()].get(getOffset());
        mCurrentPosition++;
        return result;
    }

    @Override
    public void read(byte[] b) {
        int index = getIndex();
        mByteBuffers[index].position(getOffset());
        if (b.length <= mByteBuffers[index].remaining()) {
            mByteBuffers[index].get(b, 0, b.length);
        } else {
            // Wrapped read
            int split = mBufferSize - mByteBuffers[index].position();
            mByteBuffers[index].get(b, 0, split);
            mByteBuffers[index + 1].position(0);
            mByteBuffers[index + 1].get(b, split, b.length - split);
        }
        mCurrentPosition += b.length;
    }

    @Override
    public char readChar() {
        char result = mByteBuffers[getIndex()].getChar(getOffset());
        mCurrentPosition += 2;
        return result;
    }

    @Override
    public short readShort() {
        short result = mByteBuffers[getIndex()].getShort(getOffset());
        mCurrentPosition += 2;
        return result;
    }

    @Override
    public int readInt() {
        int result = mByteBuffers[getIndex()].getInt(getOffset());
        mCurrentPosition += 4;
        return result;
    }

    @Override
    public long readLong() {
        long result = mByteBuffers[getIndex()].getLong(getOffset());
        mCurrentPosition += 8;
        return result;
    }

    @Override
    public float readFloat() {
        float result = mByteBuffers[getIndex()].getFloat(getOffset());
        mCurrentPosition += 4;
        return result;
    }

    @Override
    public double readDouble() {
        double result = mByteBuffers[getIndex()].getDouble(getOffset());
        mCurrentPosition += 8;
        return result;
    }

    @Override
    public void setPosition(long position) {
        mCurrentPosition = position;
    }

    @Override
    public long position() {
        return mCurrentPosition;
    }

    @Override
    public boolean hasRemaining() {
        return mCurrentPosition < mLength;
    }

    @Override
    public long remaining() {
        return mLength - mCurrentPosition;
    }

    private int getIndex() {
        return (int) (mCurrentPosition >> mBufferWidth);
    }

    private int getOffset() {
        // mCurrentPosition % BUFFER_SIZE
        return (int) (mCurrentPosition & (mBufferSize - 1));
    }
}
