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
package com.redhat.devtools.lsp4ij.features;

import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.inlayhint.LSPInlayHintsProvider;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

/**
 * The API of the inlay hints providers that plugins built against 0.21.0 use.
 */
public class InlayHintsProvidersApiOf021Test extends TestCase {

    public void testAProviderWithoutANameOfItsOwnIsNamedLSP() {
        // A provider built against 0.21.0 didn't need to name itself: without this name, IntelliJ fails with
        // AbstractMethodError when it asks for it
        var provider = new AbstractLSPInlayHintsProvider() {
            @Override
            protected void doCollect(@NotNull PsiFile psiFile,
                                     @NotNull Editor editor,
                                     @NotNull PresentationFactory factory,
                                     @NotNull InlayHintsSink inlayHintsSink) {
            }
        };

        assertEquals("LSP", provider.getName());
    }

    @SuppressWarnings("removal")
    public void testTheProviderIdOf021IsThePrefixOfTheProviderIdOfEachLanguage() {
        assertEquals(LSPInlayHintsProvider.PROVIDER_ID_PREFIX, LSPInlayHintsProvider.PROVIDER_ID);
        assertTrue(LSPInlayHintsProvider.getProviderId("typescript").startsWith(LSPInlayHintsProvider.PROVIDER_ID + "."));
    }
}
