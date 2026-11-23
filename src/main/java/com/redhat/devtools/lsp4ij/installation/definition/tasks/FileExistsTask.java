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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.NaturalComparator;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Installer task that checks whether a file exists at a given path.
 * Supports glob patterns (e.g. {@code *.jar}, {@code clangd*}) in any path segment.
 * <p>
 * The file it finds is the property {@code ${check.file}} of the tasks that run after it, as its {@code onSuccess}: the
 * path as written in {@code file}, with its {@code ${key}} properties resolved and its macros, as {@code $USER_HOME$},
 * kept, and the segments from the first glob on replaced by those of the file found. Of several files that the glob
 * matches, it is the last in natural order, which compares the numbers in a name by their value, segment by segment:
 * {@code clangd_19.1.10/bin/clangd} after {@code clangd_19.1.2/bin/clangd}. A check which finds no file removes the
 * property.
 *
 * <pre>
 * "fileExists": {
 *   "name": "Check if server is installed",
 *   "file": "$USER_HOME$/.lsp4ij/lsp/clojure-lsp/clojure-lsp"
 * }
 * </pre>
 */
public class FileExistsTask extends InstallerTask {

    /**
     * The property of the file that the check found, as {@code ${check.file}}.
     */
    public static final String CHECK_FILE_PROPERTY = "check.file";

    // The last of several files that a glob matches is the one found, as the highest version of clangd_<version>/
    private static final Comparator<Path> NATURAL_ORDER = ((Comparator<Path>) FileExistsTask::compareNaturally)
            .thenComparing(Comparator.naturalOrder());

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
        String path = context.resolveValues(file);
        // Resolve $USER_HOME$, $PROJECT_DIR$ and other IntelliJ macros
        String resolvedPath = CommandUtils.resolveCommandLine(path, context.getProject());

        String found;
        if (resolvedPath.contains("*") || resolvedPath.contains("?")) {
            found = matchGlob(path, context.getProject());
        } else {
            found = Files.isRegularFile(Paths.get(resolvedPath)) ? path : null;
        }

        if (found != null) {
            context.print("File exists: " + resolvedPath);
            context.putProperty(CHECK_FILE_PROPERTY, found);
            return true;
        }
        context.print("File not found: " + resolvedPath);
        context.removeProperty(CHECK_FILE_PROPERTY);
        return false;
    }

    /**
     * Returns the file that the given glob path matches, as written in the given path, with the segments from the first
     * glob on replaced by those of the file, and the last in natural order of several.
     */
    private static @Nullable String matchGlob(@NotNull String globPath, @Nullable Project project) {
        // Find the position of the first glob character
        int firstGlobPos = -1;
        for (int i = 0; i < globPath.length(); i++) {
            char c = globPath.charAt(i);
            if (c == '*' || c == '?') {
                firstGlobPos = i;
                break;
            }
        }
        if (firstGlobPos < 0) {
            return null;
        }

        // Split into base directory (before glob) and glob pattern (from glob segment onwards)
        int baseDirEnd = Math.max(globPath.lastIndexOf('/', firstGlobPos), globPath.lastIndexOf('\\', firstGlobPos));
        if (baseDirEnd < 0) {
            return null;
        }

        String base = globPath.substring(0, baseDirEnd);
        String separator = globPath.substring(baseDirEnd, baseDirEnd + 1);
        Path baseDir = Paths.get(CommandUtils.resolveCommandLine(base, project).replace('\\', '/'));
        String globPattern = CommandUtils.resolveCommandLine(globPath.substring(baseDirEnd + 1), project)
                .replace('\\', '/');

        if (!Files.isDirectory(baseDir)) {
            return null;
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
                    .map(baseDir::relativize)
                    // Normalize to forward slashes for consistent matching
                    .filter(relative -> matcher.matches(Paths.get(relative.toString().replace('\\', '/'))))
                    .max(NATURAL_ORDER)
                    .map(relative -> base + separator + StreamSupport.stream(relative.spliterator(), false)
                            .map(Path::toString)
                            .collect(Collectors.joining(separator)))
                    .orElse(null);
        } catch (IOException | UncheckedIOException | PatternSyntaxException e) {
            return null;
        }
    }

    private static int compareNaturally(@NotNull Path first, @NotNull Path second) {
        int count = Math.min(first.getNameCount(), second.getNameCount());
        for (int i = 0; i < count; i++) {
            int result = NaturalComparator.INSTANCE.compare(first.getName(i).toString(), second.getName(i).toString());
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(first.getNameCount(), second.getNameCount());
    }
}
