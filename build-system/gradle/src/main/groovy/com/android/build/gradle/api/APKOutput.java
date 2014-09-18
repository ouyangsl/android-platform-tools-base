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

package com.android.build.gradle.api;

import com.android.annotations.NonNull;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Type;

/**
 * Represents a resource output from a variant configuration.
 *
 * Depending on split requirements, there can be more than one output from aapt tool and each
 * output file is represented by an instance of this class.
 */
public abstract class APKOutput {

    /**
     * Type of package file, either the main APK or a pure split APK file containing resources for
     * a particular split dimension.
     */
    public enum OutputType {
        MAIN, SPLIT
    }

    /**
     * Split dimension type
     */
    public enum SplitType {
        DENSITY, ABI, LANGUAGE
    }

    @NonNull
    protected final OutputType mOutputType;

    @NonNull
    protected final File mOutputFile;

    protected APKOutput(@NonNull OutputType outputType, @NonNull File outputFile) {
        mOutputFile = outputFile;
        mOutputType = outputType;
    }

    @NonNull
    public OutputType getType() {
        return mOutputType;
    }

    @NonNull
    public File getOutputFile() {
        return mOutputFile;
    }

    public static final class SplitAPKOutput extends APKOutput {
        @NonNull private final SplitType mSplitType;
        @NonNull private final String mSplitIdentifier;
        @NonNull private final String mSplitSuffix;

        public SplitAPKOutput(@NonNull OutputType outputType,
                @NonNull SplitType splitType,
                @NonNull String splitIdentifier,
                @NonNull String splitSuffix,
                @NonNull File outputFile) {
            super(outputType, outputFile);
            mSplitType = splitType;
            mSplitIdentifier = splitIdentifier;
            mSplitSuffix = splitSuffix;
        }

        /**
         * String identifying the split within its dimension. For instance, for a
         * {@link APKOutput.SplitType#DENSITY}, a split identifier
         * can be "xxhdpi".
         *
         * @return the split identifier (bounded by its split type).
         */
        @NonNull
        public String getSplitIdentifier() {
            return mSplitIdentifier;
        }

        /**
         * AAPT can generate filenames that have a suffix passed the root file name + dimension
         * identifier, this suffix is stored here to be able to identify it in the
         * @return the generated file suffix.
         */
        @NonNull
        public String getSplitSuffix() {
            return mSplitSuffix;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("OutputType", mOutputType)
                    .add("SplitType", mSplitType)
                    .add("SplitIdentifier", mSplitIdentifier)
                    .add("SplitSuffix", mSplitSuffix)
                    .add("File", mOutputFile.getAbsolutePath())
                    .toString();
        }

        /**
         * JSON deserializer for loading previously saved instances...
         */
        public static class JsonDeserializer implements com.google.gson.JsonDeserializer<SplitAPKOutput> {

            @Override
            public SplitAPKOutput deserialize(JsonElement jsonElement, Type type,
                    JsonDeserializationContext jsonDeserializationContext)
                    throws JsonParseException {
                return new SplitAPKOutput(
                        OutputType.valueOf(((JsonObject) jsonElement).get("mOutputType").getAsString()),
                        SplitType.valueOf(((JsonObject) jsonElement).get("mSplitType").getAsString()),
                        ((JsonObject) jsonElement).get("mSplitIdentifier").getAsString(),
                        ((JsonObject) jsonElement).get("mSplitSuffix").getAsString(),
                        new File(((JsonObject) jsonElement).getAsJsonObject(
                                "mOutputFile").get("path").getAsString()));
            }
        }
    }

    public static final class MainAPKOutput extends APKOutput {

        public MainAPKOutput(File outputFile) {
            super(OutputType.MAIN, outputFile);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("OutputType", mOutputFile)
                    .add("File", mOutputFile.getAbsolutePath())
                    .toString();
        }
    }

    public static ImmutableList<SplitAPKOutput> load(File inputFile)
            throws FileNotFoundException {

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(SplitAPKOutput.class,
                new SplitAPKOutput.JsonDeserializer());
        Gson gson = gsonBuilder.create();

        ImmutableList.Builder<SplitAPKOutput> builder = ImmutableList.builder();

        for (SplitAPKOutput vo : gson.fromJson(new FileReader(inputFile),
                SplitAPKOutput[].class)) {
            builder.add(vo);
        }
        return builder.build();
    }
}
