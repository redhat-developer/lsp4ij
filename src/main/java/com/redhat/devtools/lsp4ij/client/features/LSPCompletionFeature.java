/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.completion.CompletionPrefix;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.capabilities.CompletionCapabilityRegistry;
import com.redhat.devtools.lsp4ij.ui.IconMapper;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * LSP completion feature.
 */
@ApiStatus.Experimental
public class LSPCompletionFeature extends AbstractLSPDocumentFeature {

    private CompletionCapabilityRegistry completionCapabilityRegistry;

    public static class LSPCompletionContext {

        private final @NotNull CompletionParameters parameters;
        private final @NotNull LanguageServerItem languageServer;
        private Boolean signatureHelpSupported;
        private Boolean resolveCompletionSupported;

        public LSPCompletionContext(@NotNull CompletionParameters parameters, @NotNull LanguageServerItem languageServer) {
            this.parameters = parameters;
            this.languageServer = languageServer;
        }

        public @NotNull CompletionParameters getParameters() {
            return parameters;
        }

        public boolean isSignatureHelpSupported() {
            if (signatureHelpSupported == null) {
                signatureHelpSupported = languageServer.getClientFeatures().getSignatureHelpFeature().isSupported(parameters.getOriginalFile());
            }
            return signatureHelpSupported;
        }

        public boolean isResolveCompletionSupported() {
            if (resolveCompletionSupported == null) {
                resolveCompletionSupported = languageServer.getClientFeatures().getCompletionFeature().isResolveCompletionSupported(parameters.getOriginalFile());
            }
            return resolveCompletionSupported;
        }

