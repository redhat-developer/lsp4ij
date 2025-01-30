/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and inlayHint
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.capabilities;

import com.google.gson.JsonObject;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeature;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import org.eclipse.lsp4j.InlayHintRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Server capability registry for 'textDocument/inlayHint'.
 */
public class InlayHintCapabilityRegistry extends TextDocumentServerCapabilityRegistry<InlayHintRegistrationOptions> {

    private static final @NotNull Predicate<@NotNull ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            hasCapability(sc.getInlayHintProvider());

    private static final @NotNull Predicate<@NotNull ServerCapabilities> RESOLVE_SERVER_CAPABILITIES_PREDICATE = sc -> {
        var inlayHintProvider =sc.getInlayHintProvider();
        if (inlayHintProvider != null && inlayHintProvider.isRight()) {
            return hasCapability(inlayHintProvider.getRight().getResolveProvider());
        }
        return false;
    };

    private static final @Nullable Predicate<@NotNull InlayHintRegistrationOptions> RESOLVE_REGISTRATION_OPTIONS_PREDICATE = o ->
            hasCapability(o.getResolveProvider());

    public InlayHintCapabilityRegistry(@NotNull LSPClientFeatures clientFeatures) {
        super(clientFeatures, EditorFeatureType.DECLARATIVE_INLAY_HINT );
    }

    class ExtendedInlayHintRegistrationOptions extends InlayHintRegistrationOptions implements ExtendedDocumentSelector.DocumentFilersProvider {
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
    protected @Nullable InlayHintRegistrationOptions create(@NotNull JsonObject registerOptions) {
        return JSONUtils.getLsp4jGson()
                .fromJson(registerOptions,
                        ExtendedInlayHintRegistrationOptions.class);
    }

    /**
     * Returns true if the language server can support inlayHint and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support inlayHint and false otherwise.
     */
    public boolean isInlayHintSupported(@NotNull PsiFile file) {
        return super.isSupported(file, SERVER_CAPABILITIES_PREDICATE);
    }

    /**
     * Returns true if the language server can support resolve inlayHint and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support resolve inlayHint and false otherwise.
     */
    public boolean isResolveInlayHintSupported(@NotNull PsiFile file) {
        return super.isSupported(file, RESOLVE_SERVER_CAPABILITIES_PREDICATE, RESOLVE_REGISTRATION_OPTIONS_PREDICATE);
    }

}
