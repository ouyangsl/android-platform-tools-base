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
package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_HOST;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PATH;
import static com.android.SdkConstants.ATTR_PATH_PATTERN;
import static com.android.SdkConstants.ATTR_PATH_PREFIX;
import static com.android.SdkConstants.ATTR_SCHEME;
import static com.android.xml.AndroidManifest.ATTRIBUTE_MIME_TYPE;
import static com.android.xml.AndroidManifest.ATTRIBUTE_NAME;
import static com.android.xml.AndroidManifest.ATTRIBUTE_PORT;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_DATA;
import static com.android.xml.AndroidManifest.NODE_INTENT;
import static com.android.xml.AndroidManifest.NODE_MANIFEST;
import static com.android.xml.AndroidManifest.NODE_METADATA;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
<<<<<<< HEAD   (cdfb32 Merge "Fixing NPE when getting extra package display name" i)
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.LintClient;
=======
import com.android.tools.lint.client.api.XmlParser;
>>>>>>> BRANCH (57afbd Add java lint check for app indexing api)
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
<<<<<<< HEAD   (cdfb32 Merge "Fixing NPE when getting extra package display name" i)
import com.android.tools.lint.detector.api.Project;
=======
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
>>>>>>> BRANCH (57afbd Add java lint check for app indexing api)
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
<<<<<<< HEAD   (cdfb32 Merge "Fixing NPE when getting extra package display name" i)
import java.util.Collections;
import java.util.List;
=======
import java.util.List;
import java.util.Set;

import lombok.ast.AstVisitor;
import lombok.ast.ClassDeclaration;
import lombok.ast.Expression;
import lombok.ast.MethodInvocation;
import lombok.ast.NormalTypeBody;
import lombok.ast.TypeMember;
import lombok.ast.TypeReference;
import lombok.ast.VariableDeclaration;
>>>>>>> BRANCH (57afbd Add java lint check for app indexing api)


/**
 * Check if the usage of App Indexing is correct.
 */
