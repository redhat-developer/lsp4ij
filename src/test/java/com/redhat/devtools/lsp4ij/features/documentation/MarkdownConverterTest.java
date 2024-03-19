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

import org.junit.Test;

import static com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter.toHTML;
import static org.junit.Assert.*;

/**
 * Test Markdown conversion to HTML
 */
public class MarkdownConverterTest {

    @Test
    public void tablesConversion() {
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
        assertEquals(html, toHTML(markdown));
    }

    @Test
    public void linksConversion() {
        String markdown = "Here is a link [example](https://example.com)";
        String html = "<p>Here is a link <a href=\"https://example.com\">example</a></p>\n";
        assertEquals(html, toHTML(markdown));
    }

    @Test
    public void codeBlockConversion() {
        String markdown = """
                Here's some java code:
                
                ```java
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
                <pre><code class="language-java">    @Test
                    public void linksConversion() {
                        String markdown = &quot;Here is a link [example](https://example.com)&quot;;
                        String html = &quot;&lt;p&gt;Here is a link &lt;a href=\\&quot;https://example.com\\&quot;&gt;example&lt;/a&gt;&lt;/p&gt;\\n&quot;;
                        assertEquals(html, convert(markdown));
                    }
                </code></pre>
                """;
        assertEquals(html, toHTML(markdown));

        markdown = """
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
        html = """
                <p>Here's some XML code:</p>
                <pre><code class="language-xml">&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;
                &lt;note&gt;
                  &lt;to&gt;Angelo&lt;/to&gt;
                  &lt;from&gt;Fred&lt;/from&gt;
                  &lt;heading&gt;Tests&lt;/heading&gt;
                  &lt;body&gt;I wrote them!&lt;/body&gt;
                &lt;/note&gt;
                </code></pre>
                """;
        assertEquals(html, toHTML(markdown));
    }

    @Test
    public void listsConversion() {
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
        assertEquals(html, toHTML(markdown));
    }
    
    @Test
    public void miscellaneousConversions() {
        assertEquals("<p>This <em>is</em> <code>my code</code></p>\n", toHTML("This _is_ `my code`"));
        assertEquals("<p>The <code>&lt;project&gt;</code> element is the root of the descriptor.</p>\n", toHTML("The `<project>` element is the root of the descriptor."));
        assertEquals("<h1>Hey Man</h1>\n", toHTML("# Hey Man #"));
        assertEquals("<p>ONLY_THIS_TEXT</p>\n", toHTML("ONLY_THIS_TEXT"));
        assertEquals("<p>This is<br />\n<strong>bold</strong></p>\n", toHTML("""
                                                                                      This is
                                                                                      **bold**
                                                                                      """));
    }

    @Test
    public void multiLineConversion() {
        String markdown = """
                multi
                line
                `HTML`
                stuff
                """;

        String html = """
                <p>multi<br />
                line<br />
                <code>HTML</code><br />
                stuff</p>
                """;
        assertEquals(html, toHTML(markdown));

        markdown = """
                multi
                
                line `HTML` stuff
                """;

        html = """
                <p>multi</p>
                <p>line <code>HTML</code> stuff</p>
                """;
        assertEquals(html, toHTML(markdown));
    }
}