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

package com.android.tools.lint;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.intellij.mock.MockProject;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/*
Remaining work:
- fix visitors. Get rid of visitor and just pass "handle" methods instead?
- get rid of the "inspection bridge" stuff. Not needed now.
- Finish extracting quickfixes for the inspections.
- Move the SizeConstraint etc code over to the new lint checks.
   Make sure I have all the new checks, including the @Size stuff
   I fixed for Romain.

TODO:
Verify UClassLiteralExpression (.class syntax) in API detector
Go through ALL visitors and make sure we don't call super from the visitors
(or call return false for that matter.)
Similarly make sure I have unit tests for API checking both super classes and super interfaces!


Other questions now that I can't access wifi:
PsiElementBacked: I see that the JavaContext#suppressedWithComment code is
only working for "PSI backed" elements. Is that intentional? Are all statements
backed by PSI?

toAndroidReferenceViaResolve has references to uast-java APIs; that won't
be necessary right?  (JavaAbstractUExpression)

Changes I've made:
- lint testing APIs
- in UAST using evaluator?
- getting rid of the "uast" specialized methods and overloading instead --
    e.g. getLocation(UElement), report(UElement)


What about javaConstantEvaluator? How do I evaluate stuff?

Migration notes:
- tell people to look out for PsiBinaryExpression: Could be PsiPolyadicExpression, e.t.
     x + y + z +
  is NOT considered ((x + y) + y) as nested PsiBinaryExpressions!


  risk: Many visitors end up calling super.visit something. But that's wrong. We don't
  want EACH visitor to recurse through the rest of the tree, ONLY to handle THAT NODE.
  Maybe I need a dedicated class!


  Make note of location differences.
  Calls now don't include receivers.
  Have new getCallLocation method which indicates if you want receiver and/or argument list.



Is this distinction important?
<                 PsiClass containingClass = calledMethod.getContainingClass();
---
>                 PsiClass containingClass = UastUtils.getContainingClass(calledMethod);
123a126


Another common conversion:
PsiCallExpression to UCallExpression:
call.getMethodExpression().getReferenceName() => call.getMethodName()

Also the one for arguments.


Unit tests can't cheat and put R classes in the same file. Must be top level!
Also forces a real package.


NOTE: Don't try to manually ( in detector ) try to figure out if a field is a resource
reference. Just use AndroidReference.toAndroidReference etc to look it up.
See MergeRootFrameLayoutDetector for an example.



Instead of
node instanceof UBinaryExpressionWithType
use
UastExpressionUtils.isTypeCast

See also UastExpressionUtils.isNewArrayWithInitializer etc.
 */
public class UastMigrator {
    public static final boolean WRITE_IN_PLACE = true;

    private final File inputRoot;
    private final File outputDir;

    public UastMigrator(File inputRoot, File outputDir) {
        this.inputRoot = inputRoot;
        this.outputDir = outputDir;
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }

    public void migrate(File... files) {
        for (File file : files) {
            migrate(file);
        }
    }

