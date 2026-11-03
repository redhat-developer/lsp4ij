/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPSemanticTokensFileViewProviderFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests the text which {@link LSPSemanticTokenDocumentationProvider} gives to the Ctrl+hover hint. The hint passes it
 * to {@link UIUtil#getHtmlBody(String)}, so the tests check the result of that method as well.
 */
public class LSPSemanticTokenDocumentationProviderTest extends LSPSemanticTokensFileViewProviderFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.ts";

    // language=typescript
    private static final String TEST_FILE_BODY = """
            function foo() {}
            """;

    // language=json
    private static final String MOCK_SEMANTIC_TOKENS_PROVIDER_JSON = """
            {
              "legend": {
                "tokenTypes": ["function"],
                "tokenModifiers": ["declaration"]
              },
              "full": true
            }
            """;

    // language=json
    private static final String MOCK_SEMANTIC_TOKENS_JSON = """
            {
              "data": [0, 9, 3, 0, 1]
            }
            """;

    // language=markdown
    private static final String MARKDOWN_WITH_TABLE = """
            ### Arguments

            | Name | Default |
            | :--- | :--- |
            | `user` | |
            | `password` | `secret` |

            Opens the shop and logs in as the given user.
            """;

    public LSPSemanticTokenDocumentationProviderTest() {
        super("*.ts", "typescript");
    }

    public void testMarkdownIsPutIntoAnHtmlElement() {
        PsiFile file = openFile();

        String hintText = getHintText(file, new Hover(new MarkupContent(MarkupKind.MARKDOWN, MARKDOWN_WITH_TABLE)));

        assertTrue(hintText, hintText.startsWith("<html>"));
        assertTrue(hintText, hintText.endsWith("</html>"));
        String body = UIUtil.getHtmlBody(hintText);
        assertFalse(body, body.contains("<br>"));
        assertTrue(body, body.contains("<table>"));
        assertTrue(body, body.contains("Opens the shop"));
    }

    public void testSeveralMarkdownContentsAreInOneHtmlElement() {
        PsiFile file = openFile();

        String hintText = getHintText(file, new Hover(List.of(Either.forLeft("First."), Either.forLeft("Second."))));

        assertEquals(hintText, 1, StringUtil.getOccurrenceCount(hintText, "<html>"));
        String body = UIUtil.getHtmlBody(hintText);
        assertFalse(body, body.contains("<br>"));
        assertTrue(body, body.contains("First."));
        assertTrue(body, body.contains("Second."));
    }

    public void testPlainTextStaysAsItIs() {
        PsiFile file = openFile();

        String hintText = getHintText(file, new Hover(new MarkupContent(MarkupKind.PLAINTEXT, "First line\nSecond line")));

        assertEquals("First line\nSecond line", hintText);
        assertEquals("First line<br>Second line", UIUtil.getHtmlBody(hintText));
    }

    private @NotNull PsiFile openFile() {
        return initialize(TEST_FILE_NAME, TEST_FILE_BODY, MOCK_SEMANTIC_TOKENS_PROVIDER_JSON, MOCK_SEMANTIC_TOKENS_JSON, true);
    }

    private static @NotNull String getHintText(@NotNull PsiFile file, @NotNull Hover hover) {
        MockLanguageServer.INSTANCE.setHover(hover);
        PsiElement element = file.findElementAt(TEST_FILE_BODY.indexOf("foo"));
        assertInstanceOf(element, LSPSemanticTokenPsiElement.class);
        String hintText = new LSPSemanticTokenDocumentationProvider().getQuickNavigateInfo(element, element);
        assertNotNull(hintText);
        return hintText;
    }
}
