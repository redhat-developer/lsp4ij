package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.LSPPsiElementFactory;
import com.redhat.devtools.lsp4ij.features.implementation.LSPImplementationParams;
import com.redhat.devtools.lsp4ij.features.implementation.LSPImplementationSupport;
import org.eclipse.lsp4j.Location;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

public class LSPWorkspaceImplementationsSearch implements QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPWorkspaceImplementationsSearch.class);

    @Override
    public boolean execute(@NotNull DefinitionsScopedSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiElement> consumer) {
        Project project = queryParameters.getProject();
        if (project.isDisposed()) {
            return false;
        }

        PsiElement element = queryParameters.getElement();
        if (!element.isValid()) {
            return false;
        }

        PsiFile file = element.getContainingFile();
        if ((file == null) || !file.isValid()) {
            return false;
        }

        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            // The file is not associated to a language server
            return false;
        }

        // Check if the file can support the feature
        if (!LanguageServiceAccessor.getInstance(project)
                .hasAny(file.getVirtualFile(), ls -> ls.getClientFeatures().getImplementationFeature().isImplementationSupported(file))) {
            return false;
        }

        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            return false;
        }

        int offset = element.getTextRange().getStartOffset();
        LSPImplementationParams params = new LSPImplementationParams(
                LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile()),
                LSPIJUtils.toPosition(offset, document),
                offset
        );
        LSPImplementationSupport implementationSupport = LSPFileSupport.getSupport(file).getImplementationSupport();
        CompletableFuture<List<Location>> implementationsFuture = implementationSupport.getImplementations(params);
        try {
            waitUntilDone(implementationsFuture, file);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests textDocument/implementation
            implementationSupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests textDocument/implementation
            implementationSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/implementation' request", e);
        }

        if (isDoneNormally(implementationsFuture)) {
            // textDocument/implementations has been collected correctly
            List<Location> implementations = implementationsFuture.getNow(null);
            if (implementations != null) {
                for (Location implementation : implementations) {
                    consumer.process(LSPPsiElementFactory.toPsiElement(implementation, project));
                }
            }
        }

        return false;
    }
}
