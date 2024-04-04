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

import com.redhat.devtools.lsp4ij.features.files.PathPatternMatcher;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationPatternKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * File operation filter matcher.
 */
public class FileOperationFilterMatcher {

    private enum PatternKind {
        File,
        Folder,
        Both;
    }

    @Nullable
    private final PathPatternMatcher pathPattern;
    private final PatternKind patternKind;

    public FileOperationFilterMatcher(@NotNull FileOperationFilter filter) {
        FileOperationPattern pattern = filter.getPattern();
        if (pattern != null) {
            this.pathPattern = toPathPattern(pattern);
            this.patternKind = getPatternKind(pattern.getMatches());
        } else {
            this.pathPattern = null;
            this.patternKind = null;
        }
    }

    private PatternKind getPatternKind(String matches) {
        if (FileOperationPatternKind.File.equals(matches)) {
            return PatternKind.File;
        }
        if (FileOperationPatternKind.Folder.equals(matches)) {
            return PatternKind.Folder;
        }
        return PatternKind.Both;
    }

    @Nullable
    private PathPatternMatcher toPathPattern(@NotNull FileOperationPattern pattern) {
        String glob = pattern.getGlob() != null && !pattern.getGlob().isEmpty() ? pattern.getGlob() : null;
        return glob != null ? new PathPatternMatcher(glob) : null;
    }

    public boolean isMatch(URI fileUri, boolean isFolder) {
        if (pathPattern == null) {
            // Invalid file operation pattern
            return false;
        }
        if (!isMatchPatternKind(isFolder)) {
            return false;
        }
        // TODO : match scheme
        return pathPattern.matches(fileUri);
    }

    private boolean isMatchPatternKind(boolean isFolder) {
        return (patternKind == PatternKind.Both || (isFolder && patternKind == PatternKind.Folder) || (!isFolder && patternKind == PatternKind.File));
    }
}
