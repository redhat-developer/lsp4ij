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

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * LSP {@link FindUsagesHandler} implementation used just for some PsiFile which have none find usage handler.
 * It provides the capability to consume "Find Usages" action to show LSP references, implementations, etc
 * even if the PsiFile have none {@link FindUsagesHandler} and call after that the {@link LSPUsageSearcher}.
 */
public class LSPFindUsagesHandler extends FindUsagesHandler {

    public LSPFindUsagesHandler(@NotNull PsiElement psiElement) {
        super(psiElement);
    }

    @Override
    public boolean processElementUsages(@NotNull PsiElement element,
                                        @NotNull Processor<? super UsageInfo> processor,
                                        @NotNull FindUsagesOptions options) {
        /**
         * LSP {@link FindUsagesHandler} implementation which does nothing and return true
         * (see https://github.com/JetBrains/intellij-community/blob/6fa97b2b6dc1d15be130c8c512102ead37a44ece/platform/lang-impl/src/com/intellij/find/findUsages/FindUsagesManager.java#L397)
         * to process element usages with LSP custom Usage searcher {@link LSPUsageSearcher}
         * (see https://github.com/JetBrains/intellij-community/blob/6fa97b2b6dc1d15be130c8c512102ead37a44ece/platform/lang-impl/src/com/intellij/find/findUsages/FindUsagesManager.java#L403).
         */
        return true;
    }

}