public class AppIndexingApiDetector extends Detector
        implements Detector.XmlScanner, Detector.JavaScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AppIndexingApiDetector.class, Scope.MANIFEST_SCOPE);

    private static final Implementation IMPLEMENTATION_JAVA = new Implementation(
            AppIndexingApiDetector.class, Scope.MANIFEST_AND_JAVA_SCOPE, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE_ERROR = Issue.create("AppIndexingError", //$NON-NLS-1$
            "Wrong Usage of App Indexing",
            "Ensures the app can correctly handle deep links and integrate with " +
                    "App Indexing for Google search.",
            Category.USABILITY, 5, Severity.ERROR, IMPLEMENTATION)
            .addMoreInfo("https://g.co/AppIndexing");

    public static final Issue ISSUE_WARNING = Issue.create("AppIndexingWarning", //$NON-NLS-1$
            "Missing App Indexing Support",
            "Ensures the app can correctly handle deep links and integrate with " +
                    "App Indexing for Google search.",
            Category.USABILITY, 5, Severity.WARNING, IMPLEMENTATION)
            .addMoreInfo("https://g.co/AppIndexing");

    public static final Issue ISSUE_JAVA_ERROR = Issue.create("AppIndexingJavaError", //$NON-NLS-1$
            "Wrong Usage of App Indexing Api",
            "Ensures the app can correctly handle deep links and integrate with " +
                    "App Indexing for Google search.",
            Category.USABILITY, 5, Severity.ERROR, IMPLEMENTATION_JAVA)
            .addMoreInfo("https://g.co/AppIndexing");

    public static final Issue ISSUE_JAVA_WARNING = Issue.create("AppIndexingJavaWARNING", //$NON-NLS-1$
            "Missing App Indexing Api Code",
            "Ensures the app can correctly handle deep links and integrate with " +
                    "App Indexing for Google search.",
            Category.USABILITY, 5, Severity.WARNING, IMPLEMENTATION_JAVA)
            .addMoreInfo("https://g.co/AppIndexing");

    private static final String[] PATH_ATTR_LIST = new String[]{ATTR_PATH_PREFIX, ATTR_PATH,
            ATTR_PATH_PATTERN};

    private static final String APP_INDEX_START = "start"; //$NON-NLS-1$
    private static final String APP_INDEX_END = "end"; //$NON-NLS-1$
    private static final String CLIENT_CONNECT = "connect"; //$NON-NLS-1$
    private static final String CLIENT_DISCONNECT = "disconnect"; //$NON-NLS-1$

    private Set<String> activitiesToCheck = Sets.newHashSet();
    private List<MethodInvocation> startMethod = Lists.newArrayList();
    private List<MethodInvocation> endMethod = Lists.newArrayList();
    private List<MethodInvocation> connectMethod = Lists.newArrayList();
    private List<MethodInvocation> disconnectMethod = Lists.newArrayList();

    /** Constructs a new {@link AppIndexingApiDetector} */
    public AppIndexingApiDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                NODE_APPLICATION,
                NODE_INTENT
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element intent) {
        // check GMS
        if (intent.getNodeName().equals(NODE_APPLICATION)) {
            if (!hasGMS(intent)) {
                context.report(ISSUE_WARNING, intent, context.getLocation(intent),
                        "GMS should be added to the app");
            }
            return;
        }

        boolean actionView = hasActionView(intent);
        boolean browsable = isBrowsable(intent);
        boolean isHttp = false;
        boolean hasScheme = false;
        boolean hasHost = false;
        boolean hasPort = false;
        boolean hasPath = false;
        boolean hasMimeType = false;
        Element firstData = null;
        List<Element> children = LintUtils.getChildren(intent);
        for (Element data : children) {
            if (data.getNodeName().equals(NODE_DATA)) {
                if (firstData == null) {
                    firstData = data;
                }
                if (isHttpSchema(data)) {
                    isHttp = true;
                }
                checkSingleData(context, data);

                for (String name : PATH_ATTR_LIST) {
                    if (data.hasAttributeNS(ANDROID_URI, name)) {
                        hasPath = true;
                    }
                }

                if (data.hasAttributeNS(ANDROID_URI, ATTR_SCHEME)) {
                    hasScheme = true;
                }

                if (data.hasAttributeNS(ANDROID_URI, ATTR_HOST)) {
                    hasHost = true;
                }

                if (data.hasAttributeNS(ANDROID_URI, ATTRIBUTE_PORT)) {
                    hasPort = true;
                }

                if (data.hasAttributeNS(ANDROID_URI, ATTRIBUTE_MIME_TYPE)) {
                    hasMimeType = true;
                }
            }
        }

        // In data field, a URL is consisted by
        // <scheme>://<host>:<port>[<path>|<pathPrefix>|<pathPattern>]
        // Each part of the URL should not have illegal character.
        if ((hasPath || hasHost || hasPort) && !hasScheme) {
            context.report(ISSUE_ERROR, firstData, context.getLocation(firstData),
                    "android:scheme missing");
        }

        if ((hasPath || hasPort) && !hasHost) {
            context.report(ISSUE_ERROR, firstData, context.getLocation(firstData),
                    "android:host missing");
        }

        if (actionView && browsable) {
            if (firstData == null) {
                // If this activity is an ACTION_VIEW action with category BROWSABLE, but doesn't
                // have data node, it may be a mistake and we will report error.
                context.report(ISSUE_ERROR, intent, context.getLocation(intent),
                        "Missing data node?");
            } else if (!hasScheme && !hasMimeType) {
                // If this activity is an action view, is browsable, but has neither a
                // URL nor mimeType, it may be a mistake and we will report error.
                context.report(ISSUE_ERROR, firstData, context.getLocation(firstData),
                        "Missing URL for the intent filter?");
            }
        }

        // If this activity is an ACTION_VIEW action, has a http URL but doesn't have
        // BROWSABLE, it may be a mistake and and we will report warning.
        if (actionView && isHttp && !browsable) {
            context.report(ISSUE_WARNING, intent, context.getLocation(intent),
                    "Activity supporting ACTION_VIEW is not set as BROWSABLE");
        }
    }

    private static boolean hasGMS(Element application) {
        List<Element> children = LintUtils.getChildren(application);
        for (Element metadata : children) {
            if (metadata.getNodeName().equals(NODE_METADATA)) {
                if (metadata.hasAttributeNS(ANDROID_URI, ATTR_NAME)) {
                    Attr attr = metadata.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_NAME);
                    if (attr.getValue().equals("com.google.android.gms.version")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasActionView(Element intent) {
        List<Element> children = LintUtils.getChildren(intent);
        for (Element action : children) {
            if (action.getNodeName().equals(NODE_ACTION)) {
                if (action.hasAttributeNS(ANDROID_URI, ATTRIBUTE_NAME)) {
                    Attr attr = action.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_NAME);
                    if (attr.getValue().equals("android.intent.action.VIEW")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isBrowsable(Element intent) {
        List<Element> children = LintUtils.getChildren(intent);
        for (Element child : children) {
            if (child.getNodeName().equals(NODE_CATEGORY)) {
                if (child.hasAttributeNS(ANDROID_URI, ATTRIBUTE_NAME)) {
                    Attr attr = child.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_NAME);
                    if (attr.getNodeValue().equals("android.intent.category.BROWSABLE")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isHttpSchema(Element data) {
        if (data.hasAttributeNS(ANDROID_URI, ATTR_SCHEME)) {
            String value = data.getAttributeNodeNS(ANDROID_URI, ATTR_SCHEME).getValue();
            if (value.equalsIgnoreCase("http") || value.equalsIgnoreCase("https")) {
                return true;
            }
        }
        return false;
    }

    private static void checkSingleData(XmlContext context, Element data) {
        // path, pathPrefix and pathPattern should starts with /.
        for (String name : PATH_ATTR_LIST) {
            if (data.hasAttributeNS(ANDROID_URI, name)) {
                Attr attr = data.getAttributeNodeNS(ANDROID_URI, name);
                String path = replaceUrlWithValue(context, attr.getValue());
                if (!path.startsWith("/") && !path.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
                    context.report(ISSUE_ERROR, attr, context.getLocation(attr),
                            "android:" + name + " attribute should start with '/', but it is : "
                                    + path);
                }
            }
        }

        // port should be a legal number.
        if (data.hasAttributeNS(ANDROID_URI, ATTRIBUTE_PORT)) {
            Attr attr = data.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_PORT);
            try {
                String port = replaceUrlWithValue(context, attr.getValue());
                Integer.parseInt(port);
            } catch (NumberFormatException e) {
                context.report(ISSUE_ERROR, attr, context.getLocation(attr),
                        "android:port is not a legal number");
            }
        }

        // Each field should be non empty.
        NamedNodeMap attrs = data.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node item = attrs.item(i);
            if (item.getNodeType() == Node.ATTRIBUTE_NODE) {
                Attr attr = (Attr) attrs.item(i);
                if (attr.getValue().isEmpty()) {
                    context.report(ISSUE_ERROR, attr, context.getLocation(attr),
                            attr.getName() + " cannot be empty");
                }
            }
        }
    }

<<<<<<< HEAD   (cdfb32 Merge "Fixing NPE when getting extra package display name" i)
    private static String replaceUrlWithValue(@NonNull XmlContext context,
            @NonNull String str) {
        Project project = context.getProject();
        LintClient client = context.getClient();
        if (!client.supportsProjectResources()) {
            return str;
        }
        ResourceUrl style = ResourceUrl.parse(str);
        if (style == null || style.type != ResourceType.STRING || style.framework) {
            return str;
        }
        AbstractResourceRepository resources = client.getProjectResources(project, true);
        if (resources == null) {
            return str;
        }
        List<ResourceItem> items = resources.getResourceItem(ResourceType.STRING, style.name);
        if (items == null || items.isEmpty()) {
            return str;
        }
        ResourceValue resourceValue = items.get(0).getResourceValue(false);
        if (resourceValue == null) {
            return str;
        }
        return resourceValue.getValue() == null ? str : resourceValue.getValue();
=======
    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                APP_INDEX_START,
                APP_INDEX_END,
                CLIENT_CONNECT,
                CLIENT_DISCONNECT
        );
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation node) {
        if (node.astName().astValue().equals(APP_INDEX_START)
                && node.astOperand().toString().endsWith("AppIndex.AppIndexApi")) {
            startMethod.add(node);
        } else if (node.astName().astValue().equals(APP_INDEX_END)
                && node.astOperand().toString().endsWith("AppIndex.AppIndexApi")) {
            endMethod.add(node);
        } else if (node.astName().astValue().equals(CLIENT_CONNECT)) {
            connectMethod.add(node);
        } else if (node.astName().astValue().equals(CLIENT_DISCONNECT)) {
            disconnectMethod.add(node);
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof JavaContext) {
            JavaContext javaContext = (JavaContext) context;
            ClassDeclaration classDeclare = getClassDeclaration(javaContext);
            if (classDeclare != null) {
                // find the activity classes that need app activity annotation
                getActivitiesToCheck(context);

                // app activity annotated but no support in manifest
                boolean hasIntent = activitiesToCheck.contains(classDeclare.astName().astValue());
                if (!hasIntent) {
                    for (MethodInvocation method : startMethod) {
                        javaContext.report(ISSUE_JAVA_ERROR, method,
                                javaContext.getLocation(method.astName()),
                                "Missing app indexing support in manifest");
                    }
                    for (MethodInvocation method : endMethod) {
                        javaContext.report(ISSUE_JAVA_ERROR, method,
                                javaContext.getLocation(method.astName()),
                                "Missing app indexing support in manifest");
                    }
                    return;
                }

                // GoogleApiClient should exist
                boolean hasClient = hasVariable(classDeclare, "GoogleApiClient");
                if (!hasClient) {
                    javaContext.report(ISSUE_JAVA_ERROR, classDeclare,
                            javaContext.getLocation(classDeclare.astName()),
                            "Missing GoogleApiClient");
                }

                // `AppIndex.AppIndexApi.start` and `AppIndex.AppIndexApi.end` should exist
                if (startMethod.isEmpty() && endMethod.isEmpty()) {
                    javaContext.report(ISSUE_JAVA_WARNING, classDeclare,
                            javaContext.getLocation(classDeclare.astName()),
                            "Missing app indexing api call");
                    return;
                }

                for (MethodInvocation startNode : startMethod) {
                    Expression startClient = startNode.astArguments().first();

                    // GoogleApiClient `connect` should exist
                    if (!hasOperand(startClient, connectMethod)) {
                        String message = String.format("GoogleApiClient `%1$s` has not connected",
                                startClient.toString());
                        javaContext.report(ISSUE_JAVA_ERROR, startClient,
                                javaContext.getLocation(startClient), message);
                    }

                    // `AppIndex.AppIndexApi.end` should pair with `AppIndex.AppIndexApi.start`
                    if (!hasArgument(startClient, endMethod)) {
                        javaContext.report(ISSUE_JAVA_ERROR, startNode,
                                javaContext.getLocation(startNode.astName()),
                                "Missing corresponding `AppIndex.AppIndexApi.end` method");
                    }
                }

                for (MethodInvocation endNode : endMethod) {
                    Expression endClient = endNode.astArguments().first();

                    // GoogleApiClient `disconnect` should exist
                    if (!hasOperand(endClient, disconnectMethod)) {
                        String message = String.format("GoogleApiClient `%1$s` has not connected",
                                endClient.toString());
                        javaContext.report(ISSUE_JAVA_ERROR, endClient,
                                javaContext.getLocation(endClient), message);
                    }

                    // `AppIndex.AppIndexApi.start` should pair with `AppIndex.AppIndexApi.end`
                    if (!hasArgument(endClient, startMethod)) {
                        javaContext.report(ISSUE_JAVA_ERROR, endNode,
                                javaContext.getLocation(endNode.astName()),
                                "Missing corresponding `AppIndex.AppIndexApi.start` method");
                    }
                }
            }
        }
    }

    // get names of activities with intent filters
    private void getActivitiesToCheck(Context context) {
        List<File> manifestFiles = context.getProject().getManifestFiles();
        XmlParser xmlParser = context.getDriver().getClient().getXmlParser();
        if (xmlParser != null) {
            for (File manifest : manifestFiles) {
                XmlContext xmlContext = new XmlContext(
                        context.getDriver(), context.getProject(), null, manifest, null,
                        xmlParser);
                Document doc = xmlParser.parseXml(xmlContext);
                if (doc != null) {
                    List<Element> children = LintUtils.getChildren(doc);
                    for (Element child : children) {
                        if (child.getNodeName().equals(NODE_MANIFEST)) {
                            List<Element> apps = getChildrenByName(child, NODE_APPLICATION);
                            for (Element app : apps) {
                                List<Element> acts = getChildrenByName(app, NODE_ACTIVITY);
                                for (Element act: acts) {
                                    List<Element> intents = getChildrenByName(act, NODE_INTENT);
                                    for (Element intent : intents) {
                                        List<Element> data = getChildrenByName(intent, NODE_DATA);
                                        if (!data.isEmpty()
                                                && act.hasAttributeNS(
                                                ANDROID_URI, ATTRIBUTE_NAME)) {
                                            Attr attr = act.getAttributeNodeNS(
                                                    ANDROID_URI, ATTRIBUTE_NAME);
                                            activitiesToCheck.add(attr.getValue().substring(1));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static List<Element> getChildrenByName(Element parent, String name) {
        List<Element> result = Lists.newArrayList();
        List<Element> children = LintUtils.getChildren(parent);
        for (Element child : children) {
            if (child.getNodeName().equals(name)) {
                result.add(child);
            }
        }
        return result;
    }

    // get ClassDeclaration of a java file
    private static ClassDeclaration getClassDeclaration(JavaContext context) {
        lombok.ast.Node compilationUnit = context.getCompilationUnit();
        List<lombok.ast.Node> nodeList;
        if (compilationUnit != null) {
            nodeList = compilationUnit.getChildren();
            for (lombok.ast.Node node : nodeList) {
                if (node instanceof ClassDeclaration) {
                    return (ClassDeclaration) node;
                }
            }
        }
        return null;
    }

    // check whether ClassDeclaration has variables of a certain type
    private static boolean hasVariable(ClassDeclaration classDeclaration, String type) {
        NormalTypeBody body = classDeclaration.astBody();
        if (body != null) {
            for (TypeMember member : body.astMembers()) {
                if (member instanceof VariableDeclaration) {
                    VariableDeclaration variable = (VariableDeclaration) member;
                    TypeReference typeReference = variable.astDefinition().astTypeReference();
                    if (typeReference.toString().equals(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // check whether a method with a certain argument exists in the method set
    private static boolean hasArgument(Expression argument, List<MethodInvocation> list) {
        for (MethodInvocation method : list) {
            Expression argument1 = method.astArguments().first();
            if(argument.toString().equals(argument1.toString())) {
                return true;
            }
        }
        return false;
    }

    // check whether a method with a certain operand exists in the method set
    private static boolean hasOperand(Expression operand, List<MethodInvocation> list) {
        for (MethodInvocation method : list) {
            Expression operand1 = method.astOperand();
            if(operand.toString().equals(operand1.toString())) {
                return true;
            }
        }
        return false;
>>>>>>> BRANCH (57afbd Add java lint check for app indexing api)
    }
}
