/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.launching;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * File type settings.
 *
 * "fileType": {
 *   "name": "LESS",
 *   "patterns": [
 *     "*.less"
 *   ]
 * }
 *
 */
public class FileTypeSettings {

    @Attribute("name")
    private String name;

    @XCollection
    private List<String> patterns;

    public FileTypeSettings() {

    }

    public FileTypeSettings(@Nullable String name, @Nullable List<String> patterns) {
        this.name = name;
        this.patterns = patterns;
    }

    /**
     * Returns the file type name.
     * @return the file type name.
     */
    public @Nullable String getName() {
        return name;
    }

    public @Nullable List<String> getPatterns() {
        return patterns;
    }
}
