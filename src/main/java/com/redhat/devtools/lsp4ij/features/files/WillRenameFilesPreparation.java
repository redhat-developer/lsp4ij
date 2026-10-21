/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends the {@code workspace/willRenameFiles} requests of a rename or move refactoring before its
 * write action.
 * <p>
 * IntelliJ calls {@link AbstractLSPFileListener#prepareChange} for a rename or move made in the IDE
 * in the write action that makes it, so waiting there for the language server response blocks the
 * UI. A refactoring knows which files it renames or moves before its write action, though:
 * <ol>
 *   <li>{@link WillRenameFilesRenameProcessor} and {@link WillRenameFilesMoveHandler} plan the
 *   renames when the refactoring prepares them,</li>
 *   <li>{@link WillRenameFilesRefactoringHelper} sends the requests and waits for their responses
 *   under the progress the refactoring shows before its write action,</li>
 *   <li>the file listeners then use these responses for the VFS events of the refactoring,
 *   instead of sending the requests again,</li>
 *   <li>and the responses that no VFS event used are dropped once the refactoring is done.</li>
 * </ol>
 * Other file operations still send their requests from {@link AbstractLSPFileListener#prepareChange}.
 */
@Service(Service.Level.PROJECT)
public final class WillRenameFilesPreparation {

    /**
     * A file or directory that a refactoring is about to rename or move.
     *
     * @param element   the element the refactoring was invoked on
     * @param file      the file or directory
     * @param newParent the directory that will contain it
     * @param newName   its new name
     */
    record FileRenamePlan(@NotNull PsiElement element,
                          @NotNull VirtualFile file,
                          @NotNull VirtualFile newParent,
                          @NotNull String newName) {

        private boolean hasSameDestination(@NotNull FileRenamePlan other) {
            return newParent.equals(other.newParent) && newName.equals(other.newName);
        }
    }

    private final @NotNull Project project;

    // The renames planned since the last refactoring sent its requests, by file, guarded by itself
    private final Map<VirtualFile, FileRenamePlan> plans = new LinkedHashMap<>();

    public WillRenameFilesPreparation(@NotNull Project project) {
        this.project = project;
    }

    public static WillRenameFilesPreparation getInstance(@NotNull Project project) {
        return project.getService(WillRenameFilesPreparation.class);
    }

    /**
     * Returns whether a started language server of the project may ask for
     * {@code workspace/willRenameFiles}, which each server then checks against its file operation filters.
     */
    static boolean isWillRenameFilesSupported(@NotNull Project project) {
        return LanguageServiceAccessor.getInstance(project).getStartedServers().stream()
                .anyMatch(server -> server.isActive() &&
                        LanguageServerItem.isWillRenameFilesSupported(server.getServerCapabilitiesSync()));
    }

    void plan(@NotNull FileRenamePlan rename) {
        synchronized (plans) {
            // A rename plans the files again for each element it renames automatically, so a plan for the
            // same destination keeps the element the refactoring was invoked on. A plan for another
            // destination was made by a refactoring that stopped before its write action.
            plans.merge(rename.file(), rename, (planned, latest) ->
                    planned.element().isValid() && planned.hasSameDestination(latest) ? planned : latest);
        }
    }

    /**
     * Sends the {@code workspace/willRenameFiles} requests for the renames planned by the refactoring
     * invoked on the given elements, and waits for their responses.
     *
     * @param elements the elements the refactoring was invoked on
     */
    void sendAndAwait(@NotNull List<PsiElement> elements) {
        var refactored = new HashSet<>(elements);
        List<FileRenamePlan> renames;
        synchronized (plans) {
            renames = plans.values().stream().filter(rename -> refactored.contains(rename.element())).toList();
            // The others were planned by a refactoring that stopped before its write action
            plans.clear();
        }
        if (renames.isEmpty()) {
            return;
        }
        var servers = List.copyOf(LanguageServiceAccessor.getInstance(project).getStartedServers());
        // A refactoring without an id publishes no refactoringDone that would have dropped its requests
        servers.forEach(server -> server.getFileListener().clearWillRenameFiles());
        // Send all the requests before waiting for any of their responses
        servers.forEach(server -> server.getFileListener().sendWillRenameFiles(renames));
        servers.forEach(server -> server.getFileListener().awaitWillRenameFiles());
    }

    private void clear() {
        synchronized (plans) {
            plans.clear();
        }
        LanguageServiceAccessor.getInstance(project).getStartedServers()
                .forEach(server -> server.getFileListener().clearWillRenameFiles());
    }

    /**
     * Drops the plans and the responses of a refactoring that its VFS events did not use, once it is done.
     */
    public static final class RefactoringListener implements RefactoringEventListener {

        private final @NotNull Project project;

        public RefactoringListener(@NotNull Project project) {
            this.project = project;
        }

        @Override
        public void refactoringDone(@NotNull String refactoringId, @Nullable RefactoringEventData afterData) {
            // Without the service, no refactoring planned a rename
            var preparation = project.getServiceIfCreated(WillRenameFilesPreparation.class);
            if (preparation != null) {
                preparation.clear();
            }
        }
    }
}
