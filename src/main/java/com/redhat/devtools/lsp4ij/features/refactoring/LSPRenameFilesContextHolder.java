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
package com.redhat.devtools.lsp4ij.features.refactoring;

/**
 * As the IntelliJ {@link com.intellij.refactoring.listeners.RefactoringEventListener} API
 * doesn't provide the capability to store data during the refactoring process,
 * the LSP rename files context is stored in a Thread local.
 */
public class LSPRenameFilesContextHolder {

    private static ThreadLocal<LSPRenameFilesContext> contextThreadLocal = new ThreadLocal<LSPRenameFilesContext>();

    public static void set(LSPRenameFilesContext serverIds) {
        contextThreadLocal.set(serverIds);
    }

    public static LSPRenameFilesContext get() {
        return contextThreadLocal.get();
    }
}
