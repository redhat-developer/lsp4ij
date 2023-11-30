/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.documentation;

import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.lang.documentation.ExternalDocumentationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.io.URLUtil;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.operations.completion.LSPCompletionProposal;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import org.eclipse.lsp4j.MarkupContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link DocumentationProviderEx} implementation for LSP to support:
 *
 * <ul>
 *     <li>textDocument/hover</li>
 *     <li>documentation for completion item</li>
 * </ul>.
 */
public class LSPDocumentationProvider extends DocumentationProviderEx implements ExternalDocumentationHandler {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    private static final Key<Integer> TARGET_OFFSET_KEY = new Key<>(LSPDocumentationProvider.class.getName());

    private static final Key<LSPTextHoverForFile> LSP_HOVER_KEY = new Key<>(LSPTextHoverForFile.class.getName());

    @Nullable
    @Override
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return generateDoc(element, originalElement);
    }

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement, int targetOffset) {
        if (contextElement != null) {
            // Store the offset where the hover has been triggered
            contextElement.putUserData(TARGET_OFFSET_KEY, targetOffset);
        }
        return super.getCustomDocumentationElement(editor, file, contextElement, targetOffset);
    }

    @Nullable
    @Override
    public String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        try {
            Project project = element.getProject();
            if (project.isDisposed()) {
                return null;
            }
            Editor editor = null;
            List<MarkupContent> markupContent = null;
            if (element instanceof LSPPsiElementForLookupItem) {
                // Show documentation for a given completion item in the "documentation popup" (see IJ Completion setting)
                // (LSP textDocument/completion request)
                editor = LSPIJUtils.editorForElement(element);
                markupContent = ((LSPPsiElementForLookupItem) element).getDocumentation();
            } else {
                // Show documentation for a hovered element (LSP textDocument/hover request).
                if (originalElement == null) {
                    return null;
                }
                editor = LSPIJUtils.editorForElement(originalElement);
                if (editor != null) {
                    LSPTextHoverForFile hover = editor.getUserData(LSP_HOVER_KEY);
                    if (hover == null) {
                        hover = new LSPTextHoverForFile(editor);
                        editor.putUserData(LSP_HOVER_KEY, hover);
                    }
                    int targetOffset = getTargetOffset(originalElement);
                    markupContent = hover.getHoverContent(originalElement, targetOffset, editor);
                }
            }

            if (editor == null || markupContent == null || markupContent.isEmpty()) {
                return null;
            }
            String s = markupContent
                    .stream()
                    .map(m -> m.getValue())
                    .collect(Collectors.joining("\n\n"));
            return styleHtml(editor, RENDERER.render(PARSER.parse(s)));
        } finally {
            if (originalElement != null) {
                originalElement.putUserData(TARGET_OFFSET_KEY, null);
            }
        }
    }

    private static int getTargetOffset(PsiElement originalElement) {
        Integer targetOffset = originalElement.getUserData(TARGET_OFFSET_KEY);
        if (targetOffset != null) {
            return targetOffset;
        }
        int startOffset = originalElement.getTextOffset();
        int textLength = originalElement.getTextLength();
        return startOffset + textLength / 2;
    }

    @Nullable
    @Override
    public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
        if (object instanceof LSPCompletionProposal) {
            MarkupContent documentation = ((LSPCompletionProposal) object).getDocumentation();
            if (documentation != null) {
                return new LSPPsiElementForLookupItem(documentation, psiManager, element);
            }
        }
        return null;
    }

    @Nullable
    @Override
    public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
        return null;
    }

    @Override
    public boolean handleExternal(PsiElement element, PsiElement originalElement) {
        return false;
    }

    @Override
    public boolean handleExternalLink(PsiManager psiManager, String link, PsiElement context) {
        //Ignore non-local uri (http(s), mailto, ftp...)
        if (URLUtil.URL_PATTERN.matcher(link).matches()) {
            return false;
        }
        VirtualFile file = LSPIJUtils.findResourceFor(link);
        if (file != null) {
            FileEditorManager.getInstance(psiManager.getProject()).openFile(file, true, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean canFetchDocumentationLink(String link) {
        return false;
    }

    @Override
    public @NotNull String fetchExternalDocumentation(@NotNull String link, @Nullable PsiElement element) {
        return null;
    }


    public static String styleHtml(Editor editor, String htmlBody) {
        if (htmlBody == null || htmlBody.isEmpty()) {
            return htmlBody;
        }
        Color background = editor.getColorsScheme().getDefaultBackground();
        Color foreground = editor.getColorsScheme().getDefaultForeground();

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
