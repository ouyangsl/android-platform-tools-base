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
package com.android.ide.common.vectordrawable;

import static com.android.io.Images.readImage;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.util.GeneratorTester;
import com.android.testutils.TestResources;

import junit.framework.TestCase;

import org.junit.Assert;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/** Tests for {@link Svg2Vector} and {@link VdPreview} classes. */
public class VectorDrawableGeneratorTest extends TestCase {
    private static final int IMAGE_SIZE = 64;
    /** Due to rendering differences between AWT implementations on different operating systems. */
    private static final float DIFF_THRESHOLD_PERCENT = 1.25f;

    private static final Pattern INVALID_XML_PATTERN = Pattern.compile("pathData=\"\\s*\"");

    private static final GeneratorTester GENERATOR_TESTER =
            GeneratorTester.withTestDataRelativePath(
                    "tools/base/sdk-common/src/test/resources/testData/vectordrawable");

    private enum FileType {
        SVG,
        XML
    }

    /** Checks conversion and returns contents of the error log. */
    @Nullable
    private String checkVectorConversion(
            @NonNull String testFileName,
            @NonNull FileType type,
            boolean dumpXml,
            @Nullable String expectedError)
            throws Exception {
        return checkVectorConversion(testFileName, type, dumpXml, expectedError, IMAGE_SIZE);
    }

    /** Checks conversion and returns contents of the error log. */
    @Nullable
    private String checkVectorConversion(
            @NonNull String testFileName,
            @NonNull FileType type,
            boolean dumpXml,
            @Nullable String expectedError,
            int imageSize)
            throws Exception {
        String incomingFileName;
        if (type == FileType.SVG) {
            incomingFileName = testFileName + ".svg";
        } else {
            incomingFileName = testFileName + ".xml";
        }
        String imageName = testFileName + ".png";

        String parentDirName =  "vectordrawable" + File.separator;
        Path parentDir =
                TestResources.getDirectory(getClass(), "/testData/vectordrawable").toPath();
        Path incomingFile = parentDir.resolve(incomingFileName);
        String xmlContent;
        String errorLog = null;
        if (type == FileType.SVG) {
            OutputStream outStream = new ByteArrayOutputStream();
            errorLog = Svg2Vector.parseSvgToXml(incomingFile, outStream);
            if (expectedError != null) {
                assertNotNull(errorLog);
                assertFalse(errorLog.isEmpty());
                assertTrue(errorLog.contains(expectedError));
            }
            xmlContent = outStream.toString();
            if (xmlContent.isEmpty()) {
                if (expectedError == null) {
                    fail("Empty XML file.");
                }
                return errorLog;
            }
            if (dumpXml) {
                Path tempXmlFile = parentDir.resolve(imageName + ".xml");
                try (PrintWriter writer = new PrintWriter(tempXmlFile.toFile(), UTF_8.name())) {
                    writer.println(xmlContent);
                }
            }
            if (INVALID_XML_PATTERN.matcher(xmlContent).find()) {
                fail("Invalid VectorDrawable produced");
            }
        } else {
            xmlContent = new String(Files.readAllBytes(incomingFile), StandardCharsets.UTF_8);
        }

        VdPreview.TargetSize imageTargetSize =
                VdPreview.TargetSize.createFromMaxDimension(imageSize);
        StringBuilder builder = new StringBuilder();
        BufferedImage image =
                VdPreview.getPreviewFromVectorXml(imageTargetSize, xmlContent, builder);

        String imageNameWithParent = parentDirName + imageName;
        Path pngFile = parentDir.resolve(imageName);
        if (Files.notExists(pngFile)) {
            String golden = imageNameWithParent;
            String path = parentDir.toString();
            int pos = path.replace('\\', '/').indexOf("/tools/base/");
            if (pos > 0) {
                golden = path.substring(0, pos) + File.separator
                        + GENERATOR_TESTER.getTestDataRelPath() + File.separator + imageName;
            }
            GENERATOR_TESTER.generateGoldenImage(image, golden, imageName);
            fail("Golden file " + golden + " didn't exist, created by the test.");
        } else {
            BufferedImage goldenImage = readImage(pngFile);
            GeneratorTester.assertImageSimilar(
                    imageNameWithParent, goldenImage, image, DIFF_THRESHOLD_PERCENT);
        }

        return errorLog;
    }

