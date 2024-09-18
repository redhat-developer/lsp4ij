/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Process data provider (pid and commands)
 */
public interface ProcessDataProvider {

    /**
     * Returns the current process pid and null otherwise.
     *
     * @return the current process pid and null otherwise.
     */
    @Nullable Long getPid();

    /**
     * Returns the commands used to start the language server.
     *
     * @return the commands used to start the language server.
     */
    List<String> getCommands();
}
