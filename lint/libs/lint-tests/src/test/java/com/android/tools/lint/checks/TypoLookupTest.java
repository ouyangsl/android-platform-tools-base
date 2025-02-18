/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;
import com.android.tools.lint.checks.infrastructure.TestLintClient;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Detector;

import com.google.common.base.Splitter;

import kotlin.text.Charsets;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("javadoc")
public class TypoLookupTest extends AbstractCheckTest {
    private static final String SEPARATOR = "->";

    public void testCapitalization() {
        LintClient client = createClient();
        // Make sure it can be read in
        TypoLookup db = TypoLookup.get(client, "de", null);
        assertNotNull(db);
        assertNotNull(db.getTypos("Andriod".getBytes(Charsets.UTF_8), 0, "Andriod".length()));
    }

    public void testDictionary_English() throws Exception {
        validateDictionary("en");
    }

    public void testDictionary_German() throws Exception {
        validateDictionary("de");
    }

    public void testDictionary_Spanish() throws Exception {
        validateDictionary("es");
    }

    public void testDictionary_Hungarian() throws Exception {
        validateDictionary("hu");
    }

    public void testDictionary_Italian() throws Exception {
        validateDictionary("it");
    }

    public void testDictionary_Norwegian() throws Exception {
        validateDictionary("nb");
    }

    public void testDictionary_Portuguese() throws Exception {
        validateDictionary("pt");
    }

    public void testDictionary_Turkish() throws Exception {
        validateDictionary("tr");
    }

    public void test1() {
        TypoLookup db = TypoLookup.get(createClient(), "en", null);
        assertNull(db.getTypos("hello", 0, "hello".length()));
        assertNull(db.getTypos("this", 0, "this".length()));

        assertNotNull(db.getTypos("wiht", 0, "wiht".length()));
        assertNotNull(db.getTypos("woudl", 0, "woudl".length()));
        assertEquals("would", db.getTypos("woudl", 0, "woudl".length()).get(1));
        assertEquals("would", db.getTypos("  woudl  ", 2, 7).get(1));
        assertNotNull(db.getTypos("foo wiht bar", 4, 8));

        List<String> typos = db.getTypos("throught", 0, "throught".length());
        assertEquals("throught", typos.get(0)); // the typo
        assertEquals("thought", typos.get(1));
        assertEquals("through", typos.get(2));
        assertEquals("throughout", typos.get(3));

        // Capitalization handling
        assertNotNull(db.getTypos("Woudl", 0, "Woudl".length()));
        assertNotNull(db.getTypos("Enlish", 0, "Enlish".length()));
        assertNull(db.getTypos("enlish", 0, "enlish".length()));
        assertNull(db.getTypos("enlish".getBytes(Charsets.UTF_8), 0, "enlish".length()));
        assertNotNull(db.getTypos("ok", 0, "ok".length()));
        assertNotNull(db.getTypos("Ok", 0, "Ok".length()));
        assertNull(db.getTypos("OK", 0, "OK".length()));
    }

    public void testRegion() {
        TypoLookup db = TypoLookup.get(createClient(), "en", "US");
        assertNotNull(db);
        assertNotNull(db.getTypos("wiht", 0, "wiht".length()));
        db = TypoLookup.get(createClient(), "en", "GB");
        assertNotNull(db.getTypos("wiht", 0, "wiht".length()));
    }

    public void test2() {
        TypoLookup db = TypoLookup.get(createClient(), "nb", null);
        assertNotNull(db);
        assertNull(db.getTypos("hello", 0, "hello".length()));
        assertNull(db.getTypos("this", 0, "this".length()));

        assertNotNull(db.getTypos("altid", 0, "altid".length()));
        assertEquals("alltid", db.getTypos("altid", 0, "altid".length()).get(1));
        assertEquals("alltid", db.getTypos("  altid  ", 2, 7).get(1));
        assertNotNull(db.getTypos("foo altid bar", 4, 9));

        // Test utf-8 string which isn't ASCII
        String s = "karriære";
        byte[] sb = s.getBytes(Charsets.UTF_8);
        assertNotNull(db.getTypos(sb, 0, sb.length));

        assertEquals("karrière", db.getTypos(sb, 0, sb.length).get(1));
    }

