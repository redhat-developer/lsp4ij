/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.workspaceFolder;

import java.util.Arrays;
import java.util.List;

/**
 * Workspace folder strategy based on marker files/directories.
 *
 * <p>This strategy discovers workspace folders dynamically by walking up the directory tree
 * looking for marker files like .git, pyproject.toml, pom.xml, etc.
 * Folders are always discovered lazily as files are opened.</p>
 */
public class MarkersWorkspaceFolderStrategy extends BaseWorkspaceFolderStrategy {

    public MarkersWorkspaceFolderStrategy(String... markers) {
        this(Arrays.asList(markers));
    }

    public MarkersWorkspaceFolderStrategy(List<String> markers) {
        setRootType(RootType.MARKERS);
        setLazy(true);
        setMarkers(markers);
    }
}
