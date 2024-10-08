/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and rename
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.capabilities;

import com.google.gson.JsonObject;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Server capability registry for 'textDocument/rename'.
 */
public class RenameCapabilityRegistry extends TextDocumentServerCapabilityRegistry<RenameOptions> {

    private static final @NotNull Predicate<@NotNull ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            hasCapability(sc.getRenameProvider());

    private static final @NotNull Predicate<@NotNull ServerCapabilities> PREPARE_RENAME_SERVER_CAPABILITIES_PREDICATE = sc -> {
        Either<Boolean, RenameOptions> renameProvider =sc.getRenameProvider();
        if (renameProvider != null && renameProvider.isRight()) {
            return hasCapability(renameProvider.getRight().getPrepareProvider());
        }
        return false;
    };

    private static final @Nullable Predicate<@NotNull RenameOptions> PREPARE_RENAME_REGISTRATION_OPTIONS_PREDICATE = o ->
            hasCapability(o.getPrepareProvider());

    public RenameCapabilityRegistry(@NotNull LSPClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    class ExtendedRenameOptions extends RenameOptions implements ExtendedDocumentSelector.DocumentFilersProvider {
        private transient ExtendedDocumentSelector documentSelector;

        @Override
        public List<ExtendedDocumentSelector.ExtendedDocumentFilter> getFilters() {
            if (documentSelector == null) {
                documentSelector = new ExtendedDocumentSelector(super.getDocumentSelector());
            }
            return documentSelector.getFilters();
        }
    }

    @Override
    protected @Nullable RenameOptions create(@NotNull JsonObject registerOptions) {
        return JSONUtils.getLsp4jGson()
                .fromJson(registerOptions,
                        ExtendedRenameOptions.class);
    }

    /**
     * Returns true if the language server can support rename and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support rename and false otherwise.
     */
    public boolean isRenameSupported(@NotNull PsiFile file) {
        return super.isSupported(file, SERVER_CAPABILITIES_PREDICATE);
    }

    /**
     * Returns true if the language server can support prepareRename and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support prepareRename and false otherwise.
     */
    public boolean isPrepareRenameSupported(@NotNull PsiFile file) {
        return super.isSupported(file, PREPARE_RENAME_SERVER_CAPABILITIES_PREDICATE, PREPARE_RENAME_REGISTRATION_OPTIONS_PREDICATE);
    }
}
