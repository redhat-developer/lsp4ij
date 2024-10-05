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

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract class for any LSP document feature.
 */
@ApiStatus.Experimental
public abstract class AbstractLSPDocumentFeature extends AbstractLSPFeature {

    /**
     * Returns true if the LSP feature is enabled for the given file and false otherwise.
     *
     * <p>
     *     This enable state is called before starting the language server which matches the file.
     * </p>
     *
     * @param file the file.
     *
     * @return true if the LSP feature is enabled for the given file and false otherwise.
     */
    public boolean isEnabled(@NotNull PsiFile file) {
        return true;
    }

    /**
     * Returns true if the LSP feature is supported for the given file and false otherwise.
     *
     * <p>
     *     This supported state is called after starting the language server which matches the file and user the LSP server capabilities.
     * </p>
     *
     * @param file the file.
     * @return true if the LSP feature is supported for the given file and false otherwise.
     */
    public abstract boolean isSupported(@NotNull PsiFile file);

}
