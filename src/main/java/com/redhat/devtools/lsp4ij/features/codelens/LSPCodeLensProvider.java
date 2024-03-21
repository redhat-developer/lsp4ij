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
package com.redhat.devtools.lsp4ij.features.codelens;

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
    public static final String LSP_CODE_LENS_PROVIDER_ID = "LSPCodeLensProvider";
    public static final String LSP_CODE_LENS_GROUP_ID = "LSPCodeLens";
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

    @NotNull
    @Override
    public String getGroupId() {
        return LSP_CODE_LENS_GROUP_ID;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
        return "LSP";
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
            waitUntilDone(future, psiFile);
        } catch (CancellationException | ProcessCanceledException e) {
            return CodeVisionState.NotReady.INSTANCE;
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/codeLens' request", e);
            return CodeVisionState.NotReady.INSTANCE;
        }
        if (isDoneNormally(future)) {
            // All code lenses from all language server are loaded.
            List<Pair<TextRange, CodeVisionEntry>> result = new ArrayList<>();
            List<CodeLensData> data = future.getNow(null);
            if (data == null) {
                return CodeVisionState.Companion.getREADY_EMPTY();
            }
            if (!data.isEmpty()) {
                // At this step codelens are sorted by line number
                // Create IJ CodeVision from LSP CodeLens
                // As CodeVision cannot support showing several CodeVision entries for the same line, we create
                // CodeVision entry with different providerId ('LSPCodelensProvider', 'LSPCodelensProvider0', 'LSPCodelensProvider1', etc)
                CodeLensData previous = null;
                int nbCodeLensForCurrentLine = -1;
                for (var codeLensData : data) {
                    if (previous != null) {
                        if (getCodeLensLine(previous) == getCodeLensLine(codeLensData)) {
                            // The current LSP Codelens must be shown in the same line as previous LSP Codelens,
                            // increment nbCodeLensForCurrentLine to generate the proper providerId LSPCodelensProvider0', 'LSPCodelensProvider1', etc
                            nbCodeLensForCurrentLine++;
                        } else {
                            nbCodeLensForCurrentLine = -1;
                        }
                    }
                    CodeLens codeLens = codeLensData.codeLens();
                    var resolvedCodeLensFuture = codeLensData.resolvedCodeLensFuture();
                    if (resolvedCodeLensFuture != null && !resolvedCodeLensFuture.isDone()) {
                        // The resolve code lens future is not finished, wait for...
                        try {
                            waitUntilDone(resolvedCodeLensFuture, psiFile);
                        } catch (CancellationException | ProcessCanceledException e) {
                            // Do nothing
                        } catch (ExecutionException e) {
                            LOGGER.error("Error while consuming LSP 'textDocument/resolveCodeLens' request", e);
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
                            CodeVisionEntry entry = createCodeVisionEntry(codeLens, nbCodeLensForCurrentLine, codeLensData.languageServer(), project);
                            result.add(new Pair<>(textRange, entry));
                        }
                    }
                    previous = codeLensData;

                }
            }
            // Returns the code visions
            return ReadAction.compute(() -> new Ready(result));
        }
        return CodeVisionState.NotReady.INSTANCE;
    }

    static int sortCodeLensByLine(CodeLensData cl1, CodeLensData cl2) {
        return getCodeLensLine(cl2) - getCodeLensLine(cl1);
    }

    private static int getCodeLensLine(CodeLensData codeLensData) {
        return codeLensData.codeLens().getRange().getStart().getLine();
    }

    private static boolean acceptsFile(@Nullable PsiFile psiFile) {
        return LanguageServersRegistry.getInstance().isFileSupported(psiFile);
    }

    @NotNull
    private TextCodeVisionEntry createCodeVisionEntry(@NotNull CodeLens codeLens, int nbCodeLensForCurrentLine, @NotNull LanguageServerItem languageServer, @NotNull Project project) {
        Command command = codeLens.getCommand();
        String text = getCodeLensContent(codeLens);
        String commandId = command.getCommand();
        // If LSP CodeLens is the  first lens for the current line, we use the same providerId as this LSPCodeLensProvider ('LSPCodeLensProvider)
        // other we generate a providerId like 'LSPCodeLensProvider0' and as DummyCodeVisionProvider are registered with 'LSPCodeLensProvider0', etc
        // the code vision entry will be updated correctly
        // See https://github.com/JetBrains/intellij-community/blob/f18aa7b9d65ab4b03d75a26aaec1e726821dc4d7/platform/lang-impl/src/com/intellij/codeInsight/codeVision/CodeVisionHost.kt#L348
        String providerId = nbCodeLensForCurrentLine == -1 ? getId() : getId() + nbCodeLensForCurrentLine;
        if (StringUtils.isEmpty(commandId)) {
            // Create a simple text code vision.
            return new TextCodeVisionEntry(text, providerId, null, text, text, Collections.emptyList());
        }
        // Code lens defines a command, create a clickable code vsion to execute the command.
        return new ClickableTextCodeVisionEntry(text, providerId, (e, editor) -> {
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
        CompletableFuture<List<CodeLensData>> future;
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
        return List.of(new CodeVisionRelativeOrdering.CodeVisionRelativeOrderingBefore("lsp"));
    }

    static String getCodeLensContent(CodeLens codeLens) {
        Command command = codeLens.getCommand();
        if (command == null || command.getTitle().isEmpty()) {
            return null;
        }
        return command.getTitle();
    }
}