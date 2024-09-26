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
package com.redhat.devtools.lsp4ij.commands;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;

/**
 * LSP command contetx.
 */
public class LSPCommandContext {

    public enum ExecutedBy {
        CODE_LENS,
        INLAY_HINT,
        CODE_ACTION,
        COMPLETION,
        OTHER;
    }

    @NotNull
    private final Command command;
    @NotNull
    private final Project project;
    private boolean showNotificationError;
    @Nullable
    private VirtualFile file;
    @Nullable
    private PsiFile psiFile;
    @Nullable
    private Editor editor;
    @Nullable
    private Component source;
    @Nullable
    private InputEvent inputEvent;
    @Nullable
    private LanguageServerItem preferredLanguageServer;
    @Nullable
    private String preferredLanguageServerId;

    public LSPCommandContext(@NotNull Command command,
                             @NotNull PsiFile psiFile,
                             @NotNull ExecutedBy executedBy,
                             @Nullable Editor editor,
                             @Nullable LanguageServerItem preferredLanguageServer) {
        this(command, psiFile.getProject(), executedBy);
        this.setPsiFile(psiFile);
        this.setEditor(editor);
        this.setPreferredLanguageServer(preferredLanguageServer);
    }

    public LSPCommandContext(@NotNull Command command,
                             @NotNull Project project) {
        this(command, project, ExecutedBy.OTHER);
    }

    public LSPCommandContext(@NotNull Command command,
                             @NotNull Project project,
                             @NotNull ExecutedBy executedBy) {
        this.command = command;
        this.project = project;
        this.showNotificationError = UserDefinedLanguageServerSettings.getInstance(project).isShowNotificationErrorForCommand(executedBy);
    }

    @NotNull
    public Command getCommand() {
        return command;
    }

    @NotNull
    public Project getProject() {
        return project;
    }

    @Nullable
    public LanguageServerItem getPreferredLanguageServer() {
        return preferredLanguageServer;
    }

    public LSPCommandContext setPreferredLanguageServer(LanguageServerItem preferredLanguageServer) {
        this.preferredLanguageServer = preferredLanguageServer;
        return this;
    }

    public @Nullable String getPreferredLanguageServerId() {
        return preferredLanguageServerId;
    }

    public LSPCommandContext setPreferredLanguageServerId(@Nullable String preferredLanguageServerId) {
        this.preferredLanguageServerId = preferredLanguageServerId;
        return this;
    }

    public boolean isShowNotificationError() {
        return showNotificationError;
    }

    public LSPCommandContext setShowNotificationError(boolean showNotificationError) {
        this.showNotificationError = showNotificationError;
        return this;
    }

    public VirtualFile getFile() {
        return file;
    }

    public LSPCommandContext setFile(VirtualFile file) {
        this.file = file;
        return this;
    }

    public PsiFile getPsiFile() {
        return psiFile;
    }

    public LSPCommandContext setPsiFile(PsiFile psiFile) {
        this.psiFile = psiFile;
        return this;
    }

    public Editor getEditor() {
        return editor;
    }

    public LSPCommandContext setEditor(Editor editor) {
        this.editor = editor;
        return this;
    }

    public Component getSource() {
        return source;
    }

    public LSPCommandContext setSource(Component source) {
        this.source = source;
        return this;
    }

    public InputEvent getInputEvent() {
        return inputEvent;
    }

    public LSPCommandContext setInputEvent(InputEvent inputEvent) {
        this.inputEvent = inputEvent;
        return this;
    }
}
