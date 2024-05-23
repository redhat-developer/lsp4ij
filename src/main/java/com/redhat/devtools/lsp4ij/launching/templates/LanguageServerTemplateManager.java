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
package com.redhat.devtools.lsp4ij.launching.templates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.internal.IntelliJPlatformUtils;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.List;

import static com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate.*;

/**
 * Language server template manager.
 */
public class LanguageServerTemplateManager {
    public static final String LANGUAGE_ID = "languageId";
    public static final String FILE_TYPE = "fileType";
    public static final String DEFAULT = "default";
    public static final String PROGRAM_ARGS = "programArgs";
    public static final String LANGUAGE = "language";
    public static final String LANGUAGE_MAPPINGS = "languageMappings";
    public static final String PATTERNS = "patterns";
    public static final String FILE_TYPE_MAPPINGS = "fileTypeMappings";
    public static final String NAME = "name";


    private final static Logger LOGGER = LoggerFactory.getLogger(LanguageServerTemplateManager.class);

    private static final String TEMPLATES_DIR = "templates";

    private final List<LanguageServerTemplate> templates;

    private String lsName;

    public static LanguageServerTemplateManager getInstance() {
        return ApplicationManager.getApplication().getService(LanguageServerTemplateManager.class);
    }

    // TODO: This should not be necessary anymore. Loading serves from folders
    private LanguageServerTemplateManager() {
        LanguageServerTemplates root = null;
        if (root != null) {
            templates = root.getTemplates()
                    .stream()
                    .filter(t -> IntelliJPlatformUtils.isDevMode() || !t.isDev())
                    .toList();
        } else {
            templates = Collections.emptyList();
        }
    }

    public List<LanguageServerTemplate> getTemplates() {
        return templates;
    }

    @Nullable
    static Reader loadTemplateReader(@NotNull String path) {
        var is = LanguageServerTemplateManager.class.getClassLoader().getResourceAsStream(TEMPLATES_DIR + "/" + path);
        return is !=null? new InputStreamReader(new BufferedInputStream(is)) : null;
    }

    @Nullable
    public LanguageServerTemplate importLsTemplate(@NotNull VirtualFile templateFolder) {
        try {
            return createLsTemplate(templateFolder);
        } catch (IOException ex) {
            LOGGER.warn(ex.getLocalizedMessage(), ex);
        }
        return null;
    }

    @Nullable
    public LanguageServerTemplate createLsTemplate(@NotNull VirtualFile templateFolder) throws IOException {
        String templateJson = null;
        String settingsJson = null;
        String initializationOptionsJson = null;
        String description = null;

        for (VirtualFile file : templateFolder.getChildren()) {
            if (file.isDirectory()) {
                continue;
            }
            switch (file.getName()) {
                case TEMPLATE:
                    templateJson = VfsUtilCore.loadText(file);
                    break;
                case SETTINGS:
                    settingsJson = VfsUtilCore.loadText(file);
                    break;
                case INITIALIZATION_OPTIONS:
                    initializationOptionsJson = VfsUtilCore.loadText(file);
                    break;
                case README:
                    description = VfsUtilCore.loadText(file);
                    break;
                default:
                    break;
            }
        }

        if (templateJson == null) {
            // Don't continue, if no template.json is found
            return null;
        }
        if (settingsJson == null) {
            settingsJson = "{}";
        }
        if (initializationOptionsJson == null) {
            initializationOptionsJson = "{}";
        }

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(LanguageServerTemplate.class, new LanguageServerTemplateDeserializer());
        Gson gson = builder.create();

        LanguageServerTemplate template = gson.fromJson(templateJson, LanguageServerTemplate.class);
        template.setConfiguration(settingsJson);
        template.setInitializationOptions(initializationOptionsJson);
        if (StringUtils.isNotBlank(description)) {
            template.setDescription(description);
        }

        return template;
    }

    public void exportLsTemplates(@NotNull VirtualFile exportZip, @NotNull List<LanguageServerDefinition> lsDefinitions) {
        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                exportZip.setBinaryContent(createZipFromLanguageServers(lsDefinitions));
            } catch (IOException ex) {
                LOGGER.warn(ex.getLocalizedMessage(), ex);
            }
        });
    }

    /**
     * Creates a zip file by handling each user defined language server definitions
     * @return zip file as a byte array
     */
    private byte[] createZipFromLanguageServers(@NotNull List<LanguageServerDefinition> lsDefinitions) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (LanguageServerDefinition lsDefinition : lsDefinitions) {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(UserDefinedLanguageServerDefinition.class, new LanguageServerDefinitionSerializer())
                    .setPrettyPrinting()
                    .create();
            String template = gson.toJson(lsDefinition);
            String initializationOptions = ((UserDefinedLanguageServerDefinition) lsDefinition).getInitializationOptionsContent();
            String settings = ((UserDefinedLanguageServerDefinition) lsDefinition).getConfigurationContent();
            lsName = lsDefinition.getDisplayName();

            writeToZip(TEMPLATE, template, zos);
            writeToZip(INITIALIZATION_OPTIONS, initializationOptions, zos);
            writeToZip(SETTINGS, settings, zos);
            zos.closeEntry();
        }

        zos.close();
        return baos.toByteArray();
    }

    /**
     * Writes a file (name + content) to a zip output stream
     * @param filename name of the file to write
     * @param content file content
     * @param zos to write the file to
     */
    private void writeToZip(String filename, String content, ZipOutputStream zos) throws IOException {
        if (StringUtils.isBlank(content)) {
            content = "{}";
        }

        ZipEntry entry = new ZipEntry(lsName + "/" + filename);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}