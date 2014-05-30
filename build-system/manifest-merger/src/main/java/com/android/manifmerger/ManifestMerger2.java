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

package com.android.manifmerger;

import static com.android.manifmerger.PlaceholderHandler.KeyBasedValueResolver;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.android.utils.SdkUtils;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * merges android manifest files, idempotent.
 */
@Immutable
public class ManifestMerger2 {

    @NonNull
    private final File mManifestFile;

    @NonNull
    private final KeyBasedValueResolver<String> mPlaceHolderValueResolver;

    @NonNull
    private final KeyBasedValueResolver<SystemProperty> mSystemPropertyResolver;

    private final ILogger mLogger;
    private final ImmutableList<Pair<String, File>> mLibraryFiles;
    private final ImmutableList<File> mFlavorsAndBuildTypeFiles;
    private final ImmutableList<Invoker.Feature> mOptionalFeatures;
    private final MergeType mMergeType;

    private ManifestMerger2(
            @NonNull ILogger logger,
            @NonNull File mainManifestFile,
            @NonNull ImmutableList<Pair<String, File>> libraryFiles,
            @NonNull ImmutableList<File> flavorsAndBuildTypeFiles,
            @NonNull ImmutableList<Invoker.Feature> optionalFeatures,
            @NonNull KeyBasedValueResolver<String> placeHolderValueResolver,
            @NonNull KeyBasedValueResolver<SystemProperty> systemPropertiesResolver,
            @NonNull MergeType mergeType) {
        this.mSystemPropertyResolver = systemPropertiesResolver;
        this.mPlaceHolderValueResolver = placeHolderValueResolver;
        this.mManifestFile = mainManifestFile;
        this.mLogger = logger;
        this.mLibraryFiles = libraryFiles;
        this.mFlavorsAndBuildTypeFiles = flavorsAndBuildTypeFiles;
        this.mOptionalFeatures = optionalFeatures;
        this.mMergeType = mergeType;
    }

    /**
     * Perform high level ordering of files merging and delegates actual merging to
     * {@link XmlDocument#merge(XmlDocument, com.android.manifmerger.MergingReport.Builder)}
     *
     * @return the merging activity report.
     * @throws MergeFailureException if the merging cannot be completed (for instance, if xml
     * files cannot be loaded).
     */
    private MergingReport merge() throws MergeFailureException {
        // initiate a new merging report
        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mLogger);

        SelectorResolver selectors = new SelectorResolver();
        // invariant : xmlDocumentOptional holds the higher priority document and we try to
        // merge in lower priority documents.
        Optional<XmlDocument> xmlDocumentOptional = Optional.absent();
        for (File inputFile : mFlavorsAndBuildTypeFiles) {
            mLogger.info("Merging flavors and build manifest %s \n", inputFile.getPath());
            xmlDocumentOptional = merge(xmlDocumentOptional,
                    load(new ManifestInfo(null, inputFile, ManifestInfo.Type.OVERLAY), selectors),
                    mergingReportBuilder);

            if (!xmlDocumentOptional.isPresent()) {
                return mergingReportBuilder.build();
            }
        }
        // load all the libraries xml files up front to have a list of all possible node:selector
        // values.
        List<LoadedManifestInfo> loadedLibraryDocuments = loadLibraries(selectors);

        mLogger.info("Merging main manifest %s\n", mManifestFile.getPath());
        LoadedManifestInfo loadedMainManifestInfo = load(
                new ManifestInfo(mManifestFile.getName(), mManifestFile, ManifestInfo.Type.MAIN),
                selectors);
        xmlDocumentOptional =
                merge(xmlDocumentOptional, loadedMainManifestInfo, mergingReportBuilder);

        if (!xmlDocumentOptional.isPresent()) {
            return mergingReportBuilder.build();
        }

        // force main manifest package into resulting merged file when creating a library manifest.
        if (mMergeType == MergeType.LIBRARY) {
            // extract the package name...
            String mainManifestPackageName = loadedMainManifestInfo.getXmlDocument().getRootNode()
                    .getXml().getAttribute("package");
            // save it in the selector instance.
            if (!Strings.isNullOrEmpty(mainManifestPackageName)) {
                xmlDocumentOptional.get().getRootNode().getXml()
                        .setAttribute("package", mainManifestPackageName);
            }
        }
        for (LoadedManifestInfo libraryDocument : loadedLibraryDocuments) {
            mLogger.info("Merging library manifest " + libraryDocument.getLocation());
            xmlDocumentOptional = merge(
                    xmlDocumentOptional, libraryDocument, mergingReportBuilder);
            if (!xmlDocumentOptional.isPresent()) {
                return mergingReportBuilder.build();
            }
        }

