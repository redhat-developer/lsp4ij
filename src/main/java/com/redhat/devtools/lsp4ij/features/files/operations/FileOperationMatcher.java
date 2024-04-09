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
package com.redhat.devtools.lsp4ij.features.files.operations;

import org.eclipse.lsp4j.FileOperationFilter;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.List;

/**
 * File operation matcher.
 */
public class FileOperationMatcher {

    private final List<FileOperationFilterMatcher> matchers;

    FileOperationMatcher(List<FileOperationFilter> filters) {
       this.matchers = filters
                .stream()
                .map(FileOperationFilterMatcher::new)
                .toList();
    }

    public boolean isMatch(@NotNull URI fileUri,
                           boolean isFolder) {
        for(var matcher : matchers) {
            if (matcher.isMatch(fileUri, isFolder)) {
                return true;
            }
        }
        return false;
    }
}