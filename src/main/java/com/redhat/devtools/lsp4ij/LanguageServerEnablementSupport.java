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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * API to implement with {@link LanguageServerFactory} if enable state must be managed
 * by custom settings of the adaptor plugin.
 */
public interface LanguageServerEnablementSupport {

    /**
     * Returns true if the language server is enabled for the given project and false otherwise.
     *
     * @param project the project.
     * @return true if the language server is enabled for the given project and false otherwise.
     */
    boolean isEnabled(@NotNull Project project);

    /**
     * Set enabled state of the language server for the given project.
     *
     * @param enabled the enabled state.
     * @param project the project.
     */
    void setEnabled(boolean enabled, @NotNull Project project);

}
