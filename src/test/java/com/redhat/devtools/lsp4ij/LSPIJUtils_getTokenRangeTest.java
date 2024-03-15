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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Tests for {@link LSPIJUtils#getTokenRange(Document, int)}.
 */
public class LSPIJUtils_getTokenRangeTest extends BasePlatformTestCase {

    public void testEmpty() {
        assertTokenRange("|", null);
        assertTokenRange("|    ", null);
        assertTokenRange("    |    ", null);
    }

    public void testSimple() {
        assertTokenRange("f|oo", "[foo]");
        assertTokenRange("|foo", "[foo]");
        assertTokenRange("foo|", "[foo]");

        assertTokenRange("\nf|oo\n", "\n[foo]\n");
        assertTokenRange("\n|foo\n", "\n[foo]\n");
        assertTokenRange("\nfoo|\n", "\n[foo]\n");
    }

    public void testSimpleWithSpaces() {
        assertTokenRange(" f|oo", " [foo]");
        assertTokenRange(" |foo", " [foo]");
        assertTokenRange(" foo|", " [foo]");

        assertTokenRange("f|oo ", "[foo] ");
        assertTokenRange("|foo ", "[foo] ");
        assertTokenRange("foo| ", "[foo] ");

        assertTokenRange(" f|oo ", " [foo] ");
        assertTokenRange(" |foo ", " [foo] ");
        assertTokenRange(" foo| ", " [foo] ");

        assertTokenRange(" f|oo bar ", " [foo] bar ");
        assertTokenRange(" foo b|ar ", " foo [bar] ");
    }

    public void testObjectPart() {
        assertTokenRange("fo|o.bar()", "[foo].bar()");
        assertTokenRange("\nfo|o.bar()\n", "\n[foo].bar()\n");
    }

    public void testMethod() {
        assertTokenRange("foo.b|ar()", "foo.[bar]()");
        assertTokenRange("foo.|bar()", "foo.[bar]()");
        assertTokenRange("foo.bar|()", "foo.[bar]()");

        assertTokenRange("\nfoo.b|ar()\n", "\nfoo.[bar]()\n");
        assertTokenRange("\nfoo.|bar()\n", "\nfoo.[bar]()\n");
        assertTokenRange("\nfoo.bar|()\n", "\nfoo.[bar]()\n");
    }

    public void testBracket() {
        assertTokenRange("foo.bar(|)", null);
    }

    private static void assertTokenRange(final String contentWithOffset, String expected) {
        int offset = contentWithOffset.indexOf('|');
        String content = contentWithOffset.substring(0, offset) + contentWithOffset.substring(offset + 1);
        Document document = new DocumentImpl(content);
        TextRange textRange = LSPIJUtils.getTokenRange(document, offset);
        if (expected == null) {
            assertNull("TextRange should be null",textRange);
            return;
        }

        assertNotNull("TextRange should not be null", textRange);

        String startPart = document.getText(new TextRange(0, textRange.getStartOffset()));
        String rangePart = document.getText(textRange);
        String endPart = document.getText(new TextRange(textRange.getEndOffset(), content.length()));
        String actual = startPart + "[" + rangePart + "]" + endPart;
        assertEquals(expected, actual);
    }
}
