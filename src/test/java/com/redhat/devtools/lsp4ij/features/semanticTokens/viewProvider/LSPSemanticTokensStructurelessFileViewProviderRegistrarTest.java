/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPSemanticTokensFileViewProviderFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Tests the semantic tokens-based file view provider registrar that associates the view provider factory with abstract
 * file types used by language server definitions dynamically since it's not possible statically via plugin.xml.
 */
public class LSPSemanticTokensStructurelessFileViewProviderRegistrarTest extends LSPSemanticTokensFileViewProviderFixtureTestCase {

    public LSPSemanticTokensStructurelessFileViewProviderRegistrarTest() {
        super("*.css", "css");
    }

    public void testRegistrar() {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);

        // Create a test CSS file
        PsiFile cssFile = myFixture.configureByText("test.css", "");

        // Initialize the language server
        Project project = cssFile.getProject();
        try {
            VirtualFile virtualFile = cssFile.getVirtualFile();
            LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(virtualFile, null, null)
                    .get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // Confirm that the CSS file uses our view provider
        assertInstanceOf(cssFile.getViewProvider(), LSPSemanticTokensStructurelessFileViewProvider.class);

        // And confirm that no other abstract file types do
        FileType cssFileType = cssFile.getFileType();
        PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
        for (FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
            if ((fileType instanceof AbstractFileType) && !Objects.equals(cssFileType, fileType)) {
                PsiFile testFile = fileFactory.createFileFromText("test." + fileType.getDefaultExtension(), fileType, "");
                assertFalse(testFile.getViewProvider() instanceof LSPSemanticTokensFileViewProvider);
            }
        }
    }
}
