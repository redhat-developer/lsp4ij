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
package com.redhat.devtools.lsp4ij.dap.configurations.options;

import org.jetbrains.annotations.Nullable;

/**
 * Interface to be implemented by classes that are configurable with a working directory option.
 * This interface provides methods to get and set the working directory associated with the object.
 */
public interface WorkingDirectoryConfigurable {

    /**
     * Gets the working directory associated with this configuration.
     *
     * @return the working directory as a String, or null if no working directory is associated.
     */
    @Nullable
    String getWorkingDirectory();

    /**
     * Sets the working directory associated with this configuration.
     *
     * @param workingDirectory the working directory to set, or null to dissociate the working directory.
     */
    void setWorkingDirectory(@Nullable String workingDirectory);
}
