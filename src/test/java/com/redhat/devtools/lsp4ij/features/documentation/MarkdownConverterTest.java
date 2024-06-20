/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Test Markdown conversion to HTML
 */
public class MarkdownConverterTest extends LSPCodeInsightFixtureTestCase {

    public void testTablesConversion() {

        String markdown = """
                Does something, `somewhere`:
                                
                |                    |                    |
                |--------------------|--------------------|
                | Row One - Cell One | Row One - Cell Two |
                | Row Two - Cell One | Row Two - Cell Two |
                """;

        String html = """
                <p>Does something, <code>somewhere</code>:</p>
                <table>
                <thead>
                <tr><th> </th><th> </th></tr>
                </thead>
                <tbody>
                <tr><td>Row One - Cell One</td><td>Row One - Cell Two</td></tr>
                <tr><td>Row Two - Cell One</td><td>Row Two - Cell Two</td></tr>
                </tbody>
                </table>
                """;
        assertEquals(html, toHtml(markdown));
    }

    public void testLinksConversion() {
        String markdown = "Here is a link [example](https://example.com)";
        String html = "<p>Here is a link <a href=\"https://example.com\">example</a></p>\n";
        assertEquals(html, toHtml(markdown));
    }

    public void testListsConversion() {
        String markdown = """
                 *  Coffee
                 *  Tea
                 *  Milk 
                """;
        String html = """
                <ul>
                <li>Coffee</li>
                <li>Tea</li>
                <li>Milk</li>
                </ul>
                """;
        assertEquals(html, toHtml(markdown));
    }

    public void testMiscellaneousConversions() {
        assertEquals("<p>This <em>is</em> <code>my code</code></p>\n", toHtml("This _is_ `my code`"));
        assertEquals("<p>The <code>&lt;project&gt;</code> element is the root of the descriptor.</p>\n", toHtml("The `<project>` element is the root of the descriptor."));
        assertEquals("<h1>Hey Man</h1>\n", toHtml("# Hey Man #"));
        assertEquals("<p>ONLY_THIS_TEXT</p>\n", toHtml("ONLY_THIS_TEXT"));
        assertEquals("<p>This is\n<strong>bold</strong></p>\n", toHtml("""
                This is
                **bold**
                """));
    }

    public void testMultiLineConversion() {
        String markdown = """
                multi
                line
                `HTML`
                stuff
                """;

        String html = """
                <p>multi
                line
                <code>HTML</code>
                stuff</p>
                """;
        assertEquals(html, toHtml(markdown));

        markdown = """
                multi
                                
                line `HTML` stuff
                """;

        html = """
                <p>multi</p>
                <p>line <code>HTML</code> stuff</p>
                """;
        assertEquals(html, toHtml(markdown));
    }

    public void testSimpleCodeBlockConversion() {
        String markdown = """
                Here's some java code:
                                
                ```my-java
                    @Test
                    public void linksConversion() {
                        String markdown = "Here is a link [example](https://example.com)";
                        String html = "<p>Here is a link <a href=\\"https://example.com\\">example</a></p>\\n";
                        assertEquals(html, convert(markdown));
                    }
                ```
                """;
        String html = """
                <p>Here's some java code:</p>
                <pre><code>    @Test
                    public void linksConversion() {
                        String markdown = &quot;Here is a link [example](https://example.com)&quot;;
                        String html = &quot;&lt;p&gt;Here is a link &lt;a href=\\&quot;https://example.com\\&quot;&gt;example&lt;/a&gt;&lt;/p&gt;\\n&quot;;
                        assertEquals(html, convert(markdown));
                    }
                </code></pre>
                """;
        assertEquals(html, toHtml(markdown));

        markdown = """
                Here's some XML code:
                                
                ```my-xml
                <?xml version="1.0" encoding="UTF-8"?>
                <note>
                  <to>Angelo</to>
                  <from>Fred</from>
                  <heading>Tests</heading>
                  <body>I wrote them!</body>
                </note>
                ```
                """;
        html = """
                <p>Here's some XML code:</p>
                <pre><code>&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;
                &lt;note&gt;
                  &lt;to&gt;Angelo&lt;/to&gt;
                  &lt;from&gt;Fred&lt;/from&gt;
                  &lt;heading&gt;Tests&lt;/heading&gt;
                  &lt;body&gt;I wrote them!&lt;/body&gt;
                &lt;/note&gt;
                </code></pre>
                """;
        assertEquals(html, toHtml(markdown));
    }

