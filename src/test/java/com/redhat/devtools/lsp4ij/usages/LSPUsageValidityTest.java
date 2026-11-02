/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.FileContentUtilCore;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An LSP element is valid while its PSI file is. A usage in the Find view can outlive its PSI file: once the file is
 * deleted, or its PSI file is replaced by a new one, the usage is stale and must say so.
 *
 * @see <a href="https://github.com/redhat-developer/lsp4ij/issues/147">#147</a>
 */
public class LSPUsageValidityTest extends BasePlatformTestCase {

    private static final String TEXT = "value = value + 1";

    private static final TextRange VALUE = new TextRange(0, 5);

    public void testElementIsValidWhileItsFileIs() {
        PsiFile file = myFixture.configureByText("test.txt", TEXT);

        assertTrue(new LSPPsiElement(file, VALUE).isValid());
        assertTrue(usage(file).isValid());
    }

    public void testElementIsInvalidOnceItsFileIsDeleted() {
        PsiFile file = myFixture.configureByText("test.txt", TEXT);
        LSPPsiElement element = new LSPPsiElement(file, VALUE);
        LSPUsagePsiElement usage = usage(file);

        WriteCommandAction.runWriteCommandAction(getProject(), file::delete);

        assertFalse(file.isValid());
        assertFalse(element.isValid());
        assertFalse(usage.isValid());
        // ElementBase computes no icon for an invalid element; for a valid one whose file is invalid, it logs a
        // PsiInvalidElementAccessException as an error
        assertNull(usage.getIcon(0));
    }

    public void testUsageIsInvalidOnceItsFileIsReparsed() {
        PsiFile file = myFixture.configureByText("test.txt", TEXT);
        LSPUsagePsiElement usage = usage(file);

        // the file is still there, but its PSI file is replaced by a new one, as in the issue's "different providers"
        FileContentUtilCore.reparseFiles(file.getVirtualFile());

        assertFalse(file.isValid());
        assertFalse(usage.isValid());
        assertNull(usage.getIcon(0));
    }

    public void testStaleUsageIsInvalidInTheFindView() {
        assertStaleUsageIsInvalidInTheFindView(null, UsageInfo2UsageAdapter.class);
    }

    public void testStaleUsageWithAccessIsInvalidInTheFindView() {
        // a usage that reads or writes is shown by the read/write access adapter
        assertStaleUsageIsInvalidInTheFindView(ReadWriteAccessDetector.Access.Write, ReadWriteAccessUsageInfo2UsageAdapter.class);
    }

    private void assertStaleUsageIsInvalidInTheFindView(@Nullable ReadWriteAccessDetector.Access access,
                                                        @NotNull Class<? extends UsageInfo2UsageAdapter> adapter) {
        PsiFile file = myFixture.configureByText("test.txt", TEXT);
        LSPUsagePsiElement element = usage(file);
        element.setAccess(access);
        UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter) LSPUsageSearcher.toUsage(element);
        assertEquals(adapter, usage.getClass());
        usage.updateCachedPresentation();
        assertTrue(usage.isValid());

        // as in the issue: the reference is deleted, and the PSI file is replaced
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myFixture.getEditor().getDocument().deleteString(0, 5));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        FileContentUtilCore.reparseFiles(file.getVirtualFile());

        // the Find view updates the presentation of a usage whose file has changed, unless the usage is invalid
        usage.updateCachedPresentation();
        assertFalse(usage.isValid());
    }

    private static @NotNull LSPUsagePsiElement usage(@NotNull PsiFile file) {
        LSPUsagePsiElement usage = new LSPUsagePsiElement(file, VALUE);
        usage.setKind(LSPUsagePsiElement.UsageKind.references);
        return usage;
    }
}
