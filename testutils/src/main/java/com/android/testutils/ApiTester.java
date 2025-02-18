/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.testutils;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.Parameter;
import com.google.common.reflect.TypeToken;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kotlin.jvm.functions.Function1;
import kotlin.text.StringsKt;
import org.junit.Assert;

/**
 * Given a set of classes, return the lines of an API file that lists the accessible APIs.
 *
 * <p>See `StableApiTest` for an example of usage.
 */
public final class ApiTester {

    public ApiTester(
            String titleLine,
            Collection<ClassPath.ClassInfo> classes,
            @NonNull Filter filter,
            String errorMessage,
            URL expectedFileUrl,
            Flag... flag) {
        this(titleLine, classes, filter, errorMessage, expectedFileUrl, content -> content, flag);
    }

    public ApiTester(
            String titleLine,
            Collection<ClassPath.ClassInfo> classes,
            @NonNull Filter filter,
            String errorMessage,
            URL expectedFileUrl,
            Function1<List<String>, Collection<String>> transformFinalFileContent,
            Flag... flag) {
        this.titleLine = titleLine;
        this.classes = classes;
        this.filter = filter;
        this.errorMessage = errorMessage;
        this.expectedFileUrl = expectedFileUrl;
        this.transformFinalFileContent = transformFinalFileContent;
        this.flags = ImmutableSet.copyOf(flag);
    }

    public enum Filter {
        ALL,
        STABLE_ONLY,
        INCUBATING_ONLY,
        DEPRECATED_ONLY,
        REPLACED_BY_INCUBATING_ONLY,
    }

    public enum Flag {
        ALLOW_PUBLIC_INSTANCE_FIELD,
        OMIT_HASH,
    }

    private final String titleLine;
    private final Collection<ClassPath.ClassInfo> classes;
    private final Filter filter;
    private final String errorMessage;
    private final URL expectedFileUrl;
    private final Function1<List<String>, Collection<String>> transformFinalFileContent;
    private final Set<Flag> flags;

    private boolean classFilter(
            boolean incubatingClass,
            boolean deprecatedClass,
            boolean replacedByIncubatingClass
    ) {
        return memberFilter(incubatingClass, deprecatedClass, replacedByIncubatingClass);
    }

    private boolean memberFilter(
            boolean incubating,
            boolean deprecated,
            boolean replacedByIncubating
    ) {
        switch (filter) {
            case ALL:
                return true;
            case STABLE_ONLY:
                return !incubating;
            case INCUBATING_ONLY:
                return incubating;
            case DEPRECATED_ONLY:
                return deprecated;
            case REPLACED_BY_INCUBATING_ONLY:
                return replacedByIncubating;
        }
        throw new IllegalStateException(filter.toString());
    }

    private static final String INCUBATING_ANNOTATION = "@org.gradle.api.Incubating()";
    private static final String JAVA_DEPRECATED_ANNOTATION = "@java.lang.Deprecated";
    private static final String KOTLIN_DEPRECATED_ANNOTATION = "@kotlin.Deprecated";
    private static final String REPLACED_BY_INCUBATING_ANNOTATION = "@com.android.build.api.annotations.ReplacedByIncubating";

    public void checkApiElements() throws IOException {
        checkApiElements(clazz -> getApiElements(clazz).collect(Collectors.toList()));
    }

    public void checkApiElements(@NonNull Function1<Class<?>, Collection<String>> transform)
            throws IOException {

        Collection<String> apiElements =
                transformFinalFileContent.invoke(getApiElements(transform));

        List<String> expectedApiElements =
                Splitter.on("\n")
                        .omitEmptyStrings()
                        .splitToList(Resources.toString(expectedFileUrl, Charsets.UTF_8));

        if (apiElements.equals(expectedApiElements)) {
            return;
        }
        String diff =
                TestUtils.getDiff(
                        expectedApiElements.toArray(new String[0]),
                        apiElements.toArray(new String[0]));
        throw new AssertionError(errorMessage + "\n" + diff);
    }

    public void updateFile(String dirPath) throws IOException {
        updateFile(dirPath, clazz -> getApiElements(clazz).collect(Collectors.toList()));
    }

    public void updateFile(
            String dirPath, @NonNull Function1<Class<?>, Collection<String>> transform)
            throws IOException {
        Path dir = TestUtils.resolveWorkspacePath(dirPath);
        Path file = dir.resolve(StringsKt.substringAfterLast(expectedFileUrl.getFile(), '/', "?"));
        Collection<String> content = transformFinalFileContent.invoke(getApiElements(transform));
        List<String> previous = Files.readAllLines(file);
        Files.writeString(file, Joiner.on("\n").join(content) + "\n");
        if (!previous.equals(content)) {
            System.out.println();
            System.out.println("Applied diff to " + file);
            System.out.println(
                    TestUtils.getDiff(
                            previous.toArray(new String[0]), content.toArray(new String[0])));
        } else {
            System.out.println("No updates to " + file);
        }
        System.out.println();
    }

