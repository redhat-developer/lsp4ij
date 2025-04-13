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
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.features.LSPCodeLensFeature;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.internal.PsiFileChangedException;
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

        final VirtualFile file = editor.getVirtualFile();
        ExecuteLSPFeatureStatus acceptsFile = ProjectIndexingManager.canExecuteLSPFeature(file, project);
        if (acceptsFile == ExecuteLSPFeatureStatus.NOT) {
            return CodeVisionState.Companion.getREADY_EMPTY();
        }
        if (acceptsFile == ExecuteLSPFeatureStatus.AFTER_INDEXING) {
            return CodeVisionState.NotReady.INSTANCE;
        }

        return computeCodeVisionUnderReadAction(() -> {

            PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
            if (psiFile == null) {
                return CodeVisionState.Companion.getREADY_EMPTY();
            }

            LSPCodeLensSupport codeLensSupport = LSPFileSupport.getSupport(psiFile).getCodeLensSupport();
            LSPCodeLensEditorFactoryListener.setCodelensSupport(editor, codeLensSupport);
            CompletableFuture<CodeLensDataResult> future = fetchCodeLenses(codeLensSupport);
            try {
                waitUntilDone(future, psiFile);
            } catch (PsiFileChangedException e) {
                // The file content has changed, cancel the LSP textDocument/codeLens requests.
                codeLensSupport.cancel();
                throw e;
            }
            if (isDoneNormally(future)) {
                List<Pair<TextRange, CodeVisionEntry>> result = new ArrayList<>();
                CodeLensDataResult data = future.getNow(null);
                if (data == null) {
                    return CodeVisionState.Companion.getREADY_EMPTY();
                }
                var codelensData = data.getCodeLensData();
                if (!codelensData.isEmpty()) {
                    List<CodeLensData> applicableCodeLens = codelensData;
                    if (data.hasToResolve()) {
                        // There is some codelens to resolve
                        applicableCodeLens = new ArrayList<>();
                        var resolveContext = LSPCodeLensEditorFactoryListener.getCodeLensResolveContext(editor);
                        // Get the codelens to resolve which are inside the view port range (visible lines)
                        @Nullable CompletableFuture<Void> visibleCodeLensToResolve =
                                collectApplicableCodeLens(codelensData,
                                        applicableCodeLens,
                                        resolveContext.getFirstViewportLine(),
                                        resolveContext.getLastViewportLine());
                        if (visibleCodeLensToResolve != null) {
                            // Resolve all visible code lens
                            try {
                                waitUntilDone(visibleCodeLensToResolve, psiFile);
                            } catch (PsiFileChangedException e) {
                                visibleCodeLensToResolve.cancel(true);
                                return CodeVisionState.NotReady.INSTANCE;
                            }
                        }
                    }

                    Map<LanguageServerItem, LSPCodeLensFeature.LSPCodeLensContext> codeLensContexts = new HashMap<>();
                    CodeLensData previous = null;
                    int nbCodeLensForCurrentLine = -1;

                    for (var codeLensData : applicableCodeLens) {
                        if (previous != null) {
                            if (getCodeLensLine(previous) == getCodeLensLine(codeLensData)) {
                                nbCodeLensForCurrentLine++;
                            } else {
                                nbCodeLensForCurrentLine = -1;
                            }
                        }
                        CodeLens codeLens = codeLensData.getCodeLens();

                        if (codeLens.getCommand() != null) {
                            var ls = codeLensData.getLanguageServer();
                            var codeLensFeature = ls.getClientFeatures().getCodeLensFeature();
                            String text = codeLens.getCommand().getTitle();
                            if (!StringUtils.isEmpty(text)) {
                                var context = codeLensContexts.get(ls);
                                if (context == null) {
                                    context = new LSPCodeLensFeature.LSPCodeLensContext(psiFile, ls);
                                    codeLensContexts.put(ls, context);
                                }
                                String providerId = nbCodeLensForCurrentLine == -1 ? getId() : getId() + nbCodeLensForCurrentLine;
                                CodeVisionEntry entry = codeLensFeature.createCodeVisionEntry(codeLens, providerId, context);
                                if (entry != null) {
                                    TextRange textRange = LSPIJUtils.toTextRange(codeLens.getRange(), editor.getDocument(), null, true);
                                    if (textRange != null) {
                                        result.add(new Pair<>(textRange, entry));
                                    }
                                }
                            }
                        }
                        previous = codeLensData;
                    }
                }
                return new Ready(result);
            }
            return CodeVisionState.NotReady.INSTANCE;
        }, project);
    }

    /**
     * Collects the applicable code lens for the given editor and data.
     * This method processes code lens data, filtering them based on whether they need to be resolved and if they are
     * within the currently visible lines in the editor.
     *
     * @param data               A list of code lens data to be evaluated.
     * @param applicableCodeLens A list to collect the code lenses that are applicable for the editor's viewport.
     * @param firstVisibleLine   The first visible line.
     * @param lastVisibleLine    The first visible line.
     * @return A CompletableFuture that completes when all applicable code lens are resolved.
     */
    @Nullable
    private static CompletableFuture<Void> collectApplicableCodeLens(@NotNull List<CodeLensData> data,
                                                                     @NotNull List<CodeLensData> applicableCodeLens,
                                                                     int firstVisibleLine,
                                                                     int lastVisibleLine) {

        if (data.isEmpty()) {
            return null;
        }

        // Collect the visible codelens to resolve
        List<CodeLensData> visibleCodeLensToResolve = getVisibleCodeLensToResolve(data,
                applicableCodeLens,
                firstVisibleLine,
                lastVisibleLine);

        // If there are any code lenses to resolve within the visible viewport, resolve them asynchronously.
        if (visibleCodeLensToResolve != null) {
            // Stream over the visible code lenses and resolve them, collecting all futures.
            var resolveFutures = visibleCodeLensToResolve
                    .stream()
                    .map(CodeLensData::resolveCodeLens) // Resolve each code lens.
                    .toList(); // Collect the futures in a list.

            // Return a CompletableFuture that completes when all resolve futures are done.
            return CompletableFutures.allOf(resolveFutures.toArray(new CompletableFuture[0]));
        }

        // If no code lenses need to be resolved, return null.
        return null;
    }

    private static @Nullable List<CodeLensData> getVisibleCodeLensToResolve(@NotNull List<CodeLensData> data,
                                                                            @NotNull List<CodeLensData> applicableCodeLens,
                                                                            int firstVisibleLine,
                                                                            int lastVisibleLine) {
        // Variable to hold the code lens data that needs to be resolved and is within the visible lines.
        List<CodeLensData> visibleCodeLensToResolve = null;

        // Iterate over the provided code lens data to filter and categorize it.
        for (var codeLensData : data) {
            // Check if the code lens needs resolution.
            if (codeLensData.isToResolve()) {
                int codeLensLine = getCodeLensLine(codeLensData); // Get the line number where the code lens should be shown.

                // Check if the code lens is within the visible viewport range.
                if (codeLensLine >= firstVisibleLine && codeLensLine <= lastVisibleLine) {
                    // If the list for visible code lenses to resolve is not yet initialized, initialize it.
                    if (visibleCodeLensToResolve == null) {
                        visibleCodeLensToResolve = new ArrayList<>();
                    }
                    // Add the code lens data to the list of visible code lenses that need resolution.
                    visibleCodeLensToResolve.add(codeLensData);
                    applicableCodeLens.add(codeLensData);
                }
            } else {
                // If the code lens doesn't need resolution, add it to the applicableCodeLens list.
                applicableCodeLens.add(codeLensData);
            }
        }
        return visibleCodeLensToResolve;
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
                assert (ApplicationManager.getApplication().isUnitTestMode());
                return ReadAction.compute(computable);
            }
        } catch (ReadAction.CannotReadException e) {
            return CodeVisionState.NotReady.INSTANCE;
        } catch (ProcessCanceledException e) {
            return CodeVisionState.NotReady.INSTANCE;
        } catch (CancellationException e) {
            return CodeVisionState.NotReady.INSTANCE;
        } catch (Throwable e) {
            LOGGER.error("Error while consuming LSP 'textDocument/codeLens' request", e);
            return CodeVisionState.Companion.getREADY_EMPTY();
        }
    }

    static int sortCodeLensByLine(@NotNull CodeLensData cl1, @NotNull CodeLensData cl2) {
        return getCodeLensLine(cl1) - getCodeLensLine(cl2);
    }

    public static int getCodeLensLine(CodeLensData codeLensData) {
        return codeLensData.getCodeLens().getRange().getStart().getLine();
    }

    private static CompletableFuture<CodeLensDataResult> fetchCodeLenses(@NotNull LSPCodeLensSupport codeLensSupport) {
        var params = new CodeLensParams(LSPIJUtils.toTextDocumentIdentifier(codeLensSupport.getFile().getVirtualFile()));
        CompletableFuture<CodeLensDataResult> future;
        try {
            future = codeLensSupport.getCodeLenses(params);
        } catch (CancellationException e) {
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
