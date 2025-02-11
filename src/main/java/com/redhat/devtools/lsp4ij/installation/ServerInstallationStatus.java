/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation;

/**
 * Enum representing the installation status of a server.
 */
public enum ServerInstallationStatus {
    /**
     * The server is not installed.
     */
    NOT_INSTALLED,

    /**
     * Checking server installed
     */
    CHECKING_INSTALLED,

    /**
     * The server is currently being installed.
     */
    INSTALLING,

    /**
     * The server has been successfully installed.
     */
    INSTALLED;
}