    public void testMultiWords() {
        // Some language dictionaries contain multi-word sequences (e.g. where there's a
        // space on the left hand side). This needs some particular care in the lookup
        // which is usually word oriented.
        TypoLookup db = TypoLookup.get(createClient(), "de", "DE");

        // all zu->allzu

        // Text handling
        String t = "all zu";
        assertNotNull(db.getTypos(t, 0, t.length()));
        assertEquals("allzu", db.getTypos(t, 0, t.length()).get(1));

        // Byte handling
        byte[] text = "all zu".getBytes(Charsets.UTF_8);
        assertNotNull(db.getTypos(text, 0, text.length));
        assertEquals("allzu", db.getTypos(text, 0, text.length).get(1));

        // Test automatically extending search beyond current word
        text = "all zu".getBytes(Charsets.UTF_8);
        assertNotNull(db.getTypos(text, 0, 3));
        assertEquals("allzu", db.getTypos(text, 0, text.length).get(1));

        text = ") all zu (".getBytes(Charsets.UTF_8);
        assertNotNull(db.getTypos(text, 2, 8));
        assertEquals("allzu", db.getTypos(text, 2, 8).get(1));

        text = "am einem".getBytes(Charsets.UTF_8);
        assertNotNull(db.getTypos(text, 0, text.length));
        assertEquals("an einem", db.getTypos(text, 0, text.length).get(1));
    }

    public void testGlobbing() {
        TypoLookup db = TypoLookup.get(createClient(), "de", null);

        // Authorisierung*->Autorisierung*
        String text = "Authorisierungscode";
        byte[] bytes = text.getBytes(Charsets.UTF_8);

        assertNotNull(db.getTypos(text, 0, text.length()));
        assertEquals("Autorisierungscode", db.getTypos(text, 0, text.length()).get(1));
        assertEquals(text, db.getTypos(text, 0, text.length()).get(0));

        assertNotNull(db.getTypos(bytes, 0, bytes.length));
        assertEquals("Autorisierungscode", db.getTypos(bytes, 0, bytes.length).get(1));

        // befindet ein*->befindet sich ein*
        text = "wo befindet eine ip";
        assertEquals("befindet sich eine", db.getTypos(text, 3, 16).get(1));

        // zurück ge*->zurückge*
        text = "zurück gefoobaren";
        bytes = text.getBytes(Charsets.UTF_8);
        assertNotNull(db.getTypos(bytes, 0, bytes.length));
        assertEquals("zurückgefoobaren", db.getTypos(bytes, 0, bytes.length).get(1));
    }

    public void testComparisons() throws Exception {
        // Ensure that the two comparison methods agree

        LintClient client = createClient();
        for (String locale : new String[] {"de", "nb", "es", "en", "pt", "hu", "it", "tr"}) {
            String filename = String.format("/typos/typos-%1$s.txt", locale);
            List<String> lines = readFile(filename);

            Set<String> typos = new HashSet<>(2000);
            for (String line : lines) {
                if (line.isEmpty() || line.trim().startsWith("#")) {
                    continue;
                }

                int index = line.indexOf(SEPARATOR);
                if (index == -1) {
                    continue;
                }
                String typo = line.substring(0, index).trim();
                typos.add(typo);
            }

            List<String> words = new ArrayList<>(typos);

            // Make sure that the two comparison methods agree on all the strings
            // (which should be in a semi-random order now that they're in a set ordered
            // by their hash codes)

            String prevText = words.get(0) + '\000';
            byte[] prevBytes = prevText.getBytes(Charsets.UTF_8);

            for (int i = 1; i < words.size(); i++) {
                String text = words.get(i) + '\000';
                byte[] bytes = text.getBytes(Charsets.UTF_8);

                int textCompare =
                        TypoLookup.compare(prevBytes, 0, (byte) 0, text, 0, text.length());
                int byteCompare =
                        TypoLookup.compare(prevBytes, 0, (byte) 0, bytes, 0, bytes.length);
                assertEquals(
                        "Word " + text + " versus prev " + prevText + " at " + i,
                        Math.signum(textCompare),
                        Math.signum(byteCompare));
            }
        }
    }

