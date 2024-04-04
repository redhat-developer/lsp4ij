package com.redhat.devtools.lsp4ij.refactoring;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

public class LSPRenamePsiElementProcessor extends RenamePsiElementProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPRenamePsiElementProcessor.class);

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        return  (element instanceof LSPPsiElement) ||
                ((element instanceof PsiFile) && LanguageServersRegistry.getInstance().isFileSupported((PsiFile) element));
    }

    @Override
    public void prepareRenaming(@NotNull PsiElement element,
                                @NotNull String newName,
                                @NotNull Map<PsiElement, String> allRenames,
                                @NotNull SearchScope scope) {
        PsiFile file = element.getContainingFile();
        var project = file.getProject();

        URI oldFileUri = LSPIJUtils.toUri(file);
        int index = oldFileUri.toASCIIString().lastIndexOf('/');
        String newUri = oldFileUri.toASCIIString().substring(0, index) + "/" + newName;
        var params = new RenameFilesParams(List.of(new FileRename(oldFileUri.toASCIIString(), newUri)));

        CancellationSupport cancellationSupport = new CancellationSupport();
        var willRenameFilesFuture =
                LanguageServiceAccessor.getInstance(project)
                        .getLanguageServers(file.getVirtualFile(), LanguageServerItem::isWillRenameFilesSupported)
                        .thenComposeAsync(languageServerItems -> {


                            List<CompletableFuture<WorkspaceEdit>> r = languageServerItems
                                    .stream()
                                    .map(ls -> cancellationSupport
                                            .execute(ls.getWorkspaceService().willRenameFiles(params), ls, LSPRequestConstants.WORKSPACE_WILL_RENAME_FILES))
                                    .toList();

                            return r.get(0);
                        });

        try {
            // Wait upon the future is finished and stop the wait if there are some ProcessCanceledException.
            waitUntilDone(willRenameFilesFuture, file);
        } catch (CancellationException | ProcessCanceledException e) {
            //return CodeVisionState.NotReady.INSTANCE;
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'workspace/willRenameFiles' request", e);
            //return CodeVisionState.NotReady.INSTANCE;
        }
        if (isDoneNormally(willRenameFilesFuture)) {
            WorkspaceEdit edit = willRenameFilesFuture.getNow(null);
            if(edit != null) {
                var documentChanges = edit.getDocumentChanges();
                if(documentChanges != null) {
                    for (var documentChange : documentChanges) {
                        if (documentChange.isLeft()) {
                            var ed = documentChange.getLeft();
                            String uri = ed.getTextDocument().getUri();
                            VirtualFile p = LSPIJUtils.findResourceFor(uri);
                            if (p != null) {
                                Document document = LSPIJUtils.getDocument(p);
                                PsiFile pf = LSPIJUtils.getPsiFile(p, project);
                                ed.getEdits()
                                        .forEach(e -> {
                                            TextRange textRange=LSPIJUtils.toTextRange(e.getRange(), document);
                                            var elt = new LSPRenamePsiElement(pf, textRange, e);
                                            allRenames.put(elt, e.getNewText());
                                        });


                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void renameElement(@NotNull PsiElement element, @NotNull String newName, UsageInfo @NotNull [] usages, @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
        if(element instanceof LSPRenamePsiElement) {
            ((LSPRenamePsiElement) element).setName(newName);
            ((LSPRenamePsiElement) element).rename();
            if (listener != null) {
                listener.elementRenamed(element);
            }
        }
        super.renameElement(element, newName, usages, listener);
    }


}
