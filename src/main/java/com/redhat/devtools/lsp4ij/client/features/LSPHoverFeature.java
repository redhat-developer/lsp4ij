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
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter;
import com.redhat.devtools.lsp4ij.server.capabilities.HoverCapabilityRegistry;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP hover feature.
 */
@ApiStatus.Experimental
public class LSPHoverFeature extends AbstractLSPDocumentFeature {

    private HoverCapabilityRegistry hoverCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isHoverSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support hover and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support hover and false otherwise.
     */
    public boolean isHoverSupported(@NotNull PsiFile file) {
        return getHoverCapabilityRegistry().isHoverSupported(file);
    }

    public HoverCapabilityRegistry getHoverCapabilityRegistry() {
        if (hoverCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            hoverCapabilityRegistry = new HoverCapabilityRegistry(clientFeatures);
            hoverCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return hoverCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (hoverCapabilityRegistry != null) {
            hoverCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    /**
     * Returns the HTML content from the given LSP Markup content and null otherwise.
     *
     * @param content the LSP Markup content.
     * @param file    the file.
     * @return the HTML content from the given LSP Markup content and null otherwise.
     */
    @Nullable
    public String getContent(@NotNull MarkupContent content,
                             @NotNull PsiFile file) {
        if (MarkupKind.MARKDOWN.equals(content.getKind())) {
            return convertMarkDownToHtml(content, file);
        }
        return content.getValue();
    }

    @Nullable
    protected String convertMarkDownToHtml(@NotNull MarkupContent content,
                                           @NotNull PsiFile file) {
        var project = file.getProject();
        return MarkdownConverter.getInstance(project)
                .toHtml(StringUtilRt.convertLineSeparators(content.getValue()), file);
    }

}
