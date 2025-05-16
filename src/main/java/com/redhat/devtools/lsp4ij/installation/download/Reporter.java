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
package com.redhat.devtools.lsp4ij.installation.download;

import org.jetbrains.annotations.NotNull;

/**
 * Interface for reporting progress, status messages, and cancellation checks during
 * long-running operations such as downloads or installations.
 */
public interface Reporter {

    /**
     * Updates the current status message.
     *
     * @param text the status message to display; must not be null
     */
    void setText(@NotNull String text);

    /**
     * Updates the current status message including an exception that occurred.
     *
     * @param text the status message to display; must not be null
     * @param e the exception related to this status; must not be null
     */
    void setText(@NotNull String text, @NotNull Exception e);

    /**
     * Checks whether the current operation has been canceled.
     *
     * @throws RuntimeException if the operation has been canceled
     */
    void checkCanceled();
}
