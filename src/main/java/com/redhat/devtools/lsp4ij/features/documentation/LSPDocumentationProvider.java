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
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.lang.documentation.ExternalDocumentationHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.io.URLUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.internal.SimpleLanguageUtils;
import com.redhat.devtools.lsp4ij.features.completion.LSPCompletionProposal;
import org.eclipse.lsp4j.MarkupContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;
import static com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter.toHTML;

/**
 * {@link DocumentationProviderEx} implementation for LSP to support:
 *
 * <ul>
 *     <li>textDocument/hover</li>
 *     <li>documentation for completion item</li>
 * </ul>.
 */
public class LSPDocumentationProvider extends DocumentationProviderEx implements ExternalDocumentationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDocumentationProvider.class);

    private static final Key<Integer> TARGET_OFFSET_KEY = new Key<>(LSPDocumentationProvider.class.getName());

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
        if (contextElement != null && SimpleLanguageUtils.isSupported(file.getLanguage())) {
            return contextElement;
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
                PsiFile psiFile = originalElement  != null ?  originalElement.getContainingFile() : null;
                if (!LanguageServersRegistry.getInstance().isFileSupported(psiFile)) {
                    return null;
                }
                VirtualFile file = LSPIJUtils.getFile(originalElement);
                if (file == null) {
                    return null;
                }
                final Document document = LSPIJUtils.getDocument(file);
                if (document == null) {
                    return null;
                }
                editor = LSPIJUtils.editorForElement(originalElement);
                int targetOffset = getTargetOffset(originalElement);
                LSPHoverSupport hoverSupport = LSPFileSupport.getSupport(element.getContainingFile()).getHoverSupport();
                CompletableFuture<List<MarkupContent>> hoverFuture = hoverSupport.getHover(targetOffset, document);

                try {
                    waitUntilDone(hoverFuture, psiFile);
                } catch (ProcessCanceledException | CancellationException e) {
                    // cancel the LSP requests textDocument/hover
                    hoverSupport.cancel();
                } catch (ExecutionException e) {
                    LOGGER.error("Error while consuming LSP 'textDocument/hover' request", e);
                }

                if (isDoneNormally(hoverFuture)) {
                    // textDocument/hover has been collected correctly
                    markupContent = hoverFuture.getNow(null);
                }
            }

            if (markupContent == null || markupContent.isEmpty()) {
                return null;
            }
            String s = markupContent
                    .stream()
                    .map(MarkupContent::getValue)
                    .collect(Collectors.joining("\n\n"));
            return styleHtml(editor, toHTML(s));
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


    public static String styleHtml(@Nullable Editor editor, String htmlBody) {
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
