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
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import static com.redhat.devtools.lsp4ij.LSP4IJAssert.assertTokenRange;

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

    public void testProperties() {
        assertTokenRange("|foo=bar", "[foo]=bar");
        assertTokenRange("f|oo=bar", "[foo]=bar");
        assertTokenRange("fo|o=bar", "[foo]=bar");
        assertTokenRange("foo|=bar", "[foo]=bar");

        assertTokenRange("foo=|bar", "foo=[bar]");
        assertTokenRange("foo=b|ar", "foo=[bar]");
        assertTokenRange("foo=ba|r", "foo=[bar]");
        assertTokenRange("foo=bar|", "foo=[bar]");
    }

    public void testBracket() {
        assertTokenRange("foo.bar(|)", null);
    }
}
