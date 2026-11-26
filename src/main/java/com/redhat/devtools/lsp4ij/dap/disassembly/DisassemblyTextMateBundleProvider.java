/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Angelo Zerr - implementation of DAP disassembly editor support
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.disassembly;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.redhat.devtools.lsp4ij.internal.PluginUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * TextMate bundle provider for Disassembly file.
 */
public class DisassemblyTextMateBundleProvider implements TextMateBundleProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisassemblyTextMateBundleProvider.class);

    private static final String RESOURCE_PATH = "/textmate";
    private static final String BUNDLE_NAME = "disasm";

    @NotNull
    @Override
    public List<PluginBundle> getBundles() {
        Path bundlePath = getBundlePath();
        PluginBundle pluginBundle = new PluginBundle(BUNDLE_NAME, bundlePath);
        return List.of(pluginBundle);
    }

    private Path getBundlePath() {
        try {
            PluginDescriptor plugin = PluginUtils.getPluginDescriptor();
            String version = plugin.getVersion();
            String path = plugin.getPluginPath() + "/bundles/" + version;
            return copyResourceDirectory(path, List.of("package.json", "syntaxes/disassembly.json"));
        } catch (IOException ex) {
            LOGGER.error("Bundles error: " + ex);
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Copies the given resources of the bundle into the target directory, if they aren't there yet.
     *
     * <p>Several processes can copy them at once, such as test JVMs that share the plugin directory, or IDEs that
     * share a plugin installation. So each file is written to a temporary file of its own first, then moved into
     * place in one step: no one reads a part of a file, and a file that another process wrote meanwhile, with the
     * same content, is kept.</p>
     */
    @VisibleForTesting
    static Path copyResourceDirectory(@NotNull String target, List<String> fileNames) throws IOException {
        Path targetPath = Paths.get(target);
        Files.createDirectories(targetPath);
        for (String fileName: fileNames) {
            Path pathToCopyFile = new File(target + "/" + fileName).toPath();
            if (Files.notExists(pathToCopyFile)) {
                var dir = pathToCopyFile.getParent();
                Files.createDirectories(dir);
                Path temporaryFile = Files.createTempFile(dir, pathToCopyFile.getFileName().toString(), ".tmp");
                try {
                    try (InputStream resource = Objects.requireNonNull(
                            DisassemblyTextMateBundleProvider.class.getResourceAsStream(RESOURCE_PATH + "/" + fileName))) {
                        Files.copy(resource, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try {
                        Files.move(temporaryFile, pathToCopyFile, StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException ex) {
                        // Another process moved its copy into place first. On Windows, a file that is open can't
                        // be replaced.
                        if (Files.notExists(pathToCopyFile)) {
                            throw ex;
                        }
                    }
                } finally {
                    Files.deleteIfExists(temporaryFile);
                }
            }
        }
        return targetPath;
    }
}