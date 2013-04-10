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

package com.android.ide.common.res2;

import static com.android.SdkConstants.ANDROID_NEW_ID_PREFIX;
import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_PREFIX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DeclareStyleableResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.google.common.base.Splitter;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A resource.
 *
 * This includes the name, type, source file as a {@link ResourceFile} and an optional {@link Node}
 * in case of a resource coming from a value file.
 *
 */
class ResourceItem extends DataItem<ResourceFile> implements Configurable  {

    private static final int DEFAULT_NS_PREFIX_LEN = ANDROID_NS_NAME_PREFIX.length();

    private final ResourceType mType;
    private Node mValue;

    private ResourceValue mResourceValue;

    /**
     * Constructs the object with a name, type and optional value.
     *
     * Note that the object is not fully usable as-is. It must be added to a ResourceFile first.
     *
     * @param name the name of the resource
     * @param type the type of the resource
     * @param value an optional Node that represents the resource value.
     */
    ResourceItem(@NonNull String name, @NonNull ResourceType type, Node value) {
        super(name);
        mType = type;
        mValue = value;
    }

    /**
     * Returns the type of the resource.
     * @return the type.
     */
    @NonNull
    public ResourceType getType() {
        return mType;
    }

    /**
     * Returns the optional value of the resource. Can be null
     * @return the value or null.
     */
    public Node getValue() {
        return mValue;
    }

    /**
     * Sets the value of the resource and set its state to TOUCHED.
     * @param from the resource to copy the value from.
     */
    void setValue(ResourceItem from) {
        mValue = from.mValue;
        setTouched();
    }

    @Override
    public FolderConfiguration getConfiguration() {
        assert getSource() != null;

        String qualifier = getSource().getQualifiers();
        if (qualifier.isEmpty()) {
            return new FolderConfiguration();
        }

        return FolderConfiguration.getConfigFromQualifiers(Splitter.on('-').split(qualifier));
    }

    /**
     * Returns a key for this resource. They key uniquely identifies this resource by combining
     * resource type, qualifiers, and name.
     *
     * If the resource has not been added to a {@link ResourceFile}, this will throw an
     * {@link IllegalStateException}.
     *
     * @return the key for this resource.
     *
     * @throws IllegalStateException if the resource is not added to a ResourceFile
     */
    @Override
    String getKey() {
        if (getSource() == null) {
            throw new IllegalStateException(
                    "ResourceItem.getKey called on object with no ResourceFile: " + this);
        }
        String qualifiers = getSource().getQualifiers();
        if (!qualifiers.isEmpty()) {
            return mType.getName() + "-" + qualifiers + "/" + getName();
        }

        return mType.getName() + "/" + getName();
    }

    @Override
    void addExtraAttributes(Document document, Node node, String namespaceUri) {
        NodeUtils.addAttribute(document, node, null, ATTR_TYPE, mType.getName());
    }

    @Override
    Node getAdoptedNode(Document document) {
        return NodeUtils.adoptNode(document, mValue);
    }

    @Override
    protected void wasTouched() {
        mResourceValue = null;
    }

    @Nullable
    ResourceValue getResourceValue(boolean isFrameworks) {
        if (mResourceValue == null) {
            if (mValue == null) {
                mResourceValue = new ResourceValue(mType, getName(),
                        getSource().getFile().getAbsolutePath(), isFrameworks);
            } else {
                mResourceValue = parseXmlToResourceValue(isFrameworks);
            }
        }

        return mResourceValue;
    }

    /**
     * Returns a formatted string usable in an XML to use for the {@link ResourceItem}.
     * @param system Whether this is a system resource or a project resource.
     * @return a string in the format @[type]/[name]
     */
    public String getXmlString(ResourceType type, boolean system) {
        if (type == ResourceType.ID /* && isDeclaredInline()*/) {
            return (system ? ANDROID_NEW_ID_PREFIX : NEW_ID_PREFIX) + "/" + getName();
        }

        return (system ? ANDROID_PREFIX : PREFIX_RESOURCE_REF) + type.getName() + "/" + getName();
    }

