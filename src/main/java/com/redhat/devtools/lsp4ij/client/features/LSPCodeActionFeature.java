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
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.capabilities.CodeActionCapabilityRegistry;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP codeAction feature.
 */
@ApiStatus.Experimental
public class LSPCodeActionFeature extends AbstractLSPDocumentFeature {

    private CodeActionCapabilityRegistry codeActionCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isCodeActionSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support codeAction and false otherwise.
     *
     * @param file    the file.
     * @return true if the file associated with a language server can support codeAction and false otherwise.
     */
    public boolean isCodeActionSupported(@NotNull PsiFile file) {
        return getCodeActionCapabilityRegistry().isCodeActionSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support resolve codeAction and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support resolve codeAction and false otherwise.
     */
    public boolean isResolveCodeActionSupported(@NotNull PsiFile file) {
        return getCodeActionCapabilityRegistry().isResolveCodeActionSupported(file);
    }


    /**
     * Returns true if quick fixes are enabled and false otherwise.
     *
     * @return true if quick fixes are enabled and false otherwise.
     */
    public boolean isQuickFixesEnabled(@NotNull PsiFile file) {
        return isEnabled(file);
    }

    /**
     * Returns true if intent action are enabled and false otherwise.
     *
     * @return true if intent action are enabled and false otherwise.
     */
    public boolean isIntentionActionsEnabled(@NotNull PsiFile file) {
        return isEnabled(file);
    }

    /**
     * Returns the IntelliJ intention action text from the given LSP code action and null to ignore the code action.
     *
     * @param codeAction the LSP code action.
     * @return the IntelliJ intention action  text from the given LSP code action and null to ignore the code action.
     */
    @Nullable
    public String getText(@NotNull CodeAction codeAction) {
        return codeAction.getTitle();
    }

    /**
     * Returns the IntelliJ intention action family name from the given LSP code action.
     *
     * @param codeAction the LSP code action.
     * @return the IntelliJ intention action family name from the given LSP code action.
     */
    @NotNull
    public String getFamilyName(@NotNull CodeAction codeAction) {
        String kind = codeAction.getKind();
        if (StringUtils.isNotBlank(kind)) {
            switch (kind) {
                case CodeActionKind.QuickFix:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.quickfix");
                case CodeActionKind.Refactor:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.refactor");
                case CodeActionKind.RefactorExtract:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.refactor.extract");
                case CodeActionKind.RefactorInline:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.refactor.inline");
                case CodeActionKind.RefactorRewrite:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.refactor.rewrite");
                case CodeActionKind.Source:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.source");
                case CodeActionKind.SourceFixAll:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.source.fixAll");
                case CodeActionKind.SourceOrganizeImports:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.source.organizeImports");
            }
        }
        return LanguageServerBundle.message("lsp.intention.code.action.kind.empty");
    }

    /**
     * Returns the IntelliJ intention action text from the given LSP command and null to ignore the command.
     *
     * @param command the LSP command.
     * @return the IntelliJ intention action  text from the given LSP command and null to ignore the command.
     */
    @Nullable
    public String getText(@NotNull Command command) {
        return command.getTitle();
    }

    /**
     * Returns the IntelliJ intention action family name from the given LSP command.
     *
     * @param command the LSP command.
     * @return the IntelliJ intention action family name from the given LSP command.
     */
    @NotNull
    public String getFamilyName(@NotNull Command command) {
        return "LSP Command";
    }

    public CodeActionCapabilityRegistry getCodeActionCapabilityRegistry() {
        if (codeActionCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            codeActionCapabilityRegistry = new CodeActionCapabilityRegistry(clientFeatures);
            codeActionCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return codeActionCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if(codeActionCapabilityRegistry != null) {
            codeActionCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
}
