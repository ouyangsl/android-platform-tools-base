/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TARGET_SDK_VERSION;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.tools.lint.detector.api.TextFormat.RAW;
import static com.android.xml.AndroidManifest.ATTRIBUTE_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;
import static com.android.xml.AndroidManifest.NODE_USES_PERMISSION;
import static com.android.xml.AndroidManifest.NODE_USES_SDK;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects various issues for Chrome OS devices.
 */
public class ChromeOSDetector extends Detector implements Detector.XmlScanner {
    private static final Implementation IMPLEMENTATION =
            new Implementation(ChromeOSDetector.class, Scope.MANIFEST_SCOPE);

    /** Using hardware unsupported by Chrome OS devices */
    public static final Issue UNSUPPORTED_CHROME_OS_HARDWARE =
            Issue.create("UnsupportedChromeOSHardware", //$NON-NLS-1$
                         "Unsupported Chrome OS Hardware Feature",
                         "The <uses-feature> element should not require this unsupported Chrome OS "
                                 + "hardware feature. Any uses-feature not explicitly marked with "
                                 + "required=\"false\" is necessary on the device to be installed "
                                 + "on. Ensure that any features that might prevent it from being "
                                 + "installed on a Chrome OS device are reviewed and marked as not "
                                 + "required in the manifest.",
                         Category.CORRECTNESS, 6, Severity.ERROR, IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/topic/arc/manifest.html#incompat-entries");

    /** Permission implies required hardware unsupported by Chrome OS */
    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE =
            Issue.create("PermissionImpliesUnsupportedChromeOSHardware", //$NON-NLS-1$
                         "Permission Implies Unsupported Chrome OS Hardware",

                         "The <uses-permission> element should not require a permission that "
                                 + " implies an unsupported Chrome OS hardware feature. Google "
                                 + "Play assumes that certain hardware-related permissions "
                                 + "indicate that the underlying hardware features are required by "
                                 + "default. To fix the issue, consider declaring the "
                                 + "corresponding uses-feature element with required=\"false\" "
                                 + "attribute.",
                         Category.CORRECTNESS, 3, Severity.WARNING, IMPLEMENTATION)
                    .addMoreInfo(
                            "http://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    /** Targeting SDK version prior to N implies missing window management features */
    public static final Issue TARGET_SDK_PRIOR_TO_N =
            Issue.create("TargetSdkPriorToN", //$NON-NLS-1$
                         "Target SDK prior to N",
                         "The <uses-sdk> element should specify a targetSdkVersion of 24 or higher "
                                 + "to indicate that window management features are enabled.",
                         Category.CORRECTNESS, 3, Severity.WARNING, IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/topic/arc/index.html#support-new-features");

    private static final String HARDWARE_FEATURE_CAMERA = "android.hardware.camera"; //$NON-NLS-1$

    private static final String HARDWARE_FEATURE_CAMERA_AUTOFOCUS =
            "android.hardware.camera.autofocus"; //$NON-NLS-1$

    private static final String HARDWARE_FEATURE_TELEPHONY =
            "android.hardware.telephony"; //$NON-NLS-1$

    private static final String ANDROID_PERMISSION_CAMERA =
            "android.permission.CAMERA"; //$NON-NLS-1$

    // https://developer.android.com/topic/arc/manifest.html#incompat-entries
    private static final Set<String> UNSUPPORTED_HARDWARE_FEATURES =
            ImmutableSet.<String>builder()
                    .add(HARDWARE_FEATURE_CAMERA)
                    .add(HARDWARE_FEATURE_CAMERA_AUTOFOCUS) //$NON-NLS-1$
                    .add("android.hardware.camera.capability.manual_post_processing") //$NON-NLS-1$
                    .add("android.hardware.camera.capability.manual_sensor") //$NON-NLS-1$
                    .add("android.hardware.camera.capability.raw") //$NON-NLS-1$
                    .add("android.hardware.camera.flash") //$NON-NLS-1$
                    .add("android.hardware.camera.level.full") //$NON-NLS-1$
                    .add("android.hardware.consumerir") //$NON-NLS-1$
                    .add("android.hardware.location.gps") //$NON-NLS-1$
                    .add("android.hardware.nfc") //$NON-NLS-1$
                    .add("android.hardware.nfc.hce") //$NON-NLS-1$
                    .add("android.hardware.sensor.barometer") //$NON-NLS-1$
                    .add(HARDWARE_FEATURE_TELEPHONY)
                    .add("android.hardware.telephony.cdma") //$NON-NLS-1$
                    .add("android.hardware.telephony.gsm") //$NON-NLS-1$
                    .add("android.hardware.touchscreen") //$NON-NLS-1$
                    .add("android.hardware.type.automotive") //$NON-NLS-1$
                    .add("android.hardware.type.television") //$NON-NLS-1$
                    .add("android.hardware.usb.accessory") //$NON-NLS-1$
                    .add("android.hardware.usb.host") //$NON-NLS-1$
                    // Partially-supported, only on some Chrome OS devices.
                    .add("android.hardware.sensor.accelerometer") //$NON-NLS-1$
                    .add("android.hardware.sensor.compass") //$NON-NLS-1$
                    .add("android.hardware.sensor.gyroscope") //$NON-NLS-1$
                    .add("android.hardware.sensor.light") //$NON-NLS-1$
                    .add("android.hardware.sensor.proximity") //$NON-NLS-1$
                    .add("android.hardware.sensor.stepcounter") //$NON-NLS-1$
                    .add("android.hardware.sensor.stepdetector") //$NON-NLS-1$
                    // Software features not currently supported on Chrome OS devices.
                    .add("android.software.app_widgets") //$NON-NLS-1$
                    .add("android.software.device_admin") //$NON-NLS-1$
                    .add("android.software.home_screen") //$NON-NLS-1$
                    .add("android.software.input_methods") //$NON-NLS-1$
                    .add("android.software.leanback") //$NON-NLS-1$
                    .add("android.software.live_wallpaper") //$NON-NLS-1$
                    .add("android.software.live_tv") //$NON-NLS-1$
                    .add("android.software.managed_users") //$NON-NLS-1$
                    .add("android.software.midi") //$NON-NLS-1$
                    .add("android.software.sip") //$NON-NLS-1$
                    .add("android.software.sip.voip") //$NON-NLS-1$
                    .build();

    private static final Map<String, String> PERMISSIONS_TO_IMPLIED_UNSUPPORTED_HARDWARE =
            ImmutableMap.<String, String>builder()
                    .put(ANDROID_PERMISSION_CAMERA,
                            HARDWARE_FEATURE_CAMERA)
                    .put("android.permission.CALL_PHONE", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .put("android.permission.CALL_PRIVILEGED", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .put("android.permission.MODIFY_PHONE_STATE", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .put("android.permission.PROCESS_OUTGOING_CALLS", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .put("android.permission.READ_SMS", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .put("android.permission.RECEIVE_SMS", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .put("android.permission.RECEIVE_MMS", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .put("android.permission.RECEIVE_WAP_PUSH", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .put("android.permission.SEND_SMS", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .put("android.permission.WRITE_APN_SETTINGS", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .put("android.permission.WRITE_SMS", //$NON-NLS-1$
                            HARDWARE_FEATURE_TELEPHONY)
                    .build();

    /**
     * If you change number of parameters or order, update
     * {@link #getHardwareFeature(String, TextFormat)}
     */
    private static final String USES_HARDWARE_ERROR_MESSAGE_FORMAT =
            "Permission exists without corresponding hardware `<uses-feature "
            + "android:name=\"%1$s\" required=\"false\">` tag.";

    /** Constructs a new {@link ChromeOSDetector} check */
    public ChromeOSDetector() {}

    /** Used for {@link PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE} */
    private boolean mUsesFeatureCamera;

    /** Used for {@link PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE} */
    private boolean mUsesFeatureCameraAutofocus;

    /** All permissions that imply unsupported Chrome OS hardware. */
    private List<String> mUnsupportedHardwareImpliedPermissions;

    /** All Unsupported Chrome OS uses features in use by the current manifest.*/
    private Set<String> mAllUnsupportedChromeOSUsesFeatures;

    /** Set containing unsupported Chrome OS uses-features elements without required="false" */
    private Set<String> mUnsupportedChromeOSUsesFeatures;

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE, NODE_USES_PERMISSION, NODE_USES_SDK);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mUsesFeatureCamera = false;
        mUsesFeatureCameraAutofocus = false;
        mUnsupportedHardwareImpliedPermissions = Lists.newArrayListWithExpectedSize(2);
        mUnsupportedChromeOSUsesFeatures = Sets.newHashSetWithExpectedSize(2);
        mAllUnsupportedChromeOSUsesFeatures = Sets.newHashSetWithExpectedSize(2);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        XmlContext xmlContext = (XmlContext) context;

        if (!context.getMainProject().isLibrary()) {
            // Report all unsupported Chrome OS hardware uses-feature.
            // These point to all unsupported Chrome OS uses features that have not be marked
            // required = false;
            if (!mUnsupportedChromeOSUsesFeatures.isEmpty()
                    && xmlContext.isEnabled(UNSUPPORTED_CHROME_OS_HARDWARE)) {
                List<Element> usesFeatureElements = findUsesFeatureElements(
                        mUnsupportedChromeOSUsesFeatures, xmlContext.document);
                for (Element element : usesFeatureElements) {
                    Attr attrRequired = element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_REQUIRED);
                    Node location = attrRequired == null ? element : attrRequired;
                    xmlContext.report(UNSUPPORTED_CHROME_OS_HARDWARE, location,
                            xmlContext.getLocation(location),
                            "Expecting `android:required=\"false\"` for this hardware "
                                    + "feature that may not be supported by all Chrome OS "
                                    + "devices.");
                }
            }

            // Report permissions implying unsupported hardware
            if (!mUnsupportedHardwareImpliedPermissions.isEmpty()
                    && xmlContext.isEnabled(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)) {
                Collection<String> filteredPermissions = Collections2.filter(
                        mUnsupportedHardwareImpliedPermissions, new Predicate<String>() {
                            @Override
                            public boolean apply(String input) {
                                // Special-case handling for camera permission - needs to check that
                                // both camera and camera autofocus features are present and se to
                                // android:required="false".
                                if (ANDROID_PERMISSION_CAMERA.equals(input)) {
                                    return (!mUsesFeatureCamera || !mUsesFeatureCameraAutofocus);
                                }
                                // Filter out all permissions that already have their
                                // corresponding implied hardware declared in
                                // the AndroidManifest.xml
                                String usesFeature =
                                        PERMISSIONS_TO_IMPLIED_UNSUPPORTED_HARDWARE.get(input);
                                return usesFeature != null
                                        && !mAllUnsupportedChromeOSUsesFeatures.contains(
                                                   usesFeature);
                            }
                        });

                List<Element> permissionsWithoutUsesFeatures =
                        findPermissionElements(filteredPermissions, xmlContext.document);

                for (Element permissionElement : permissionsWithoutUsesFeatures) {
                    String name = permissionElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    String unsupportedHardwareNames[] = new String[2];
                    unsupportedHardwareNames[0] =
                            PERMISSIONS_TO_IMPLIED_UNSUPPORTED_HARDWARE.get(name);

                    // Special-case handling of camera permission - either or both implied features
                    // might be missing.
                    if (ANDROID_PERMISSION_CAMERA.equals(name)) {
                        if (mUsesFeatureCamera) {
                            unsupportedHardwareNames[0] = null;
                        }
                        if (!mUsesFeatureCameraAutofocus) {
                            unsupportedHardwareNames[1] = HARDWARE_FEATURE_CAMERA_AUTOFOCUS;
                        }
                    }

                    for (String unsupportedHardwareName : unsupportedHardwareNames) {
                        if (unsupportedHardwareName != null) {
                            String message = String.format(
                                    USES_HARDWARE_ERROR_MESSAGE_FORMAT, unsupportedHardwareName);
                            xmlContext.report(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                                    permissionElement, xmlContext.getLocation(permissionElement),
                                    message);
                        }
                    }
                }
            }
        }
    }

    private static List<Element> findPermissionElements(
            Collection<String> permissions, Document document) {
        Node manifestElement = document.getDocumentElement();
        if (manifestElement == null) {
            return Collections.emptyList();
        }
        List<Element> nodes = new ArrayList<Element>(permissions.size());
        for (Element child : LintUtils.getChildren(manifestElement)) {
            if (TAG_USES_PERMISSION.equals(child.getTagName())
                    && permissions.contains(child.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                nodes.add(child);
            }
        }
        return nodes;
    }

    /**
     * Method to find all matching uses-feature elements in one go.
     * Rather than iterating over the entire list of child nodes only to return the one that
     * match a particular featureName, we use this method to iterate and return all the
     * uses-feature elements of interest in a single iteration of the manifest element's children.
     *
     * @param featureNames The set of all features to look for inside the
     *                     <code>&lt;manifest&gt;</code> node of the document.
     * @param document The document/root node to use for iterating.
     * @return A list of all <code>&lt;uses-feature&gt;</code> elements that match the featureNames.
     */
    private static List<Element> findUsesFeatureElements(
            @NonNull Set<String> featureNames, @NonNull Document document) {
        Node manifestElement = document.getDocumentElement();
        if (manifestElement == null) {
            return Collections.emptyList();
        }
        List<Element> nodes = new ArrayList<Element>(featureNames.size());
        for (Element child : LintUtils.getChildren(manifestElement)) {
            if (TAG_USES_FEATURE.equals(child.getTagName())
                    && featureNames.contains(child.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                nodes.add(child);
            }
        }
        return nodes;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String elementName = element.getTagName();

        if (NODE_USES_FEATURE.equals(elementName)) {
            // Ensures that unsupported hardware features aren't required.
            Attr name = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (name != null) {
                String featureName = name.getValue();
                if (isUnsupportedHardwareFeature(featureName)) {
                    mAllUnsupportedChromeOSUsesFeatures.add(featureName);
                    Attr required = element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_REQUIRED);
                    if (required == null || Boolean.parseBoolean(required.getValue())) {
                        mUnsupportedChromeOSUsesFeatures.add(featureName);
                    }
                    // Special-case tracking of features implicitly needed by camera permission.
                    if (HARDWARE_FEATURE_CAMERA.equals(featureName)) {
                        mUsesFeatureCamera = true;
                    }
                    if (HARDWARE_FEATURE_CAMERA_AUTOFOCUS.equals(featureName)) {
                        mUsesFeatureCameraAutofocus = true;
                    }
                }
            }
        } else if (NODE_USES_PERMISSION.equals(elementName)) {
            // Store all <uses-permission> tags that imply unsupported hardware
            String permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (PERMISSIONS_TO_IMPLIED_UNSUPPORTED_HARDWARE.containsKey(permissionName)) {
                mUnsupportedHardwareImpliedPermissions.add(permissionName);
            }
        } else if (NODE_USES_SDK.equals(elementName)) {
            if (context.isEnabled(TARGET_SDK_PRIOR_TO_N)) {
                Attr targetSdkVersionNode =
                        element.getAttributeNodeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
                if (targetSdkVersionNode != null) {
                    String target = targetSdkVersionNode.getValue();
                    try {
                        int api = Integer.parseInt(target);
                        if (api < 24) {
                            context.report(TARGET_SDK_PRIOR_TO_N, element,
                                    context.getLocation(targetSdkVersionNode),
                                    "The <uses-sdk> element should specify a "
                                            + "targetSdkVersion of 24 or higher, and the "
                                            + "application should support the window management "
                                            + "features made available in Android 7.0.");
                        }
                    } catch (NumberFormatException e) {
                        // Ignore: AAPT will enforce this.
                    }
                }
            }
        }
    }

    private static boolean isUnsupportedHardwareFeature(@NonNull String featureName) {
        for (String prefix : UNSUPPORTED_HARDWARE_FEATURES) {
            if (featureName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Assumes that the node is a direct child of the given Node.
     */
    private static Node getElementWithTagName(@NonNull String tagName, @NonNull Node node) {
        for (Element child : LintUtils.getChildren(node)) {
            if (tagName.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Given an error message created by this lint check, return the corresponding featureName
     * that it suggests should be added.
     * (Intended to support quickfix implementations for this lint check.)
     *
     * @param errorMessage The error message originally produced by this detector.
     * @param format The format of the error message.
     * @return the corresponding featureName, or null if not recognized
     */
    @SuppressWarnings("unused") // Used by the IDE
    @Nullable
    public static String getHardwareFeature(
            @NonNull String errorMessage, @NonNull TextFormat format) {
        List<String> parameters = LintUtils.getFormattedParameters(
                RAW.convertTo(USES_HARDWARE_ERROR_MESSAGE_FORMAT, format), errorMessage);
        if (parameters.size() == 1) {
            return parameters.get(0);
        }
        return null;
    }
}
