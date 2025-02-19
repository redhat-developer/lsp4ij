/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Tests for {@link LSPIJEditorUtils}.
 */
public class LSPIJEditorUtilsTest extends BasePlatformTestCase {

    public void testTextMateQuoteCharactersForTypeScript() {
        testTextMateQuoteCharacters("ts", Set.of('\'', '"', '`'));
    }

    public void testTextMateQuoteCharactersForPhp() {
        testTextMateQuoteCharacters("php", Set.of('\'', '"'));
    }

    private void testTextMateQuoteCharacters(@NotNull String fileExtension,
                                             @NotNull Set<Character> expectedQuoteCharacters) {
        Project project = myFixture.getProject();
        assertNotNull(project);
        VirtualFile virtualFile = myFixture.createFile("test." + fileExtension, "");
        assertNotNull(virtualFile);
        PsiFile file = LSPIJUtils.getPsiFile(virtualFile, project);
        assertNotNull(file);
        myFixture.openFileInEditor(virtualFile);

        Set<Character> quoteCharacters = LSPIJEditorUtils.getQuoteCharacters(file);
        assertEquals(expectedQuoteCharacters, quoteCharacters);
    }
}
