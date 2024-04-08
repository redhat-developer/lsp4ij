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
package com.redhat.devtools.lsp4ij.features.codeLens;

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * LSP Codelens settings provider.
 */
public class LSPCodeLensSettingsProvider implements CodeVisionGroupSettingProvider {

    @NotNull
    @Override
    public String getGroupId() {
        return LSPCodeLensProvider.LSP_CODE_LENS_GROUP_ID;
    }


    @Nls
    @NotNull
    @Override
    public String getGroupName() {
        return LanguageServerBundle.message("codeLens.group.name");
    }

    @Nls
    @NotNull
    @Override
    public String getDescription() {
        return LanguageServerBundle.message("codeLens.group.description");
    }
}
