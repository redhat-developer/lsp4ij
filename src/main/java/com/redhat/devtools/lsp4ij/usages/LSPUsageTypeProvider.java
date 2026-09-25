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
import com.intellij.psi.PsiElement;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProviderEx;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokenPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP usage type provider to show results in the Usages view, categorized by:
 *
 * <ul>
 *     <li>Declarations</li>
 *     <li>Definitions</li>
 *     <li>Type Definitions</li>
 *     <li>References, or Value read and Value write for references whose access is known</li>
 *     <li>Implementations</li>
 * </ul>>
 */
public class LSPUsageTypeProvider implements UsageTypeProviderEx {

    private static final UsageType DECLARATIONS_USAGE_TYPE = new UsageType(LanguageServerBundle.messagePointer("usage.type.declarations"));
    private static final UsageType DEFINITIONS_USAGE_TYPE = new UsageType(LanguageServerBundle.messagePointer("usage.type.definitions"));
    private static final UsageType TYPE_DEFINITIONS_USAGE_TYPE = new UsageType(LanguageServerBundle.messagePointer("usage.type.typeDefinitions"));

    private static final UsageType REFERENCES_USAGE_TYPE = new UsageType(LanguageServerBundle.messagePointer("usage.type.references"));

    private static final UsageType IMPLEMENTATIONS_USAGE_TYPE = new UsageType(LanguageServerBundle.messagePointer("usage.type.implementations"));

    @Override
    public UsageType getUsageType(final @NotNull PsiElement element) {
        return getUsageType(element, UsageTarget.EMPTY_ARRAY);
    }

    @Override
    public UsageType getUsageType(PsiElement element, UsageTarget @NotNull [] targets) {
        if (element instanceof LSPUsagePsiElement usageElement) {
            return switch (usageElement.getKind()) {
                case declarations -> DECLARATIONS_USAGE_TYPE;
                case definitions -> DEFINITIONS_USAGE_TYPE;
                case typeDefinitions -> TYPE_DEFINITIONS_USAGE_TYPE;
                case references -> getReferenceUsageType(usageElement.getAccess());
                case implementations -> IMPLEMENTATIONS_USAGE_TYPE;
            };
        }
        // We can also have local usages to semantic token elements which are references
        else if (element instanceof LSPSemanticTokenPsiElement) {
            return REFERENCES_USAGE_TYPE;
        }
        return null;
    }

    private static UsageType getReferenceUsageType(@Nullable ReadWriteAccessDetector.Access access) {
        if (access == null) {
            return REFERENCES_USAGE_TYPE;
        }
        // A reference that both reads and writes is a write, as the platform types any ReadWriteAccessUsage that writes
        return access == ReadWriteAccessDetector.Access.Read ? UsageType.READ : UsageType.WRITE;
    }
}
