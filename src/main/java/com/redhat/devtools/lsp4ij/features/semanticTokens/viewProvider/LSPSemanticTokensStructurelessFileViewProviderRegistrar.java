/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.psi.FileTypeFileViewProviders;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * We can't register plain text/abstract file type files by language or a single file type statically in plugin.xml, so
 * register them dynamically.
 */
public class LSPSemanticTokensStructurelessFileViewProviderRegistrar implements ProjectActivity {

    @Override
    @Nullable
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // Collect the abstract file types that aren't already associated with our file view provider factory
        List<AbstractFileType> unregisteredAbstractFileTypes = Arrays.stream(FileTypeManager.getInstance().getRegisteredFileTypes())
                .filter(fileType -> fileType instanceof AbstractFileType)
                .filter(fileType -> !(FileTypeFileViewProviders.INSTANCE.forFileType(fileType) instanceof LSPSemanticTokensStructurelessFileViewProviderFactory))
                .map(fileType -> (AbstractFileType) fileType)
                .toList();
        if (!unregisteredAbstractFileTypes.isEmpty()) {
            // Associate them with our file view provider factory
            for (FileType abstractFileType : unregisteredAbstractFileTypes) {
                FileTypeFileViewProviders.INSTANCE.addExplicitExtension(abstractFileType, LSPSemanticTokensStructurelessFileViewProviderFactory.INSTANCE);
            }
        }

        return null;
    }
}