    /** Checks SVG conversion and returns contents of the error log. */
    @Nullable
    private String checkSvgConversion(@NonNull String filename) throws Exception {
        return checkVectorConversion(filename, FileType.SVG, false, null);
    }

    private void checkXmlConversion(@NonNull String filename) throws Exception {
        checkVectorConversion(filename, FileType.XML, false, null);
    }

    private void checkSvgConversionAndContainsError(
            @NonNull String filename, @Nullable String expectedError) throws Exception {
        checkVectorConversion(filename, FileType.SVG, false, expectedError);
    }

    /** Checks SVG conversion and returns contents of the error log. */
    @SuppressWarnings("unused") // Method intended for debugging.
    @Nullable
    private String checkSvgConversionDebug(@NonNull String filename) throws Exception {
        return checkVectorConversion(filename, FileType.SVG, true, null);
    }

    //////////////////////////////////////////////////////////
    // Tests start here:
    public void testSvgFillAlpha() throws Exception {
        checkSvgConversion("ic_add_to_notepad_black");
    }

    public void testSvgArcto1() throws Exception {
        checkSvgConversion("test_arcto_1");
    }

    public void testSvgArcto2() throws Exception {
        checkSvgConversion("test_arcto_2");
    }

    public void testSvgControlPoints01() throws Exception {
        checkSvgConversion("test_control_points_01");
    }

    public void testSvgControlPoints02() throws Exception {
        checkSvgConversion("test_control_points_02");
    }

    public void testSvgControlPoints03() throws Exception {
        checkSvgConversion("test_control_points_03");
    }

    public void testSvgContentCut() throws Exception {
        checkSvgConversion("ic_content_cut_24px");
    }

    public void testSvgInput() throws Exception {
        checkSvgConversion("ic_input_24px");
    }

    public void testSvgLiveHelp() throws Exception {
        checkSvgConversion("ic_live_help_24px");
    }

    public void testSvgLocalLibrary() throws Exception {
        checkSvgConversion("ic_local_library_24px");
    }

    public void testSvgLocalPhone() throws Exception {
        checkSvgConversion("ic_local_phone_24px");
    }

    public void testSvgMicOff() throws Exception {
        checkSvgConversion("ic_mic_off_24px");
    }

    public void testSvgShapes() throws Exception {
        checkSvgConversion("ic_shapes");
    }

    public void testSvgEllipse() throws Exception {
        checkSvgConversion("test_ellipse");
    }

    public void testSvgTempHigh() throws Exception {
        checkSvgConversion("ic_temp_high");
    }

    public void testSvgPlusSign() throws Exception {
        checkSvgConversion("ic_plus_sign");
    }

    public void testSvgPolylineStrokeWidth() throws Exception {
        checkSvgConversion("ic_polyline_strokewidth");
    }

    public void testSvgStrokeWidthUniformTransform() throws Exception {
        checkSvgConversion("ic_strokewidth_uniform_transform");
    }

    public void testSvgStrokeWidthNonuniformTransform() throws Exception {
        checkSvgConversionAndContainsError(
                "ic_strokewidth_nonuniform_transform",
                "Scaling of the stroke width is approximate");
    }

    public void testSvgSemiTransparentMaskNotValid() throws Exception {
        checkSvgConversionAndContainsError(
                "ic_semitransparent_mask",
                "Semitransparent mask cannot be represented by a vector drawable");
    }

    public void testSvgEmptyAttributes() throws Exception {
        checkSvgConversion("ic_empty_attributes");
    }

    public void testSvgEmptyPathData() throws Exception {
        checkSvgConversion("ic_empty_path_data");
    }

    public void testSvgSimpleGroupInfo() throws Exception {
        checkSvgConversion("ic_simple_group_info");
    }

    public void testSvgContainsError() throws Exception {
        checkSvgConversionAndContainsError("ic_contains_ignorable_error",
                "ERROR @ line 16: <switch> is not supported\n" +
                "ERROR @ line 17: <foreignObject> is not supported");
    }

    public void testParseError() throws Exception {
        checkSvgConversionAndContainsError(
                "test_parse_error",
                "ERROR: Element type \"path\" must be followed by either attribute specifications, \">\" or \"/>\".");
    }

    public void testSvgLineToMoveTo() throws Exception {
        checkSvgConversion("test_lineto_moveto");
    }

