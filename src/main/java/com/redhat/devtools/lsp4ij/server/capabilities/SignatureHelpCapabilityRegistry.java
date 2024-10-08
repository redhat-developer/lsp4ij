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
package com.redhat.devtools.lsp4ij.server.capabilities;

import com.google.gson.JsonObject;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.SignatureHelpRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Server capability registry for 'textDocument/signatureHelp'.
 */
public class SignatureHelpCapabilityRegistry extends TextDocumentServerCapabilityRegistry<SignatureHelpRegistrationOptions> {

    private static final @NotNull Predicate<@NotNull ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            sc.getSignatureHelpProvider() != null;

    public SignatureHelpCapabilityRegistry(@NotNull LSPClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    class ExtendedSignatureHelpRegistrationOptions extends SignatureHelpRegistrationOptions implements ExtendedDocumentSelector.DocumentFilersProvider {
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
    protected @Nullable SignatureHelpRegistrationOptions create(@NotNull JsonObject registerOptions) {
        return JSONUtils.getLsp4jGson()
                .fromJson(registerOptions,
                        ExtendedSignatureHelpRegistrationOptions.class);
    }

    /**
     * Returns true if the language server can support signatureHelp and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support signatureHelp and false otherwise.
     */
    public boolean isSignatureHelpSupported(@NotNull PsiFile file) {
        return super.isSupported(file, SERVER_CAPABILITIES_PREDICATE);
    }

    /**
     * Returns true if the given character is defined as "signature trigger" in the server capability of the language server and false otherwise.
     *
     * @param file      the file.
     * @param charTyped the current typed character.
     * @return true if the given character is defined as "signature trigger" in the server capability of the language server and false otherwise.
     */
    public boolean isSignatureTriggerCharactersSupported(@NotNull PsiFile file,
                                                          String charTyped) {
        return super.isSupported(file,
                sc -> isSignatureTriggerCharactersSupported(sc, charTyped),
                o -> isMatchTriggerCharacters(o.getTriggerCharacters(), charTyped));
    }

    private static boolean isSignatureTriggerCharactersSupported(@Nullable ServerCapabilities serverCapabilities,
                                                                  String charTyped) {
        var triggerCharacters = serverCapabilities.getSignatureHelpProvider() != null ? serverCapabilities.getSignatureHelpProvider().getTriggerCharacters() : null;
        return isMatchTriggerCharacters(triggerCharacters, charTyped);
    }

    private static boolean isMatchTriggerCharacters(@Nullable List<String> characters,
                                                    @NotNull String charTyped) {
        if (characters == null || characters.isEmpty()) {
            return false;
        }
        return characters.contains(charTyped);
    }

}
