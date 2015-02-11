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
package com.android.tools.rpclib.schema;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.rpc.AtomInfo;
import com.android.tools.rpclib.rpc.AtomStream;
import com.android.tools.rpclib.rpc.ParameterInfo;
import com.android.tools.rpclib.rpc.Schema;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A random-access reader of {@link Atom}s.
 * </p>
 * The {@link AtomReader} holds the entire collection of atoms in binary
 * packed form, and to reduce memory overhead, only unpacks these to Java
 * structures on {@link #read}.
 */
public class AtomReader {
  private final Schema schema;
  private final byte[] data;
  private final List<Segment> atomSegments;
  private final java.util.Map<Integer, Integer> atomTypeToIndex;

  public AtomReader(AtomStream stream, Schema schema) throws IOException {
    short[] data = stream.getData();
    this.schema = schema;
    this.data = new byte[data.length];
    this.atomSegments = new ArrayList<Segment>();

    for (int i = 0; i < data.length; i++) {
      this.data[i] = (byte)data[i];
    }
    calculateAtomInfos();

    atomTypeToIndex = new HashMap<Integer, Integer>(schema.getAtoms().length);
    AtomInfo[] atomInfos = schema.getAtoms();
    for (int i = 0; i < atomInfos.length; ++i) {
      Integer previous = atomTypeToIndex.put(atomInfos[i].getType(), i);
      assert (previous == null); // Make sure there are no duplicates.
    }
  }

  /**
   * @return the number of atoms in the collection.
   */
  public int count() {
    return atomSegments.size();
  }

  /**
   * Unpack and return a single atom with the specified index.
   *
   * @param index the index of the atom.
   * @return the unpacked atom structure.
   */
  public Atom read(long index) throws IOException {
    assert (index <= Integer.MAX_VALUE);
    Segment segment = atomSegments.get((int)index);
    ByteArrayInputStream stream = new ByteArrayInputStream(data, segment.offset, segment.size);
    Decoder decoder = new Decoder(stream);
    int type = decoder.uint16();
    int contextId = decoder.int32();
    assert (contextId >= 0); // Sanity check.
    Integer infoIndex = atomTypeToIndex.get(type);
    if (infoIndex == null) {
      throw new RuntimeException("Atom type " + type + "not found in schema.");
    }

    AtomInfo atomInfo = schema.getAtoms()[infoIndex];

    Parameter[] parameters = new Parameter[atomInfo.getParameters().length];
    for (int i = 0; i < parameters.length; i++) {
      ParameterInfo parameterInfo = atomInfo.getParameters()[i];
      Object value = Unpack.Type(atomInfo.getParameters()[i].getType(), decoder);
      parameters[i] = new Parameter(parameterInfo, value);
    }

    return new Atom(contextId, atomInfo, parameters);
  }

  private void calculateAtomInfos() throws IOException {
    ByteArrayInputStream stream = new ByteArrayInputStream(data);
    Decoder decoder = new Decoder(stream);
    int offset = 0;
    while (true) {
      int size = decoder.uint16();
      if (size > 0) {
        offset += 2; // Skip size
        atomSegments.add(new Segment(offset, size));
        size -= 2; // Skip size
        stream.skip(size);
        offset += size;
      }
      else {
        return;
      }
    }
  }

  private static class Segment {
    private final int offset;
    private final int size;

    private Segment(int offset, int size) {
      this.offset = offset;
      this.size = size;
    }
  }
}
