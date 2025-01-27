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

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.server.capabilities.FoldingRangeCapabilityRegistry;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP foldingRange feature.
 */
@ApiStatus.Experimental
public class LSPFoldingRangeFeature extends AbstractLSPDocumentFeature {

    private FoldingRangeCapabilityRegistry foldingRangeCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isFoldingRangeSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support foldingRange and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support foldingRange and false otherwise.
     */
    public boolean isFoldingRangeSupported(@NotNull PsiFile file) {
        return getFoldingRangeCapabilityRegistry().isFoldingRangeSupported(file);
    }

    public FoldingRangeCapabilityRegistry getFoldingRangeCapabilityRegistry() {
        if (foldingRangeCapabilityRegistry == null) {
            initFoldingRangeCapabilityRegistry();
        }
        return foldingRangeCapabilityRegistry;
    }

    private synchronized void initFoldingRangeCapabilityRegistry() {
        if (foldingRangeCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        foldingRangeCapabilityRegistry = new FoldingRangeCapabilityRegistry(clientFeatures);
        foldingRangeCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (foldingRangeCapabilityRegistry != null) {
            foldingRangeCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    /**
     * Whether or not the specified folding range should be collapsed by default. The default implementation returns
     * true for folding ranges when:
     * <ul>
     *     <li><code>kind</code> is {@link FoldingRangeKind#Imports} and {@link CodeFoldingSettings#COLLAPSE_IMPORTS}
     *         is true
     *     </li>
     *     <li><code>kind</code> is {@link FoldingRangeKind#Comment}, {@link CodeFoldingSettings#COLLAPSE_FILE_HEADER}
     *         is true, and the folding range starts on the file's first line
     *     </li>
     * </ul>
     *
     * @param file         the PSI file
     * @param foldingRange the folding range
     * @return true if the folding range should be collapsed by default; otherwise false
     */
    public boolean isCollapsedByDefault(@NotNull PsiFile file, @NotNull FoldingRange foldingRange) {
        String foldingRangeKind = foldingRange.getKind();
        if (foldingRangeKind != null) {
            CodeFoldingSettings codeFoldingSettings = CodeFoldingSettings.getInstance();

            // Imports
            if (codeFoldingSettings.COLLAPSE_IMPORTS &&
                FoldingRangeKind.Imports.equals(foldingRangeKind)) {
                return true;
            }

            // File header comments
            else if (codeFoldingSettings.COLLAPSE_FILE_HEADER &&
                     FoldingRangeKind.Comment.equals(foldingRangeKind) &&
                     (foldingRange.getStartLine() == 0)) {
                return true;
            }
        }

        return false;
    }
}
