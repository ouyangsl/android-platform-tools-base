package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import lombok.ast.AstVisitor;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.Node;
import lombok.ast.StringLiteral;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detector that looks for leaked credentials in strings.
 */
public class StringAuthLeakDetector extends Detector implements Detector.JavaScanner {

    /** Looks for hidden code */
    public static final Issue AUTH_LEAK = Issue.create(
      "AuthLeak",
      "Code might contain an auth leak",
            "Strings in java apps can be discovered by decompiling apps, this lint check looks " +
            "for code which looks like it may contain an url with a username and password",
      Category.SECURITY,
      6,
      Severity.WARNING,
      new Implementation(StringAuthLeakDetector.class, Scope.JAVA_FILE_SCOPE));

    @Nullable
    @Override
    public List<Class<? extends Node>> getApplicableNodeTypes() {
        return Collections.<Class<? extends Node>>singletonList(StringLiteral.class);
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

        private final JavaContext mContext;

        private AuthLeakChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitStringLiteral(StringLiteral node) {
            Matcher matcher = AUTH_REGEXP.matcher(node.rawValue());
            if (matcher.find()) {
                String password = matcher.group(3);
                if (password == null || (password.startsWith("%") && password.endsWith("s"))) {
                    return super.visitStringLiteral(node);
                }

                int offset = node.getPosition().getStart();
                Location location = Location.create(mContext.file, mContext.getContents(),
                        offset + matcher.start(), offset + matcher.end());
                mContext.report(AUTH_LEAK, node, location, "Possible credential leak");
            }

            return super.visitStringLiteral(node);
        }
    }
}
