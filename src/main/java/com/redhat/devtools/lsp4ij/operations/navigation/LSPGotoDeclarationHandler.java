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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationUtil;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class LSPGotoDeclarationHandler implements GotoDeclarationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LSPGotoDeclarationHandler.class);

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (!LanguageServersRegistry.getInstance().isLanguageSupported(sourceElement.getLanguage())) {
            return null;
        }
        VirtualFile file = LSPIJUtils.getFile(sourceElement);
        if (file == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        URI uri = LSPIJUtils.toUri(file);
        Document document = editor.getDocument();
        DefinitionParams params = new DefinitionParams(LSPIJUtils.toTextDocumentIdentifier(uri), LSPIJUtils.toPosition(offset, document));
        Set<PsiElement> targets = new HashSet<>();
        final CancellationSupport cancellationSupport = new CancellationSupport();
        try {
            Project project = editor.getProject();
            LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(file, capabilities -> LSPIJUtils.hasCapability(capabilities.getDefinitionProvider()))
                    .thenComposeAsync(languageServers ->
                            cancellationSupport.execute(
                                    CompletableFuture.allOf(
                                            languageServers
                                                    .stream()
                                                    .map(server ->
                                                            cancellationSupport.execute(server.getServer().getTextDocumentService().definition(params))
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

    private List<PsiElement> toElements(Project project, Either<List<? extends Location>, List<? extends LocationLink>> definitions) {
        List<? extends Location> locations = definitions != null ? toLocation(definitions) : Collections.emptyList();
        return locations.stream().map(location -> toElement(project, location)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private PsiElement toElement(Project project, Location location) {
        return ApplicationManager.getApplication().runReadAction((Computable<PsiElement>) () -> {
            PsiElement element = null;
            VirtualFile file = LSPIJUtils.findResourceFor(location.getUri());
            if (file != null) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile != null) {
                    Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
                    if (document != null) {
                        element = psiFile.findElementAt(LSPIJUtils.toOffset(location.getRange().getStart(), document));
                    }
                }
            }
            return element;
        });
    }

    /**
     * Unify the definition result has a list of Location.
     *
     * @param definitions the definition result
     * @return the list of locations
     */
    private List<? extends Location> toLocation(Either<List<? extends Location>, List<? extends LocationLink>> definitions) {
        if (definitions.isLeft()) {
            return definitions.getLeft();
        } else {
            return definitions.getRight().stream().map(link -> new Location(link.getTargetUri(), link.getTargetRange())).collect(Collectors.toList());
        }
    }
}
