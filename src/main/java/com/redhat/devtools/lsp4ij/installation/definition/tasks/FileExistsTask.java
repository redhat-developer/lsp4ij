/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Installer task that checks whether a file exists at a given path.
 * Supports glob patterns (e.g. {@code *.jar}, {@code clangd*}) in any path segment.
 *
 * <pre>
 * "fileExists": {
 *   "name": "Check if server is installed",
 *   "file": "$USER_HOME$/.lsp4ij/lsp/clojure-lsp/clojure-lsp"
 * }
 * </pre>
 */
public class FileExistsTask extends InstallerTask {

    private final @NotNull String file;

    public FileExistsTask(@Nullable String id,
                          @Nullable String name,
                          @Nullable InstallerTask onFail,
                          @Nullable InstallerTask onSuccess,
                          @NotNull String file,
                          @NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        super(id, name, onFail, onSuccess, serverInstallerDescriptor);
        this.file = file;
    }

    @Override
    public boolean run(@NotNull InstallerContext context) {
        // Resolve ${key} template variables
        String resolvedPath = context.resolveValues(file);
        // Resolve $USER_HOME$, $PROJECT_DIR$ and other IntelliJ macros
        resolvedPath = CommandUtils.resolveCommandLine(resolvedPath, context.getProject());

        boolean exists;

        if (resolvedPath.contains("*") || resolvedPath.contains("?")) {
            exists = matchesGlob(resolvedPath);
        } else {
            exists = Files.isRegularFile(Paths.get(resolvedPath));
        }

        if (exists) {
            context.print("File exists: " + resolvedPath);
        } else {
            context.print("File not found: " + resolvedPath);
        }

        return exists;
    }

    private static boolean matchesGlob(@NotNull String globPath) {
        String normalized = globPath.replace('\\', '/');

        // Find the position of the first glob character
        int firstGlobPos = -1;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '*' || c == '?') {
                firstGlobPos = i;
                break;
            }
        }
        if (firstGlobPos < 0) {
            return false;
        }

        // Split into base directory (before glob) and glob pattern (from glob segment onwards)
        int baseDirEnd = normalized.lastIndexOf('/', firstGlobPos);
        if (baseDirEnd < 0) {
            return false;
        }

        Path baseDir = Paths.get(normalized.substring(0, baseDirEnd));
        String globPattern = normalized.substring(baseDirEnd + 1);

        if (!Files.isDirectory(baseDir)) {
            return false;
        }

        // ** can match any number of directories, so don't limit depth
        int depth = globPattern.contains("**")
                ? Integer.MAX_VALUE
                : (int) globPattern.chars().filter(c -> c == '/').count() + 1;

        try (Stream<Path> files = Files.walk(baseDir, depth)) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
            return files
                    .filter(p -> !p.equals(baseDir))
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> {
                        Path relative = baseDir.relativize(p);
                        // Normalize to forward slashes for consistent matching
                        String relativePath = relative.toString().replace('\\', '/');
                        return matcher.matches(Paths.get(relativePath));
                    });
        } catch (IOException | UncheckedIOException | PatternSyntaxException e) {
            return false;
        }
    }
}