    private List<String> getApiElements(
            @NonNull Function1<Class<?>, Collection<String>> transform) {

        List<String> stableClasses =
                classes.stream()
                        .flatMap(classInfo -> transform.invoke(classInfo.load()).stream())
                        .distinct()
                        .sorted()
                        .collect(ImmutableList.toImmutableList());

        ImmutableList.Builder<String> lines = ImmutableList.builder();
        lines.add(titleLine);
        lines.add("-------------------------------------------------------------------------");
        lines.add("ATTENTION REVIEWER: If this needs to be changed, please make sure changes");
        lines.add("below are backwards compatible.");
        lines.add("-------------------------------------------------------------------------");
        if (!flags.contains(Flag.OMIT_HASH)) {
            lines.add("Sha256 of below classes:");
            lines.add(
                    Hashing.sha256()
                            .hashString(Joiner.on("\n").join(stableClasses), Charsets.UTF_8)
                            .toString());
            lines.add("-------------------------------------------------------------------------");
        }
        lines.addAll(stableClasses);
        return lines.build();
    }

    public Stream<String> getApiElements(@NonNull Class<?> klass) {
        if (!Modifier.isPublic(klass.getModifiers())) {
            return Stream.empty();
        }

        boolean incubatingClass = isIncubating(klass);
        boolean deprecatedClass = isDeprecated(klass);
        boolean replacedByIncubatingClass = isReplacedByIncubating(klass);

        Set<String> deprecatedKotlinProperties = new HashSet<>();

        Arrays.stream(klass.getClasses())
                .filter(it -> it.getName().equals(klass.getName() + "$DefaultImpls"))
                .findFirst()
                .ifPresent(
                        clazz ->
                                Arrays.stream(clazz.getDeclaredMethods())
                                        .filter(
                                                method ->
                                                        method.getName().endsWith("$annotations")
                                                                && isDeprecated(
                                                                        Invokable.from(method)))
                                        .forEach(
                                                deprecatedMethod -> {
                                                    String propertyName =
                                                            StringsKt.removeSuffix(
                                                                    deprecatedMethod.getName(),
                                                                    "$annotations");
                                                    if (propertyName.startsWith("get")) {
                                                        propertyName =
                                                                StringsKt.removePrefix(
                                                                        propertyName, "get");
                                                    } else if (propertyName.startsWith("is")) {
                                                        propertyName =
                                                                StringsKt.removePrefix(
                                                                        propertyName, "is");
                                                    } else {
                                                        throw new RuntimeException(
                                                                "unexpected name "
                                                                        + deprecatedMethod
                                                                                .getName());
                                                    }
                                                    deprecatedKotlinProperties.add(propertyName);
                                                }));

        for (Field field : klass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && Modifier.isPublic(field.getModifiers())) {
                if (flags.contains(Flag.ALLOW_PUBLIC_INSTANCE_FIELD)) {
                    Assert.fail(
                            String.format(
                                    "Public instance field %s exposed in class %s.",
                                    field.getName(), klass.getName()));
                }
            }
        }

        // streams for all the fields.
        Stream<Stream<String>> streams =
                Stream.of(
                        // Constructors:
                        Stream.of(klass.getDeclaredConstructors())
                                .map(Invokable::from)
                                .filter(ApiTester::isPublic)
                                .filter(
                                        invokable ->
                                                memberFilter(
                                                        incubatingClass || isIncubating(invokable),
                                                        deprecatedClass || isDeprecated(invokable),
                                                        replacedByIncubatingClass
                                                                || isReplacedByIncubating(invokable)
                                                ))
                                .map(ApiTester::getApiElement)
                                .filter(Objects::nonNull),
                        // Methods:
                        Stream.of(klass.getDeclaredMethods())
                                .map(Invokable::from)
                                .filter(ApiTester::isPublic)
                                .filter(
                                        invokable ->
                                                memberFilter(
                                                        incubatingClass || isIncubating(invokable),
                                                        deprecatedClass
                                                                || isDeprecated(invokable)
                                                                || isDeprecatedKotlinProperty(
                                                                        deprecatedKotlinProperties,
                                                                        invokable),
                                                        replacedByIncubatingClass || isReplacedByIncubating(invokable)
                                                ))
                                .map(ApiTester::getApiElement)
                                .filter(Objects::nonNull),
                        // Fields:
                        Stream.of(klass.getDeclaredFields())
                                .filter(
                                        field ->
                                                !Modifier.isStatic(field.getModifiers())
                                                        && Modifier.isPublic(field.getModifiers()))
                                .map(ApiTester::getApiElement),

                        // Finally, all inner classes:
                        Stream.of(klass.getDeclaredClasses()).flatMap(this::getApiElements));