    public void testSvgLineToMoveTo2() throws Exception {
        checkSvgConversion("test_lineto_moveto2");
    }

    public void testSvgLineToMoveToViewbox1() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox1");
    }

    public void testSvgLineToMoveToViewbox2() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox2");
    }

    public void testSvgLineToMoveToViewbox3() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox3");
    }

    // It seems like different implementations has different results on this svg.
    public void testSvgLineToMoveToViewbox4() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox4");
    }

    public void testSvgLineToMoveToViewbox5() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox5");
    }

    public void testSvgImplicitLineToAfterMoveTo() throws Exception {
        checkSvgConversion("test_implicit_lineto_after_moveto");
    }

    public void testRoundRectPercentage() throws Exception {
        checkSvgConversion("test_round_rect_percentage");
    }

    public void testSvgColorFormats() throws Exception {
        checkSvgConversion("test_color_formats");
    }

    public void testSvgPaintOrder() throws Exception {
        checkSvgConversion("test_paint_order");
    }

    public void testSvgTransformArcComplex1() throws Exception {
        checkSvgConversion("test_transform_arc_complex1");
    }

    public void testSvgTransformArcComplex2() throws Exception {
        checkSvgConversion("test_transform_arc_complex2");
    }

    public void testSvgTransformArcRotateScaleTranslate() throws Exception {
        checkSvgConversion("test_transform_arc_rotate_scale_translate");
    }

    public void testSvgTransformArcScale() throws Exception {
        checkSvgConversion("test_transform_arc_scale");
    }

    public void testSvgTransformArcScaleRotate() throws Exception {
        checkSvgConversion("test_transform_arc_scale_rotate");
    }

    public void testSvgTransformArcSkewx() throws Exception {
        checkSvgConversion("test_transform_arc_skewx");
    }

    public void testSvgTransformArcSkewy() throws Exception {
        checkSvgConversion("test_transform_arc_skewy");
    }

    public void testSvgTransformBigArcComplex() throws Exception {
        checkSvgConversion("test_transform_big_arc_complex");
    }

    public void testSvgTransformBigArcComplexViewbox() throws Exception {
        checkSvgConversion("test_transform_big_arc_complex_viewbox");
    }

    public void testSvgTransformBigArcScale() throws Exception {
        checkSvgConversion("test_transform_big_arc_translate_scale");
    }

    public void testSvgTransformDegenerateArc() throws Exception {
        checkSvgConversion("test_transform_degenerate_arc");
    }

    public void testSvgArcWithoutSeparatorBetweenFlags() throws Exception {
        checkSvgConversion("test_arc_without_separator_between_flags");
    }

    public void testSvgTransformCircleRotate() throws Exception {
        checkSvgConversion("test_transform_circle_rotate");
    }

    public void testSvgTransformCircleScale() throws Exception {
        checkSvgConversion("test_transform_circle_scale");
    }

    public void testSvgTransformCircleMatrix() throws Exception {
        checkSvgConversion("test_transform_circle_matrix");
    }

    public void testSvgTransformRectMatrix() throws Exception {
        checkSvgConversion("test_transform_rect_matrix");
    }

    public void testSvgTransformRoundRectMatrix() throws Exception {
        checkSvgConversion("test_transform_round_rect_matrix");
    }

    public void testSvgTransformRectRotate() throws Exception {
        checkSvgConversion("test_transform_rect_rotate");
    }

    public void testSvgTransformRectScale() throws Exception {
        checkSvgConversion("test_transform_rect_scale");
    }

    public void testSvgTransformRectSkewx() throws Exception {
        checkSvgConversion("test_transform_rect_skewx");
    }

    public void testSvgTransformRectSkewy() throws Exception {
        checkSvgConversion("test_transform_rect_skewy");
    }

    public void testSvgTransformRectTranslate() throws Exception {
        checkSvgConversion("test_transform_rect_translate");
    }

    public void testSvgTransformHVLoopBasic() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_basic");
    }

    public void testSvgTransformHVLoopTranslate() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_translate");
    }

    public void testSvgTransformHVLoopMatrix() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_matrix");
    }

    public void testSvgTransformHVACComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_a_c_complex");
    }

    public void testSvgTransformHVAComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_a_complex");
    }

    public void testSvgTransformHVCQ() throws Exception {
        checkSvgConversion("test_transform_h_v_c_q");
    }

    public void testSvgTransformHVCQComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_c_q_complex");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformHVLoopComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_complex");
    }

    public void testSvgTransformHVSTComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_s_t_complex");
    }

    public void testSvgTransformHVSTComplex2() throws Exception {
        checkSvgConversion("test_transform_h_v_s_t_complex2");
    }

    public void testSvgTransformCQNoMove() throws Exception {
        checkSvgConversion("test_transform_c_q_no_move");
    }
    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformMultiple1() throws Exception {
        checkSvgConversion("test_transform_multiple_1");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformMultiple2() throws Exception {
        checkSvgConversion("test_transform_multiple_2");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformMultiple3() throws Exception {
        checkSvgConversion("test_transform_multiple_3");
    }

    public void testSvgTransformMultiple4() throws Exception {
        checkSvgConversion("test_transform_multiple_4");
    }

    public void testSvgTransformGroup1() throws Exception {
        checkSvgConversion("test_transform_group_1");
    }

    public void testSvgTransformGroup2() throws Exception {
        checkSvgConversion("test_transform_group_2");
    }

    public void testSvgTransformGroup3() throws Exception {
        checkSvgConversion("test_transform_group_3");
    }

    public void testSvgTransformGroup4() throws Exception {
        checkSvgConversion("test_transform_group_4");
    }

    public void testSvgTransformEllipseRotateScaleTranslate() throws Exception {
        checkSvgConversion("test_transform_ellipse_rotate_scale_translate");
    }

    public void testSvgTransformEllipseComplex() throws Exception {
        checkSvgConversion("test_transform_ellipse_complex");
    }

    public void testSvgMoveAfterClose1() throws Exception {
        checkSvgConversion("test_move_after_close1");
    }

    public void testSvgMoveAfterClose2() throws Exception {
        checkSvgConversion("test_move_after_close2");
    }

    public void testSvgMoveAfterClose3() throws Exception {
        checkSvgConversion("test_move_after_close3");
    }

    public void testSvgMoveAfterCloseTransform() throws Exception {
        checkSvgConversion("test_move_after_close_transform");
    }

    public void testSvgFillRuleEvenOdd() throws Exception {
        checkSvgConversion("test_fill_type_evenodd");
    }

    public void testSvgFillRuleNonzero() throws Exception {
        checkSvgConversion("test_fill_type_nonzero");
    }

    public void testSvgFillRuleNoRule() throws Exception {
        checkSvgConversion("test_fill_type_no_rule");
    }

    public void testSvgBlackFill() throws Exception {
        checkSvgConversion("test_black_fill");
    }

    public void testSvgDefsUseTransform() throws Exception {
        checkSvgConversion("test_defs_use_shape2");
    }

    public void testSvgDefsUseColors() throws Exception {
        checkSvgConversion("test_defs_use_colors");
    }

    public void testSvgDefsUseNoGroup() throws Exception {
        checkSvgConversion("test_defs_use_no_group");
    }

    public void testSvgDefsUseNestedGroups() throws Exception {
        checkSvgConversion("test_defs_use_nested_groups");
    }

    public void testSvgDefsUseNestedGroups2() throws Exception {
        checkSvgConversion("test_defs_use_nested_groups2");
    }

    public void testSvgUseWithoutDefs() throws Exception {
        checkSvgConversion("test_use_no_defs");
    }

    public void testSvgDefsUseMultiAttrib() throws Exception {
        checkSvgConversion("test_defs_use_multi_attr");
    }

    public void testSvgDefsUseTransformRotate() throws Exception {
        checkSvgConversion("test_defs_use_transform");
    }

    public void testSvgDefsUseTransformInDefs() throws Exception {
        checkSvgConversion("test_defs_use_transform2");
    }

    public void testSvgDefsUseOrderMatters() throws Exception {
        checkSvgConversion("test_defs_use_use_first");
    }

    public void testSvgDefsUseIndirect() throws Exception {
        checkSvgConversion("test_defs_use_chain");
    }

    public void testSvgDefsUseCircularDependency() throws Exception {
        checkSvgConversionAndContainsError(
                "test_defs_use_circular_dependency",
                "ERROR @ line 6: Circular dependency of <use> nodes: hhh -> hhh\n" +
                "ERROR @ line 9: Circular dependency of <use> nodes: ccc -> ddd (line 11) -> eee (line 10) -> ccc\n" +
                "ERROR @ line 12: Circular dependency of <use> nodes: ggg -> fff (line 8) -> ggg");
    }

    public void testSvgUnsupportedElement() throws Exception {
        String errors = checkSvgConversion("test_unsupported_element");
        assertEquals("ERROR @ line 4: <text> is not supported", errors);
    }

    public void testSvgImageOnly() throws Exception {
        checkSvgConversionAndContainsError("test_image_only",
                                           "ERROR @ line 11: <image> is not supported");
    }

    public void testSvgEmptyAttribute() throws Exception {
        checkSvgConversion("test_empty_attribute");
    }

    // Clip Path Tests
    public void testSvgClipPathGroup() throws Exception {
        checkSvgConversion("test_clip_path_group");
    }

    public void testSvgClipPathGroup2() throws Exception {
        checkSvgConversion("test_clip_path_group_2");
    }

    public void testSvgClipPathTranslateChildren() throws Exception {
        checkSvgConversion("test_clip_path_translate_children");
    }

    public void testSvgClipPathTranslateAffected() throws Exception {
        checkSvgConversion("test_clip_path_translate_affected");
    }

    public void testSvgClipPathIsGroup() throws Exception {
        checkSvgConversion("test_clip_path_is_group");
    }

    public void testSvgClipPathMultiShapeClip() throws Exception {
        checkSvgConversion("test_clip_path_mult_clip");
    }

    public void testSvgClipPathOverGroup() throws Exception {
        checkSvgConversion("test_clip_path_over_group");
    }

    public void testSvgClipPathRect() throws Exception {
        checkSvgConversion("test_clip_path_rect");
    }

    public void testSvgClipPathRectOverClipPath() throws Exception {
        checkSvgConversion("test_clip_path_rect_over_circle");
    }

    public void testSvgClipPathTwoRect() throws Exception {
        checkSvgConversion("test_clip_path_two_rect");
    }

    public void testSvgClipPathSinglePath() throws Exception {
        checkSvgConversion("test_clip_path_path_over_rect");
    }

    public void testSvgClipPathOrdering() throws Exception {
        checkSvgConversion("test_clip_path_ordering");
    }

    public void testSvgClipEvenOdd() throws Exception {
        checkSvgConversion("test_clip_path_evenodd");
    }

    public void testSvgClipEvenOddAndNonZero() throws Exception {
        checkSvgConversion("test_clip_path_evenodd_and_nonzero");
    }

    public void testSvgClipRuleOutsideOfClipPath() throws Exception {
        checkSvgConversion("test_clip_rule_outside_of_clippath");
    }

    public void testSvgMask() throws Exception {
        checkSvgConversion("test_mask");
    }

    public void testSvgMaskUnsupported() throws Exception {
        checkVectorConversion("test_mask_unsupported", FileType.SVG, false,
                              "Semitransparent mask cannot be represented by a vector drawable");
    }

    // Style tests start here
    public void testSvgStyleBasicShapes() throws Exception {
        checkSvgConversion("test_style_basic_shapes");
    }

    public void testSvgStyleBlobfish() throws Exception {
        checkSvgConversion("test_style_blobfish");
    }

    public void testSvgStyleCircle() throws Exception {
        checkSvgConversion("test_style_circle");
    }

    public void testSvgStyleGroup() throws Exception {
        checkSvgConversion("test_style_group");
    }

    public void testSvgStyleGroupClipPath() throws Exception {
        checkSvgConversion("test_style_group_clip_path");
    }

    public void testSvgStyleGroupDuplicateAttr() throws Exception {
        checkSvgConversion("test_style_group_duplicate_attr");
    }

    public void testSvgStyleMultiClass() throws Exception {
        checkSvgConversion("test_style_multi_class");
    }

    public void testSvgStyleTwoShapes() throws Exception {
        checkSvgConversion("test_style_two_shapes");
    }

    public void testSvgStylePathClassNames() throws Exception {
        checkSvgConversion("test_style_path_class_names");
    }

    public void testSvgStyleShortVersion() throws Exception {
        checkSvgConversion("test_style_short_version");
    }

    public void testSvgStyleCombined() throws Exception {
        checkSvgConversion("test_style_combined");
    }

    // Gradient tests start here
    // The following gradient test files currently fail and do not have corresponding test cases:
    // test_gradient_linear_transform_matrix
    // test_gradient_linear_transform_matrix_2
    // test_gradient_linear_transform_scale_rotate
    // test_gradient_linear_transform_scale_translate_rotate
    public void testSvgGradientLinearCoordinatesNegativePercentage() throws Exception {
        checkSvgConversion("test_gradient_linear_coordinates_negative_percentage");
    }

    public void testSvgGradientLinearNoCoordinates() throws Exception {
        checkSvgConversion("test_gradient_linear_no_coordinates");
    }

    public void testSvgGradientLinearNoUnits() throws Exception {
        checkSvgConversion("test_gradient_linear_no_units");
    }

    public void testSvgGradientLinearObjectBoundingBox() throws Exception {
        checkSvgConversion("test_gradient_linear_object_bounding_box");
    }

    public void testSvgGradientLinearOffsetDecreasing() throws Exception {
        checkSvgConversion("test_gradient_linear_offset_decreasing");
    }

    public void testSvgGradientLinearOffsetOutOfBounds() throws Exception {
        checkSvgConversion("test_gradient_linear_offset_out_of_bounds");
    }

    public void testSvgGradientLinearOffsetUndefined() throws Exception {
        checkSvgConversion("test_gradient_linear_offset_undefined");
    }

    public void testSvgGradientLinearOneStop() throws Exception {
        checkSvgConversion("test_gradient_linear_one_stop");
    }

    public void testSvgGradientLinearOverlappingStops() throws Exception {
        checkSvgConversion("test_gradient_linear_overlapping_stops");
    }

    public void testSvgGradientLinearSpreadPad() throws Exception {
        checkSvgConversion("test_gradient_linear_spread_pad");
    }

    public void testSvgGradientLinearSpreadReflect() throws Exception {
        checkSvgConversion("test_gradient_linear_spread_reflect");
    }

    public void testSvgGradientLinearSpreadRepeat() throws Exception {
        checkSvgConversion("test_gradient_linear_spread_repeat");
    }

    public void testSvgGradientLinearStopOpacity() throws Exception {
        checkSvgConversion("test_gradient_linear_stop_opacity");
    }

    public void testSvgGradientLinearStopOpacityHalf() throws Exception {
        checkSvgConversion("test_gradient_linear_stop_opacity_half");
    }

    public void testSvgGradientLinearStroke() throws Exception {
        checkSvgConversion("test_gradient_linear_stroke");
    }

    public void testSvgGradientLinearThreeStops() throws Exception {
        checkSvgConversion("test_gradient_linear_three_stops");
    }

    public void testSvgGradientLinearTransformGroupScaleTranslate() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_group_scale_translate");
    }

    public void testSvgGradientLinearTransformMatrix3() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_matrix_3");
    }

    public void testSvgGradientLinearTransformMatrixScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_matrix_scale");
    }

    public void testSvgGradientLinearTransformRotate() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_rotate");
    }

    public void testSvgGradientLinearTransformRotateScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_rotate_scale");
    }

    public void testSvgGradientLinearTransformRotateTranslateScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_rotate_translate_scale");
    }

    public void testSvgGradientLinearTransformScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_scale");
    }

    public void testSvgGradientLinearTransformTranslate() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_translate");
    }

    public void testSvgGradientLinearTransformTranslateRotate() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_translate_rotate");
    }

    public void testSvgGradientLinearTransformTranslateRotateScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_translate_rotate_scale");
    }

    public void testSvgGradientLinearTransformTranslateScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_translate_scale");
    }

    public void testSvgGradientLinearTransformTranslateScaleShapeTransform() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_translate_scale_shape_transform");
    }

    public void testSvgGradientLinearUserSpaceOnUse() throws Exception {
        checkSvgConversion("test_gradient_linear_user_space_on_use");
    }

    public void testSvgGradientLinearXYNumbers() throws Exception {
        checkSvgConversion("test_gradient_linear_x_y_numbers");
    }

    public void testSvgGradientLinearHref() throws Exception {
        checkSvgConversion("test_gradient_linear_href");
    }

    public void testSvgGradientTransform() throws Exception {
        checkSvgConversion("test_gradient_transform");
    }

    public void testGradientObjectTransformation() throws Exception {
        checkSvgConversion("test_gradient_object_transformation");
    }

    public void testSvgGradientComplex() throws Exception {
        checkSvgConversion("test_gradient_complex");
    }

    public void testSvgGradientComplex2() throws Exception {
        checkSvgConversion("test_gradient_complex_2");
    }

    public void testSvgGradientRadialCoordinates() throws Exception {
        checkSvgConversion("test_gradient_radial_coordinates");
    }

    public void testSvgGradientRadialNoCoordinates() throws Exception {
        checkSvgConversion("test_gradient_radial_no_coordinates");
    }

    public void testSvgGradientRadialNoStops() throws Exception {
        checkVectorConversion(
                "test_gradient_radial_no_stops", FileType.SVG, false, "has no stop info");
    }

    public void testSvgGradientRadialNoUnits() throws Exception {
        checkSvgConversion("test_gradient_radial_no_units");
    }

    public void testSvgGradientRadialObjectBoundingBox() throws Exception {
        checkSvgConversion("test_gradient_radial_object_bounding_box");
    }

    public void testSvgGradientRadialOneStop() throws Exception {
        checkSvgConversion("test_gradient_radial_one_stop");
    }

    public void testSvgGradientRadialOverlappingStops() throws Exception {
        checkSvgConversion("test_gradient_radial_overlapping_stops");
    }

    public void testSvgGradientRadialRNegative() throws Exception {
        checkSvgConversion("test_gradient_radial_r_negative");
    }

    public void testSvgGradientRadialRZero() throws Exception {
        checkSvgConversion("test_gradient_radial_r_zero");
    }

    public void testSvgGradientRadialSpreadPad() throws Exception {
        checkSvgConversion("test_gradient_radial_spread_pad");
    }

    public void testSvgGradientRadialSpreadReflect() throws Exception {
        checkSvgConversion("test_gradient_radial_spread_reflect");
    }

    public void testSvgGradientRadialSpreadRepeat() throws Exception {
        checkSvgConversion("test_gradient_radial_spread_repeat");
    }

    public void testSvgGradientRadialStopOpacity() throws Exception {
        checkSvgConversion("test_gradient_radial_stop_opacity");
    }

    public void testSvgGradientRadialStopOpacityFraction() throws Exception {
        checkSvgConversion("test_gradient_radial_stop_opacity_fraction");
    }

    public void testSvgGradientRadialStroke() throws Exception {
        checkSvgConversion("test_gradient_radial_stroke");
    }

    public void testSvgGradientRadialThreeStops() throws Exception {
        checkSvgConversion("test_gradient_radial_three_stops");
    }

    public void testSvgGradientRadialUserSpaceOnUse() throws Exception {
        checkSvgConversion("test_gradient_radial_user_space_on_use");
    }

    public void testSvgGradientRadialUserSpace2() throws Exception {
        checkSvgConversion("test_gradient_radial_user_space_2");
    }

    public void testSvgGradientRadialTransformTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate");
    }

    public void testSvgGradientRadialTransformTranslateUserSpace() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_userspace");
    }

    public void testSvgGradientRadialTransformTranslateScale() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_scale");
    }

    public void testSvgGradientRadialTransformScaleTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_scale_translate");
    }

    public void testSvgGradientRadialTransformMatrix() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_matrix");
    }

    public void testSvgGradientRadialTransformRotate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate");
    }

    public void testSvgGradientRadialTransformRotateScale() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate_scale");
    }

    public void testSvgGradientRadialTransformRotateScaleTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate_scale_translate");
    }

    public void testSvgGradientRadialTransformRotateTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate_translate");
    }

    public void testSvgGradientRadialTransformRotateTranslate2() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate_translate2");
    }

    public void testSvgGradientRadialTransformRotateTranslateScale() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate_translate_scale");
    }

    public void testSvgGradientRadialTransformScale() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_scale");
    }

    public void testSvgGradientRadialTransformScaleRotate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_scale_rotate");
    }

    public void testSvgGradientRadialTransformScaleRotateTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_scale_rotate_translate");
    }

    public void testSvgGradientRadialTransformScaleTranslateRotate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_scale_translate_rotate");
    }

    public void testSvgGradientRadialTransformTranslateRotate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_rotate");
    }

    public void testSvgGradientRadialTransformTranslateRotateScale() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_rotate_scale");
    }

    public void testSvgGradientRadialTransformTranslateScaleRotate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_scale_rotate");
    }

    public void testSvgGradientRadialTransformTranslateGroupScaleTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_group_scale_translate");
    }

    public void testSvgGradientRadialUnitsAsNumbers() throws Exception {
        checkSvgConversion("test_gradient_radial_units_as_numbers");
    }

    public void testSvgGradientRadialCoordinatesNegativePercentage() throws Exception {
        checkSvgConversion("test_gradient_radial_coordinates_negative_percentage");
    }

    public void testLocaleWithDecimalComma() throws Exception {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.FRANCE);
        try {
            checkSvgConversion("test_locale_with_decimal_comma");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    public void testLocaleWithDashAsMinus() throws Exception {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(new Locale("SE", "sv"));
        try {
            checkSvgConversion("test_locale_with_dash_as_minus");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    public void testClipPathOrder() throws Exception {
        checkSvgConversion("ic_clip_path_ordering");
    }

    public void testSvgNestedUse() throws Exception {
        checkSvgConversion("test_nested_use");
    }

    // XML files start here.
    public void testXmlIconSizeOpacity() throws Exception {
        checkXmlConversion("ic_size_opacity");
    }

    public void testXmlTintAndOpacity() throws Exception {
        checkXmlConversion("test_tint_and_opacity");
    }

    public void testXmlColorFormats() throws Exception {
        checkXmlConversion("test_xml_color_formats");
    }

    public void testXmlColorAlpha() throws Exception {
        checkXmlConversion("test_fill_stroke_alpha");
    }

    public void testXmlTransformation1() throws Exception {
        checkXmlConversion("test_xml_transformation_1");
    }

    public void testXmlTransformation2() throws Exception {
        checkXmlConversion("test_xml_transformation_2");
    }

    public void testXmlTransformation3() throws Exception {
        checkXmlConversion("test_xml_transformation_3");
    }

    public void testXmlTransformation4() throws Exception {
        checkXmlConversion("test_xml_transformation_4");
    }

    public void testXmlTransformation5() throws Exception {
        checkXmlConversion("test_xml_transformation_5");
    }

    public void testXmlTransformation6() throws Exception {
        checkXmlConversion("test_xml_transformation_6");
    }

    public void testXmlScaleStroke1() throws Exception {
        checkXmlConversion("test_xml_scale_stroke_1");
    }

    public void testXmlScaleStroke2() throws Exception {
        checkXmlConversion("test_xml_scale_stroke_2");
    }

    public void testXmlRenderOrder1() throws Exception {
        checkXmlConversion("test_xml_render_order_1");
    }

    public void testXmlRenderOrder2() throws Exception {
        checkXmlConversion("test_xml_render_order_2");
    }

    public void testXmlRepeatedA1() throws Exception {
        checkXmlConversion("test_xml_repeated_a_1");
    }

    public void testXmlRepeatedA2() throws Exception {
        checkXmlConversion("test_xml_repeated_a_2");
    }

    public void testXmlRepeatedCQ() throws Exception {
        checkXmlConversion("test_xml_repeated_cq");
    }

    public void testXmlRepeatedST() throws Exception {
        checkXmlConversion("test_xml_repeated_st");
    }

    public void testXmlStroke1() throws Exception {
        checkXmlConversion("test_xml_stroke_1");
    }

    public void testXmlStroke2() throws Exception {
        checkXmlConversion("test_xml_stroke_2");
    }

    public void testXmlStroke3() throws Exception {
        checkXmlConversion("test_xml_stroke_3");
    }

    public void testPathDataInStringResource() throws Exception {
        try {
            checkXmlConversion("test_pathData_in_string_resource");
            fail("Expecting exception.");
        } catch (ResourcesNotSupportedException e) {
            Assert.assertEquals("@string/pathDataAsString", e.getValue());
            Assert.assertEquals("android:pathData", e.getName());
        }
    }

    /**
     * We aren't really interested in the content of the image produced in this test, we're just
     * testing that resource references aren't touched when they're in the tools: attribute
     * namespace.
     */
    public void testPathDataInStringToolsResource() throws Exception {
        checkXmlConversion("test_pathData_in_string_tools_resource");
    }

    public void testSmallVectorWithTint() throws Exception {
        checkVectorConversion("test_small_image_with_tint", FileType.XML, false, null, 16);
    }

    public void testLegacyArcFlags() throws Exception {
        checkVectorConversion("test_legacy_arc_flags", FileType.XML, false, null, 16);
    }
}
