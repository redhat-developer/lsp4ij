/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files.operations.executors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Ref;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.features.files.operations.FileOperationsManager;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.RefactoringOnFileOperationsKind;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDoneWithBackgroundTask;

/**
 * Abstract class to execute an LSP 'workspace/will*Files' request to collect {@link WorkspaceEdit}
 * and send the proper 'workspace/did*Files' notification when to apply {@link WorkspaceEdit}
 * has been done with successful.
 * .
 */
public abstract class WillOperationFilesExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WillOperationFilesExecutor.class);

    protected final FileOperationsManager fileOperationsManager;
    private final String featureName;

    protected WillOperationFilesExecutor(FileOperationsManager fileOperationsManager, String featureName) {
        this.fileOperationsManager = fileOperationsManager;
        this.featureName = featureName;
    }

    public CompletableFuture<WorkspaceEdit> executeWillOperationFiles(@NotNull URI oldFileUri,
                                                                      boolean isFolder) {
        return executeWillOperationFiles(oldFileUri, null, isFolder, null);
    }
    public CompletableFuture<WorkspaceEdit> executeWillOperationFiles(@NotNull URI oldFileUri,
                                                                      @Nullable URI newFileUri,
                                                                      boolean isFolder,
                                                                      @Nullable Document document) {
        var languageServerWrapper = fileOperationsManager.getLanguageServerWrapper();
        var project = languageServerWrapper.getProject();
        final Map<String, Document> documentProvider = document != null ? new HashMap<>() : null;
        if (document != null) {
            documentProvider.put(oldFileUri.toASCIIString(), document);
        }
        // 1) Prepare workspace/will*Files future (workspace/willRenameFiles, workspace/willCreateFiles, workspace/willDeleteFiles).
        CancellationSupport cancellationSupport = new CancellationSupport();
        var willFilesFuture =
                cancellationSupport.execute(languageServerWrapper
                        .getInitializedServer()
                        .thenComposeAsync(ls -> {
                            return executeWillOperationFiles(oldFileUri, newFileUri, ls, cancellationSupport);
                        }));

        // 2) Execute the workspace/will*Files future in a Background task to notify to the user that some refactoring is collecting.
        waitUntilDoneWithBackgroundTask(willFilesFuture,
                LanguageServerBundle.message("lsp.file.operation.refactoring.preparing", languageServerWrapper.getServerDefinition().getDisplayName()),
                project);

        willFilesFuture.handle((edit, error) -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {

            }
            // Here, workspace/will*Files future is done with or without error.
            if (hasChanges(edit) && canApplyRefactoring(project, languageServerWrapper.getServerDefinition())) {
                // There are some changes and refactoring can be applied.
                try {
                    if (newFileUri != null) {
                        // In case of rename files, the WorkspaceEdit is applied for the old file Uri.
                        // We need to replace the old file Uri from WorkspaceEdit changes with the new file Uri
                //        updateWorkspaceEdit(edit, oldFileUri.toASCIIString(), newFileUri.toASCIIString());
                    }
                    // Apply workspace edit.
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        try {
                            LSPIJUtils.applyWorkspaceEdit(edit, null, documentProvider);
                        }
                        catch(Exception e) {

                        }
                    });

                    if (newFileUri != null) {
                        didClose(oldFileUri);
                    }
                    // The apply edit has been done correctly, send a did*Files (ex : didRenameFiles, didCreateFiles, didDeleteFiles)
                    // notifications if needed.
                    sendDidOperationFilesNotificationIfNeeded(oldFileUri, newFileUri, isFolder);

                } catch (Exception e) {
                    LOGGER.error("Error while applying LSP edit from LSP '" + featureName + "' request", e);
                }
            }

            return null;
        });
        return willFilesFuture;
    }

    /**
     * Returns true if refactoring can be applied and false otherwise.
     *
     * @param project          the project.
     * @param serverDefinition the language server definition.
     * @return true if refactoring can be applied and false otherwise.
     */
    private static boolean canApplyRefactoring(@NotNull Project project,
                                               @NotNull LanguageServerDefinition serverDefinition) {
        var settings = UserDefinedLanguageServerSettings.getInstance(project);
        switch (settings.getRefactoringOnFileOperationsKind()) {
            case apply:
                return true;
            case skip:
                return false;
            default: {
                // Open the "Apply Refactoring?" confirmation dialog.
                Ref<Boolean> ref = Ref.create(true);
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    ref.set(
                            MessageDialogBuilder.yesNo(LanguageServerBundle.message("lsp.file.operation.refactoring.confirm.dialog.title"),
                                            LanguageServerBundle.message("lsp.file.operation.refactoring.confirm.dialog.message",
                                                    serverDefinition.getDisplayName()))
                                    .noText(LanguageServerBundle.message("lsp.file.operation.refactoring.skip.button"))
                                    .doNotAsk(new DoNotAskOption.Adapter() {
                                        @Override
                                        public void rememberChoice(boolean isSelected, int exitCode) {
                                            if (isSelected) {
                                                RefactoringOnFileOperationsKind kind =
                                                        exitCode == DialogWrapper.OK_EXIT_CODE ?
                                                                RefactoringOnFileOperationsKind.apply :
                                                                RefactoringOnFileOperationsKind.skip;
                                                settings.setRefactoringOnFileOperationsKind(kind);
                                            }
                                        }
                                    }).ask(project));
                });
                return ref.get();
            }
        }
    }

    private static boolean hasChanges(@Nullable WorkspaceEdit edit) {
        if (edit == null) {
            return false;
        }
        var documentChanges = edit.getDocumentChanges();
        if (documentChanges != null && !documentChanges.isEmpty()) {
            return true;
        }
        var changes = edit.getChanges();
        return changes != null && !changes.isEmpty();
    }

    private static void updateWorkspaceEdit(@NotNull WorkspaceEdit edit,
                                            @NotNull String oldFileUri,
                                            @NotNull String newFileUri) {
        updateDocumentChanges(edit.getDocumentChanges(), oldFileUri, newFileUri);
        updateChanges(edit.getChanges(), oldFileUri, newFileUri);
    }

    private static void updateDocumentChanges(@Nullable List<Either<TextDocumentEdit, ResourceOperation>> documentChanges,
                                              @NotNull String oldFileUri,
                                              @NotNull String newFileUri) {
        if (documentChanges == null || documentChanges.isEmpty()) {
            return;
        }
        for (var documentChange : documentChanges) {
            if (documentChange.isLeft()) {
                var textDocument = documentChange.getLeft().getTextDocument();
                if (textDocument != null && oldFileUri.equals(textDocument.getUri())) {
                    textDocument.setUri(newFileUri);
                }
            }
        }
    }


    private static void updateChanges(Map<String, List<TextEdit>> changes, String oldFileUri, String newFileUri) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        var edits = changes.get(oldFileUri);
        if (edits != null) {
            changes.put(newFileUri, edits);
            changes.remove(oldFileUri);
        }
    }


    private CompletableFuture<WorkspaceEdit> executeWillOperationFiles(@NotNull URI oldFileUri,
                                                                       @Nullable URI newFileUri,
                                                                       @NotNull LanguageServer languageServer,
                                                                       @NotNull CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(
                executeWillOperationFiles(oldFileUri, newFileUri, languageServer),
                fileOperationsManager.getLanguageServerWrapper(),
                featureName);
    }

    protected abstract void sendDidOperationFilesNotificationIfNeeded(@NotNull URI oldFileUri,
                                                                      @Nullable URI newFileUri,
                                                                      boolean isFolder);

    protected abstract CompletableFuture<WorkspaceEdit> executeWillOperationFiles(@NotNull URI oldFileUri,
                                                                                  @Nullable URI newFileUri,
                                                                                  @NotNull LanguageServer languageServer);

    private void didClose(URI fileUri) {
        var languageServerWrapper = fileOperationsManager.getLanguageServerWrapper();
        if (languageServerWrapper.isConnectedTo(fileUri)) {
            languageServerWrapper.disconnect(fileUri, false);
        }
    }
}
