/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlayhint;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * IntelliJ shows the tooltip of an inlay as HTML, so the tooltip of an inlay hint, or of one of its label parts, is
 * converted to HTML: Markdown is rendered, and plain text is escaped.
 */
public class InlayHintTooltipTest extends LSPCodeInsightFixtureTestCase {

    public InlayHintTooltipTest() {
        super("*.foo");
    }

    public void testAPlainTooltipIsEscapedAndKeepsItsLineBreaks() {
        assertEquals("List&lt;String&gt; &amp; more<br>second line",
                LSPInlayHintsProvider.toHtmlTooltip(Either.forLeft("List<String> & more\nsecond line"), file()));
    }

    public void testAMarkdownTooltipIsRendered() {
        String html = LSPInlayHintsProvider.toHtmlTooltip(
                Either.forRight(new MarkupContent(MarkupKind.MARKDOWN, "The **type** of `x`")), file());
        assertNotNull(html);
        assertTrue(html, html.contains("<strong>type</strong>"));
        assertTrue(html, html.contains("<code>x</code>"));
    }

    public void testAPlainTextMarkupTooltipIsEscaped() {
        assertEquals("a &lt;b&gt;",
                LSPInlayHintsProvider.toHtmlTooltip(Either.forRight(new MarkupContent(MarkupKind.PLAINTEXT, "a <b>")), file()));
    }

    public void testAnEmptyOrMissingTooltipIsNone() {
        assertNull(LSPInlayHintsProvider.toHtmlTooltip(null, file()));
        assertNull(LSPInlayHintsProvider.toHtmlTooltip(Either.forLeft(""), file()));
        assertNull(LSPInlayHintsProvider.toHtmlTooltip(Either.forRight(new MarkupContent(MarkupKind.MARKDOWN, "")), file()));
    }

    private PsiFile file() {
        return myFixture.configureByText("main.foo", "text");
    }
}
