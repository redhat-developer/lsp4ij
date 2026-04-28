package com.redhat.devtools.lsp4ij.features.references;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

public final class LSPReferenceCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPReferenceCollector.class);

    public static List<LocationData> collect(@NotNull PsiFile psiFile,
                                             @NotNull Document document,
                                             int offset) {
        LSPReferenceSupport referenceSupport = LSPFileSupport.getSupport(psiFile).getReferenceSupport();
        var params = new LSPReferenceParams(new TextDocumentIdentifier(), LSPIJUtils.toPosition(offset, document), offset);
        CompletableFuture<List<LocationData>> referencesFuture = referenceSupport.getReferences(params);
        try {
            waitUntilDone(referencesFuture, psiFile);
        } catch (CancellationException ex) {
            referenceSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/references' request", e);
        }

        if (isDoneNormally(referencesFuture)) {
            List<LocationData> locations = referencesFuture.getNow(null);
            if (locations != null) {
                return locations;
            }
        }
        return Collections.emptyList();
    }
}