        // done with proper merging phase, now we need to trim unwanted elements, placeholder
        // substitution and system properties injection.
        ElementsTrimmer.trim(xmlDocumentOptional.get(), mergingReportBuilder);
        if (mergingReportBuilder.hasErrors()) {
            return mergingReportBuilder.build();
        }

        // do placeholder substitution
        PlaceholderHandler placeholderHandler = new PlaceholderHandler();
        placeholderHandler.visit(
                xmlDocumentOptional.get(),
                mPlaceHolderValueResolver,
                mergingReportBuilder);
        if (mergingReportBuilder.hasErrors()) {
            return mergingReportBuilder.build();
        }

        // perform system property injection.
        performSystemPropertiesInjection(mergingReportBuilder, xmlDocumentOptional.get());

        XmlDocument finalMergedDocument = xmlDocumentOptional.get();
        PostValidator.validate(finalMergedDocument, mergingReportBuilder);
        if (mergingReportBuilder.hasErrors()) {
            finalMergedDocument.getRootNode().addMessage(mergingReportBuilder,
                    MergingReport.Record.Severity.WARNING,
                    "Post merge validation failed");
        }

        // only remove tools annotations if we are packaging an application.
        if (mMergeType == MergeType.APPLICATION) {
            finalMergedDocument =
                    ToolsInstructionsCleaner.cleanToolsReferences(finalMergedDocument, mLogger);
        }

        if (finalMergedDocument != null) {
            mergingReportBuilder.setMergedDocument(finalMergedDocument);
        }

        MergingReport build = mergingReportBuilder.build();
        StdLogger stdLogger = new StdLogger(StdLogger.Level.INFO);
        build.log(stdLogger);
        stdLogger.verbose(build.getMergedDocument().get().prettyPrint());

