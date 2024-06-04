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

import com.intellij.codeInsight.codeVision.CodeVisionProvider;
import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory;
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderAdapter;
import com.intellij.openapi.project.Project;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory to create 10 dummy code vision provider with ids 'LSPCodelensProvider0', 'LSPCodelensProvider1', etc
 */
public class DummyCodeVisionProviderFactory implements CodeVisionProviderFactory {
    @NotNull
    @Override
    public Sequence<CodeVisionProvider<?>> createProviders(@NotNull Project project) {
        CodeVisionProvider<?>[] s = createDummyProviders(10);
        return SequencesKt.sequenceOf(s);
    }

    private CodeVisionProvider<?>[] createDummyProviders(int size) {
        List<CodeVisionProvider<?>> list = new ArrayList<>(size);
        for (int i = 0; i <=size; i++) {
            list.add(new CodeVisionProviderAdapter(new DummyCodeVisionProvider(i)));
        }
        return list.toArray(new CodeVisionProvider<?>[size]);
    }
}
