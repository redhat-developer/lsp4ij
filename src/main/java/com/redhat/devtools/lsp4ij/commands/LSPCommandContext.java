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
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;

/**
 * LSP command contetx.
 */
public class LSPCommandContext {

    @NotNull
    private final Command command;
    @NotNull
    private final Project project;
    @Nullable
    private VirtualFile file;
    @Nullable
    private  PsiFile psiFile;
    @Nullable
    private  Editor editor;
    @Nullable
    private  Component source;
    @Nullable
    private  InputEvent inputEvent;
    @Nullable
    private LanguageServerItem preferredLanguageServer;

    public LSPCommandContext(@NotNull Command command,
                             @NotNull PsiFile psiFile,
                             @Nullable Editor editor,
                             @Nullable LanguageServerItem preferredLanguageServer) {
        this(command, psiFile.getProject());
        this.setPsiFile(psiFile);
        this.setEditor(editor);
        this.setPreferredLanguageServer(preferredLanguageServer);
    }

    public LSPCommandContext(@NotNull Command command,
                             @NotNull Project project) {
        this.command = command;
        this.project = project;
    }

    @NotNull
    public Command getCommand() {
        return command;
    }

    @NotNull
    public Project getProject() {
        return project;
    }

    public LanguageServerItem getPreferredLanguageServer() {
        return preferredLanguageServer;
    }

    public LSPCommandContext setPreferredLanguageServer(LanguageServerItem preferredLanguageServer) {
        this.preferredLanguageServer = preferredLanguageServer;
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
