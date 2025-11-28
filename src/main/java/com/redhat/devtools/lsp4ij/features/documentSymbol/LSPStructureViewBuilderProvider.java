/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentSymbol;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewBuilderProvider;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Structure View provider for all {@link AbstractFileType} instances that are associated with
 * an active Language Server.
 *
 * <p>IntelliJ Community Edition and other versions may define certain file types as
 * {@link AbstractFileType} (for example, CSS in IntelliJ CE). These file types do not
 * provide a native Structure View because they lack a fully featured PSI or
 * StructureView implementation.</p>
 *
 * <p>This provider enables a Structure View for such files if a Language Server
 * supports them. It does so by leveraging the PSI language detected for the file
 * and obtaining the corresponding {@link PsiStructureViewFactory}.</p>
 *
 * <p>Workflow:</p>
 * <ol>
 *   <li>Check if the file type is an {@link AbstractFileType}.
 *       Only these file types are considered, since PSI-backed languages already have
 *       a Structure View.</li>
 *   <li>Verify if the file is supported by a registered Language Server.
 *       If not, return {@code null} to disable the LSP Structure View.</li>
 *   <li>Retrieve the PSI for the file. IntelliJ assigns the file a language,
 *       for example {@code CssLanguage} for CSS files.</li>
 *   <li>Obtain the {@link PsiStructureViewFactory} for the PSI language.
 *       In the context of LSP4IJ, this factory is generally an instance of
 *       {@link LSPDocumentSymbolStructureViewFactory} provided by the plugin,
 *       which uses LSP document symbols to build the Structure View.</li>
 *   <li>Return the {@link StructureViewBuilder} from the factory,
 *       enabling a Structure View (outline) for this AbstractFileType.</li>
 * </ol>
 *
 * <p>This mechanism is generic and works for any AbstractFileType that has an LSP
 * providing document symbols. CSS is used here as an example in IntelliJ CE, but
 * other AbstractFileType instances (e.g., log files, custom text-based file types)
 * are handled the same way.</p>
 */
public class LSPStructureViewBuilderProvider implements StructureViewBuilderProvider {

    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(
            @NotNull FileType fileType,
            @NotNull VirtualFile file,
            @NotNull Project project
    ) {
        // 1. Only handle AbstractFileType (e.g., CSS in IntelliJ CE)
        if (!(fileType instanceof AbstractFileType)) {
            return null;
        }

        // 2. Activate Structure View only if a Language Server supports this file
        if (!LanguageServersRegistry.getInstance().isFileSupported(file, project)) {
            return null;
        }

        // 3. Retrieve the PSI for the file. Even AbstractFileType files may have a PSI language
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null) {
            return null;
        }

        // 4. Obtain the StructureView factory for the PSI language.
        //    In the LSP4IJ context, this factory is generally an instance of
        //    LSPDocumentSymbolStructureViewFactory, which builds the StructureView
        //    from LSP document symbols.
        PsiStructureViewFactory factory =
                LanguageStructureViewBuilder.getInstance().forLanguage(psiFile.getLanguage());
        if (factory == null) {
            return null;
        }

        // 5. Return the StructureViewBuilder provided by the factory
        return factory.getStructureViewBuilder(psiFile);
    }
}
