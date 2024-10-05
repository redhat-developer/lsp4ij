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
package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * LSP formatting which can support only textDocument/formatting.
 */
public class LSPFormattingOnlyService extends AbstractLSPFormattingService {

    private static final Set<Feature> FEATURES = Collections.emptySet();

    @Override
    public @NotNull Set<Feature> getFeatures() {
        return FEATURES;
    }

    @Override
    protected boolean canSupportFormatting(LSPFormattingFeature formattingFeature, PsiFile file) {
        return formattingFeature.isFormattingSupported(file) &&
                !formattingFeature.isRangeFormattingSupported(file);
    }

}
