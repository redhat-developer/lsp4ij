/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test Markdown conversion to HTML by using PsiFile.
 */
public class MarkdownConverterWithPsiFileTest extends LSPCodeInsightFixtureTestCase {

    private static final String USER_HOME = System.getProperty("user.home");

    public void testHighlightCodeBlockConversion() {
        // Here code block language is not set, the language is retrieved from the PsiFile.
        String fileName = "test.txt";

        String markdown = """
                Here's some XML code:
                                
                ```
                <?xml version="1.0" encoding="UTF-8"?>
                <note>
                  <to>Angelo</to>
                  <from>Fred</from>
                  <heading>Tests</heading>
                  <body>I wrote them!</body>
                </note>
                ```
                """;

        // As file is NOT an XML file, there are no syntax coloration
        String html = """
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

        assertMarkdownConverter(fileName, markdown, html);
    }

    public void testXmlHighlightCodeBlockConversion() {
        // Here code block language is not set, the language is retrieved from the PsiFile.
        String fileName = "test.xml";

        String markdown = """
                Here's some XML code:
                                
                ```
                <?xml version="1.0" encoding="UTF-8"?>
                <note>
                  <to>Angelo</to>
                  <from>Fred</from>
                  <heading>Tests</heading>
                  <body>I wrote them!</body>
                </note>
                ```
                """;

        // As file is an XML file, the XML syntax coloration is used.
        String html = """
                <p>Here's some XML code:</p>
                <pre><span style="font-style:italic;">&lt;?</span><span style="color:#0000ff;font-weight:bold;">xml&#32;version</span><span style="color:#008000;font-weight:bold;">="1.0"&#32;</span><span style="color:#0000ff;font-weight:bold;">encoding</span><span style="color:#008000;font-weight:bold;">="UTF-8"</span><span style="font-style:italic;">?&gt;<br></span><span style="">&lt;</span><span style="color:#000080;font-weight:bold;">note</span><span style="">&gt;</span><span style=""><br></span><span style="">&#32;&#32;</span><span style="">&lt;</span><span style="color:#000080;font-weight:bold;">to</span><span style="">&gt;</span><span style="">Angelo</span><span style="">&lt;/</span><span style="color:#000080;font-weight:bold;">to</span><span style="">&gt;</span><span style=""><br></span><span style="">&#32;&#32;</span><span style="">&lt;</span><span style="color:#000080;font-weight:bold;">from</span><span style="">&gt;</span><span style="">Fred</span><span style="">&lt;/</span><span style="color:#000080;font-weight:bold;">from</span><span style="">&gt;</span><span style=""><br></span><span style="">&#32;&#32;</span><span style="">&lt;</span><span style="color:#000080;font-weight:bold;">heading</span><span style="">&gt;</span><span style="">Tests</span><span style="">&lt;/</span><span style="color:#000080;font-weight:bold;">heading</span><span style="">&gt;</span><span style=""><br></span><span style="">&#32;&#32;</span><span style="">&lt;</span><span style="color:#000080;font-weight:bold;">body</span><span style="">&gt;</span><span style="">I&#32;wrote&#32;them!</span><span style="">&lt;/</span><span style="color:#000080;font-weight:bold;">body</span><span style="">&gt;</span><span style=""><br></span><span style="">&lt;/</span><span style="color:#000080;font-weight:bold;">note</span><span style="">&gt;</span></pre>
                """;

        assertMarkdownConverter(fileName, markdown, html);
    }

    public void testAbsoluteFileLink() {
        String markdown = "[foo](file:///" + USER_HOME + "/lsp/foo.txt)";
        assertMarkdownConverter("bar.txt", markdown, "<p><a href=\"file:///" + USER_HOME + "/lsp/foo.txt\">foo</a></p>\n");
    }

    public void testAbsoluteFileLinkWithSlash() {
        String markdown = "[foo](/lsp/foo.txt)";
        assertMarkdownConverter("bar.txt", markdown, "<p><a href=\"file://$SYSTEM_BASE_DIR$/lsp/foo.txt\">foo</a></p>\n");
    }

    public void testRelativeFileLink() {
        String markdown = "[foo](lsp/foo.txt)";
        assertMarkdownConverter("bar.txt", markdown, "<p><a href=\"file://$FILE_BASE_DIR$/lsp/foo.txt\">foo</a></p>\n");
    }

    private void assertMarkdownConverter(String fileName, String markdown, String html) {
        myFixture.configureByText(fileName, "");
        var file = myFixture.getFile();

        String systemBaseDir = getSystemBaseDir();
        html = html.replace("$SYSTEM_BASE_DIR$", systemBaseDir);

        String fileBaseDir = getFileBaseDir(file);
        html = html.replace("$FILE_BASE_DIR$", fileBaseDir);

        String actual = MarkdownConverter.getInstance(file.getProject()).toHtml(markdown, file);
        assertEquals("$SYSTEM_BASE_DIR$=" + systemBaseDir + ", $FILE_BASE_DIR$=" + fileBaseDir, html, actual);
    }

    private String getSystemBaseDir() {
        String systemBaseDir = LSPIJUtils.toUri(Paths.get("/").toFile()).toASCIIString();
        systemBaseDir = systemBaseDir.substring("file://".length());
        if (systemBaseDir.endsWith("/")) {
            systemBaseDir = systemBaseDir.substring(0, systemBaseDir.length() - 1);
        }
        return systemBaseDir;
    }

    private static @NotNull String getFileBaseDir(PsiFile file) {
        Path baseDir = file.getVirtualFile().getParent().getFileSystem().getNioPath(file.getVirtualFile().getParent());
        String fileBaseDir = LSPIJUtils.toUri(baseDir.toFile()).toASCIIString();
        fileBaseDir = fileBaseDir.substring("file://".length());
        if (fileBaseDir.endsWith("/")) {
            fileBaseDir = fileBaseDir.substring(0, fileBaseDir.length() - 1);
        }
        return fileBaseDir;
    }

}