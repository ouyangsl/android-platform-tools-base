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
package com.android.ide.common.resources;

import static org.junit.Assert.assertArrayEquals;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import java.util.stream.Stream;
import junit.framework.TestCase;

public class LocaleTest extends TestCase {
    public void test1() {
        Locale locale = Locale.create("en-rUS");
        assertEquals("en", locale.qualifier.getLanguage());
        assertEquals("US", locale.qualifier.getRegion());
        assertTrue(locale.hasLanguage());
        assertTrue(locale.hasRegion());
    }

    public void test2() {
        Locale locale = Locale.create("zh");
        assertEquals("zh", locale.qualifier.getLanguage());
        assertNull(locale.qualifier.getRegion());
        assertTrue(locale.hasLanguage());
        assertFalse(locale.hasRegion());
    }

    public void testEquals() {
        Locale locale = Locale.create("zh");
        assertEquals("zh", locale.qualifier.getLanguage());
        assertNull(locale.qualifier.getRegion());
        assertTrue(locale.hasLanguage());
        assertFalse(locale.hasRegion());
    }

    public void test() {
        LocaleQualifier qualifier1 = LocaleQualifier.getQualifier("nb");
        LocaleQualifier qualifier2 = LocaleQualifier.getQualifier("no");
        LocaleQualifier qualifier3 = LocaleQualifier.getQualifier("nb-rNO");
        LocaleQualifier qualifier4 = LocaleQualifier.getQualifier("nb-rSE");
        LocaleQualifier qualifier5 = LocaleQualifier.getQualifier("no-rSE");
        assertNotNull(qualifier1);
        assertNotNull(qualifier2);
        assertNotNull(qualifier3);
        assertNotNull(qualifier4);
        assertNotNull(qualifier5);

        assertEquals(Locale.ANY, Locale.ANY);
        assertFalse(Locale.ANY.hasLanguage());
        assertFalse(Locale.ANY.hasRegion());
        //noinspection ConstantConditions
        assertFalse(Locale.create(new LocaleQualifier(LocaleQualifier.FAKE_VALUE)).hasLanguage());
        //noinspection ConstantConditions
        assertFalse(Locale.create(new LocaleQualifier(LocaleQualifier.FAKE_VALUE)).hasRegion());

        assertEquals(Locale.create(qualifier1), Locale.create(qualifier1));
        assertTrue(Locale.create(qualifier1).hasLanguage());
        assertFalse(Locale.create(qualifier1).hasRegion());
        assertTrue(Locale.create(qualifier3).hasLanguage());
        assertTrue(Locale.create(qualifier3).hasRegion());

        assertEquals(Locale.create(qualifier3), Locale.create(qualifier3));
        assertEquals(Locale.create(qualifier1), Locale.create(qualifier1));
        assertTrue(Locale.create(qualifier1).equals(Locale.create(qualifier1)));
        assertTrue(Locale.create(qualifier3).equals(Locale.create(qualifier3)));
        assertFalse(Locale.create(qualifier3).equals(Locale.create(qualifier4)));
        assertFalse(Locale.create(qualifier1).equals(Locale.create(qualifier3)));
        assertFalse(Locale.create(qualifier1).equals(Locale.create(qualifier2)));
        assertFalse(Locale.create(qualifier3).equals(Locale.create(qualifier5)));
        assertEquals("nb", Locale.create(qualifier1).toString());
        assertEquals("nb-NO", Locale.create(qualifier3).toString());

        assertEquals(Locale.create(qualifier1), Locale.create("b+nb"));
        assertEquals(Locale.create(qualifier3), Locale.create("b+nb+NO"));

        Locale defaultLocale = Locale.create(new LocaleQualifier());
        assertFalse(defaultLocale.hasLanguage());
        assertNull(defaultLocale.qualifier.getLanguage());
    }

    public void testFolderConfig() {
        FolderConfiguration config = new FolderConfiguration();
        assertEquals(Locale.ANY, Locale.create(config));
        config.setLocaleQualifier(LocaleQualifier.getQualifier("en"));
        assertEquals(Locale.create("en"), Locale.create(config));
        config.setLocaleQualifier(LocaleQualifier.getQualifier("en-rUS"));
        assertEquals(Locale.create("en-rUS"), Locale.create(config));
    }

    public void testLanguageNameComparator() {
        assertArrayEquals(
                Stream.of(
                                "",
                                "zh",
                                "b+zh+Hant",
                                "zh-rCN",
                                "hr",
                                "en",
                                "en-rGB",
                                "b+sr+Cyrl+RS",
                                "b+sr+Latn+RS")
                        .map(Locale::create)
                        .toArray(),
                Stream.of(
                                "b+en+GB",
                                "b+sr+Latn+RS",
                                "",
                                "en",
                                "zh-rCN",
                                "b+zh+Hant",
                                "hr",
                                "zh",
                                "b+sr+Cyrl+RS")
                        .map(Locale::create)
                        .sorted(Locale.LANGUAGE_NAME_COMPARATOR)
                        .toArray());
    }

    public void testLanguageCodeComparator() {
        assertArrayEquals(
                Stream.of("", "en", "en-rGB", "hr", "zh", "zh-rCN", "zh-rTW")
                        .map(Locale::create)
                        .toArray(),
                Stream.of("b+en+GB", "", "en", "zh-rTW", "zh-rCN", "hr", "zh")
                        .map(Locale::create)
                        .sorted(Locale.LANGUAGE_CODE_COMPARATOR)
                        .toArray());
    }

    public void testLocaleShortLabel() {
        assertEquals("Serbian (sr)", Locale.getLocaleLabel(Locale.create("b+sr"), true));
        assertEquals("Serbian (sr / RS)", Locale.getLocaleLabel(Locale.create("sr-rRS"), true));
        assertEquals("Serbian (sr / RS)", Locale.getLocaleLabel(Locale.create("b+sr+RS"), true));
        assertEquals(
                "Serbian (sr) [Latn]", Locale.getLocaleLabel(Locale.create("b+sr+Latn"), true));
        assertEquals(
                "Serbian (sr / RS) [Latn]",
                Locale.getLocaleLabel(Locale.create("b+sr+Latn+RS"), true));
        assertEquals("Spanish (es / 419)", Locale.getLocaleLabel(Locale.create("b+es+419"), true));
    }

    public void testLocaleLongLabel() {
        assertEquals("Serbian (sr)", Locale.getLocaleLabel(Locale.create("b+sr"), false));
        assertEquals(
                "Serbian (sr) in Serbia (RS)",
                Locale.getLocaleLabel(Locale.create("sr-rRS"), false));
        assertEquals(
                "Serbian (sr) in Serbia (RS)",
                Locale.getLocaleLabel(Locale.create("b+sr+RS"), false));
        assertEquals(
                "Serbian (sr) [Latn]", Locale.getLocaleLabel(Locale.create("b+sr+Latn"), false));
        assertEquals(
                "Serbian (sr) in Serbia (RS) [Latn]",
                Locale.getLocaleLabel(Locale.create("b+sr+Latn+RS"), false));
        assertEquals(
                "Spanish (es) in Latin America and the Caribbean (419)",
                Locale.getLocaleLabel(Locale.create("b+es+419"), false));
    }
}
