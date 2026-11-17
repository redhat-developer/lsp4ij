/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.dap;

import com.intellij.lang.Language;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightPlatformTestCase;

/**
 * An expression code fragment of a JavaScript file, as when debugging JavaScript with a DAP server while the
 * JavaScript plugin is installed. It needs that plugin, so it runs in the testJavaScript task, on an IDE that bundles
 * it: {@code ./gradlew testJavaScript -PplatformType=IU}.
 */
public class DAPExpressionCodeFragmentJavaScriptPluginTest extends LightPlatformTestCase {

    public void testResolvingAReferenceInAJavaScriptFragment() {
        Language javaScript = Language.findLanguageByID("JavaScript");
        assertNotNull("The JavaScript plugin is not loaded: run ./gradlew testJavaScript on an IDE that bundles it, "
                + "such as -PplatformType=IU", javaScript);
        PsiFile jsFile = createFile("test.js", "var x = 1;");
        assertTrue(jsFile.getLanguage().isKindOf(javaScript));

        var provider = new DAPDebuggerEditorsProvider(jsFile.getFileType(), null);
        String[] expressions = {"x + y", "o[\"x\"]", "\"x\" in o", "o.hasOwnProperty(\"x\")", "Reflect.get(o, \"x\")",
                "document.getElementById(\"x\")", "require(\"x\")", "o?.x", "`${x}`", "x => x"};
        for (String text : expressions) {
            PsiFile fragment = provider.createExpressionCodeFragment(getProject(), text, jsFile, true);
            PsiTreeUtil.processElements(fragment, element -> {
                for (PsiReference reference : element.getReferences()) {
                    // Before the fix: ClassCastException DAPExpressionCodeFragment -> JSElement in JSStubBasedScopeHandler.getScope
                    if (reference instanceof PsiPolyVariantReference poly) {
                        poly.multiResolve(false);
                    } else {
                        reference.resolve();
                    }
                }
                return true;
            });
        }
    }
}