        return build;
    }

    // merge the optionally existing xmlDocument with a lower priority xml file.
    private LoadedManifestInfo load(
            ManifestInfo lowerPriorityManifest,
            KeyResolver<String> selectors) throws MergeFailureException {

        XmlDocument lowerPriorityDocument;
        try {
            lowerPriorityDocument = XmlLoader.load(selectors,
                    mSystemPropertyResolver,
                    lowerPriorityManifest.mName,
                    lowerPriorityManifest.mLocation);
        } catch (Exception e) {
            throw new MergeFailureException(e);
        }
        return new LoadedManifestInfo(lowerPriorityManifest, lowerPriorityDocument);
    }

    // merge the optionally existing xmlDocument with a lower priority xml file.
    private Optional<XmlDocument> merge(
            Optional<XmlDocument> xmlDocument,
            LoadedManifestInfo lowerPriorityDocument,
            MergingReport.Builder mergingReportBuilder) throws MergeFailureException {

        MergingReport.Result validationResult = PreValidator
                .validate(mergingReportBuilder, lowerPriorityDocument.getXmlDocument());
        if (validationResult == MergingReport.Result.ERROR) {
            mergingReportBuilder.addMessage(
                    lowerPriorityDocument.getXmlDocument().getSourceLocation(), 0, 0,
                    MergingReport.Record.Severity.ERROR,
                    "Validation failed, exiting");
            return Optional.absent();
        }
        Optional<XmlDocument> result;
        if (xmlDocument.isPresent()) {
            result = xmlDocument.get().merge(
                    lowerPriorityDocument.getXmlDocument(), mergingReportBuilder);
        } else {
            mergingReportBuilder.getActionRecorder().recordDefaultNodeAction(
                    lowerPriorityDocument.getXmlDocument().getRootNode());
            result = Optional.of(lowerPriorityDocument.getXmlDocument());
        }

        // if requested, dump each intermediary merging stage into the report.
        if (mOptionalFeatures.contains(Invoker.Feature.KEEP_INTERMEDIARY_STAGES)
                && result.isPresent()) {
            mergingReportBuilder.addMergingStage(result.get().prettyPrint());
        }

        return result;
    }

    private List<LoadedManifestInfo> loadLibraries(SelectorResolver selectors)
            throws MergeFailureException {

        ImmutableList.Builder<LoadedManifestInfo> loadedLibraryDocuments = ImmutableList.builder();
        for (Pair<String, File> libraryFile : mLibraryFiles) {
            mLogger.info("Loading library manifest " + libraryFile.getSecond().getPath());
            ManifestInfo manifestInfo = new ManifestInfo(libraryFile.getFirst(), libraryFile.getSecond(),
                    ManifestInfo.Type.LIBRARY);
            XmlDocument libraryDocument;
            try {
                libraryDocument = XmlLoader.load(selectors,
                        mSystemPropertyResolver,
                        manifestInfo.mName, manifestInfo.mLocation);
            } catch (Exception e) {
                throw new MergeFailureException(e);
            }
            // extract the package name...
            String libraryPackage = libraryDocument.getRootNode().getXml().getAttribute("package");
            // save it in the selector instance.
            if (!Strings.isNullOrEmpty(libraryPackage)) {
                selectors.addSelector(libraryPackage, libraryFile.getFirst());
            }

            loadedLibraryDocuments.add(new LoadedManifestInfo(manifestInfo, libraryDocument));
        }
        return loadedLibraryDocuments.build();
    }

    /**
     * Creates a new {@link com.android.manifmerger.ManifestMerger2.Invoker} instance to invoke
     * the merging tool to merge manifest files for an application.
     *
     * @param mainManifestFile application main manifest file.
     * @param logger the logger interface to use.
     * @return an {@link com.android.manifmerger.ManifestMerger2.Invoker} instance that will allow
     * further customization and trigger the merging tool.
     */
    public static Invoker newMerger(@NonNull File mainManifestFile,
            @NonNull ILogger logger, @NonNull MergeType mergeType) {
        return new Invoker(mainManifestFile, logger, mergeType);
    }

    /**
     * List of manifest files properties that can be directly overridden without using a
     * placeholder.
     */
    public static enum SystemProperty implements AutoAddingProperty {

        /**
         * Allow setting the merged manifest file package name.
         */
        PACKAGE {
            @Override
            public void addTo(@NonNull ActionRecorder actionRecorder,
                    @NonNull XmlDocument document,
                    @NonNull String value) {
                addToElement(this, actionRecorder, value, document.getRootNode());
            }
        },
        /**
         * http://developer.android.com/guide/topics/manifest/manifest-element.html#vcode
         */
        VERSION_CODE {
            @Override
            public void addTo(@NonNull ActionRecorder actionRecorder,
                    @NonNull XmlDocument document,
                    @NonNull String value) {
                addToElementInAndroidNS(this, actionRecorder, value, document.getRootNode());
            }
        },
        /**
         * http://developer.android.com/guide/topics/manifest/manifest-element.html#vname
         */
        VERSION_NAME {
            @Override
            public void addTo(@NonNull ActionRecorder actionRecorder,
                    @NonNull XmlDocument document,
                    @NonNull String value) {
                addToElementInAndroidNS(this, actionRecorder, value, document.getRootNode());
            }
        },
        /**
         * http://developer.android.com/guide/topics/manifest/uses-sdk-element.html#min
         */
        MIN_SDK_VERSION {
            @Override
            public void addTo(@NonNull ActionRecorder actionRecorder,
                    @NonNull XmlDocument document,
                    @NonNull String value) {
                addToElementInAndroidNS(this, actionRecorder, value,
                        createOrGetUseSdk(actionRecorder, document));
            }
        },
        /**
         * http://developer.android.com/guide/topics/manifest/uses-sdk-element.html#target
         */
        TARGET_SDK_VERSION {
            @Override
            public void addTo(@NonNull ActionRecorder actionRecorder,
                    @NonNull XmlDocument document,
                    @NonNull String value) {
                addToElementInAndroidNS(this, actionRecorder, value,
                        createOrGetUseSdk(actionRecorder, document));
            }
        };

        public String toCamelCase() {
            return SdkUtils.constantNameToCamelCase(name());
        }

        // utility method to add an attribute which name is derived from the enum name().
        private static void addToElement(
                SystemProperty systemProperty,
                ActionRecorder actionRecorder,
                String value,
                XmlElement to) {

            to.getXml().setAttribute(systemProperty.toCamelCase(), value);
            XmlAttribute xmlAttribute = new XmlAttribute(to,
                    to.getXml().getAttributeNode(systemProperty.toCamelCase()), null);
            actionRecorder.recordAttributeAction(xmlAttribute, new Actions.AttributeRecord(
                    Actions.ActionType.INJECTED,
                    new Actions.ActionLocation(
                            to.getSourceLocation(),
                            PositionImpl.UNKNOWN),
                    xmlAttribute.getId(),
                    null, /* reason */
                    null /* attributeOperationType */));
        }

        // utility method to add an attribute in android namespace which local name is derived from
        // the enum name().
        private static void addToElementInAndroidNS(
                SystemProperty systemProperty,
                ActionRecorder actionRecorder,
                String value,
                XmlElement to) {

            String toolsPrefix = XmlUtils.lookupNamespacePrefix(
                    to.getXml(), SdkConstants.ANDROID_URI, SdkConstants.ANDROID_NS_NAME, false);
            to.getXml().setAttributeNS(SdkConstants.ANDROID_URI,
                    toolsPrefix + XmlUtils.NS_SEPARATOR + systemProperty.toCamelCase(),
                    value);
            Attr attr = to.getXml().getAttributeNodeNS(SdkConstants.ANDROID_URI,
                    systemProperty.toCamelCase());

            XmlAttribute xmlAttribute = new XmlAttribute(to, attr, null);
            actionRecorder.recordAttributeAction(xmlAttribute,
                    new Actions.AttributeRecord(
                            Actions.ActionType.INJECTED,
                            new Actions.ActionLocation(
                                    to.getSourceLocation(),
                                    PositionImpl.UNKNOWN),
                            xmlAttribute.getId(),
                            null, /* reason */
                            null /* attributeOperationType */
                    )
            );

        }

        // utility method to create or get an existing use-sdk xml element under manifest.
        // this could be made more generic by adding more metadata to the enum but since there is
        // only one case so far, keep it simple.
        private static XmlElement createOrGetUseSdk(
                ActionRecorder actionRecorder, XmlDocument document) {

            Element manifest = document.getXml().getDocumentElement();
            NodeList usesSdks = manifest
                    .getElementsByTagName(ManifestModel.NodeTypes.USES_SDK.toXmlName());
            if (usesSdks.getLength() == 0) {
                // create it first.
                Element useSdk = manifest.getOwnerDocument().createElement(
                        ManifestModel.NodeTypes.USES_SDK.toXmlName());
                manifest.appendChild(useSdk);
                XmlElement xmlElement = new XmlElement(useSdk, document);
                Actions.NodeRecord nodeRecord = new Actions.NodeRecord(
                        Actions.ActionType.INJECTED,
                        new Actions.ActionLocation(xmlElement.getSourceLocation(),
                                PositionImpl.UNKNOWN),
                        xmlElement.getId(),
                        "use-sdk injection requested",
                        NodeOperationType.STRICT);
                actionRecorder.recordNodeAction(xmlElement, nodeRecord);
                return xmlElement;
            } else {
                return new XmlElement((Element) usesSdks.item(0), document);
            }
        }
    }

    /**
     * Defines the merging type expected from the tool.
     */
    public enum MergeType {
        /**
         * Application merging type is used when packaging an application with a set of imported
         * libraries. The resulting merged android manifest is final and is not expected to be
         * imported in another application.
         */
        APPLICATION,

        /**
         * Library merging typee is used when packaging a library. The resulting android manifest
         * file will not merge in all the imported libraries this library depends on. Also the tools
         * annotations will not be removed as they can be useful when later importing the resulting
         * merged android manifest into an application.
         */
        LIBRARY
    }

    /**
     * Defines a property that can add or override itself into an XML document.
     */
    public interface AutoAddingProperty {

        /**
         * Add itself (possibly just override the current value) with the passed value
         * @param actionRecorder to record actions.
         * @param document the xml document to add itself to.
         * @param value the value to set of this property.
         */
        void addTo(@NonNull ActionRecorder actionRecorder,
                @NonNull XmlDocument document,
                @NonNull String value);
    }

    /**
     * Perform {@link com.android.manifmerger.ManifestMerger2.SystemProperty} injection.
     * @param mergingReport to log actions and errors.
     * @param xmlDocument the xml document to inject into.
     */
    protected void performSystemPropertiesInjection(
            MergingReport.Builder mergingReport,
            XmlDocument xmlDocument) {
        for (SystemProperty systemProperty : SystemProperty.values()) {
            String propertyOverride = mSystemPropertyResolver.getValue(systemProperty);
            if (propertyOverride != null) {
                systemProperty.addTo(mergingReport.getActionRecorder(), xmlDocument, propertyOverride);
            }
        }
    }

    /**
     * This class will hold all invocation parameters for the manifest merging tool.
     *
     * There are broadly three types of input to the merging tool :
     * <ul>
     *     <li>Build types and flavors overriding manifests</li>
     *     <li>Application main manifest</li>
     *     <li>Library manifest files</li></lib>
     * </ul>
     *
     * Only the main manifest file is a mandatory parameter.
     *
     * High level description of the merging will be as follow :
     * <ol>
     *     <li>Build type and flavors will be merged first in the order they were added. Highest
     *     priority file added first, lowest added last.</li>
     *     <li>Resulting document is merged with lower priority application main manifest file.</li>
     *     <li>Resulting document is merged with each library file manifest file in the order
     *     they were added. Highest priority added first, lowest added last.</li>
     *     <li>Resulting document is returned as results of the merging process.</li>
     * </ol>
     *
     */
    public static final class Invoker<T extends Invoker<T>>{

        protected final File mMainManifestFile;

        protected final ImmutableMap.Builder<SystemProperty, String> mSystemProperties =
                new ImmutableMap.Builder<SystemProperty, String>();

        protected final ILogger mLogger;

        protected final ImmutableMap.Builder<String, String> mPlaceHolders =
                new ImmutableMap.Builder<String, String>();

        /**
         * Sets a value for a {@link com.android.manifmerger.ManifestMerger2.SystemProperty}
         * @param override the property to set
         * @param value the value for the property
         * @return itself.
         */
        public Invoker setOverride(SystemProperty override, String value) {
            mSystemProperties.put(override, value);
            return thisAsT();
        }

        /**
         * Adds a new placeholder name and value for substitution.
         * @return itself.
         */
        public Invoker setPlaceHolderValue(String placeHolderName, String value) {
            mPlaceHolders.put(placeHolderName, value);
            return thisAsT();
        }

        /**
         * Optional behavior of the merging tool can be turned on by setting these Feature.
         */
        public enum Feature {

            /**
             * Keep all intermediary merged files during the merging process. This is particularly
             * useful for debugging/tracing purposes.
             */
            KEEP_INTERMEDIARY_STAGES,

            /**
             * When logging file names, use {@link java.io.File#getName()} rather than
             * {@link java.io.File#getPath()}
             */
            PRINT_SIMPLE_FILENAMES
        }

        private final ImmutableList.Builder<Pair<String, File>> mLibraryFilesBuilder =
                new ImmutableList.Builder<Pair<String, File>>();
        private final ImmutableList.Builder<File> mFlavorsAndBuildTypeFiles =
                new ImmutableList.Builder<File>();
        private final ImmutableList.Builder<Feature> mFeaturesBuilder =
                new ImmutableList.Builder<Feature>();
        private final MergeType mMergeType;

        /**
         * Creates a new builder with the mandatory main manifest file.
         * @param mainManifestFile application main manifest file.
         * @param logger the logger interface to use.
         */
        private Invoker(
                @NonNull File mainManifestFile, @NonNull ILogger logger, MergeType mergeType) {
            this.mMainManifestFile = Preconditions.checkNotNull(mainManifestFile);
            this.mLogger = logger;
            this.mMergeType = mergeType;
        }

        /**
         * Add one library file manifest, will be added last in the list of library files which will
         * make the parameter the lowest priority library manifest file.
         * @param file the library manifest file to add.
         * @return itself.
         */
        public Invoker addLibraryManifest(File file) {
            if (mMergeType == MergeType.LIBRARY) {
                throw new IllegalStateException(
                        "Cannot add library dependencies manifests when creating a library");
            }
            mLibraryFilesBuilder.add(Pair.of(file.getName(), file));
            return thisAsT();
        }

        public Invoker addLibraryManifests(List<Pair<String, File>> namesAndFiles) {
            if (mMergeType == MergeType.LIBRARY && !namesAndFiles.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot add library dependencies manifests when creating a library");
            }
            mLibraryFilesBuilder.addAll(namesAndFiles);
            return thisAsT();
        }

        /**
         * Add several library file manifests at then end of the list which will make them the
         * lowest priority manifest files. The relative priority between all the files passed as
         * parameters will be respected.
         * @param files library manifest files to add last.
         * @return itself.
         */
        public Invoker addLibraryManifests(File... files) {
            for (File file : files) {
                addLibraryManifest(file);
            }
            return thisAsT();
        }

        /**
         * Add a flavor or build type manifest file last in the list.
         * @param file build type or flavor manifest file
         * @return itself.
         */
        public Invoker addFlavorAndBuildTypeManifest(File file) {
            this.mFlavorsAndBuildTypeFiles.add(file);
            return thisAsT();
        }

        /**
         * Add several flavor or build type manifest files last in the list. Relative priorities
         * between the passed files as parameters will be respected.
         * @param files build type of flavor manifest files to add.
         * @return itself.
         */
        public Invoker addFlavorAndBuildTypeManifests(File... files) {
            this.mFlavorsAndBuildTypeFiles.add(files);
            return thisAsT();
        }

        /**
         * Sets some optional features for the merge tool.
         *
         * @param features one to many features to set.
         * @return itself.
         */
        public Invoker withFeatures(Feature...features) {
            mFeaturesBuilder.add(features);
            return thisAsT();
        }

        /**
         * Perform the merging and return the result.
         *
         * @return an instance of {@link com.android.manifmerger.MergingReport} that will give
         * access to all the logging and merging records.
         *
         * This method can be invoked several time and will re-do the file merges.
         *
         * @throws com.android.manifmerger.ManifestMerger2.MergeFailureException if the merging cannot be completed successfully.
         */
        public MergingReport merge() throws MergeFailureException {

            // provide some free placeholders values.
            ImmutableMap<SystemProperty, String> systemProperties = mSystemProperties.build();
            if (systemProperties.containsKey(SystemProperty.PACKAGE)) {
                mPlaceHolders.put("packageName", systemProperties.get(SystemProperty.PACKAGE));
            }

            ManifestMerger2 manifestMerger =
                    new ManifestMerger2(
                            mLogger,
                            mMainManifestFile,
                            mLibraryFilesBuilder.build(),
                            mFlavorsAndBuildTypeFiles.build(),
                            mFeaturesBuilder.build(),
                            new MapBasedKeyBasedValueResolver<String>(mPlaceHolders.build()),
                            new MapBasedKeyBasedValueResolver<SystemProperty>(
                                    systemProperties),
                            mMergeType);
            return manifestMerger.merge();
        }

        @SuppressWarnings("unchecked")
        private T thisAsT() {
            return (T) this;
        }
    }

    /**
     * Helper class for map based placeholders key value pairs.
     */
    public static class MapBasedKeyBasedValueResolver<T> implements KeyBasedValueResolver<T> {

        private final ImmutableMap<T, String> keyValues;

        public MapBasedKeyBasedValueResolver(Map<T, String> keyValues) {
            this.keyValues = ImmutableMap.copyOf(keyValues);
        }

        @Nullable
        @Override
        public String getValue(@NonNull T key) {
            return keyValues.get(key);
        }
    }

    private static class ManifestInfo {

        private ManifestInfo(String name, File location, Type type) {
            mName = name;
            mLocation = location;
            mType = type;
        }

        enum Type {
            OVERLAY, MAIN, LIBRARY
        }

        private final String mName;
        private final File mLocation;
        private final Type mType;

        File getLocation() {
            return mLocation;
        }
    }

    private static class LoadedManifestInfo extends ManifestInfo {

        private final XmlDocument mXmlDocument;

        private LoadedManifestInfo(ManifestInfo manifestInfo ,XmlDocument xmlDocument) {
            super(manifestInfo.mName, manifestInfo.mLocation, manifestInfo.mType);
            mXmlDocument = xmlDocument;
        }

        public XmlDocument getXmlDocument() {
            return mXmlDocument;
        }
    }

    /**
     * Implementation a {@link com.android.manifmerger.KeyResolver} capable of resolving all
     * selectors value in the context of the passed libraries to this merging activities.
     */
    static class SelectorResolver implements KeyResolver<String> {

        private final Map<String, String> mSelectors = new HashMap<String, String>();

        protected void addSelector(String key, String value) {
            mSelectors.put(key, value);
        }

        @Nullable
        @Override
        public String resolve(String key) {
            return mSelectors.get(key);
        }

        @Override
        public Iterable<String> getKeys() {
            return mSelectors.keySet();
        }
    }

    // a wrapper exception to all sorts of failure exceptions that can be thrown during merging.
    public static class MergeFailureException extends Exception {

        protected MergeFailureException(Exception cause) {
            super(cause);
        }
    }
}