        if (classFilter(incubatingClass, deprecatedClass, replacedByIncubatingClass)) {
            streams = Stream.concat(streams, Stream.of(Stream.of(getApiElement(klass))));
        }

        return streams.flatMap(Function.identity());
    }

    @NonNull
    private static String getApiElement(@NonNull Class<?> apiClass) {
        StringBuilder classItem = new StringBuilder(apiClass.getName());
        Class<?> superclass = apiClass.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            classItem.append(" extends ").append(superclass.getName());
        }
        Class<?>[] interfaces = apiClass.getInterfaces();
        if (interfaces.length > 0) {
            classItem.append(" implements ");
        }
        classItem.append(
                Arrays.stream(interfaces).map(Class::getName).collect(Collectors.joining(", ")));
        return classItem.toString();
    }

    private static String getApiElement(Field field) {
        return String.format(
                "%1$s.%2$s: Field - %3$s",
                field.getDeclaringClass().getName(),
                field.getName(),
                field.getGenericType().toString());
    }

    private static boolean isPublic(Invokable<?, ?> invokable) {
        return invokable.isPublic();
    }

    private static boolean isIncubating(@NonNull AnnotatedElement element) {
        Annotation[] annotations = element.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.toString().equals(INCUBATING_ANNOTATION)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isDeprecatedKotlinProperty(
            @NonNull Set<String> deprecatedKotlinProperties, @NonNull Invokable element) {
        if (element.getName().startsWith("get") || element.getName().startsWith("set")) {
            return deprecatedKotlinProperties.contains(element.getName().substring(3));
        } else if (element.getName().startsWith("is")) {
            return deprecatedKotlinProperties.contains(element.getName().substring(2));
        }

        return false;
    }

    private static boolean isDeprecated(@NonNull AnnotatedElement element) {
        Annotation[] annotations = element.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.toString().startsWith(JAVA_DEPRECATED_ANNOTATION)
                    || annotation.toString().startsWith(KOTLIN_DEPRECATED_ANNOTATION)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isReplacedByIncubating(@NonNull AnnotatedElement element) {
        Annotation[] annotations = element.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.toString().startsWith(REPLACED_BY_INCUBATING_ANNOTATION)) {
                return true;
            }
        }

        return false;
    }

    public static String getApiElement(Invokable<?, ?> invokable) {
        String className = invokable.getDeclaringClass().getName();
        String parameters =
                invokable
                        .getParameters()
                        .stream()
                        .map(Parameter::getType)
                        .map(ApiTester::typeToString)
                        .collect(Collectors.joining(", "));
        String descriptor = typeToString(invokable.getReturnType()) + " (" + parameters + ")";

        String name = invokable.getName();

        // ignore some weird annotations method generated by Kotlin because
        // they are not generated/seen when building with Bazel
        if (name.endsWith("$annotations")) {
            return null;
        }

        if (name.equals(className)) {
            name = "<init>";
        }

        String thrownExceptions = "";
        ImmutableList<TypeToken<? extends Throwable>> exceptionTypes =
                invokable.getExceptionTypes();
        if (!exceptionTypes.isEmpty()) {
            thrownExceptions =
                    exceptionTypes
                            .stream()
                            .map(ApiTester::typeToString)
                            .collect(Collectors.joining(", ", " throws ", ""));
        }

        return String.format("%s.%s: %s%s", className, name, descriptor, thrownExceptions);
    }

    private static String typeToString(TypeToken<?> typeToken) {
        if (typeToken.isArray()) {
            return typeToString(typeToken.getComponentType()) + "[]";
        } else {
            // Workaround for JDK 8 bug https://bugs.openjdk.java.net/browse/JDK-8054213
            // Bug only appears on Unix derived OSes so not on Windows.
            // This ugly hack should be removed as soon as we update our JDK to JDK 9 or above
            // as it was checked that it is not necessary any longer.
            String expandedName = typeToken.toString();
            // if there are no inner class, there is no bug.
            if (!expandedName.contains("$")) {
                return expandedName;
            }
            // if there is an inner class, getRawType() will return the correct name but we
            // are missing the generic information so adding it back manually.
            return typeToken.getRawType().getName()
                    + (expandedName.contains("<")
                            ? expandedName.substring(expandedName.indexOf('<'))
                            : "");
        }
    }
}
