/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Go to Implementation asks the language server for the implementations of an element at the name of the element.
 * The text range of an element with real PSI, like a decorated Python function, can start before its name.
 */
public class LSPWorkspaceImplementationsSearchTest extends LSPCodeInsightFixtureTestCase {

    // a Python file with a decorated function, as in #1821
    private static final String TEXT = "def foo[T](fn: T) -> T: ...\n\n\n@foo\ndef bar(): ...\n";
    // "@foo\ndef bar(): ..." in TEXT, the function with its decorator
    private static final TextRange FUNCTION = new TextRange(TEXT.indexOf("@foo"), TEXT.length() - 1);
    // "bar" in TEXT, the name of the function
    private static final TextRange NAME = TextRange.from(TEXT.indexOf("bar"), "bar".length());
    // the start of "bar"
    private static final Position AT_NAME = new Position(4, 4);

    public LSPWorkspaceImplementationsSearchTest() {
        super("*.foo");
    }

    public void testADecoratedFunctionIsSearchedAtItsName() throws Exception {
        PsiFile file = open();

        // not at its decorator, "@foo" at (3, 0)
        assertEquals(AT_NAME, searchedPosition(file, new TestElement(file, FUNCTION, NAME.getStartOffset())));
    }

    public void testAWordIsSearchedAtItsStart() throws Exception {
        PsiFile file = open();

        // the element of a file without real PSI is the word at the caret
        assertEquals(AT_NAME, searchedPosition(file, new LSPPsiElement(file, NAME)));
    }

    public void testAnElementWithATextOffsetOutsideItIsSearchedAtItsStart() throws Exception {
        PsiFile file = open();

        // -1, the default text offset of a light element
        assertEquals(AT_NAME, searchedPosition(file, new TestElement(file, NAME, -1)));
    }

    public void testAnElementWithATextOffsetAfterItIsSearchedAtItsStart() throws Exception {
        PsiFile file = open();

        // one character past the end of the element, still in the document
        assertEquals(AT_NAME, searchedPosition(file, new TestElement(file, NAME, NAME.getEndOffset() + 1)));
    }

    private PsiFile open() throws Exception {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setImplementationProvider(true);
            return capabilities;
        });
        PsiFile file = myFixture.configureByText("main.foo", TEXT);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        // the function is its own implementation, so that no "not found" hint is shown
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockImplementations(
                List.of(new Location(LSPIJUtils.toUriAsString(file), new Range(AT_NAME, new Position(4, 7)))));
        return file;
    }

    private static @NotNull Position searchedPosition(@NotNull PsiFile file, @NotNull PsiElement element) throws Exception {
        // Go to Implementation searches in a background thread
        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> new LSPWorkspaceImplementationsSearch()
                        .execute(new DefinitionsScopedSearch.SearchParameters(element), implementation -> true))
                .get(30, TimeUnit.SECONDS);
        List<ImplementationParams> requests = MockLanguageServer.INSTANCE.getTextDocumentService().getImplementationRequests();
        assertEquals(1, requests.size());
        assertEquals(LSPIJUtils.toUriAsString(file), requests.get(0).getTextDocument().getUri());
        return requests.get(0).getPosition();
    }

    /**
     * An element with real PSI, whose text offset is given: for a named element, it is the offset of its name.
     */
    private static class TestElement extends FakePsiElement {

        private final @NotNull PsiFile file;
        private final @NotNull TextRange textRange;
        private final int textOffset;

        TestElement(@NotNull PsiFile file, @NotNull TextRange textRange, int textOffset) {
            this.file = file;
            this.textRange = textRange;
            this.textOffset = textOffset;
        }

        @Override
        public PsiElement getParent() {
            return file;
        }

        @Override
        public PsiFile getContainingFile() {
            return file;
        }

        @Override
        public TextRange getTextRange() {
            return textRange;
        }

        @Override
        public int getTextOffset() {
            return textOffset;
        }
    }
}
