/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.templates;

import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.internal.IntelliJPlatformUtils;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Abstract class for LSP/DAP server template manager.
 */
public abstract class ServerTemplateManager<T extends ServerTemplate> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTemplateManager.class);

    private final @NotNull String templatesDir;
    private final @NotNull List<T> templates = new ArrayList<>();

    /**
     * Loads templates from resources/{lsp|dap}templates
     */
    protected ServerTemplateManager(@NotNull String templatesDir) {
        this.templatesDir = templatesDir;
        VirtualFile templateRoot = getTemplateRoot();
        if (templateRoot != null) {
            for (VirtualFile templateDir : templateRoot.getChildren()) {
                if (templateDir.isDirectory()) {
                    try {
                        T template = importServerTemplate(templateDir);
                        if (template != null) {
                            if (IntelliJPlatformUtils.isDevMode() || !template.isDev()) {
                                templates.add(template);
                            }
                        } else {
                            LOGGER.warn("No template found in {}", templateDir);
                        }
                    } catch (IOException ex) {
                        LOGGER.warn(ex.getLocalizedMessage(), ex);
                    }
                }
            }
        } else {
            LOGGER.warn("No templateRoot found, no templates ");
        }
        // Sort templates by name
        templates.sort(Comparator.comparing(ServerTemplate::getName));
    }

    /**
     * Load the resources/templates file as a VirtualFile from the jar
     *
     * @return template root as virtual file or null, if one couldn't be found
     */
    @Nullable
    public VirtualFile getTemplateRoot() {
        URL url = LanguageServerTemplateManager.class.getClassLoader().getResource(templatesDir);
        if (url == null) {
            LOGGER.warn("No {} directory/url found", templatesDir);
            return null;
        }
        try {
            // url looks like jar:file:/Users/username/Library/Application%20Support/JetBrains/IDEVersion/plugins/LSP4IJ/lib/instrumented-lsp4ij-version.jar!/templates
            String filePart = url.toURI().getRawSchemeSpecificPart(); // get un-decoded, URI compatible part
            // filePart looks like file:/Users/username/Library/Application%20Support/JetBrains/IDEVersion/plugins/LSP4IJ/lib/instrumented-lsp4ij-version.jar!/templates
            LOGGER.debug("Templates filePart : {}", filePart);
            String resourcePath = new URI(filePart).getSchemeSpecificPart();// get decoded part (i.e. converts %20 to spaces ...)
            // resourcePath looks like /Users/username/Library/Application Support/JetBrains/IDEVersion/plugins/LSP4IJ/lib/instrumented-lsp4ij-version.jar!/templates/
            LOGGER.debug("Templates resources path from uri : {}", resourcePath);
            return JarFileSystem.getInstance().findFileByPath(resourcePath);
        } catch (URISyntaxException e) {
            LOGGER.warn(e.getMessage());
        }
        return null;
    }

    public @NotNull List<T> getTemplates() {
        return templates;
    }


    @Nullable
    public abstract T importServerTemplate(@NotNull VirtualFile templateFolder) throws IOException;

    public @Nullable T findTemplateById(@NotNull String templateId) {
        return templates
                .stream()
                .filter(t -> templateId.equals(t.getId()))
                .findFirst()
                .orElse(null);
    }
}