    public void testComparison1() {
        String prevText = "heraus gebracht\u0000";
        byte[] prevBytes = prevText.getBytes(Charsets.UTF_8);

        String text = "Päsident\u0000";
        byte[] bytes = text.getBytes(Charsets.UTF_8);

        int textCompare = TypoLookup.compare(prevBytes, 0, (byte) 0, text, 0, text.length());
        int byteCompare = TypoLookup.compare(prevBytes, 0, (byte) 0, bytes, 0, bytes.length);
        assertTrue(byteCompare < 0);
        assertTrue(textCompare < 0);
        assertEquals(
                "Word " + text + " versus prev " + prevText,
                Math.signum(textCompare),
                Math.signum(byteCompare));
    }

    public void testComparison2() {
        String prevText = "intepretation\u0000";
        byte[] prevBytes = prevText.getBytes(Charsets.UTF_8);

        String text = "Woudl\u0000";
        byte[] bytes = text.getBytes(Charsets.UTF_8);

        int textCompare = TypoLookup.compare(prevBytes, 0, (byte) 0, text, 0, text.length());
        int byteCompare = TypoLookup.compare(prevBytes, 0, (byte) 0, bytes, 0, bytes.length);
        assertTrue(byteCompare < 0);
        assertTrue(textCompare < 0);
        assertEquals(
                "Word " + text + " versus prev " + prevText,
                Math.signum(textCompare),
                Math.signum(byteCompare));

        // Reverse capitalization and ensure that it's still the same
        prevText = "Intepretation\u0000";
        prevBytes = prevText.getBytes(Charsets.UTF_8);

        text = "woudl\u0000";
        bytes = text.getBytes(Charsets.UTF_8);

        textCompare = TypoLookup.compare(prevBytes, 0, (byte) 0, text, 0, text.length());
        byteCompare = TypoLookup.compare(prevBytes, 0, (byte) 0, bytes, 0, bytes.length);
        assertTrue(byteCompare < 0);
        assertTrue(textCompare < 0);
        assertEquals(
                "Word " + text + " versus prev " + prevText,
                Math.signum(textCompare),
                Math.signum(byteCompare));
    }

    // Some dictionaries contain actual sentences regarding usage; these must be stripped out.
    // They're just hardcoded here as we find them
    private static final String[] sRemove =
            new String[] {
                "- besser ganz darauf verzichten",
                "oft fälschlich für \"angekündigt\"",
                "hinausgehende* − insb. „darüber hinausgehende“",
                " - besser ganz darauf verzichten",
                "svw. bzw. so viel wie bzw. sprachverwandt"
            };

    @Override
    protected TestLintClient createClient() {
        return new ToolsBaseTestLintClient();
    }

