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
package com.redhat.devtools.lsp4ij.operations.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationUtil;
import com.redhat.devtools.lsp4ij.operations.LSPPsiElement;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static com.redhat.devtools.lsp4ij.operations.LSPPsiElementFactory.toPsiElement;

public class LSPGotoDeclarationHandler implements GotoDeclarationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LSPGotoDeclarationHandler.class);

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (!LanguageServersRegistry.getInstance().isFileSupported(sourceElement.getContainingFile())) {
            return null;
        }
        VirtualFile file = LSPIJUtils.getFile(sourceElement);
        if (file == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        Document document = editor.getDocument();
        DefinitionParams params = new DefinitionParams(LSPIJUtils.toTextDocumentIdentifier(file), LSPIJUtils.toPosition(offset, document));
        Set<PsiElement> targets = new HashSet<>();
        final CancellationSupport cancellationSupport = new CancellationSupport();
        try {
            Project project = editor.getProject();
            LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(file, LanguageServerItem::isDefinitionSupported)
                    .thenComposeAsync(languageServers ->
                            cancellationSupport.execute(
                                    CompletableFuture.allOf(
                                            languageServers
                                                    .stream()
                                                    .map(server ->
                                                            cancellationSupport.execute(server
                                                                            .getTextDocumentService()
                                                                            .definition(params), server, "Definition")
                                                                    .thenAcceptAsync(definitions -> targets.addAll(toElements(project, definitions))))
                                                    .toArray(CompletableFuture[]::new))))
                    .get(1_000, TimeUnit.MILLISECONDS);
        } catch (ResponseErrorException | ExecutionException | CancellationException e) {
            // do not report error if the server has cancelled the request
            if (!CancellationUtil.isRequestCancelledException(e)) {
                LOGGER.warn(e.getLocalizedMessage(), e);
            }
        } catch (TimeoutException e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn(e.getLocalizedMessage(), e);
        }
        return targets.toArray(new PsiElement[targets.size()]);
    }

    private static List<LSPPsiElement> toElements(Project project, Either<List<? extends Location>, List<? extends LocationLink>> definitions) {
        if (definitions == null) {
            return Collections.emptyList();
        }
        if (definitions.isLeft()) {
            return definitions.getLeft()
                    .stream()
                    .map(location -> toPsiElement(location, project))
                    .filter(Objects::nonNull)
                    .toList();
        }
        return definitions.getRight()
                .stream()
                .map(location -> toPsiElement(location, project))
                .filter(Objects::nonNull)
                .toList();
    }

}
