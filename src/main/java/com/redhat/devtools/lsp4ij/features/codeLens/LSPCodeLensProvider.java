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
package com.redhat.devtools.lsp4ij.features.codeLens;

import com.intellij.codeInsight.codeVision.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.EDT;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.features.LSPCodeLensFeature;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import kotlin.Pair;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        final Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return CodeVisionState.Companion.getREADY_EMPTY();
        }
        if (DumbService.isDumb(project)) {
            return CodeVisionState.NotReady.INSTANCE;
        }
        final VirtualFile file = editor.getVirtualFile();
        if (!acceptsFile(file, project)) {
            return CodeVisionState.Companion.getREADY_EMPTY();
        }
        return computeCodeVisionUnderReadAction(() -> {

            PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
            if (psiFile == null) {
                return CodeVisionState.Companion.getREADY_EMPTY();
            }
            // Here PsiFile is associated with some language servers.

            // Get LSP code lenses from cache or create them
            CompletableFuture<List<CodeLensData>> future = getCodeLenses(psiFile);
            // Wait until the future is finished and stop the wait if there are some ProcessCanceledException.
            waitUntilDone(future, psiFile);
            if (isDoneNormally(future)) {
                // All code lenses from all language server are loaded.
                List<Pair<TextRange, CodeVisionEntry>> result = new ArrayList<>();
                List<CodeLensData> data = future.getNow(null);
                if (data == null) {
                    return CodeVisionState.Companion.getREADY_EMPTY();
                }
                if (!data.isEmpty()) {
                    Map<LanguageServerItem, LSPCodeLensFeature.LSPCodeLensContext> codeLensContexts = new HashMap<>();
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
                            } catch (ProcessCanceledException e) {
                                //Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
                                //TODO delete block when minimum required version is 2024.2
                            } catch (CancellationException e) {
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
                            var codeLensFeature = codeLensData.languageServer().getClientFeatures().getCodeLensFeature();
                            // Code lens is valid, create the proper code vision entry and text range.
                            String text = codeLens.getCommand().getTitle();
                            if (!StringUtils.isEmpty(text)) {
                                // If LSP CodeLens is the  first lens for the current line, we use the same providerId as this LSPCodeLensProvider ('LSPCodeLensProvider)
                                // other we generate a providerId like 'LSPCodeLensProvider0' and as DummyCodeVisionProvider are registered with 'LSPCodeLensProvider0', etc
                                // the code vision entry will be updated correctly
                                // See https://github.com/JetBrains/intellij-community/blob/f18aa7b9d65ab4b03d75a26aaec1e726821dc4d7/platform/lang-impl/src/com/intellij/codeInsight/codeVision/CodeVisionHost.kt#L348
                                var ls = codeLensData.languageServer();
                                var context = codeLensContexts.get(ls);
                                if (context == null) {
                                    context = new LSPCodeLensFeature.LSPCodeLensContext(psiFile, ls);
                                    codeLensContexts.put(ls, context);
                                }
                                String providerId = nbCodeLensForCurrentLine == -1 ? getId() : getId() + nbCodeLensForCurrentLine;
                                CodeVisionEntry entry = codeLensFeature.createCodeVisionEntry(codeLens, providerId, context);
                                if (entry != null) {
                                    TextRange textRange = LSPIJUtils.toTextRange(codeLens.getRange(), editor.getDocument(), null, true);
                                    result.add(new Pair<>(textRange, entry));
                                }
                            }
                        }
                        previous = codeLensData;

                    }
                }
                // Returns the code visions
                return new Ready(result);
            }
            return CodeVisionState.NotReady.INSTANCE;
        }, project);
    }

    private static boolean acceptsFile(@Nullable VirtualFile file, @NotNull Project project) {
        return LanguageServersRegistry.getInstance().isFileSupported(file, project);
    }

    private static CodeVisionState computeCodeVisionUnderReadAction(@NotNull ThrowableComputable<CodeVisionState, Throwable> computable,
                                                                    @NotNull Project project) {
        if (DumbService.isDumb(project)) {
            return CodeVisionState.NotReady.INSTANCE;
        }
        try {
            if (!EDT.isCurrentThreadEdt()) {
                return ReadAction.computeCancellable(computable);
            } else {
                // In tests [computeCodeVision] is executed in sync mode on EDT
                assert (ApplicationManager.getApplication().isUnitTestMode());
                return ReadAction.compute(computable);
            }
        } catch (ReadAction.CannotReadException e) {
            return CodeVisionState.NotReady.INSTANCE;
        } catch (ProcessCanceledException e) {
            //Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            return CodeVisionState.NotReady.INSTANCE;
        } catch (CancellationException e) {
            return CodeVisionState.NotReady.INSTANCE;
        } catch (Throwable e) {
            LOGGER.error("Error while consuming LSP 'textDocument/codeLens' request", e);
            return CodeVisionState.Companion.getREADY_EMPTY();
        }
    }

    static int sortCodeLensByLine(CodeLensData cl1, CodeLensData cl2) {
        return getCodeLensLine(cl2) - getCodeLensLine(cl1);
    }

    private static int getCodeLensLine(CodeLensData codeLensData) {
        return codeLensData.codeLens().getRange().getStart().getLine();
    }

    private static CompletableFuture<List<CodeLensData>> getCodeLenses(@NotNull PsiFile psiFile) {
        LSPCodeLensSupport codeLensSupport = LSPFileSupport.getSupport(psiFile).getCodeLensSupport();
        var params = new CodeLensParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()));
        CompletableFuture<List<CodeLensData>> future;
        try {
            future = codeLensSupport.getCodeLenses(params);
        } catch (CancellationException e) {
            // In some case, the PsiFile is modified and the cancellation support throws a CancellationException
            // get it again...
            future = codeLensSupport.getCodeLenses(params);
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

}