    /**
     * Compares the ResourceItem {@link #getValue()} together and returns true if they are the same.
     * @param resource The ResourceItem object to compare to.
     * @return true if equal
     */
    public boolean compareValueWith(ResourceItem resource) {
        if (mValue != null && resource.mValue != null) {
            return NodeUtils.compareElementNode(mValue, resource.mValue);
        }

        return mValue == resource.mValue;
    }

    @Override
    public String toString() {
        return "ResourceItem{" +
                "mName='" + getName() + '\'' +
                ", mType=" + mType +
                ", mStatus=" + getStatus() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ResourceItem that = (ResourceItem) o;

        if (mType != that.mType) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + mType.hashCode();
        return result;
    }

    @Nullable
    private ResourceValue parseXmlToResourceValue(boolean isFrameworks) {
        assert mValue != null;

        NamedNodeMap attributes = mValue.getAttributes();
        ResourceType type = getType(mValue.getLocalName(), attributes);
        if (type == null) {
            return null;
        }

        ResourceValue value;
        String name = getName();

        switch (type) {
            case STYLE:
                String parent = getAttributeValue(attributes, ATTR_PARENT);
                value = parseStyleValue(new StyleResourceValue(type, name, parent, isFrameworks));
                break;
            case DECLARE_STYLEABLE:
                value = new DeclareStyleableResourceValue(
                        type, name, isFrameworks);
                break;
            case ATTR:
                value = parseAttrValue(new AttrResourceValue(type, name, isFrameworks));
                break;
            default:
                value = parseValue(new ResourceValue(type, name, isFrameworks));
                break;
        }

        return value;
    }

    @Nullable
    private ResourceType getType(String qName, NamedNodeMap attributes) {
        String typeValue;

        // if the node is <item>, we get the type from the attribute "type"
        if (SdkConstants.TAG_ITEM.equals(qName)) {
            typeValue = getAttributeValue(attributes, ATTR_TYPE);
        } else {
            // the type is the name of the node.
            typeValue = qName;
        }

        return ResourceType.getEnum(typeValue);
    }

    @Nullable
    private static String getAttributeValue(NamedNodeMap attributes, String attributeName) {
        Attr attribute = (Attr) attributes.getNamedItem(attributeName);
        if (attribute != null) {
            return attribute.getValue();
        }

        return null;
    }

    @NonNull
    private ResourceValue parseStyleValue(@NonNull StyleResourceValue styleValue) {
        NodeList children = mValue.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = child.getAttributes();
                String name = getAttributeValue(attributes, ATTR_NAME);
                if (name != null) {

                    // is the attribute in the android namespace?
                    boolean isFrameworkAttr = styleValue.isFramework();
                    if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                        name = name.substring(DEFAULT_NS_PREFIX_LEN);
                        isFrameworkAttr = true;
                    }

                    ResourceValue resValue = new ResourceValue(null, name, isFrameworkAttr);
                    resValue.setValue(
                            ValueXmlHelper.unescapeResourceString(getTextNode(child), false, true));
                    styleValue.addValue(resValue, isFrameworkAttr);
                }
            }
        }

        return styleValue;
    }

    @NonNull
    private ResourceValue parseAttrValue(@NonNull AttrResourceValue attrValue) {
        NodeList children = mValue.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = child.getAttributes();
                String name = getAttributeValue(attributes, ATTR_NAME);
                if (name != null) {
                    String value = getAttributeValue(attributes, ATTR_VALUE);
                    if (value != null) {
                        try {
                            // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we
                            // use Long.decode instead.
                            attrValue.addValue(name, (int)(long)Long.decode(value));
                        } catch (NumberFormatException e) {
                            // pass, we'll just ignore this value
                        }
                    }
                }
            }
        }

        return attrValue;
    }

    @NonNull
    private ResourceValue parseValue(@NonNull ResourceValue value) {
        value.setValue(ValueXmlHelper.unescapeResourceString(getTextNode(mValue), false, true));
        return value;
    }

    @NonNull
    private static String getTextNode(Node node) {
        StringBuilder sb = new StringBuilder();

        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            short nodeType = child.getNodeType();

            switch (nodeType) {
                case Node.ELEMENT_NODE:
                    String str = getTextNode(child);
                    sb.append(str);
                    break;
                case Node.TEXT_NODE:
                    sb.append(child.getNodeValue());
                    break;
            }
        }

        return sb.toString();
    }
}
