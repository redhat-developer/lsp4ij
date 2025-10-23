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

import com.intellij.execution.configuration.EnvironmentVariablesData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface to be implemented by classes that are configurable with a {@link com.intellij.execution.configuration.EnvironmentVariablesData} option.
 * This interface provides methods to get and set the environment variables data associated with the object.
 */
public interface EnvironmentVariablesDataConfigurable {

    /**
     * Gets the environment variables data associated with this configuration.
     *
     * @return the environment variables data as a String, or null.
     */
    @NotNull
    EnvironmentVariablesData getEnvData();

    /**
     * Sets the environment variables data associated with this configuration.
     *
     * @param envData the environment variables data to set, or null.
     */
    void setEnvData(@NotNull EnvironmentVariablesData envData);
}