    private void migrate(File file) {
        try {
            if (!file.isAbsolute()) {
                file = new File(inputRoot, file.getPath());
            }
            if (!file.exists()) {
                System.err.println(file + " does not exist");
                return;
            }

            File output;
            if (WRITE_IN_PLACE) {
                output = file;
            } else {
                output = new File(outputDir, file.getName());
            }

            migrate(file, Files.toString(file, Charsets.UTF_8), output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void migrate(File file, String source, File output) {
        LintCoreProjectEnvironment environment = LintCoreProjectEnvironment.create();
        updateClassPath(environment);

        VirtualFile virtualFile = StandardFileSystems.local().findFileByPath(file.getPath());
        if (virtualFile == null) {
            System.err.println("FATAL: Couldn't find VFS file for " + file);
            return;
        }

        VirtualFile sourceRoot = StandardFileSystems.local().findFileByPath(inputRoot.getPath());
        environment.addSourcesToClasspath(sourceRoot);

        MockProject project = environment.getProject();
        PsiFile javaFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (javaFile == null) {
            System.err.println("FATAL: Couldn't create PSI file for " + file);
            return;
        }
        migrate(source, (PsiJavaFile) javaFile, output);
    }

    private static void updateClassPath(LintCoreProjectEnvironment environment) {
        // This is the compilation path for the lint-checks module; this is ugly hardcoding
        File root = new File("/Users/tnorbye/dev/studio/dev");
        for (String relative : new String[]{
                "out/build/base/lint-api/build/classes/main",
                "out/build/base/common/build/classes/main",
                "out/build/base/sdk-common/build/classes/main",
                "out/build/base/builder-model/build/classes/main",
                "out/build/base/layoutlib-api/build/classes/main",
                "prebuilts/tools/common/m2/repository/com/google/guava/guava/18.0/guava-18.0.jar",
                "prebuilts/tools/common/m2/repository/org/ow2/asm/asm-analysis/5.0.4/asm-analysis-5.0.4.jar",
                "prebuilts/tools/common/m2/repository/org/ow2/asm/asm-commons/5.0.4/asm-commons-5.0.4.jar",
                "prebuilts/tools/common/m2/repository/org/ow2/asm/asm-tree/5.0.4/asm-tree-5.0.4.jar",
                "prebuilts/tools/common/m2/repository/org/ow2/asm/asm-util/5.0.4/asm-util-5.0.4.jar",
                "prebuilts/tools/common/m2/repository/org/ow2/asm/asm/5.0.4/asm-5.0.4.jar",
                "prebuilts/tools/common/m2/repository/com/android/tools/external/lombok/lombok-ast/0.2.3/lombok-ast-0.2.3.jar",
                "prebuilts/tools/common/uast/uast-162.2228.14.jar"
        }) {
            File dir = new File(root, relative);
            if (dir.exists()) {
                if (dir.isDirectory()) {
                    VirtualFile dirFile = StandardFileSystems.local()
                            .findFileByPath(dir.getPath());
                    environment.addSourcesToClasspath(dirFile);

                } else {
                    environment.addJarToClassPath(dir);
                }
            } else {
                System.out.println("Warning: " + dir + " is not a file or directory");
            }
        }
    }

    private static class Replacement implements Comparable<Replacement> {

        final String source;
        final int start;
        final int end;
        final String replacement;

        public Replacement(String source, int start, int end, String replacement) {
            this.source = source;
            this.start = start;
            this.end = end;
            this.replacement = replacement;
        }

        @Override
        public int compareTo(@NotNull Replacement o) {
            return o.start - start;
        }

        @Override
        public String toString() {
            return "Replacement{" +
                    "start=" + start +
                    ", end=" + end +
                    ", replacement='" + replacement + '\'' +
                    ", replacing=" + source.substring(start, end) +
                    '}';
        }
    }

    private List<Replacement> replacements = Lists.newArrayList();

    private Map<String, String> apiMapping = Maps.newHashMap();

    {
        // PSI to UAST classes
        apiMapping.put("PsiElement", "UElement");
        apiMapping.put("PsiImportStatement", "UImportStatement");
        apiMapping.put("PsiJavaCodeReferenceElement", "UReferenceExpression");
        // Make note that when dealing with method signatures we often get PsiMethod,
        // PsiModifierList, PsiParameter, PsiAnnotation!
        apiMapping.put("PsiAnnotation", "UAnnotation");
        apiMapping.put("PsiSwitchStatement", "USwitchExpression");
        apiMapping.put("PsiLocalVariable", "ULocalVariable");
        apiMapping.put("PsiDeclarationStatement", "UDeclarationsExpression");
        apiMapping.put("PsiParameter", "UParameter");
        apiMapping.put("PsiMethod", "UMethod");
        apiMapping.put("PsiMethodCallExpression", "UCallExpression");
        apiMapping.put("PsiNewExpression", "UCallExpression");
        apiMapping.put("PsiExpression", "UExpression");
        apiMapping.put("PsiReference", "UReferenceExpression");
        apiMapping.put("PsiReferenceExpression", "UReferenceExpression");
        apiMapping.put("PsiReturnStatement", "UReturnExpression");
        apiMapping.put("PsiPrefixExpression", "UPrefixExpression"); // Or UUnaryExpression
        apiMapping.put("PsiPostfixExpression", "UPostfixExpression"); // Or UUnaryExpression
        apiMapping.put("PsiBinaryExpression", "UBinaryExpression");
        apiMapping.put("PsiIfStatement", "UIfExpression");
        apiMapping.put("PsiWhileStatement", "UWhileExpression");
        apiMapping.put("PsiDoWhileStatement", "UDoWhileExpression");
        apiMapping.put("PsiClass", "UClass");
        apiMapping.put("PsiLiteral", "ULiteralExpression");
        apiMapping.put("PsiAnonymousClass", "UAnonymousClass");
        apiMapping.put("PsiPolyadicExpression", "UBinaryExpression"); // No polyadic expression in UAST
        apiMapping.put("PsiThrowStatement", "UThrowExpression");
        apiMapping.put("PsiParenthesizedExpression", "UParenthesizedExpression");
        apiMapping.put("PsiIdentifier", "USimpleNameReferenceExpression");
        apiMapping.put("PsiNameValuePair", "UNamedExpression");
        apiMapping.put("PsiCatchSection", "UCatchClause");
        apiMapping.put("PsiImportStaticStatement", "UImportStatement");
        apiMapping.put("PsiCallExpression", "UCallExpression");
        apiMapping.put("PsiTryStatement", "UTryExpression");
        apiMapping.put("PsiSwitchLabelStatement", "USwitchClauseExpression");
        apiMapping.put("PsiTypeCastExpression", "UBinaryExpressionWithType");
        apiMapping.put("PsiForeachStatement", "UForEachExpression");
        apiMapping.put("PsiThisExpression", "UThisExpression");
        apiMapping.put("PsiSuperExpression", "USuperExpression");
        apiMapping.put("PsiClassObjectAccessExpression", "UClassLiteralExpression");
        apiMapping.put("IElementType", "UastBinaryOperator");
        apiMapping.put("PsiConditionalExpression", "UIfExpression");
        apiMapping.put("", "");
        // TODO: What maps to a UReturnExpression


        // Put fully resolved symbols in there as well
        Set<String> psi = Sets.newHashSet(apiMapping.keySet());
        for (String cls : psi) {
            apiMapping.put("com.intellij.psi." + cls, "org.jetbrains.uast." + cls);
        }

        // Lint API classes

        apiMapping.put("JavaPsiScanner", "UastScanner");

        // but AbstractUastVisitor when extending; special cased in lookup
        apiMapping.put("JavaElementVisitor", "UastVisitor");
        apiMapping.put("com.intellij.psi.JavaElementVisitor", "org.jetbrains.uast.visitor.UastVisitor");

        apiMapping.put("JavaRecursiveElementVisitor", "AbstractUastVisitor");
        apiMapping.put("com.intellij.psi.JavaRecursiveElementVisitor", "org.jetbrains.uast.visitor.AbstractUastVisitor");

        // Not always true, but generally for the getParentOfType() calls which is
        // most of our PsiTreeUtil usage
        apiMapping.put("PsiTreeUtil", "UastUtils");

        // Method names
        apiMapping.put("getApplicablePsiTypes", "getApplicableUastTypes");
        apiMapping.put("createPsiVisitor", "createUastVisitor");
        apiMapping.put("getApplicablePsiTypes", "getApplicableUastTypes");
        apiMapping.put("resolveMethod", "resolve");
        apiMapping.put("getRExpression", "getRightOperand");
        apiMapping.put("getLExpression", "getLeftOperand");
        apiMapping.put("getArgumentList", "getValueArguments");
        apiMapping.put("visitReferenceExpression", "visitSimpleNameReferenceExpression");
        apiMapping.put("visitReturnStatement", "visitReturnExpression");
        apiMapping.put("getParent", "getContainingElement");
        apiMapping.put("getThenBranch", "getThenExpression");
        apiMapping.put("getElseBranch", "getElseExpression");
        apiMapping.put("getTypeParameters", "getTypeArguments");
        apiMapping.put("getOwner", "getContainingElement");
        apiMapping.put("getDeclaredElements", "getDeclarations");
        apiMapping.put("visitDeclarationStatement", "visitDeclarationsExpression");
        apiMapping.put("visitMethodCallExpression", "visitCallExpression");
        apiMapping.put("getText", "asSourceString");
        apiMapping.put("getReturnValue", "getReturnExpression");
        apiMapping.put("getInitializer", "getUastInitializer");
        apiMapping.put("visitForeachStatement", "visitForEachExpression");
        apiMapping.put("getType", "getExpressionType");
        apiMapping.put("visitClassObjectAccessExpression", "visitClassLiteralExpression");
        apiMapping.put("getOperationTokenType", "getOperator");
        apiMapping.put("getCatchSections", "getCatchClauses");


        // TODO: Map
        //   LintUtils.isNullLiteral to UastLiteralUtils.isNullLiteral

        //apiMapping.put("", "");
//        apiMapping.put("", "");

        // TODO: Insert some import statements?
        // TODO: Handle fully qaulified names in the source code
    }

    /*
     * Migration notes:
     *
     * UImportStatement doesn't have getQualifiedName: Must resolve class first, THEN look
     *
     * resolve() returns PsiElement, not UElement. The converter will generally translate
     * PsiMethod into UMethod, but you don't want to do that when dealing with resolve results!
     *
     *   <                     if (annotation.getParent() instanceof PsiModifierList
     *   <                         && annotation.getParent().getParent() instanceof PsiMethod) {
     *   ---
     *   >                     if (annotation.getContainingElement() instanceof UMethod) {
     *   381,383c376,378
     *
     * Similarly, for method call receivers:
     *    <                                         operand = ((UCallExpression)operand).
     *    <                                                 getMethodExpression().getQualifierExpression();
     *    ---
     *    >                                         operand = ((UCallExpression)operand).getReceiver();
     *
     *
     *
     * getReferenceName:
     *     call.getMethodExpression().getReferenceName();
     *  can be replaced by just call.getMethodName()
     *  and call.resolveMethod() should be replaced by just call.resolve()
     *
     *
     * Instead of
     *    parent instanceof PsiAssignmentExpression
     * use
     *    UastExpressionUtils.isAssignment(parent)
     *
     *
     * context.getMethodCall(call) -> call.getMethodName()
     *
     *
     * And for parameter iteration:
     *    1117c1128
     *    <         PsiExpression[] args = call.getArgumentList().getExpressions();
     *    ---
     *    >         List<UExpression> args = call.getValueArguments();
     *
     *
     * TODO: I have to rename all visitor methods!! They need a boolean return value
     *
     * PsiImportStatement#getQualifiedName();  -- no I have to resolve the class instead
     *
     * TODO: Now that we're always calling super.getVisitor etc make sure we don't have
     * rogue visitors doing a lot of extra work! Take for eaxmple look at the Import visitor
     * in WrongImportDetector!
     *
     *
     * Difficulty with using my own implementation:
     *    Static Utility methods such as UastExpressionUtils (btw, note that Open Type
     *    does NOT find this method which is in callUtils.kt with @file:JvmName("UastExpressionUtils")
     *
     *  Convert
     *           if (call.getMethodExpression().getQualifier() == null) {
     * to
     *           call.getReceiver()
     *
        //   LintUtils.isNullLiteral to UastLiteralUtils.isNullLiteral

        PsiExpression#getType => UExpression.getExpressionType
     *
     *
     * getParent() => getContainingElement
     *
     *
     * Make a point out of how we don't have separate visitor methods for
     *   (1) class vs anonymous class, and
     *   (2) method call vs constructor invocation
     * so how you change those visitors into a generic visitor that multiplexes
     * on isConstructor or isAnonymous/instanceof UAnonymousClass
     *
     *
     * visitAssignmentExpression -> visitBinaryExpression and check if (UastExpressionUtils.isAssignment(node)) {
     *
     *
     * Make note about *just* returning true from all visitor methods returned
     *
     * Note how we have PSI elements for "outside" of method bodies -- classes, methods,
     * fields (which are variables). You use the UastContext to get inside method bodies --
     * as well as field initializers. For example for a field use
     *                         UExpression initializer = context.getUastContext().getInitializerBody(
                                (PsiVariable) resolved);

        Note also that there are many utility methods and classes --
        UastUtils, UastLintUtils, UastExpressionUtils. For example
                if (UastExpressionUtils.isNewArray(lastArg) ||
                    UastExpressionUtils.isArrayInitializer(lastArg)) {
        as well as the isNullLiteral etc methods. And getAsString.

        Talk about the getCallLocation call vs getLocation.


        node.getContainingFile() => UastUtils.getContainingFile(node).getPsi()


        To get the UClass for a method, use
          UastUtils.getContainingUClass(method)
         Note the "U" in there. It's not just getContainingClass.
     */

    private boolean replace(
            @NonNull String fullSource,
            @Nullable String symbol,
            @NonNull PsiElement element) {
        if (symbol == null) {
            return false;
        }

        String replaceWith = apiMapping.get(symbol);
        if (replaceWith == null) {
            return false;
        }

        // Don't replace import statements if we're not replacing a fully qualified name with another
        if (PsiTreeUtil.getParentOfType(element, PsiImportStatementBase.class, false) != null) {
            if (replaceWith.indexOf('.') == -1 || symbol.indexOf('.') == -1) {
                return false;
            }
        }

        if (symbol.equals("PsiElement") || symbol.equals("com.intellij.psi.PsiElement")) {
            // The visitReference method does *not* switch from PsiElement to UElement
            // See if it's in a resolve call; if so leave it alone
            PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class, true);
            //noinspection VariableNotUsedInsideIf
            if (parameter != null) {
                PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, true);
                if (method != null && method.getName().equals("visitReference")) {
                    return false;
                }
            }
        }

        // See if it's a resolve call: if so, don't translate from PSI to UAST
        if (element.getParent() instanceof PsiTypeElement &&
                element.getParent().getParent() instanceof PsiLocalVariable) {
            PsiLocalVariable var = (PsiLocalVariable) element.getParent().getParent();
            if (var.getInitializer() instanceof PsiMethodCallExpression) {
                String referenceName = ((PsiMethodCallExpression) var.getInitializer())
                        .getMethodExpression().getReferenceName();
                if (referenceName != null && referenceName.startsWith("resolve")) {
                    // It's a resolve call; *don't* change method result type from PSI to UAST
                    return false;
                }
            }
        }

        if (replaceWith.equals("UastVisitor") || replaceWith.equals("org.jetbrains.uast.visitor.UastVisitor")) {
            PsiElement parent = element.getParent();
            if (parent instanceof PsiReferenceList
                    && parent.getParent() instanceof PsiClass
                    && ((PsiClass)parent.getParent()).getExtendsList() == parent) {
                // In an extends list, use AbstractUastVisitor instead
                replaceWith = replaceWith.equals("UastVisitor")
                        ? "AbstractUastVisitor" : "org.jetbrains.uast.visitor.AbstractUastVisitor";
            }
        }

        TextRange range = element.getTextRange();
        Replacement replacement = new Replacement(fullSource,
                range.getStartOffset(),
                range.getEndOffset(),
                replaceWith);
        replacements.add(replacement);
        return true;
    }

    private void migrate(final String source, PsiJavaFile file, File output) {
        replacements.clear();

        file.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                super.visitElement(element);
            }

            @Override
            public void visitIdentifier(PsiIdentifier identifier) {
                if (!replace(source, identifier.getText(), identifier)) {
                    super.visitIdentifier(identifier);
                }
            }

            @Override
            public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
                if (!replace(source, reference.getText(), reference)) {
                    // Don't need to visit identifiers here; we do it separately
                    if (reference.getFirstChild() instanceof PsiIdentifier &&
                            reference.getFirstChild().getNextSibling() == null) {
                        return;
                    }
                    super.visitReferenceElement(reference);
                }
            }

