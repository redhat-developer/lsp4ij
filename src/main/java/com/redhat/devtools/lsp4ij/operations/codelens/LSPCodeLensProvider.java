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
package com.redhat.devtools.lsp4ij.operations.codelens;

import com.intellij.codeInsight.codeVision.*;
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry;
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.commands.CommandExecutor;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import kotlin.Pair;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.intellij.codeInsight.codeVision.CodeVisionState.Ready;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP textDocument/codeLens support.
 */
public class LSPCodeLensProvider implements CodeVisionProvider<Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPCodeLensProvider.class);

    private static final String LSP_CODE_LENS_PROVIDER_ID = "LSPCodeLensProvider";

    @NotNull
    @Override
    public CodeVisionAnchorKind getDefaultAnchor() {
        return CodeVisionAnchorKind.Top;
    }

    @NotNull
    @Override
    public String getId() {
        return LSP_CODE_LENS_PROVIDER_ID;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
        return "LSP CodeLens";
    }

    @NotNull
    @Override
    public CodeVisionState computeCodeVision(@NotNull Editor editor, Void uiData) {
        Project project = editor.getProject();
        if (project == null) {
            return CodeVisionState.Companion.getREADY_EMPTY();
        }
        if (DumbService.isDumb(project)) {
            return CodeVisionState.NotReady.INSTANCE;
        }
        VirtualFile file = LSPIJUtils.getFile(editor.getDocument());
        if (file == null) {
            return CodeVisionState.Companion.getREADY_EMPTY();
        }
        PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
        if (!acceptsFile(psiFile)) {
            return CodeVisionState.Companion.getREADY_EMPTY();
        }
        // Here PsiFile is associated with some language servers.

        // Get LSP code lenses from cache or create them
        CompletableFuture<List<CodeLensData>> future = getCodeLenses(psiFile);
        try {
            // Wait upon the future is finished and stop the wait if there are some ProcessCanceledException.
            waitUntilDone(future);
        } catch (CancellationException | ProcessCanceledException e) {
            return CodeVisionState.NotReady.INSTANCE;
        } catch (ExecutionException e) {
            LOGGER.error("Error while collecting LSP CodeLens", e);
        }
        if (isDoneNormally(future)) {
            // All code lenses from all language server are loaded.
            List<Pair<TextRange, CodeVisionEntry>> result = new ArrayList<>();
            List<CodeLensData> data = future.getNow(Collections.emptyList());
            for (var codeLensData : data) {
                CodeLens codeLens = codeLensData.codeLens();
                var resolvedCodeLensFuture = codeLensData.resolvedCodeLensFuture();
                if (resolvedCodeLensFuture != null && !resolvedCodeLensFuture.isDone()) {
                    // The resolve code lens future is not finished, wait for...
                    try {
                        waitUntilDone(resolvedCodeLensFuture);
                    } catch (CancellationException | ProcessCanceledException e) {
                        // Do nothing
                    } catch (ExecutionException e) {
                        LOGGER.error("Error while resolving LSP CodeLens", e);
                    }
                }
                if (isDoneNormally(resolvedCodeLensFuture)) {
                    // The resolve code lens future is finished, use the resolved code lens
                    CodeLens resolved = resolvedCodeLensFuture.getNow(null);
                    if (resolved != null) {
                        codeLens = resolved;
                    }
                }
                if (codeLens.getCommand() != null) {
                    // Code lens is valid, create the proper code vision entry and text range.
                    String text = codeLens.getCommand().getTitle();
                    if (!StringUtils.isEmpty(text)) {
                        TextRange textRange = LSPIJUtils.toTextRange(codeLens.getRange(), editor.getDocument(), true);
                        CodeVisionEntry entry = createCodeVisionEntry(codeLens, codeLensData.languageServer(), project);
                        result.add(new Pair(textRange, entry));
                    }
                }
            }
            // Returns the code visions
            return ReadAction.compute(() -> new Ready(result));
        }
        return CodeVisionState.NotReady.INSTANCE;
    }

    private static boolean acceptsFile(@Nullable PsiFile psiFile) {
        return LanguageServersRegistry.getInstance().isFileSupported(psiFile);
    }

    @NotNull
    private TextCodeVisionEntry createCodeVisionEntry(@NotNull CodeLens codeLens, @NotNull LanguageServerItem languageServer, @NotNull Project project) {
        Command command = codeLens.getCommand();
        String text = getCodeLensContent(codeLens);
        String commandId = command.getCommand();
        if (StringUtils.isEmpty(commandId)) {
            // Create a simple text code vision.
            return new TextCodeVisionEntry(text, this.getId(), null, text, text, Collections.emptyList());
        }
        // Code lens defines a command, create a clickable code vsion to execute the command.
        return new ClickableTextCodeVisionEntry(text, getId(), (e, editor) -> {
            if (languageServer.isResolveCodeLensSupported()) {
                languageServer.getTextDocumentService()
                        .resolveCodeLens(codeLens)
                        .thenAcceptAsync(resolvedCodeLens -> {
                                    if (resolvedCodeLens != null) {
                                        UIUtil.invokeLaterIfNeeded(() ->
                                                CommandExecutor.executeCommandClientSide(command, null, editor, project, null, e));
                                    }
                                }
                        );
            } else {
                CommandExecutor.executeCommandClientSide(command, null, editor, project, null, e);
            }
            return null;
        }, null, text, text, Collections.emptyList());
    }

    private static CompletableFuture<List<CodeLensData>> getCodeLenses(@NotNull PsiFile psiFile) {
        LSPCodeLensSupport codeLensSupport = LSPFileSupport.getSupport(psiFile).getCodeLensSupport();
        CompletableFuture<List<CodeLensData>> future = null;
        try {
            future = codeLensSupport.getCodeLenses();
        } catch (CancellationException e) {
            // In some case, the PsiFile is modified and the cancellation support throws a CancellationException
            // get it again...
            future = codeLensSupport.getCodeLenses();
        }
        return future;
    }

    @Override
    public Void precomputeOnUiThread(@NotNull Editor editor) {
        return null;
    }

    @NotNull
    @Override
    public List<CodeVisionRelativeOrdering> getRelativeOrderings() {
        return Arrays.asList(new CodeVisionRelativeOrdering.CodeVisionRelativeOrderingBefore("lsp"));
    }

    static String getCodeLensContent(CodeLens codeLens) {
        Command command = codeLens.getCommand();
        if (command == null || command.getTitle().isEmpty()) {
            return null;
        }
        return command.getTitle();
    }
}