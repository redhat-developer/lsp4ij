/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * CppCXY
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentSymbol;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP document symbol structure view model.
 */
public class LSPDocumentSymbolStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
    private final PsiFile psiFile;
    // Update queue to avoid multiple updates
    private final MergingUpdateQueue updateQueue;

    public LSPDocumentSymbolStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
        super(psiFile, editor, new LSPFileStructureViewElement(psiFile));
        this.psiFile = psiFile;
        this.updateQueue = new MergingUpdateQueue("lsp.documentSymbol", 300, true, null);
        // Register psi tree change listener
        registerPsiTreeChangeListener();
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
        return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
        return false;
    }

    @Override
    public void dispose() {
        super.dispose();
        updateQueue.cancelAllUpdates();
        updateQueue.deactivate();
        LSPDocumentSymbolSupport documentSymbolSupport = LSPFileSupport.getSupport(psiFile).getDocumentSymbolSupport();
        documentSymbolSupport.cancel();
    }

    private void registerPsiTreeChangeListener() {
        PsiManager.getInstance(psiFile.getProject()).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override
            public void childrenChanged(PsiTreeChangeEvent event) {
                if (event.getFile() != null && event.getFile().equals(psiFile)) {
                    scheduleUpdate();
                }
            }

        }, this);
    }

    private void scheduleUpdate() {
        updateQueue.queue(new Update(this) {
            @Override
            public void run() {
                var root = getRoot();
                if (root instanceof LSPFileStructureViewElement) {
                    ((LSPFileStructureViewElement) root).updateChildren(psiFile);
                }
            }
        });
    }

    static class LSPDocumentSymbolViewElement implements StructureViewTreeElement {
        private final PsiFile psiFile;
        private final DocumentSymbolData documentSymbolData;
        private final TreeElement[] children;

        public LSPDocumentSymbolViewElement(PsiFile psiFile, DocumentSymbolData documentSymbolData) {
            this.psiFile = psiFile;
            this.documentSymbolData = documentSymbolData;
            var children = this.documentSymbolData.documentSymbol().getChildren();
            if (children != null) {
                this.children = children.stream()
                        .map(child -> new LSPDocumentSymbolViewElement(psiFile, new DocumentSymbolData(child)))
                        .toArray(TreeElement[]::new);
            } else {
                this.children = TreeElement.EMPTY_ARRAY;
            }
        }

        @Override
        public Object getValue() {
            return documentSymbolData;
        }

        @Override
        public @NotNull ItemPresentation getPresentation() {
            return documentSymbolData.getPresentation();
        }

        @Override
        public TreeElement @NotNull [] getChildren() {
            return children;
        }

        @Override
        public void navigate(boolean requestFocus) {
            var selectionRange = documentSymbolData.documentSymbol().getSelectionRange();
            var start = selectionRange.getStart();

            new OpenFileDescriptor(psiFile.getProject(), psiFile.getVirtualFile(), start.getLine(), start.getCharacter()).navigate(requestFocus);
        }

        @Override
        public boolean canNavigate() {
            return true;
        }

        @Override
        public boolean canNavigateToSource() {
            return true;
        }
    }

    static class LSPFileStructureViewElement implements StructureViewTreeElement {
        private final PsiFile psiFile;
        private volatile TreeElement[] children;

        public LSPFileStructureViewElement(PsiFile psiFile) {
            this.psiFile = psiFile;
            this.children = TreeElement.EMPTY_ARRAY;
            updateChildren(psiFile);
        }

        public void updateChildren(PsiFile psiFile) {
            LSPDocumentSymbolSupport documentSymbolSupport = LSPFileSupport.getSupport(psiFile).getDocumentSymbolSupport();
            var params = new DocumentSymbolParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()));
            var documentSymbolFuture = documentSymbolSupport.getDocumentSymbols(params);
            try {
                waitUntilDone(documentSymbolFuture, psiFile);
            } catch (
                    ProcessCanceledException e) { //Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
                documentSymbolSupport.cancel();
                return;
            } catch (CancellationException e) {
                documentSymbolSupport.cancel();
                return;
            } catch (ExecutionException e) {
                return;
            }

            if (isDoneNormally(documentSymbolFuture)) {
                var documentSymbols = documentSymbolFuture.getNow(null);
                if (documentSymbols == null) {
                    return;
                }
                this.children = documentSymbols.stream()
                        .map(documentSymbol -> new LSPDocumentSymbolViewElement(psiFile, documentSymbol))
                        .toArray(TreeElement[]::new);
            }
        }

        @Override
        public Object getValue() {
            return psiFile;
        }

        @Override
        public @NotNull ItemPresentation getPresentation() {
            return new ItemPresentation() {
                @Override
                public @NlsSafe @Nullable String getPresentableText() {
                    return psiFile.getName();
                }

                @Override
                public @NlsSafe @Nullable String getLocationString() {
                    return psiFile.getVirtualFile().getCanonicalPath();
                }

                @Override
                public @Nullable Icon getIcon(boolean unused) {
                    return psiFile.getIcon(0);
                }
            };
        }

        @Override
        public TreeElement @NotNull [] getChildren() {
            return children;
        }

        @Override
        public void navigate(boolean requestFocus) {
            new OpenFileDescriptor(psiFile.getProject(), psiFile.getVirtualFile()).navigate(requestFocus);
        }

        @Override
        public boolean canNavigate() {
            return true;
        }

        @Override
        public boolean canNavigateToSource() {
            return true;
        }
    }
}