/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.navigation;

import com.intellij.codeInsight.navigation.CtrlMouseHandler2;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokensFileViewProvider;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.features.LSPPsiElementFactory.toPsiElement;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

public class LSPGotoDeclarationHandler implements GotoDeclarationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPGotoDeclarationHandler.class);

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                  int offset,
                                                  Editor editor) {
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return PsiElement.EMPTY_ARRAY;
        }
        PsiFile psiFile = sourceElement != null ? sourceElement.getContainingFile() : null;
        if (psiFile == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        if (!LanguageServersRegistry.getInstance().isFileSupported(psiFile)) {
            return PsiElement.EMPTY_ARRAY;
        }

        // If this was called for a populated semantic tokens view provider, try to get the target directly from it
        LSPSemanticTokensFileViewProvider semanticTokensFileViewProvider = LSPSemanticTokensFileViewProvider.getInstance(psiFile);
        if (semanticTokensFileViewProvider != null) {
            // If this is definitely a reference, resolve it
            if (semanticTokensFileViewProvider.isReference(offset)) {
                PsiReference reference = semanticTokensFileViewProvider.findReferenceAt(offset);
                PsiElement target = reference != null ? reference.resolve() : null;
                return target != null ? new PsiElement[]{target} : PsiElement.EMPTY_ARRAY;
            }
            // If it's definitely a declaration, just return an empty set of targets
            else if (semanticTokensFileViewProvider.isDeclaration(offset)) {
                return PsiElement.EMPTY_ARRAY;
            }
        }

        // Use LSP to find targets
        PsiElement[] targets = getGotoDeclarationTargets(sourceElement, offset);

        // If this is a semantic token-backed file and there were targets but this wasn't represented in semantic tokens
        // as a reference, stub a reference for the word at the current offset
        if (semanticTokensFileViewProvider != null) {
            if (!ArrayUtil.isEmpty(targets)) {
                TextRange wordRange = LSPIJUtils.getWordRangeAt(editor.getDocument(), psiFile, offset);
                if (wordRange != null) {
                    // This will ensure it's stubbed as a generic reference
                    semanticTokensFileViewProvider.addSemanticToken(wordRange, SemanticTokenTypes.Type, null);
                }
            }

            // NOTE: When invoked during Ctrl/Cmd+Mouseover, it's CRITICAL that we short-circuit any further Goto
            // Declaration Handler processing if this file is backed by semantic tokens and it's not a reference or a
            // declaration. Otherwise things that can't act as references will show up as hyperlinked incorrectly.
            // Unfortunately there's no symbolic state available as to whether or not this was invoked that way, so
            // we have to check the stack trace for the known caller.
            if (ExceptionUtil.currentStackTrace().contains(CtrlMouseHandler2.class.getName())) {
                throw new ProcessCanceledException();
            }
        }

        return targets;
    }

    /**
     * Uses LSP to resolve the target elements for the reference at the specified offset in the file containing the
     * provided source element.
     *
     * @param sourceElement the source element
     * @param offset        the offset
     * @return the resolved reference
     */
    @ApiStatus.Internal
    public static PsiElement @NotNull [] getGotoDeclarationTargets(@NotNull PsiElement sourceElement, int offset) {
        VirtualFile file = LSPIJUtils.getFile(sourceElement);
        if (file == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        // Consume LSP 'textDocument/definition' request
        PsiFile psiFile = sourceElement.getContainingFile();
        LSPDefinitionSupport definitionSupport = LSPFileSupport.getSupport(psiFile).getDefinitionSupport();
        var params = new LSPDefinitionParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()), LSPIJUtils.toPosition(offset, document), offset);
        CompletableFuture<List<LocationData>> definitionsFuture = definitionSupport.getDefinitions(params);
        try {
            waitUntilDone(definitionsFuture, psiFile);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests textDocument/definition
            definitionSupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests textDocument/definition
            definitionSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/definition' request", e);
        }

        if (isDoneNormally(definitionsFuture)) {
            // textDocument/definition has been collected correctly
            List<LocationData> locations = definitionsFuture.getNow(null);
            if (locations != null) {
                Project project = psiFile.getProject();
                return locations
                        .stream()
                        .map(location -> toPsiElement(location.location(), location.languageServer().getClientFeatures(), project))
                        .filter(Objects::nonNull)
                        .toArray(PsiElement[]::new);
            }
        }
        return PsiElement.EMPTY_ARRAY;
    }
}