    public void testXmlHighlightCodeBlockConversion() {
        // Test with XML syntax coloration which uses a custom lexer (not TextMate)
        String markdown = """
                Here's some XML code:
                                
                ```xml
                <?xml version="1.0" encoding="UTF-8"?>
                <note>
                  <to>Angelo</to>
                  <from>Fred</from>
                  <heading>Tests</heading>
                  <body>I wrote them!</body>
                </note>
                ```
                """;
        String html = """
                <p>Here's some XML code:</p>
                <pre><span style="font-style:italic;">&lt;?</span><span style="color:#0000ff;font-weight:bold;">xml&#32;version</span><span style="color:#008000;font-weight:bold;">="1.0"&#32;</span><span style="color:#0000ff;font-weight:bold;">encoding</span><span style="color:#008000;font-weight:bold;">="UTF-8"</span><span style="font-style:italic;">?&gt;<br></span><span style="">&lt;</span><span style="color:#000080;font-weight:bold;">note</span><span style="">&gt;</span><span style=""><br></span><span style="">&#32;&#32;</span><span style="">&lt;</span><span style="color:#000080;font-weight:bold;">to</span><span style="">&gt;</span><span style="">Angelo</span><span style="">&lt;/</span><span style="color:#000080;font-weight:bold;">to</span><span style="">&gt;</span><span style=""><br></span><span style="">&#32;&#32;</span><span style="">&lt;</span><span style="color:#000080;font-weight:bold;">from</span><span style="">&gt;</span><span style="">Fred</span><span style="">&lt;/</span><span style="color:#000080;font-weight:bold;">from</span><span style="">&gt;</span><span style=""><br></span><span style="">&#32;&#32;</span><span style="">&lt;</span><span style="color:#000080;font-weight:bold;">heading</span><span style="">&gt;</span><span style="">Tests</span><span style="">&lt;/</span><span style="color:#000080;font-weight:bold;">heading</span><span style="">&gt;</span><span style=""><br></span><span style="">&#32;&#32;</span><span style="">&lt;</span><span style="color:#000080;font-weight:bold;">body</span><span style="">&gt;</span><span style="">I&#32;wrote&#32;them!</span><span style="">&lt;/</span><span style="color:#000080;font-weight:bold;">body</span><span style="">&gt;</span><span style=""><br></span><span style="">&lt;/</span><span style="color:#000080;font-weight:bold;">note</span><span style="">&gt;</span></pre>
                """;

        assertEquals(html, toHtml(markdown));
    }

    public void testTypeScriptHighlightCodeBlockConversion() {
        // Test with TypeScript syntax coloration which uses TextMate
        String markdown = """
                Here's some TypeScript code:
                                
                ```ts
                const s = '';
                ```
                """;
        String html = """
                <p>Here's some TypeScript code:</p>
                <pre><span style="color:#000080;font-weight:bold;">const&#32;</span><span style="">s&#32;</span><span style="color:#000080;font-weight:bold;">=&#32;</span><span style="color:#008000;font-weight:bold;">''</span><span style="">;<br></span></pre>
                """;

        assertEquals(html, toHtml(markdown));
    }

    public void testTypeScriptHighlightBlockquoteConversion() {
        // When blockquote is not indented, the syntax coloration is not applied (like vscode)
        String markdown = """
                Here's some TypeScript code:
                                
                > const s = '';
                """;
        String html = """
                <p>Here's some TypeScript code:</p>
                <blockquote>
                <p>const s = '';</p>
                </blockquote>
                """;

        assertEquals(html, toHtml(markdown, null, null, "test.ts"));
    }

    public void testTypeScriptHighlightIndentedBlockquoteWithFileNameConversion() {
        // When blockquote is indented, the syntax coloration is applied (like vscode)
        // Test with TypeScript syntax coloration which uses TextMate
        String markdown = """
                Here's some TypeScript code:
                                
                >     const s = '';
                """;
        String html = """
                <p>Here's some TypeScript code:</p>
                <blockquote>
                <pre><span style="color:#000080;font-weight:bold;">const&#32;</span><span style="">s&#32;</span><span style="color:#000080;font-weight:bold;">=&#32;</span><span style="color:#008000;font-weight:bold;">''</span><span style="">;<br></span></pre>
                </blockquote>
                """;

        assertEquals(html, toHtml(markdown, null, null, "test.ts"));
    }

    public void testXmlHighlightIndentedBlockquoteWithLanguageConversion() {
        // When blockquote is indented, the syntax coloration is applied (like vscode)
        // Test with TypeScript syntax coloration which uses TextMate
        // Test with XML syntax coloration which uses a custom lexer (not TextMate)
        String markdown = """
                Here's some XML code:
                                
                >     <?xml version="1.0" encoding="UTF-8"?>
                """;
        String html = """
                <p>Here's some XML code:</p>
                <blockquote>
                <pre><span style="font-style:italic;">&lt;?</span><span style="color:#0000ff;font-weight:bold;">xml&#32;version</span><span style="color:#008000;font-weight:bold;">="1.0"&#32;</span><span style="color:#0000ff;font-weight:bold;">encoding</span><span style="color:#008000;font-weight:bold;">="UTF-8"</span><span style="font-style:italic;">?&gt;</span></pre>
                </blockquote>
                """;

        assertEquals(html, toHtml(markdown, null, XMLLanguage.INSTANCE, null));
    }

    public void testTypeScriptWithLinksInsideParagraph() {
        String markdown = "\n```typescript\nfunction foo(): void\n```\nSome content...\n\u003e and some links:\n [bar.ts](bar.ts#L2:2)\n [lsp4ij](https://github.com/redhat-developer/lsp4ij)";
        String html = "<pre><span style=\"color:#000080;font-weight:bold;\">function&#32;</span><span style=\"\">foo()</span><span style=\"color:#000080;font-weight:bold;\">:&#32;</span><span style=\"font-style:italic;\">void<br></span></pre>\n" +
                "<p>Some content...</p>\n" +
                "<blockquote>\n" +
                "<p>and some links:\n" +
                "<a href=\"bar.ts#L2:2\">bar.ts</a>\n" +
                "<a href=\"https://github.com/redhat-developer/lsp4ij\">lsp4ij</a></p>\n" +
                "</blockquote>\n";
        assertEquals(html, toHtml(markdown, null, null, "test.ts"));
    }

    private String toHtml(String markdown) {
        return MarkdownConverter.getInstance(myFixture.getProject()).toHtml(markdown);
    }

    private String toHtml(@NotNull String markdown,
                          @Nullable Path baseDir,
                          @Nullable Language language,
                          @Nullable String fileName) {
        return MarkdownConverter.getInstance(myFixture.getProject()).toHtml(markdown, baseDir, language, fileName);
    }
}