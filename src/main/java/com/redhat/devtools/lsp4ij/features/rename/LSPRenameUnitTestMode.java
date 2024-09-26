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
package com.redhat.devtools.lsp4ij.features.rename;

import org.eclipse.lsp4j.RenameParams;

/**
 * LSP rename handler used in Junit test mode to emulate
 *
 * <ul>
 *     <li>showErrorHint : when there is an error while renaming</li>
 *     <li>showRenameRefactoringDialog : when rename dialog is opened with rename parameters
 *     initialized with the prepare rename response</li>
 * </ul>
 */
public class LSPRenameUnitTestMode {

    public static interface LSPRenameUnitTestModeHandler {

        /**
         * Show error hint when there is an error while renaming.
         *
         * @param errorHintText the error hint text.
         */
        void showErrorHint(String errorHintText);

        /**
         * Show the rename dialog with the rename parameters initialized with the prepare rename response.
         *
         * @param renameParams the rename parameters.
         */
        void showRenameRefactoringDialog(RenameParams renameParams);

    }

    private static final ThreadLocal<LSPRenameUnitTestModeHandler> threadLocalHandler = new ThreadLocal<>();

    public static void set(LSPRenameUnitTestModeHandler handler) {
        threadLocalHandler.set(handler);
    }

    public static LSPRenameUnitTestModeHandler get() {
        return threadLocalHandler.get();
    }
}
