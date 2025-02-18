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
 * Interface to be implemented by classes that are configurable with a file option.
 * This interface provides methods to get and set the file associated with the object.
 */
public interface FileOptionConfigurable {

    /**
     * Gets the file associated with this configuration.
     *
     * @return the file as a String, or null if no file is associated.
     */
    @Nullable
    String getFile();

    /**
     * Sets the file associated with this configuration.
     *
     * @param file the file to set, or null to dissociate the file.
     */
    void setFile(@Nullable String file);
}
