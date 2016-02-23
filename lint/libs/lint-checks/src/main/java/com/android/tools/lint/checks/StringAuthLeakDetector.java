package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.*;
import lombok.ast.*;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringAuthLeakDetector extends Detector implements Detector.JavaScanner {

    /** Looks for hidden code */
    public static final Issue AUTH_LEAK = Issue.create(
            "AuthLeak",
            "Code might contain an auth leak",
            "Strings in java apps can be discovered by decompiling apps, this lint check looks " +
            "for code which looks like it may be contain an url with a username and password",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(StringAuthLeakDetector.class, Scope.JAVA_FILE_SCOPE))
        .setEnabledByDefault(true);

    @Nullable
    @Override
    public List<Class<? extends Node>> getApplicableNodeTypes() {
        return Collections.<Class<? extends Node>>singletonList(StringLiteral.class);
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor, @NonNull MethodInvocation node) {
    }

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return null;
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context,
                                 @Nullable AstVisitor visitor,
                                 @NonNull ConstructorInvocation node,
                                 @NonNull JavaParser.ResolvedMethod constructor) {
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context,
                                       @Nullable AstVisitor visitor,
                                       @NonNull Node node,
                                       @NonNull String type,
                                       @NonNull String name,
                                       boolean isFramework) {
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void checkClass(@NonNull JavaContext context,
                           @Nullable ClassDeclaration declaration,
                           @NonNull Node node,
                           @NonNull JavaParser.ResolvedClass resolvedClass) {
    }

    @Nullable
    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        return new AuthLeakChecker(context);
    }

    private static class AuthLeakChecker extends ForwardingAstVisitor {
        private final static String LEGAL_CHARS = "([\\w_.!~*\'()%;&=+$,-]+)";      // From RFC 2396
        private final static Pattern AUTH_REGEXP =
                Pattern.compile("([\\w+.-]+)://" + LEGAL_CHARS + ':' + LEGAL_CHARS + '@' +
                        LEGAL_CHARS);

        JavaContext mContext;

        private AuthLeakChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitStringLiteral(StringLiteral node) {
            Matcher matcher = AUTH_REGEXP.matcher(node.astValue());
            if (matcher.find()) {
                String password = matcher.group(3);
                if (password == null || (password.startsWith("%") && password.endsWith("s"))) {
                    return super.visitStringLiteral(node);
                }

                int offset = node.getPosition().getStart() + 1;
                Node root = node;
                while (root.getParent() != null) {
                    root = root.getParent();
                }
                Location location = Location.create(mContext.file, root.toString(),
                        offset + matcher.start(), offset + matcher.end());
                mContext.report(AUTH_LEAK, node, location, "Possible auth leak");
            }

            return super.visitStringLiteral(node);
        }
    }
}
