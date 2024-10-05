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

import com.intellij.codeInsight.codeVision.CodeVisionEntry;
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry;
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.commands.CommandExecutor;
import com.redhat.devtools.lsp4ij.commands.LSPCommandContext;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.capabilities.CodeLensCapabilityRegistry;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * LSP codeLens feature.
 */
@ApiStatus.Experimental
public class LSPCodeLensFeature extends AbstractLSPDocumentFeature {

    private CodeLensCapabilityRegistry codeLensCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isCodeLensSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support codelens and false otherwise.
     *
     * @param file    the Psi file.
     * @return true if the file associated with a language server can support codelens and false otherwise.
     */
    public boolean isCodeLensSupported(@NotNull PsiFile file) {
        return getCodeLensCapabilityRegistry().isCodeLensSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support resolve codelens and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support resolve codelens and false otherwise.
     */
    public boolean isResolveCodeLensSupported(@NotNull PsiFile file) {
        return getCodeLensCapabilityRegistry().isResolveCodeLensSupported(file);
    }

    public static class LSPCodeLensContext {
        private final @NotNull PsiFile psiFile;
        private final @NotNull LanguageServerItem languageServer;
        private Boolean resolveCodeLensSupported;

        public LSPCodeLensContext(@NotNull PsiFile psiFile, @NotNull LanguageServerItem languageServer) {
            this.psiFile = psiFile;
            this.languageServer = languageServer;
        }

        public @NotNull PsiFile getPsiFile() {
            return psiFile;
        }

        @NotNull
        public LanguageServerItem getLanguageServer() {
            return languageServer;
        }

        public boolean isResolveCodeLensSupported() {
            if (resolveCodeLensSupported == null) {
                resolveCodeLensSupported = languageServer.getClientFeatures().getCodeLensFeature().isResolveCodeLensSupported(getPsiFile());
            }
            return resolveCodeLensSupported;
        }

    }
    /**
     * Create an IntelliJ {@link CodeVisionEntry} from the given LSP CodeLens and null otherwise (to ignore the LSP CodeLens).
     *
     * @param codeLens       the LSP codeLens.
     * @param providerId     the code vision provider Id.
     * @param codeLensContext        the LSP CodeLens context.
     * @return an IntelliJ {@link CodeVisionEntry} from the given LSP CodeLens and null otherwise (to ignore the LSP CodeLens).
     */
    @Nullable
    public CodeVisionEntry createCodeVisionEntry(@NotNull CodeLens codeLens,
                                                 @NotNull String providerId,
                                                 @NotNull LSPCodeLensContext codeLensContext) {
        String text = getText(codeLens);
        if (text == null) {
            // Ignore the code vision entry
            return null;
        }
        Command command = codeLens.getCommand();
        String commandId = command.getCommand();
        if (StringUtils.isEmpty(commandId)) {
            // Create a simple text code vision.
            return new TextCodeVisionEntry(text, providerId, null, text, text, Collections.emptyList());
        }
        // Code lens defines a command, create a clickable code vsion to execute the command.
        return new ClickableTextCodeVisionEntry(text, providerId, (e, editor) -> {
            LSPCommandContext context = new LSPCommandContext(command, codeLensContext.getPsiFile(), LSPCommandContext.ExecutedBy.CODE_LENS, editor, codeLensContext.getLanguageServer())
                    .setInputEvent(e);
            if (codeLensContext.isResolveCodeLensSupported()) {
                codeLensContext.getLanguageServer()
                        .getTextDocumentService()
                        .resolveCodeLens(codeLens)
                        .thenAcceptAsync(resolvedCodeLens -> {
                                    if (resolvedCodeLens != null) {
                                        UIUtil.invokeLaterIfNeeded(() ->
                                                CommandExecutor.executeCommand(context));
                                    }
                                }
                        );
            } else {
                CommandExecutor.executeCommand(context);
            }
            return null;
        }, null, text, text, Collections.emptyList());
    }

    /**
     * Returns the code vision entry text from the LSP CodeLens and null otherwise (to ignore the LSP CodeLens).
     *
     * @param codeLens the LSP CodeLens
     * @return the code vision entry text from the LSP CodeLens and null otherwise (to ignore the LSP CodeLens).
     */
    @Nullable
    public String getText(@NotNull CodeLens codeLens) {
        Command command = codeLens.getCommand();
        if (command == null || command.getTitle().isEmpty()) {
            return null;
        }
        return command.getTitle();
    }

    public CodeLensCapabilityRegistry getCodeLensCapabilityRegistry() {
        if (codeLensCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            codeLensCapabilityRegistry = new CodeLensCapabilityRegistry(clientFeatures);
            codeLensCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return codeLensCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if(codeLensCapabilityRegistry != null) {
            codeLensCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
}
