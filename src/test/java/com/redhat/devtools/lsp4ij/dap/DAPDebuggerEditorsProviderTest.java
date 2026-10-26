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

import com.intellij.json.JsonFileType;
import com.intellij.json.JsonLanguage;
import com.intellij.json.json5.Json5Language;
import com.intellij.openapi.fileTypes.MockLanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.evaluation.EvaluationMode;

public class DAPDebuggerEditorsProviderTest extends LightPlatformTestCase {

    public void testWithSimpleLanguage() {
        PsiFile textFile = createFile("test.txt", "Hello, world.");
        assertEquals(PlainTextFileType.INSTANCE, textFile.getFileType());
        assertEquals(PlainTextLanguage.INSTANCE, textFile.getLanguage());

        DAPDebuggerEditorsProvider debuggerEditorsProvider = new DAPDebuggerEditorsProvider(PlainTextFileType.INSTANCE, null);

        PsiFile codeFragment = debuggerEditorsProvider.createExpressionCodeFragment(getProject(), "", textFile, true);
        assertEquals(PlainTextFileType.INSTANCE, codeFragment.getFileType());
        assertEquals(PlainTextLanguage.INSTANCE, codeFragment.getLanguage());
    }

    public void testFragmentIsPhysicalOnlyWhenAsked() {
        PsiFile textFile = createFile("test.txt", "Hello, world.");
        var provider = new DAPDebuggerEditorsProvider(PlainTextFileType.INSTANCE, null);

        assertTrue(provider.createExpressionCodeFragment(getProject(), "n", textFile, true).isPhysical());
        assertFalse(provider.createExpressionCodeFragment(getProject(), "n", textFile, false).isPhysical());
    }

    public void testTheDocumentOfAnExpressionEditorIsPhysical() {
        // IntelliJ highlights and inspects the PSI of this document, as its logpoint editor does,
        // and LocalInspectionsPass asserts that the file is physical
        PsiFile textFile = createFile("test.txt", "Hello, world.");
        var provider = new DAPDebuggerEditorsProvider(PlainTextFileType.INSTANCE, null);
        var expression = XDebuggerUtil.getInstance().createExpression("n", null, null, EvaluationMode.EXPRESSION);

        var document = provider.createDocument(getProject(), expression, textFile, EvaluationMode.EXPRESSION);

        PsiFile fragment = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
        assertNotNull(fragment);
        assertTrue(fragment.isPhysical());
    }

    public void testWithDialectLanguage() {
        // Create a file with the dialect language
        PsiFile dialectFile = PsiFileFactory.getInstance(getProject()).createFileFromText("test.json", Json5Language.INSTANCE, "{}");
        VirtualFile dialectVirtualFile = dialectFile.getVirtualFile();
        // Force the file type to be the base instead of the one inferred from the dialect language used above
        ((LightVirtualFile) dialectVirtualFile).setFileType(JsonFileType.INSTANCE);
        assertEquals(JsonFileType.INSTANCE, dialectFile.getFileType());
        assertEquals(Json5Language.INSTANCE, dialectFile.getLanguage());

        // This should also use the base file type
        DAPDebuggerEditorsProvider debuggerEditorsProvider = new DAPDebuggerEditorsProvider(MockLanguageFileType.INSTANCE, null);

        // If the original issue exists, this will fail with "JSON5 doesn't participate in view provider..."
        PsiFile codeFragment = debuggerEditorsProvider.createExpressionCodeFragment(getProject(), "", dialectFile, true);
        assertEquals(JsonFileType.INSTANCE, codeFragment.getFileType());
        // It should have degraded to the base language
        assertEquals(JsonLanguage.INSTANCE, codeFragment.getLanguage());
    }
}
