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
package com.redhat.devtools.lsp4ij;

/**
 * LSP notification constants.
 */
public class LSPNotificationConstants {

    // workspace/* LSP notifications
    public static final String WORKSPACE_DID_CHANGE_WORKSPACE_FOLDERS = "workspace/didChangeWorkspaceFolders";

    public static final String WORKSPACE_DID_CHANGE_WATCHED_FILES = "workspace/didChangeWatchedFiles";

    private LSPNotificationConstants() {

    }
}
