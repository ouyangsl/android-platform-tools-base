/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.flags;

import static com.google.common.truth.Truth.assertThat;

import com.android.flags.overrides.DefaultFlagOverrides;
import com.android.flags.overrides.PropertyOverrides;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;

public class FlagsTest {

    /** Enum for testing enum flags below. */
    private enum TestingEnum {
        FOO,
        BAR,
        BAZ,
        @SuppressWarnings({"NonAsciiCharacters", "UnicodeInCode"})
        DOTLESS_ı
    }

    @Test
    public void propertiesCanOverrideFlagValues() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("test.int", "123");
        properties.setProperty("test.bool", "true");
        properties.setProperty("test.str", "Property override");
        properties.setProperty("test.enum", "bar");

        PropertyOverrides propertyOverrides = new PropertyOverrides(properties);
        Flags flags = new Flags(propertyOverrides);
        FlagGroup group = new FlagGroup(flags, "test", "Test Group");

        Flag<Integer> flagInt = new IntFlag(group, "int", "Unused", "Unused", 10);
        Flag<Boolean> flagBool = new BooleanFlag(group, "bool", "Unused", "Unused", false);
        Flag<String> flagStr = new StringFlag(group, "str", "Unused", "Unused", "Default value");
        Flag<TestingEnum> flagEnum =
                new EnumFlag(group, "enum", "Unused", "Unused", TestingEnum.FOO);

