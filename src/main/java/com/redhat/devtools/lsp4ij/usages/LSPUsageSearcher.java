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
import com.intellij.openapi.application.ReadAction;
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
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP usage searcher to retrieve for a given file and offset:
 *
 * <ul>
 *      <li>Declarations</li>
 *      <li>Definitions</li>
 *      <li>Type Definitions</li>
 *      <li>References</li>
 *      <li>Implementations</li>
 *      <li>External References</li>
 *  </ul>
 */
public class LSPUsageSearcher extends CustomUsageSearcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPUsageSearcher.class);

    @Override
    public void processElementUsages(@NotNull PsiElement element, @NotNull Processor<? super Usage> processor, @NotNull FindUsagesOptions options) {
        // Ensure that LSP process elements usage is executed in a ReadAction.
        ReadAction.run(() -> {
            PsiFile file = element.getContainingFile();
            if (file == null) {
                return;
            }

            Project project = element.getProject();
            if (!LanguageServiceAccessor.getInstance(project).hasAny(
                    file,
                    l -> l.getClientFeatures().getUsageFeature().isSupported(file)
            )) {
                return;
            }

            // Make sure that the search scope is respected when adding usages
            SearchScope searchScope = options.searchScope;

            if (element instanceof LSPUsageTriggeredPsiElement elt) {
                if (elt.getLSPReferences() != null) {
                    elt.getLSPReferences()
                            .forEach(ref -> {
                                var psiElement = LSPUsagesManager.toPsiElement(ref.location(), ref.languageServer().getClientFeatures(), LSPUsagePsiElement.UsageKind.references, project);
                                if (psiElement != null) {
                                    VirtualFile psiElementFile = LSPIJUtils.getFile(psiElement);
                                    if (psiElementFile != null) {
                                        processor.process(new UsageInfo2UsageAdapter(new UsageInfo(psiElement)));
                                    }
                                }
                            });
                    return;
                }
            }

            // Get position where the "Find Usages" has been triggered
            Position position = getPosition(element, file);
            if (position == null) {
                return;
            }
            // Collect textDocument/definition, textDocument/references, etc
            LSPUsageSupport usageSupport = new LSPUsageSupport(file);
            LSPUsageSupport.LSPUsageSupportParams params = new LSPUsageSupport.LSPUsageSupportParams(position);
            CompletableFuture<List<LSPUsagePsiElement>> usagesFuture = usageSupport.getFeatureData(params);
            try {
                // Wait for completion of textDocument/definition, textDocument/references, etc
                waitUntilDone(usagesFuture);
                if (usagesFuture.isDone()) {
                    // Show response of textDocument/definition, textDocument/references, etc as usage info.
                    List<LSPUsagePsiElement> usages = usagesFuture.getNow(null);
                    if (usages != null) {
                        List<LSPUsagePsiElement> filteredUsages = new ArrayList<>(usages);
                        filteredUsages.removeIf(usage -> ContainerUtil.exists(usages, otherUsage -> {
                            VirtualFile usageFile = LSPIJUtils.getFile(usage);
                            if ((usageFile == null) /*||*!searchScope.contains(usageFile)*/) {
                                return true;
                            }

                            // Remove any usages that fully contain other usages, e.g., definitions when what's really
                            // wanted is the contained declaration/name identifier
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
                    }
                }
            } catch (ProcessCanceledException pce) {
                throw pce;
            } catch (Exception e) {
                LOGGER.error("Error while collection LSP Usages", e);
            }

            // TODO : as this external reference process can be very slow (for our IJ Quarkus);
            // the call of processExternalReferences is commented.
            //  Reactivate it just for some language servers (by using an lsp client featurees)
            // For completeness' sake, also collect external usages to LSP (pseudo-)elements
            /*LSPExternalReferencesFinder.processExternalReferences(
                    file,
                    element.getTextOffset(),
                    searchScope,
                    reference -> processor.process(new UsageInfo2UsageAdapter(new UsageInfo(reference)))
            );*/
        });
    }

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

