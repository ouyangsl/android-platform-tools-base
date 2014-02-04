<?xml version="1.0"?>
<recipe>

    <dependency mavenUrl="com.android.support:support-v4:+"/>
    <dependency mavenUrl="com.google.android.gms:play-services:4.0.+"/>

    <merge from="AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <merge from="res/values/strings.xml" to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="res/layout/fragment_plus_one.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/fragment_${classToResource(className)}.xml" />

    <open file="${escapeXmlAttribute(resOut)}/layout/fragment_${classToResource(className)}.xml" />

    <open file="${escapeXmlAttribute(srcOut)}/${className}.java" />

    <instantiate from="src/app_package/PlusOneFragment.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${className}.java" />

</recipe>
