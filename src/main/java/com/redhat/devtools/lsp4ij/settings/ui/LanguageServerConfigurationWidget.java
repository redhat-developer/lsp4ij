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
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Language server configuration widget used to fill the configuration expected by the language server.
 */
public class LanguageServerConfigurationWidget extends JsonTextField {
    public LanguageServerConfigurationWidget(@NotNull Project project) {
        super(project);
    }
}