        assertThat(flagInt.get()).isEqualTo(123);
        assertThat(flagBool.get()).isEqualTo(true);
        assertThat(flagStr.get()).isEqualTo("Property override");
        assertThat(flagEnum.get()).isEqualTo(TestingEnum.BAR);
    }

    @Test
    public void overrideFlagAndClearOverrideWorks() throws Exception {
        Flags flags = new Flags();

        FlagGroup group = new FlagGroup(flags, "test", "Test Group");

        Flag<Integer> flagInt = new IntFlag(group, "int", "Unused", "Unused", 10);
        Flag<Boolean> flagBool = new BooleanFlag(group, "bool", "Unused", "Unused", false);
        Flag<String> flagStr = new StringFlag(group, "str", "Unused", "Unused", "Default value");
        Flag<TestingEnum> flagEnum =
                new EnumFlag(group, "enum", "Unused", "Unused", TestingEnum.FOO);

        flags.getOverrides().put(flagInt, "456");
        flags.getOverrides().put(flagBool, "true");
        flags.getOverrides().put(flagStr, "Manual override");
        flags.getOverrides().put(flagEnum, "bar");

        assertThat(flagInt.get()).isEqualTo(456);
        assertThat(flagBool.get()).isEqualTo(true);
        assertThat(flagStr.get()).isEqualTo("Manual override");
        assertThat(flagEnum.get()).isEqualTo(TestingEnum.BAR);

        flags.getOverrides().remove(flagInt);
        flags.getOverrides().remove(flagBool);
        flags.getOverrides().remove(flagStr);
        flags.getOverrides().remove(flagEnum);

        assertThat(flagInt.get()).isEqualTo(10);
        assertThat(flagBool.get()).isEqualTo(false);
        assertThat(flagStr.get()).isEqualTo("Default value");
        assertThat(flagEnum.get()).isEqualTo(TestingEnum.FOO);
    }

    @Test
    public void mutableOverridesTakePrecedenceOverImmutableOverrides() throws Exception {
        Properties properties = new Properties();
        properties.put("test.str", "Property override");

        PropertyOverrides propertyOverrides = new PropertyOverrides(properties);
        Flags flags = new Flags(propertyOverrides);

        FlagGroup group = new FlagGroup(flags, "test", "Test Group");
        Flag<String> flagStr = new StringFlag(group, "str", "Unused", "Unused", "Default value");

        flags.getOverrides().put(flagStr, "Manual override");
        assertThat(flagStr.get()).isEqualTo("Manual override");

        flags.getOverrides().remove(flagStr);
        assertThat(flagStr.get()).isEqualTo("Property override");
    }

    @Test
    public void canSpecifyCustomUserOveriddes() throws Exception {
        DefaultFlagOverrides customMutableOverrides = new DefaultFlagOverrides();
        Flags flags = new Flags(customMutableOverrides);
        FlagGroup group = new FlagGroup(flags, "test", "Test Group");
        Flag<String> flagStr = new StringFlag(group, "str", "Unused", "Unused", "Default value");

        customMutableOverrides.put(flagStr, "Overridden value");

        assertThat(flagStr.get()).isEqualTo("Overridden value");

        customMutableOverrides.clear();

        assertThat(flagStr.get()).isEqualTo("Default value");
    }

    @Test
    public void flagsThrowsExceptionIfFlagsWithDuplicateIdsAreRegisetered() throws Exception {
        Flags flags = new Flags();
        FlagGroup group = new FlagGroup(flags, "test", "Test Group");
        Flag<String> flag1 = new StringFlag(group, "str1", "Unused", "Unused", "Str 1");
        Flag<String> flag2 = new StringFlag(group, "str2", "Unused", "Unused", "Str 2");

        try {
            // Oops. Copy/paste error...
            Flag<String> flag3 = new StringFlag(group, "str2", "Unused", "Unused", "Str 3");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void invalidOverrideValue() {
        Properties properties = new Properties();
        properties.setProperty("test.int", "madeup");
        properties.setProperty("test.enum", "madeup");

        PropertyOverrides propertyOverrides = new PropertyOverrides(properties);
        Flags flags = new Flags(propertyOverrides);
        FlagGroup group = new FlagGroup(flags, "test", "Test Group");

        Flag<Integer> flagInt = new IntFlag(group, "int", "Unused", "Unused", 10);
        Flag<TestingEnum> flagEnum =
                new EnumFlag(group, "enum", "Unused", "Unused", TestingEnum.FOO);

        assertThat(flagInt.get()).isEqualTo(10);
        assertThat(flagEnum.get()).isEqualTo(TestingEnum.FOO);
    }

    @Test
    public void validation() {
        Properties properties = new Properties();
        PropertyOverrides propertyOverrides = new PropertyOverrides(properties);
        Flags flags = new Flags(propertyOverrides);
        FlagGroup group = new FlagGroup(flags, "test", "Test Group");

        try {
            Flag<Integer> flag = new IntFlag(group, "Invalid Id", "", "", 10);
            flag.validate();
            Assert.fail("Expected validation Assert.failure");
        } catch (IllegalArgumentException error) {
            assertThat(error.getLocalizedMessage()).isEqualTo("Invalid id: test.Invalid Id");
        }

        try {
            Flag<Integer> flag = new IntFlag(group, "id", " wrong", "", 10);
            flag.validate();
            Assert.fail("Expected validation Assert.failure");
        } catch (IllegalArgumentException error) {
            assertThat(error.getLocalizedMessage()).isEqualTo("Invalid name:  wrong");
        }

        try {
            // Fail serialization: serialization is pretty locked down by typed serializers,
            // but for enums it's using capitalization which we can break
            @SuppressWarnings({"NonAsciiCharacters", "UnicodeInCode"})
            Flag<TestingEnum> flag = new EnumFlag(group, "id2", "", "", TestingEnum.DOTLESS_ı);
            flag.validate();
            Assert.fail("Expected validation Assert.failure");
        } catch (IllegalArgumentException error) {
            assertThat(error.getLocalizedMessage())
                    .isEqualTo("Default value cannot be deserialized.");
        }

        try {
            // We've already registered id above
            Flag<Integer> flag = new IntFlag(group, "id", "Id 2", "", 10);
            flag.validate();
            Assert.fail("Expected validation Assert.failure");
        } catch (IllegalArgumentException error) {
            assertThat(error.getLocalizedMessage())
                    .isEqualTo(
                            "Flag \"Id 2\" shares duplicate ID \"test.id\" with flag \" wrong\"");
        }
    }
}
