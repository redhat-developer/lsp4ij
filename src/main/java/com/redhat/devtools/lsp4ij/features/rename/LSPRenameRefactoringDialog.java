/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.features.refactoring.WorkspaceEditData;
import com.redhat.devtools.lsp4ij.internal.CancellationUtil;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.usages.LSPExternalReferencesFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDoneAsync;

/**
 * LSP rename dialog.
 * <p>
 * This class has some copy/paste of
 * <a href="https://github.com/JetBrains/intellij-community/blob/master/xml/impl/src/com/intellij/xml/refactoring/XmlTagRenameDialog.java">XMLTageRenameDialog</a>
 * adapted for LSP.
 */
class LSPRenameRefactoringDialog extends RefactoringDialog {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPRenameRefactoringDialog.class);

    @NotNull
    private final LSPRenameParams renameParams;

    @NotNull
    private final PsiFile psiFile;

    @NotNull
    private final Editor editor;

    private JLabel myTitleLabel;
    private NameSuggestionsField myNameSuggestionsField;
    private NameSuggestionsField.DataChanged myNameChangedListener;

    protected LSPRenameRefactoringDialog(@NotNull LSPRenameParams renameParams,
                                         @NotNull PsiFile psiFile,
                                         @NotNull Editor editor) {
        super(psiFile.getProject(), false);
        this.renameParams = renameParams;
        this.psiFile = psiFile;
        this.editor = editor;

        setTitle(RefactoringBundle.message("rename.title"));
        createNewNameComponent();

        init();

        myTitleLabel.setText(LanguageServerBundle.message("lsp.refactor.rename.symbol.dialog.title", renameParams.getNewName()));

        validateButtons();

    }

    private void createNewNameComponent() {
        myNameSuggestionsField = new NameSuggestionsField(new String[]{renameParams.getNewName()}, myProject, FileTypes.PLAIN_TEXT, editor);
        myNameChangedListener = this::validateButtons;
        myNameSuggestionsField.addDataChangedListener(myNameChangedListener);
    }

    @Override
    protected void doAction() {
        renameParams.setNewName(getNewName());
        doRename(renameParams, psiFile, editor);
        close(DialogWrapper.OK_EXIT_CODE);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return null;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myNameSuggestionsField.getFocusableComponent();
    }


    @Override
    protected JComponent createNorthPanel() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        myTitleLabel = new JLabel();
        panel.add(myTitleLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(myNameSuggestionsField.getComponent());

        return panel;
    }

    public String getNewName() {
        return myNameSuggestionsField.getEnteredName().trim();
    }

    @Override
    protected void validateButtons() {
        super.validateButtons();

        getPreviewAction().setEnabled(false);
    }

    @Override
    protected boolean areButtonsValid() {
        final String newName = getNewName();
        return StringUtils.isNotBlank(newName);
    }

    /**
     * Consume LSP 'textDocument/rename' request and apply the {@link org.eclipse.lsp4j.WorkspaceEdit}.
     *
     * @param renameParams the rename parameters.
     * @param psiFile      the Psi file.
     */
    static void doRename(@NotNull LSPRenameParams renameParams,
                         @NotNull PsiFile psiFile,
                         @NotNull Editor editor) {
        // Before having the language server perform the rename, see if there are any external references that should
        // also be updated upon successful completion by the language server
        int offset = editor.getCaretModel().getOffset();
        Set<PsiReference> externalReferences = getExternalReferences(psiFile, offset);

        CompletableFuture<List<WorkspaceEditData>> future = LSPFileSupport.getSupport(psiFile)
                .getRenameSupport()
                .getRename(renameParams);

        // Wait until the future is finished and stop the wait if there are some ProcessCanceledException.
        // The 'rename' is stopped:
        // - if user change the editor content
        // - if it cancels the Task
        String newName = renameParams.getNewName();
        String title = LanguageServerBundle.message("lsp.refactor.rename.progress.title", psiFile.getVirtualFile().getName(), newName);
        waitUntilDoneAsync(future, title, psiFile);

        future.handle((workspaceEdits, error) -> {
            if (error != null) {
                // Handle error
                if (CancellationUtil.isRequestCancelledException(error)) {
                    // Don't show cancelled error
                    return error;
                }
                if (error instanceof CompletionException || error instanceof ExecutionException) {
                    error = error.getCause();
                }
                // The language server throws an error, display it as hint in the editor
                LSPRenameHandler.showErrorHint(editor, error.getMessage());
                return null;
            }
            if (workspaceEdits == null || workspaceEdits.isEmpty()) {
                // Show "The element can't be renamed." hint error in the editor
                LSPRenameHandler.showErrorHint(editor, LanguageServerBundle.message("lsp.refactor.rename.cannot.be.renamed.error"));
            } else {
                // Apply the rename from the LSP WorkspaceEdit list
                WriteCommandAction.runWriteCommandAction(psiFile.getProject(), () -> {
                    workspaceEdits.forEach(workspaceEditData -> LSPIJUtils.applyWorkspaceEdit(workspaceEditData.edit()));

                    // Update any found external references with the new name
                    externalReferences.forEach(externalReference -> {
                        // Don't let a single failed external reference keep us from updating other references
                        try {
                            externalReference.handleElementRename(newName);
                        } catch (Exception e) {
                            LOGGER.warn("External reference rename failed.", e);
                        }
                    });
                });
            }
            return workspaceEdits;
        });
    }

    @Override
    protected boolean hasHelpAction() {
        return false;
    }

    @Override
    protected void dispose() {
        myNameSuggestionsField.removeDataChangedListener(myNameChangedListener);
        super.dispose();
    }

    @NotNull
    private static Set<PsiReference> getExternalReferences(@NotNull PsiFile file, int offset) {
        Set<PsiReference> externalReferences = new LinkedHashSet<>();

        // When testing, just collect references on the current thread
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            LSPExternalReferencesFinder.processExternalReferences(file, offset, reference -> {
                externalReferences.add(reference);
                return true;
            });
        } else {
            // This should happen on a progress indicator since it's performing a textual search of project
            // sources, and it must be modal as we need the results synchronously
            Project project = file.getProject();
            ProgressManager.getInstance().run(new Task.Modal(project, "Finding External References", true) {
                @Override
                public void run(@NotNull ProgressIndicator progressIndicator) {
                    progressIndicator.setIndeterminate(true);
                    ReadAction.run(() -> LSPExternalReferencesFinder.processExternalReferences(file, offset, reference -> {
                        externalReferences.add(reference);
                        return true;
                    }));
                }
            });
        }

        return externalReferences;
    }
}
