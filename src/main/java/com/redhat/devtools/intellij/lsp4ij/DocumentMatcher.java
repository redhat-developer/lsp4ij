/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.concurrent.CompletableFuture;

/**
 * When a file is opened, the LSP support connect all available language server which matches the language of the file.
 * <p>
 * DocumentMatcher provides the capability to add advanced filter like check the file name, check that project have some Java classes in the classpath.
 */
public interface DocumentMatcher {

    /**
     * Returns true if the given file matches a mapping with a language server and false otherwise.
     *
     * @param file    teh file to check.
     * @param project the file project.
     * @return true if the given file matches a mapping with a language server and false otherwise.
     */
    boolean match(@NotNull VirtualFile file, @NotNull Project project);

    /**
     * Returns true if the given file matches a mapping with a language server and false otherwise.
     * <p>
     * In this case,the match is done in async mode. A typical usecase is when the matcher need to check that a given Java class belongs to the project or the file belongs to a source folder.
     * To evaluate this match, the read action mode is required and it can create a non blocking read action to evaluate the match.
     *
     * @param file
     * @param project
     * @return true if the given file matches a mapping with a language server and false otherwise.
     * @see AbstractDocumentMatcher
     */
    default @NotNull CompletableFuture<Boolean> matchAsync(@NotNull VirtualFile file, @NotNull Project project) {
        return CompletableFuture.completedFuture(match(file, project));
    }

    /**
     * Returns true if the match must be done asynchronously and false otherwise.
     * <p>
     * A typical usecase is when IJ is indexing or read action is not allowed,this method should return true, to execute match in a non blocking read action.
     *
     * @param project the project.
     * @return true if the match must be done asynchronously and false otherwise.
     * @see AbstractDocumentMatcher
     */
    default boolean shouldBeMatchedAsynchronously(@NotNull Project project) {
        return false;
    }
}
