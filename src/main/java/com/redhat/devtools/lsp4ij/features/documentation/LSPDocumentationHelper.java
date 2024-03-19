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
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.openapi.editor.Editor;
import org.eclipse.lsp4j.MarkupContent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter.toHTML;

/**
 * LSP documentation utilities.
 */
public class LSPDocumentationHelper {

    private LSPDocumentationHelper() {

    }

    /**
     * Convert the given LSP markup content to an HTML string and an empty string otherwise.
     *
     * @param content
     * @param editor
     * @return the converted HTML of the LSP markup content and an empty string otherwise.
     */
    public static String convertToHTML(@Nullable MarkupContent content,
                                       @Nullable Editor editor) {
        if (content == null) {
            return "";
        }
        return styleHtml(editor, toHTML(content.getValue()));
    }

    private static String styleHtml(@Nullable Editor editor, String htmlBody) {
        if (htmlBody == null || htmlBody.isEmpty()) {
            return htmlBody;
        }
        Color background = editor != null ? editor.getColorsScheme().getDefaultBackground() : null;
        Color foreground = editor != null ? editor.getColorsScheme().getDefaultForeground() : null;

        StringBuilder html = new StringBuilder("<html><head><style TYPE='text/css'>html { ");
        if (background != null) {
            html.append("background-color: ")
                    .append(toHTMLrgb(background))
                    .append(";");
        }
        if (foreground != null) {
            html.append("color: ")
                    .append(toHTMLrgb(foreground))
                    .append(";");
        }
        html
                .append(" }</style></head><body>")
                .append(htmlBody)
                .append("</body></html>");
        return html.toString();
    }

    private static String toHTMLrgb(Color rgb) {
        StringBuilder builder = new StringBuilder(7);
        builder.append('#');
        appendAsHexString(builder, rgb.getRed());
        appendAsHexString(builder, rgb.getGreen());
        appendAsHexString(builder, rgb.getBlue());
        return builder.toString();
    }

    private static void appendAsHexString(StringBuilder buffer, int intValue) {
        String hexValue = Integer.toHexString(intValue);
        if (hexValue.length() == 1) {
            buffer.append('0');
        }
        buffer.append(hexValue);
    }
}
