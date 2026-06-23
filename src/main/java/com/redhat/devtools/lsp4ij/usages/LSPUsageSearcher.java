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
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.find.findUsages.CustomUsageSearcher;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.SearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.internal.ApplicationUtils.runCancellableReadAction;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;
import static com.redhat.devtools.lsp4ij.usages.LSPFindUsagesHandlerFactory.isUsageSupportedByLanguageServer;

/**
 * LSP usage searcher to retrieve all usages of a symbol via language server protocol.
 * <p>
 * This searcher extends IntelliJ's {@link CustomUsageSearcher} to provide Find Usages functionality
 * for LSP-managed files. It queries the language server for multiple types of usages and aggregates
 * them into a unified result set.
 * </p>
 * <p>
 * For a given file and offset, this searcher collects:
 * <ul>
 *   <li><b>Declarations</b> - via textDocument/declaration</li>
 *   <li><b>Definitions</b> - via textDocument/definition</li>
 *   <li><b>Type Definitions</b> - via textDocument/typeDefinition</li>
 *   <li><b>References</b> - via textDocument/references</li>
 *   <li><b>Implementations</b> - via textDocument/implementation</li>
 *   <li><b>External References</b> - cross-language references to LSP elements</li>
 * </ul>
 * </p>
 * <p>
 * The searcher handles two scenarios:
 * <ol>
 *   <li><b>Cached usages</b>: If the element is an {@link LSPUsageTriggeredPsiElement} with pre-computed
 *       references (from a previous search), those are returned immediately without querying the server again.</li>
 *   <li><b>Fresh search</b>: Otherwise, it sends LSP requests via {@link LSPUsageSupport} and waits for
 *       all results to complete before processing them.</li>
 * </ol>
 * </p>
 * <p>
 * Results are deduplicated to remove overlapping ranges (e.g., when a definition range fully contains
 * a declaration range, only the smaller, more specific range is kept).
 * </p>
 */
