/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.documentation;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.intellij.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.intellij.lsp4ij.internal.CancellationUtil;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * LSP textDocument/hover support for a given file.
 */
public class LSPTextHoverForFile implements Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPTextHoverForFile.class);
    private PsiElement lastElement;
    private int lastOffset = -1;
    private CompletableFuture<List<Hover>> lspRequest;
    private CancellationSupport previousCancellationSupport;

    public LSPTextHoverForFile(Editor editor) {
        if (editor instanceof EditorImpl) {
            Disposer.register(((EditorImpl) editor).getDisposable(), this);
        }
    }

    public List<MarkupContent> getHoverContent(PsiElement element, int targetOffset, Editor editor) {
        initiateHoverRequest(element, targetOffset);
        try {
            List<MarkupContent> result = lspRequest
                    .get(500, TimeUnit.MILLISECONDS).stream()
                    .filter(Objects::nonNull)
                    .map(LSPTextHoverForFile::getHoverString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            // The LSP hover request are finished, don't need to cancel the previous LSP requests.
            previousCancellationSupport = null;
            return result;
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
        return null;
    }


    /**
     * Initialize hover requests with hover (if available).
     *
     * @param element the PSI element.
     * @param offset  the target offset.
     */
    private void initiateHoverRequest(PsiElement element, int offset) {
        if (this.previousCancellationSupport != null) {
            // The previous LSP hover request is not finished,cancel it
            this.previousCancellationSupport.cancel();
        }
        VirtualFile file = LSPIJUtils.getFile(element);
        if (file == null) {
            return;
        }
        final Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return;
        }
        if (offset != -1 && (this.lspRequest == null || !element.equals(this.lastElement) || offset != this.lastOffset)) {
            this.lastElement = element;
            this.lastOffset = offset;
            CancellationSupport cancellationSupport = new CancellationSupport();
            this.lspRequest = LanguageServiceAccessor.getInstance(element.getProject())
                    .getLanguageServers(file, capabilities -> isHoverCapable(capabilities))
                    .thenApplyAsync(languageServers -> // Async is very important here, otherwise the LS Client thread is in
                            // deadlock and doesn't read bytes from LS
                            languageServers.stream()
                                    .map(languageServer ->
                                            cancellationSupport.execute(
                                                            languageServer.getServer().getTextDocumentService()
                                                                    .hover(LSPIJUtils.toHoverParams(offset, document)))
                                                    .join()
                                    ).filter(Objects::nonNull).collect(Collectors.toList()));
            // store the current cancel support as previous
            previousCancellationSupport = cancellationSupport;
        }
    }

    private static @Nullable MarkupContent getHoverString(Hover hover) {
        Either<List<Either<String, MarkedString>>, MarkupContent> hoverContent = hover.getContents();
        if (hoverContent.isLeft()) {
            List<Either<String, MarkedString>> contents = hoverContent.getLeft();
            if (contents == null || contents.isEmpty()) {
                return null;
            }
            String s = contents.stream().map(content -> {
                if (content.isLeft()) {
                    return content.getLeft();
                } else if (content.isRight()) {
                    MarkedString markedString = content.getRight();
                    // TODO this won't work fully until markup parser will support syntax
                    // highlighting but will help display
                    // strings with language tags, e.g. without it things after <?php tag aren't
                    // displayed
                    if (markedString.getLanguage() != null && !markedString.getLanguage().isEmpty()) {
                        return String.format("```%s%n%s%n```", markedString.getLanguage(), markedString.getValue()); //$NON-NLS-1$
                    } else {
                        return markedString.getValue();
                    }
                } else {
                    return ""; //$NON-NLS-1$
                }
            }).filter(((Predicate<String>) String::isEmpty).negate()).collect(Collectors.joining("\n\n"));
            return new MarkupContent(s, MarkupKind.PLAINTEXT);
        } else {
            return hoverContent.getRight();
        }
    }

    private static boolean isHoverCapable(ServerCapabilities capabilities) {
        return (capabilities.getHoverProvider().isLeft() && capabilities.getHoverProvider().getLeft()) || capabilities.getHoverProvider().isRight();
    }

    @Override
    public void dispose() {
        if (this.previousCancellationSupport != null) {
            // The previous LSP hover request is not finished,cancel it
            this.previousCancellationSupport.cancel();
        }
    }
}