    private void validateDictionary(String locale) throws Exception {
        // Check that all the typo files are well formed
        LintClient client = createClient();
        String filename = String.format("/typos/typos-%1$s.txt", locale);
        List<String> lines = readFile(filename);

        Set<String> typos = new HashSet<>(2000);
        List<Pattern> patterns = new ArrayList<>(100);

        for (int i = 0, n = lines.size(); i < n; i++) {
            String line = lines.get(i);
            if (line.isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            assertTrue(
                    msg(filename, i, "Line should contain '->': %1$s", line),
                    line.contains(SEPARATOR));
            int index = line.indexOf(SEPARATOR);
            String typo = line.substring(0, index).trim();
            String replacements = line.substring(index + SEPARATOR.length()).trim();

            if (typo.contains("*") && !typo.endsWith("*")) {
                fixDictionary(filename);
                fail(
                        msg(
                                filename,
                                i,
                                "Globbing (*) not supported anywhere but at the tail: %1$s",
                                line));
            } else if (typo.contains("*") && !replacements.contains("*")) {
                fail(msg(filename, i, "No glob found in the replacements for %1$s", line));
            }

            if (replacements.indexOf(',') != -1) {
                Set<String> seen = new HashSet<>();
                for (String s : Splitter.on(',').omitEmptyStrings().split(replacements)) {
                    if (seen.contains(s)) {
                        seen.add(s);
                        fixDictionary(filename);
                        fail(
                                msg(
                                        filename,
                                        i,
                                        "For typo "
                                                + typo
                                                + " there are repeated replacements ("
                                                + s
                                                + "): "
                                                + line));
                    }
                }
            }

            assertTrue(msg(filename, i, "Typo entry was empty: %1$s", line), !typo.isEmpty());
            assertTrue(
                    msg(filename, i, "Typo replacements was empty: %1$s", line),
                    !replacements.isEmpty());

            for (String remove : sRemove) {
                if (replacements.contains(remove)) {
                    fail(
                            msg(
                                    filename,
                                    i,
                                    "Replacements for typo %1$s contain description: %2$s",
                                    typo,
                                    replacements));
                }
            }
            if (typo.equals("sólo") && locale.equals("es")) {
                // sólo->solo
                // This seems to trigger a lot of false positives
                fail(
                        msg(
                                filename,
                                i,
                                "Typo %1$s triggers a lot of false positives, should be omitted",
                                typo));
            }
            if (locale.equals("tr") && (typo.equals("hiç bir") || typo.equals("öğe"))) {
                // hiç bir->hiçbir
                // öğe->öge
                // According to a couple of native speakers these are not necessarily
                // typos
                fail(
                        msg(
                                filename,
                                i,
                                "Typo %1$s triggers a lot of false positives, should be omitted",
                                typo));
            }

            if (typo.contains("*")) {
                patterns.add(Pattern.compile(typo.replace("*", ".*")));
            } else if (!patterns.isEmpty()) {
                for (Pattern pattern : patterns) {
                    if (pattern.matcher(typo).matches()) {
                        fixDictionary(filename);
                        fail(
                                msg(
                                        filename,
                                        i,
                                        "The typo " + typo + " matches an earlier glob: ignoring"));
                        continue;
                    }
                }
            }

            if (typos.contains(typo)) {
                fixDictionary(filename);
                fail(msg(filename, i, "Typo appeared more than once on lhs: %1$s", typo));
            }
            typos.add(typo);
        }

        // Make sure it can be read in
        TypoLookup db = TypoLookup.get(client, locale, null);
        assertNotNull(db);
        assertNull(db.getTypos("abcdefghijklmnopqrstuvxyz", 0, 25));
        assertNull(db.getTypos("abcdefghijklmnopqrstuvxyz".getBytes(Charsets.UTF_8), 0, 25));
        assertNotNull(db.getTypos("Andriod", 0, "Andriod".length()));
        assertNotNull(db.getTypos("Andriod".getBytes(Charsets.UTF_8), 0, "Andriod".length()));
    }

    @NonNull
    private static List<String> readFile(String filename) {
        InputStream typoStream = TypoLookup.class.getResourceAsStream(filename);
        List<String> lines =
                new BufferedReader(new InputStreamReader(typoStream, Charsets.UTF_8))
                        .lines()
                        .collect(Collectors.toList());
        return lines;
    }

    private static void fixDictionary(String original) throws Exception {
        File fixed = new File(TestUtils.getTestOutputDir().toFile(), "fixed-" + original);

        Map<String, Integer> typos = new HashMap<>(2000);
        List<Pattern> patterns = new ArrayList<>(100);
        List<String> lines = readFile(original);
        List<String> output = new ArrayList<>(lines.size());

        wordLoop:
        for (String line : lines) {
            if (line.isEmpty() || line.trim().startsWith("#")) {
                output.add(line);
                continue;
            }

            if (!line.contains(SEPARATOR)) {
                System.err.println("Commented out line missing ->: " + line);
                output.add("# " + line);
                continue;
            }
            int index = line.indexOf(SEPARATOR);
            String typo = line.substring(0, index).trim();
            String replacements = line.substring(index + SEPARATOR.length()).trim();

            if (typo.isEmpty()) {
                System.err.println("Commented out line missing a typo on the lhs: " + line);
                output.add("# " + line);
                continue;
            }
            if (replacements.isEmpty()) {
                System.err.println("Commented out line missing replacements on the rhs: " + line);
                output.add("# " + line);
                continue;
            }

            // Ensure that all the replacements are unique
            if (replacements.indexOf(',') != -1) {
                Set<String> seen = new HashSet<>();
                List<String> out = new ArrayList<>();
                boolean rewrite = false;
                for (String s : Splitter.on(',').omitEmptyStrings().split(replacements)) {
                    if (seen.contains(s)) {
                        System.err.println(
                                "For typo "
                                        + typo
                                        + " there are repeated replacements ("
                                        + s
                                        + "): "
                                        + line);
                        rewrite = true;
                    }
                    seen.add(s);
                    out.add(s);
                }
                if (rewrite) {
                    StringBuilder sb = new StringBuilder();
                    for (String s : out) {
                        if (sb.length() > 0) {
                            sb.append(",");
                        }
                        sb.append(s);
                    }
                    replacements = sb.toString();
                    line = typo + SEPARATOR + replacements;
                }
            }

            if (typo.contains("*")) {
                if (!typo.endsWith("*")) {
                    // Globbing not supported anywhere but the end
                    // Drop the whole word
                    System.err.println(
                            "Skipping typo "
                                    + typo
                                    + " because globbing is only supported at the end of the word");
                    continue;
                }
                patterns.add(Pattern.compile(typo.replace("*", ".*")));
            } else if (replacements.contains("*")) {
                System.err.println(
                        "Skipping typo "
                                + typo
                                + " because unexpected "
                                + "globbing character found in replacements: "
                                + replacements);
                continue;
            } else if (!patterns.isEmpty()) {
                for (Pattern pattern : patterns) {
                    if (pattern.matcher(typo).matches()) {
                        System.err.println(
                                "The typo " + typo + " matches an earlier glob: ignoring");
                        continue wordLoop;
                    }
                }
            }

            // TODO: Strip whitespace around ->, prefix of # etc such that reading in
            // the databases needs to do less work at runtime

            if (typos.containsKey(typo)) {
                int l = typos.get(typo);
                String prev = output.get(l);
                assertTrue(prev.startsWith(typo));
                // Append new replacements and put back into the list
                // (unless they're already listed as replacements)
                Set<String> seen = new HashSet<>();
                for (String s :
                        Splitter.on(',').split(prev.substring(prev.indexOf(SEPARATOR) + 2))) {
                    seen.add(s);
                }
                for (String s : Splitter.on(',').omitEmptyStrings().split(replacements)) {
                    if (!seen.contains(s)) {
                        prev = prev + "," + s;
                    }
                    seen.add(s);
                }
                output.set(l, prev);
            } else {
                typos.put(typo, output.size());
                output.add(line);
            }
        }

        Writer writer =
                new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(fixed), Charsets.UTF_8));
        for (String line : output) {
            writer.write(line);
            writer.write('\n');
        }
        writer.close();

        System.err.println("==> Wrote fixed typo file to " + fixed.getPath());
    }

    private static String msg(String file, int line, String message, Object... args) {
        return file + ':' + Integer.toString(line + 1) + ':' + ' ' + String.format(message, args);
    }

    @Override
    protected Detector getDetector() {
        fail("This is not used in the TypoLookupTest");
        return null;
    }
}