            //@Override
            //public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            //    if (expression.getMethodExpression().getReferenceName().equals("resolveMethod")) {
            //        System.out.println("hre");
            //    }
            //    super.visitMethodCallExpression(expression);
            //}
            //
            //@Override
            //public void visitLocalVariable(PsiLocalVariable variable) {
            //    String text = variable.getText();
            //    if (variable.getName().equals("method")) {
            //        System.out.println("here");
            //    }
            //    super.visitLocalVariable(variable);
            //}

            @Override
            public void visitParameter(PsiParameter parameter) {
                if ("resolved".equals(parameter.getName())) {
                    System.out.println("here");
                }
                super.visitParameter(parameter);
            }

            @Override
            public void visitTypeElement(PsiTypeElement type) {
                if (!(type instanceof PsiPrimitiveType)) {
                    // Workaround: ECJ map doesn't do a good job with wildcard types
                    String text = type.getText();
                    if (text.contains("<")) {
                        for (String key : apiMapping.keySet()) {
                            int index = text.indexOf(key);
                            if (index > 0) { // ignore index==0 - handled by visitReferenceElement
                                TextRange range = type.getTextRange();
                                Replacement replacement =
                                        new Replacement(source, range.getStartOffset() + index,
                                                range.getStartOffset() + index + key.length(),
                                                apiMapping.get(key));
                                replacements.add(replacement);
                                return;
                            }
                        }
                    }
                }
                super.visitTypeElement(type);
            }
        });

        try {
            Files.write(performReplacements(source), output, Charsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Migrated source to " + output.getPath());
    }

    private String performReplacements(String source) {
        // Reverse order:
        Collections.sort(replacements);

        //System.out.println("Replacements map:\n" + Joiner.on("\n").join(replacements));

        // Make sure there are no overlaps
        for (int i = 1; i < replacements.size(); i++) {
            Replacement prev = replacements.get(i - 1);
            Replacement replacement = replacements.get(i);
            if (prev.start < replacement.end) {
                System.out.println("OVERLAP in replacement regions: This is a fatal error.");
                System.out.println("The overlapping regions are " + prev + " and " + replacement);
                System.exit(-1);
            }
        }

        for (Replacement replacement : replacements) {
            // Inefficient but performance doesn't matter here
            source = source.substring(0, replacement.start) +
                    replacement.replacement + source.substring(replacement.end);
        }

        /* Finally some comment fixups:
            <     // ---- Implements JavaScanner ----
            ---
            >     // ---- Implements UastScanner ----
         */
        source = source.replace("Implements JavaScanner", "Implements UastScanner");
        source = source.replace("Implements JavaPsiScanner", "Implements UastScanner");

        // Also insert mandatory imports
        int importIndex = source.indexOf("\nimport ");
        if (importIndex != -1) {
            importIndex++; // skip \n
            source = source.substring(0, importIndex)
                    + "import org.jetbrains.uast.*;\n"
                    + "import org.jetbrains.uast.expressions.*;\n"
                    + "import org.jetbrains.uast.util.*;\n"
                    + "import org.jetbrains.uast.visitor.*;\n"
                    + "import com.android.tools.lint.detector.api.Detector.UastScanner;\n"
                    + source.substring(importIndex);
        }

        return source;
    }

    public static void main(String[] args) {
        UastMigrator migrator = new UastMigrator(
                new File(
                        "/Users/tnorbye/dev/studio/dev/tools/base/lint/libs/lint-checks/src/main/java"),
                new File("/tmp/lint"));

        migrator.migrate(new File("com/android/tools/lint/checks/ExifInterfaceDetector.java"));

        // This one seems to have been changed a lot -- figure out if it was on my end, or their
        // end, or both
        //migrator.migrate(new File("com/android/tools/lint/checks/AnnotationDetector.java"));
    }
}
