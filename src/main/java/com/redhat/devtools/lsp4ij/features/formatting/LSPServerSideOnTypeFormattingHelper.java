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

package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.LSPOnTypeFormattingFeature;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Shared utility class for LSP on-type formatting.
 */
final class LSPServerSideOnTypeFormattingHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPServerSideOnTypeFormattingHelper.class);

    private LSPServerSideOnTypeFormattingHelper() {
        // Pure utility class
    }

    /**
     * Applies LSP on-type formatting for the provide parameters if possible/applicable.
     *
     * @param charTyped the type character
     * @param editor    the editor
     * @param file      the PSI file
     * @return true if on-type formatting was applied; otherwise false
     */
    static boolean applyOnTypeFormatting(char charTyped,
                                         @NotNull Editor editor,
                                         @NotNull PsiFile file) {
        OnTypeFormattingInfo onTypeFormattingInfo = getOnTypeFormattingInfo(file);
        if (onTypeFormattingInfo == null) {
            return false;
        }

        DocumentOnTypeFormattingOptions onTypeFormattingOptions = onTypeFormattingInfo.onTypeFormattingOptions();
        LSPOnTypeFormattingSupport onTypeFormattingSupport = onTypeFormattingInfo.onTypeFormattingSupport();

        // Make sure the typed character should trigger on-type formatting for this language
        Set<String> triggerCharacters = new LinkedHashSet<>();
        ContainerUtil.addIfNotNull(triggerCharacters, onTypeFormattingOptions.getFirstTriggerCharacter());
        ContainerUtil.addAllNotNull(triggerCharacters, onTypeFormattingOptions.getMoreTriggerCharacter());
        String charTypedAsString = String.valueOf(charTyped);
        if (!triggerCharacters.contains(charTypedAsString)) {
            return false;
        }

        // If so, issue a request for on-type formatting
        int offset = editor.getCaretModel().getOffset();
        Document document = editor.getDocument();
        Position position = LSPIJUtils.toPosition(offset, document);
        DocumentOnTypeFormattingParams onTypeFormattingParams = new DocumentOnTypeFormattingParams(
                LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile()),
                new FormattingOptions(LSPIJUtils.getTabSize(editor), LSPIJUtils.isInsertSpaces(editor)),
                position,
                charTypedAsString
        );
        CompletableFuture<List<TextEdit>> onTypeFormattingFutures = onTypeFormattingSupport.onTypeFormatting(onTypeFormattingParams);
        try {
            waitUntilDone(onTypeFormattingFutures, file);
        } catch (ProcessCanceledException e) {
            //Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            onTypeFormattingSupport.cancel();
            return false;
        } catch (CancellationException e) {
            onTypeFormattingSupport.cancel();
            return false;
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/onTypeFormatting' request", e);
            return false;
        }

        if (!isDoneNormally(onTypeFormattingFutures)) {
            return false;
        }

        // If on-type formatting resulted in text edits, apply them
        List<TextEdit> textEdits = onTypeFormattingFutures.getNow(Collections.emptyList());
        if (ContainerUtil.isEmpty(textEdits)) {
            return false;
        }

        LSPIJUtils.applyEdits(editor, document, textEdits);
        return true;
    }

    /**
     * Simple record type for the composite return value of {@link #getOnTypeFormattingInfo(PsiFile)}.
     */
    private record OnTypeFormattingInfo(@NotNull DocumentOnTypeFormattingOptions onTypeFormattingOptions,
                                        @NotNull LSPOnTypeFormattingSupport onTypeFormattingSupport) {
    }

    /**
     * Returns the LSP on-type formatting information for the provided file if supported. This includes the on-type
     * formatting options and the on-type formatting support.
     *
     * @param file the PSI file
     * @return the LSP on-type formatting information for the file if supported; otherwise null
     */
    @Nullable
    private static OnTypeFormattingInfo getOnTypeFormattingInfo(@NotNull PsiFile file) {
        // On-type formatting shouldn't trigger a language server to start
        Project project = file.getProject();
        Set<LanguageServerWrapper> startedLanguageServers = LanguageServiceAccessor.getInstance(project).getStartedServers();
        for (LanguageServerWrapper startedLanguageServer : startedLanguageServers) {
            // TODO: Is there a better way to ask if this started language server supports the file?
            if (startedLanguageServer.isConnectedTo(LSPIJUtils.toUri(file))) {
                LSPOnTypeFormattingFeature onTypeFormattingFeature = startedLanguageServer.getClientFeatures().getOnTypeFormattingFeature();
                if (onTypeFormattingFeature.isEnabled(file) && onTypeFormattingFeature.isSupported(file)) {
                    ServerCapabilities serverCapabilities = startedLanguageServer.getServerCapabilities();
                    DocumentOnTypeFormattingOptions onTypeFormattingProvider = serverCapabilities != null ? serverCapabilities.getDocumentOnTypeFormattingProvider() : null;
                    if (onTypeFormattingProvider != null) {
                        LSPOnTypeFormattingSupport onTypeFormattingSupport = LSPFileSupport.getSupport(file).getOnTypeFormattingSupport();
                        return new OnTypeFormattingInfo(onTypeFormattingProvider, onTypeFormattingSupport);
                    }
                }
            }
        }

        return null;
    }
}