        @NotNull
        @ApiStatus.Internal
        LanguageServerItem getLanguageServer() {
            return languageServer;
        }
    }

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isCompletionSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support completion and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support completion and false otherwise.
     */
    public boolean isCompletionSupported(@NotNull PsiFile file) {
        return getCompletionCapabilityRegistry().isCompletionSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support resolve completion and false otherwise.
     *
     * @param file the file.
     * @return true the file associated with a language server can support resolve completion and false otherwise.
     */
    public boolean isResolveCompletionSupported(@NotNull PsiFile file) {
        return getCompletionCapabilityRegistry().isResolveCompletionSupported(file);
    }

    /**
     * Returns true if the given character is defined as "completion trigger" in the server capability of the language server and false otherwise.
     *
     * @param file      the file.
     * @param charTyped the current typed character.
     * @return true if the given character is defined as "completion trigger" in the server capability of the language server and false otherwise.
     */
    public boolean isCompletionTriggerCharactersSupported(@NotNull PsiFile file, String charTyped) {
        return getCompletionCapabilityRegistry().isCompletionTriggerCharactersSupported(file, charTyped);
    }

    /**
     * Create a completion lookup element from the given LSP completion item and context and null otherwise.
     *
     * @param item    the LSP completion item.
     * @param context the LSP completion context.
     * @return a completion lookup element from the given LSP completion item and context and null otherwise.
     */
    @Nullable
    public LookupElement createLookupElement(@NotNull CompletionItem item,
                                             @NotNull LSPCompletionContext context) {
        if (StringUtils.isBlank(item.getLabel())) {
            // Invalid completion Item, ignore it
            return null;
        }
        // Update text edit range, commitCharacters, ... with item defaults if needed
        return new LSPCompletionProposal(item, context, this);
    }

    /**
     * Update the given IntelliJ lookup element presentation with the given LSP completion item.
     *
     * @param presentation the lookup element presentation to update.
     * @param item         the LSP completion .
     */
    public void renderLookupElement(@NotNull LookupElementPresentation presentation,
                                    @NotNull CompletionItem item) {
        presentation.setItemText(this.getItemText(item));
        presentation.setTypeText(this.getTypeText(item));
        presentation.setIcon(this.getIcon(item));
        presentation.setStrikeout(this.isStrikeout(item));
        presentation.setTailText(this.getTailText(item));
        presentation.setItemTextBold(this.isItemTextBold(item));
    }

    /**
     * Returns the IntelliJ lookup item text from the given LSP completion item and null otherwise.
     *
     * @param item the LSP completion item.
     * @return the IntelliJ lookup item text from the given LSP completion item and null otherwise.
     */
    @Nullable
    public String getItemText(@NotNull CompletionItem item) {
        return item.getLabel();
    }

    /**
     * Returns the IntelliJ lookup type text from the given LSP completion item and null otherwise.
     *
     * @param item the LSP completion item.
     * @return the IntelliJ lookup type text from the given LSP completion item and null otherwise.
     */
    @Nullable
    public String getTypeText(CompletionItem item) {
        var labelDetails = item.getLabelDetails();
        return labelDetails != null ? labelDetails.getDescription() : item.getDetail();
    }

    /**
     * Returns the IntelliJ lookup icon from the given LSP completion item and null otherwise.
     *
     * @param item the LSP completion item.
     * @return the IntelliJ lookup icon from the given LSP completion item and null otherwise.
     */
    @Nullable
    public Icon getIcon(@NotNull CompletionItem item) {
        return IconMapper.getIcon(item);
    }

    /**
     * Returns true if the IntelliJ lookup is strike out and false otherwise.
     *
     * @param item the completion item
     * @return true if the IntelliJ lookup is strike out and false otherwise.
     */
    public boolean isStrikeout(@NotNull CompletionItem item) {
        return (item.getTags() != null && item.getTags().contains(CompletionItemTag.Deprecated))
                || (item.getDeprecated() != null && item.getDeprecated().booleanValue());
    }

    /**
     * Returns the IntelliJ lookup tail text from the given LSP completion item and null otherwise.
     *
     * @param item the LSP completion item.
     * @return the IntelliJ lookup tail text from the given LSP completion item and null otherwise.
     */
    @Nullable
    public String getTailText(@NotNull CompletionItem item) {
        var labelDetails = item.getLabelDetails();
        return labelDetails != null ? labelDetails.getDetail() : null;
    }

    /**
     * Returns the IntelliJ lookup item text bold from the given LSP completion item and null otherwise.
     *
     * @param item the LSP completion item.
     * @return the IntelliJ lookup item text bold from the given LSP completion item and null otherwise.
     */
    public boolean isItemTextBold(@NotNull CompletionItem item) {
        return item.getKind() != null && item.getKind() == CompletionItemKind.Keyword;
    }

    /**
     * Don't override this method, we need to revisit the API and the prefix computation (to customize it).
     *
     * @param context the completion context
     * @param completionPrefix the completion prefix
     * @param result the completion result set
     * @param lookupItem the lookup item
     * @param priority the completion priority
     * @param item the completion item
     */
    @ApiStatus.Internal
    public void addLookupItem(@NotNull LSPCompletionContext context,
                              @NotNull CompletionPrefix completionPrefix,
                              @NotNull CompletionResultSet result,
                              @NotNull LookupElement lookupItem,
                              int priority,
                              @NotNull CompletionItem item) {
        // Determine whether or not completions in this language should be case-sensitive
        boolean caseSensitive = getClientFeatures().isCaseSensitive(context.getParameters().getOriginalFile());

        var prioritizedLookupItem = PrioritizedLookupElement.withPriority(lookupItem, priority);

        // Compute the prefix
        var textEditRange = ((LSPCompletionProposal) lookupItem).getTextEditRange();
        String prefix = textEditRange != null ? completionPrefix.getPrefixFor(textEditRange, item) : null;
        if (prefix != null) {
            // Add the IJ completion item (lookup item) by using the computed prefix respecting the language's case-sensitivity
            if (caseSensitive) {
                result.withPrefixMatcher(prefix)
                        .addElement(prioritizedLookupItem);
            } else {
                result.withPrefixMatcher(prefix)
                        .caseInsensitive()
                        .addElement(prioritizedLookupItem);
            }
        } else {
            // Add the IJ completion item (lookup item) by using the prefix matcher respecting the language's case-sensitivity
            if (caseSensitive) {
                result.withPrefixMatcher(result.getPrefixMatcher())
                        .addElement(prioritizedLookupItem);
            } else {
                result.withPrefixMatcher(result.getPrefixMatcher())
                        .caseInsensitive()
                        .addElement(prioritizedLookupItem);
            }
        }
    }

    /**
     * Returns true if completion item must be resolved and false otherwise when completion item is applied.
     *
     * @param item the completion item which is applied.
     * @param file the file.
     * @return true if completion item must be resolved and false otherwise when completion item is applied.
     */
    public boolean shouldResolveOnApply(@NotNull CompletionItem item,
                                        @NotNull PsiFile file) {
        return true;
    }

    public CompletionCapabilityRegistry getCompletionCapabilityRegistry() {
        if (completionCapabilityRegistry == null) {
            initCompletionCapabilityRegistry();
        }
        return completionCapabilityRegistry;
    }

    private synchronized void initCompletionCapabilityRegistry() {
        if (completionCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        completionCapabilityRegistry = new CompletionCapabilityRegistry(clientFeatures);
        completionCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (completionCapabilityRegistry != null) {
            completionCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    /**
     * Determines whether or not client-side context-aware completion sorting should be used for the specified file.
     *
     * @param file the file
     * @return true if client-side context-aware completion sorting should be used; otherwise false
     */
    public boolean useContextAwareSorting(@NotNull PsiFile file) {
        // Default to disabled
        return false;
    }

    /**
     * Whether or not an editor template should be used for invocation-only snippets.
     *
     * @param file the file
     * @return true an editor template should be used for invocation-only snippets; otherwise false
     */
    public boolean useTemplateForInvocationOnlySnippet(@NotNull PsiFile file) {
        // Default to enabled
        return true;
    }
}
