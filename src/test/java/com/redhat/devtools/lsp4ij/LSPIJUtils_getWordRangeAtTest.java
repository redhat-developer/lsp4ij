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

import static com.redhat.devtools.lsp4ij.LSP4IJAssert.assertWordRangeAt;

/**
 * Tests for {@link LSPIJUtils#getWordRangeAt(Document, int)}.
 */
public class LSPIJUtils_getWordRangeAtTest extends BasePlatformTestCase {

    public void testEmpty() {
        assertWordRangeAt("|", null);
        assertWordRangeAt("|    ", null);
        assertWordRangeAt("    |    ", null);
    }

    public void testSimple() {
        assertWordRangeAt("f|oo", "[foo]");
        assertWordRangeAt("|foo", "[foo]");
        assertWordRangeAt("foo|", "[foo]");

        assertWordRangeAt("\nf|oo\n", "\n[foo]\n");
        assertWordRangeAt("\n|foo\n", "\n[foo]\n");
        assertWordRangeAt("\nfoo|\n", "\n[foo]\n");
    }

    public void testSimpleWithSpaces() {
        assertWordRangeAt(" f|oo", " [foo]");
        assertWordRangeAt(" |foo", " [foo]");
        assertWordRangeAt(" foo|", " [foo]");

        assertWordRangeAt("f|oo ", "[foo] ");
        assertWordRangeAt("|foo ", "[foo] ");
        assertWordRangeAt("foo| ", "[foo] ");

        assertWordRangeAt(" f|oo ", " [foo] ");
        assertWordRangeAt(" |foo ", " [foo] ");
        assertWordRangeAt(" foo| ", " [foo] ");

        assertWordRangeAt(" f|oo bar ", " [foo] bar ");
        assertWordRangeAt(" foo b|ar ", " foo [bar] ");
    }

    public void testObjectPart() {
        assertWordRangeAt("fo|o.bar()", "[foo].bar()");
        assertWordRangeAt("\nfo|o.bar()\n", "\n[foo].bar()\n");
    }

    public void testMethod() {
        assertWordRangeAt("foo.b|ar()", "foo.[bar]()");
        assertWordRangeAt("foo.|bar()", "foo.[bar]()");
        assertWordRangeAt("foo.bar|()", "foo.[bar]()");

        assertWordRangeAt("\nfoo.b|ar()\n", "\nfoo.[bar]()\n");
        assertWordRangeAt("\nfoo.|bar()\n", "\nfoo.[bar]()\n");
        assertWordRangeAt("\nfoo.bar|()\n", "\nfoo.[bar]()\n");
    }

    public void testProperties() {
        assertWordRangeAt("|foo=bar", "[foo]=bar");
        assertWordRangeAt("f|oo=bar", "[foo]=bar");
        assertWordRangeAt("fo|o=bar", "[foo]=bar");
        assertWordRangeAt("foo|=bar", "[foo]=bar");

        assertWordRangeAt("foo=|bar", "foo=[bar]");
        assertWordRangeAt("foo=b|ar", "foo=[bar]");
        assertWordRangeAt("foo=ba|r", "foo=[bar]");
        assertWordRangeAt("foo=bar|", "foo=[bar]");
    }

    public void testBracket() {
        assertWordRangeAt("foo.bar(|)", null);
    }
}
