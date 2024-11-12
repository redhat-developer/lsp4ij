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

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind;
import com.intellij.codeInsight.codeVision.CodeVisionProvider;
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Dummy code vision provider which does nothing.
 *
 * <p>
 * As IJ CodeVision cannot support showing several CodeVisionEntry for a given line, we create 10
 * DummyCodeVisionProvider registered with 'LSPCodelensProvider0', 'LSPCodelensProvider1', etc... providerId since
 * {@link LSPCodeLensProvider} creates CodeVision entries with providerId 'LSPCodelensProvider0', 'LSPCodelensProvider1'
 * when there are several LSP Codelens in the same line.
 * Those dummy code vision provider are just used to register 'LSPCodelensProvider0', 'LSPCodelensProvider1' in
 * IJ CodeVision support and provides the capability to update (remove if necessary) correctly
 * previous CodeVisions created by providerId 'LSPCodelensProvider0', 'LSPCodelensProvider1', etc. which is done
 * here <a href="https://github.com/JetBrains/intellij-community/blob/f18aa7b9d65ab4b03d75a26aaec1e726821dc4d7/platform/lang-impl/src/com/intellij/codeInsight/codeVision/CodeVisionHost.kt#L348">...</a>
 * </p>
 */
public class DummyCodeVisionProvider implements CodeVisionProvider {

    private final String id;
    private final List<CodeVisionRelativeOrdering> relativeOrderings;

    public DummyCodeVisionProvider(int index) {
        id =DummyCodeVisionProviderFactory.generateProviderId(index);
        // Keep the proper order of LSP CodeLens
        relativeOrderings = List.of(new CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter(getPreviousProviderId(index)));
    }

    private static String getPreviousProviderId(int index) {
        return index == 0 ? LSPCodeLensProvider.LSP_CODE_LENS_PROVIDER_ID : LSPCodeLensProvider.LSP_CODE_LENS_PROVIDER_ID + (index - 1);
    }

    @NotNull
    @Override
    public CodeVisionAnchorKind getDefaultAnchor() {
        return CodeVisionAnchorKind.Top;
    }

    @NotNull
    @Override
    public String getId() {
        return id;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
        return "";
    }

    @NotNull
    @Override
    public List<CodeVisionRelativeOrdering> getRelativeOrderings() {
        return relativeOrderings;
    }

    @Override
    public Object precomputeOnUiThread(@NotNull Editor editor) {
        return null;
    }

    @NotNull
    @Override
    public String getGroupId() {
        // Group dummy code vision provider to have just one setting 'LSP CodeLens' in the standard 'Inlay Hints' preferences.
        return LSPCodeLensProvider.LSP_CODE_LENS_GROUP_ID;
    }
}