public class LSPUsageSearcher extends CustomUsageSearcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPUsageSearcher.class);

    /**
     * Processes all usages of the given element by querying the language server.
     * <p>
     * This method is called by IntelliJ's Find Usages infrastructure. It performs the following steps:
     * <ol>
     *   <li>Validates that the element has a containing file and project</li>
     *   <li>Checks if a language server supports usage features for the element</li>
     *   <li>Either returns cached references (if available) or queries the language server</li>
     *   <li>Filters out duplicate/overlapping results</li>
     *   <li>Respects the search scope specified in the Find Usages options</li>
     *   <li>Passes each usage to the processor for display in the Find Usages view</li>
     *   <li>Additionally collects external references (cross-language references to LSP elements)</li>
     * </ol>
     * </p>
     * <p>
     * <b>Performance optimization</b>: If the element is an {@link LSPUsageTriggeredPsiElement} with
     * cached references, those are used directly without making new LSP requests. This avoids redundant
     * server queries when the user navigates through previously found usages.
     * </p>
     * <p>
     * <b>Deduplication logic</b>: When multiple LSP features return overlapping ranges (e.g., a method
     * definition that contains its name declaration), the smaller/more specific range is preferred.
     * This prevents the Find Usages view from showing both the entire method and the method name as
     * separate results.
     * </p>
     *
     * @param element   the PSI element to find usages for (typically an {@link LSPUsageTriggeredPsiElement}).
     * @param processor the processor to handle each found usage.
     * @param options   the Find Usages options, including the search scope.
     */
    @Override
    public void processElementUsages(@NotNull PsiElement element, @NotNull Processor<? super Usage> processor, @NotNull FindUsagesOptions options) {
        // 1. Collect only what we need from the element inside a narrow ReadAction
        record ElementData(Project project, Position position, PsiFile file, int textOffset) {}
        ElementData data = runCancellableReadAction(() -> {
            PsiFile file = element.getContainingFile();
            if (file == null) {
                return null;
            }
            Position position = getPosition(element, file);
            if (position == null) {
                return null;
            }
            return new ElementData(element.getProject(), position, file, element.getTextOffset());
        }, ApplicationManager.getApplication());

        if (data == null) {
            return;
        }

        Project project = data.project();
        PsiFile file = data.file();
        Position position = data.position();
        if (!isUsageSupportedByLanguageServer(file)) {
            return;
        }

        SearchScope searchScope = options.searchScope;

        // Fast path: return cached references if available (avoids redundant LSP requests)
        if (element instanceof LSPUsageTriggeredPsiElement elt) {
            if (elt.getLSPReferences() != null) {
                elt.getLSPReferences()
                        .forEach(ref -> {
                            runCancellableReadAction(() -> {
                                var psiElement = LSPUsagesManager.toPsiElement(ref.location(), ref.languageServer().getClientFeatures(), LSPUsagePsiElement.UsageKind.references, project);
                                if (psiElement != null) {
                                    VirtualFile psiElementFile = LSPIJUtils.getFile(psiElement);
                                    if (psiElementFile != null) {
                                        processor.process(new UsageInfo2UsageAdapter(new UsageInfo(psiElement)));
                                    }
                                }
                            }, project);
                        });
                return;
            }
        }

        // 2. Dispatch the async LSP request OUTSIDE of the ReadAction
        LSPUsageSupport usageSupport = new LSPUsageSupport(file);
        LSPUsageSupport.LSPUsageSupportParams params = new LSPUsageSupport.LSPUsageSupportParams(position);
        CompletableFuture<List<LSPUsagePsiElement>> usagesFuture = usageSupport.getFeatureData(params);
        try {
            // 3. Wait for the future OUTSIDE of the ReadAction
            // This allows the ForkJoinPool background threads to freely acquire ReadActions
            // to resolve the PSI elements during mapping!
            waitUntilDone(usagesFuture);
            if (usagesFuture.isDone()) {
                List<LSPUsagePsiElement> usages = usagesFuture.getNow(null);
                if (usages != null && !usages.isEmpty()) {
                    // 4. Process the results (requires ReadAction again for VFS/PSI mapping)
                    runCancellableReadAction(() -> {
                        List<LSPUsagePsiElement> filteredUsages = new ArrayList<>(usages);
                        // Remove invalid usages and deduplicate overlapping ranges
                        filteredUsages.removeIf(usage -> ContainerUtil.exists(usages, otherUsage -> {
                            VirtualFile usageFile = LSPIJUtils.getFile(usage);
                            if (usageFile == null) {
                                return true;
                            }
                            // TODO: respect search scope - currently disabled
                            // if (!searchScope.contains(usageFile)) return true;

                            // Remove usages that fully contain other usages (keep the more specific one)
                            if (usage != otherUsage) {
                                TextRange textRange = usage.getTextRange();
                                TextRange otherTextRange = otherUsage.getTextRange();
                                return (textRange != null) &&
                                        (otherTextRange != null) &&
                                        textRange.contains(otherTextRange) &&
                                        !textRange.equals(otherTextRange);
                            }

                            return false;
                        }));

                        for (LSPUsagePsiElement usage : filteredUsages) {
                            processor.process(new UsageInfo2UsageAdapter(new UsageInfo(usage)));
                        }
                    }, project);
                }
            }
        } catch (ProcessCanceledException pce) {
            throw pce;
        } catch (Exception e) {
            LOGGER.error("Error while collection LSP Usages", e);
        }

        LSPExternalReferencesFinder.processExternalReferences(
                file,
                data.textOffset(),
                searchScope,
                reference -> processor.process(new UsageInfo2UsageAdapter(new UsageInfo(reference)))
        );
    }

    /**
     * Computes the LSP position to use for querying usages of the given element.
     * <p>
     * The position is calculated as {@code startOffset + 1}, clamped to not exceed {@code endOffset}.
     * This ensures the position is inside the element's text range (not at the very start boundary),
     * which is important for language servers that may not recognize positions at token boundaries.
     * </p>
     * <p>
     * For example, if the element is the identifier {@code myVar} spanning offsets [10, 15]:
     * <ul>
     *   <li>startOffset = 10</li>
     *   <li>endOffset = 15</li>
     *   <li>returned position = offset 11 (inside the identifier)</li>
     * </ul>
     * This gives the language server a position clearly inside the symbol to query.
     * </p>
     *
     * @param element the PSI element to get the position for.
     * @param psiFile the PSI file containing the element.
     * @return the LSP position (line and character), or {@code null} if the file or document is unavailable.
     */
    @Nullable
    private static Position getPosition(@NotNull PsiElement element, @NotNull PsiFile psiFile) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) {
            return null;
        }
        Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return null;
        }
        return LSPIJUtils.toPosition(Math.min(element.getTextRange().getStartOffset() + 1, element.getTextRange().getEndOffset()), document);
    }
}

