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

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
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
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.ReadWriteAccessUsage;
import com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.awaitWithCheckCanceled;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;

import static com.redhat.devtools.lsp4ij.internal.ApplicationUtils.runCancellableReadAction;
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
 * a declaration range, only the smaller, more specific range is kept), and to remove a reference or a type
 * definition at the location of a declaration or a definition, which is that declaration or definition again.
 * </p>
 * <p>
 * When the searched element is a language plugin's, whose own search also finds usages, a reference at the range of
 * a reference of the language plugin is reported as the language plugin reports that reference, so that the usage
 * view shows both on one line.
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
                                        processor.process(toUsage(psiElement));
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
            awaitWithCheckCanceled(usagesFuture);
            if (isDoneNormally(usagesFuture)) {
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
                        removeRepeatedDeclarations(filteredUsages);

                        for (LSPUsagePsiElement usage : filteredUsages) {
                            processor.process(toUsage(usage, element));
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
     * Removes the references and the type definitions at the location of a declaration or a definition from the given
     * usages.
     * <p>
     * Such a usage is the declaration or the definition again: the references include the declaration, as LSP4IJ asks
     * for them with {@code includeDeclaration}, and the type definition of a type is the type itself. The usage view
     * merges the usages of a line only within a group of the same usage type, so that it would show the declaration
     * once as a definition, once as a reference and once as a type definition (#729).
     * </p>
     *
     * @param usages the usages, from which the repeated declarations are removed.
     */
    private static void removeRepeatedDeclarations(@NotNull List<LSPUsagePsiElement> usages) {
        record UsageLocation(@Nullable VirtualFile file, @Nullable TextRange textRange) {

            UsageLocation(@NotNull LSPUsagePsiElement usage) {
                this(LSPIJUtils.getFile(usage), usage.getTextRange());
            }
        }
        Set<UsageLocation> declarations = usages.stream()
                .filter(usage -> usage.getKind() == LSPUsagePsiElement.UsageKind.declarations
                        || usage.getKind() == LSPUsagePsiElement.UsageKind.definitions)
                .map(UsageLocation::new)
                .collect(Collectors.toSet());
        usages.removeIf(usage -> (usage.getKind() == LSPUsagePsiElement.UsageKind.references
                || usage.getKind() == LSPUsagePsiElement.UsageKind.typeDefinitions)
                && declarations.contains(new UsageLocation(usage)));
    }

    /**
     * Wraps a usage element for the usage view.
     * <p>
     * The usage is shown with an {@link LSPUsageViewElement}, which the usage preview highlights at the range of the
     * usage, also in a file without real PSI.
     * </p>
     * <p>
     * A usage whose access is known becomes a {@link ReadWriteAccessUsage}, which is what the read and write
     * access filters and icons of the usage view act on. Any other usage is wrapped as it always was.
     * </p>
     *
     * @param usage the usage element.
     * @return the usage to report.
     */
    static @NotNull Usage toUsage(@NotNull LSPUsagePsiElement usage) {
        UsageInfo usageInfo = new UsageInfo(new LSPUsageViewElement(usage));
        ReadWriteAccessDetector.Access access = usage.getAccess();
        return access != null ? new ReadWriteAccessUsageInfo2UsageAdapter(usageInfo, access) : new UsageInfo2UsageAdapter(usageInfo);
    }

    /**
     * Wraps a usage element, found for the given searched element, for the usage view.
     * <p>
     * Find Usages of a language plugin's element, such as a Scala class, lists the usages that the language plugin's
     * own search finds as well as those of the language server. The usage view merges the usages of a line only
     * within one group, and groups a usage by its usage type and by the PSI around it, such as its method, so that it
     * would list the same reference twice: as, for instance, a method call of the language plugin and as a reference
     * of the language server (#729).
     * </p>
     * <p>
     * A reference at the range of a reference of the language plugin is therefore reported as the language plugin's
     * search reports that reference: as a usage of the reference's element, converted as Find Usages converts the
     * language plugin's usages, or with its access when that is known. The usage view then groups both alike and shows
     * them on one line. A reference that the language plugin's search does not find is still listed, in the groups of
     * the language plugin's reference.
     * </p>
     * <p>
     * Any other usage, and any usage of an element of LSP4IJ, which no language plugin searches, is wrapped by
     * {@link #toUsage(LSPUsagePsiElement)}.
     * </p>
     *
     * @param usage           the usage element.
     * @param searchedElement the element whose usages are searched.
     * @return the usage to report.
     */
    static @NotNull Usage toUsage(@NotNull LSPUsagePsiElement usage, @NotNull PsiElement searchedElement) {
        if (usage.getKind() == LSPUsagePsiElement.UsageKind.references && !(searchedElement instanceof LSPPsiElement)) {
            PsiReference reference = findLanguagePluginReference(usage);
            if (reference != null) {
                TextRange rangeInElement = reference.getRangeInElement();
                UsageInfo usageInfo = new UsageInfo(reference.getElement(), rangeInElement.getStartOffset(),
                        rangeInElement.getEndOffset());
                ReadWriteAccessDetector.Access access = usage.getAccess();
                return access != null
                        ? new ReadWriteAccessUsageInfo2UsageAdapter(usageInfo, access)
                        : UsageInfoToUsageConverter.convert(new PsiElement[]{searchedElement}, usageInfo);
            }
        }
        return toUsage(usage);
    }

    /**
     * Returns the reference of a language plugin whose range is the range of the given usage element, or null if there
     * is none.
     *
     * @param usage the usage element.
     * @return the reference of a language plugin at the range of the usage element, or null if there is none.
     */
    private static @Nullable PsiReference findLanguagePluginReference(@NotNull LSPUsagePsiElement usage) {
        TextRange textRange = usage.getTextRange();
        PsiReference reference = usage.getContainingFile().findReferenceAt(textRange.getStartOffset());
        // The references of LSP4IJ's own PSI, such as that of the semantic tokens, are not a language plugin's
        return reference != null
                && !(reference.getElement() instanceof LSPPsiElement)
                && textRange.equals(reference.getAbsoluteRange()) ? reference : null;
    }

    /**
     * Computes the LSP position to use for querying usages of the given element.
     * <p>
     * The position is calculated as {@code textOffset + 1}, clamped to not exceed {@code endOffset}.
     * This ensures the position is inside the element's text range (not at the very start boundary),
     * which is important for language servers that may not recognize positions at token boundaries.
     * </p>
     * <p>
     * The text offset of a named element is the offset of its name, while its text range can start earlier,
     * for example at the decorators of a Python function, which are not the symbol to query. A text offset
     * outside the text range, like the default one of a fake or light element, is replaced by {@code startOffset}.
     * </p>
     * <p>
     * A one-character name has no position inside it: the position is then its end, the boundary after the name.
     * </p>
     * <p>
     * For example, if the element is the identifier {@code myVar} spanning offsets [10, 15]:
     * <ul>
     *   <li>textOffset = startOffset = 10</li>
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
        TextRange textRange = element.getTextRange();
        int textOffset = element.getTextOffset();
        int offset = textRange.containsOffset(textOffset) ? textOffset : textRange.getStartOffset();
        return LSPIJUtils.toPosition(Math.min(offset + 1, textRange.getEndOffset()), document);
    }
}

