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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.Processor;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
 *  </ul>
 */
public class LSPUsageSearcher extends CustomUsageSearcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPUsageSearcher.class);

    @Override
    public void processElementUsages(@NotNull PsiElement element, @NotNull Processor<? super Usage> processor, @NotNull FindUsagesOptions options) {
        if (element instanceof LSPUsageTriggeredPsiElement elt) {
            if (elt.getLSPReferences() != null) {
                elt.getLSPReferences()
                        .forEach(ref -> {
                            var psiElement = LSPUsagesManager.toPsiElement(ref, LSPUsagePsiElement.UsageKind.references, element.getProject());
                            if (psiElement != null) {
                                processor.process(ReadAction.compute(() -> new UsageInfo2UsageAdapter(new UsageInfo(psiElement))));
                            }
                        });
                return;
            }
        }

        // Get position where the "Find Usages" has been triggered
        Position position = getPosition(element);
        // Collect textDocument/definition, textDocument/references, etc
        LSPUsageSupport usageSupport = new LSPUsageSupport(ReadAction.compute(() -> element.getContainingFile()));
        LSPUsageSupport.LSPUsageSupportParams params = new LSPUsageSupport.LSPUsageSupportParams(position);
        CompletableFuture<List<LSPUsagePsiElement>> usagesFuture = usageSupport.getFeatureData(params);
        try {
            // Wait for completion of textDocument/definition, textDocument/references, etc
            waitUntilDone(usagesFuture);
            if (usagesFuture.isDone()) {
                // Show result of textDocument/definition, textDocument/references, etc as usage info.
                List<LSPUsagePsiElement> usages = usagesFuture.getNow(null);
                if (usages != null) {
                    for (LSPUsagePsiElement usage : usages) {
                        processor.process(ReadAction.compute(() -> new UsageInfo2UsageAdapter(new UsageInfo(usage))));
                    }
                }
            }
        } catch (ExecutionException e) {
            LOGGER.error("Error while collection LSP Usages", e);
        }
    }

    private static Position getPosition(PsiElement element) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return doGetPosition(element);
        }
        return ReadAction.compute(() -> doGetPosition(element));
    }

    private static Position doGetPosition(PsiElement element) {
        Document document = LSPIJUtils.getDocument(element.getContainingFile().getVirtualFile());
        return LSPIJUtils.toPosition(Math.min(element.getTextRange().getStartOffset() + 1, element.getTextRange().getEndOffset()), document);
    }
}
