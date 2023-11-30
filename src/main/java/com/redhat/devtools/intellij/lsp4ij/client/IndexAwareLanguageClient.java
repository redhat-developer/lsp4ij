/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.client;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class IndexAwareLanguageClient extends LanguageClientImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexAwareLanguageClient.class);

    public IndexAwareLanguageClient(Project project) {
        super(project);
    }

    /**
     * Run the given function as a background task, wrapped in a read action
     *
     * @param progressTitle the progress title of the action being run
     * @param code  the function code to execute in the background
     * @param <R>   the return type
     * @return the output of the function code
     */
    protected <R> CompletableFuture<R> runAsBackground(String progressTitle, Function<ProgressIndicator, R> code) {
        return runAsBackground(progressTitle, code, null);
    }

    /**
     * Run the given function as a background task, wrapped in a read action
     *
     * @param progressTitle the progress title of the action being run
     * @param code  the function code to execute in the background
     * @param <R>   the return type
     * @return the output of the function code
     */
    protected <R> CompletableFuture<R> runAsBackground(String progressTitle, Function<ProgressIndicator, R> code, Object coalesceBy) {
        return new LSPCompletableFuture<>(code, progressTitle, IndexAwareLanguageClient.this, coalesceBy);
    }

    /**
     * This method returns the file path to display in the progress bar.
     *
     * @param fileUri the file uri.
     * @return the file path to display in the progress bar.
     */
    protected String getFilePath(String fileUri) {
        VirtualFile file = LSPIJUtils.findResourceFor(fileUri);
        if (file != null) {
            Module module = LSPIJUtils.getModule(file, getProject());
            if (module != null) {
                ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
                VirtualFile[] contentRoots = rootManager.getContentRoots();
                if (contentRoots.length > 0) {
                    return VfsUtil.findRelativePath(contentRoots[0], file, '/');
                }
            }
        }
        return fileUri;
    }
}
