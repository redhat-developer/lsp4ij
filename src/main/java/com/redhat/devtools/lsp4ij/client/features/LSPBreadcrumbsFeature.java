/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Breadcrumbs/sticky lines feature. This does not correspond directly to an LSP feature but is built upon
 * {@link LSPDocumentSymbolFeature}.
 */
@ApiStatus.Experimental
public class LSPBreadcrumbsFeature extends AbstractLSPDocumentFeature {

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
    }

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        // Requires the document symbol feature
        return getClientFeatures().getDocumentSymbolFeature().isSupported(file);
    }
}
