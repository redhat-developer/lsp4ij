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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate.*;

/**
 * Language server template manager.
 */
public class LanguageServerTemplateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerTemplateManager.class);

    private static final String TEMPLATES_DIR = "templates";

    private final List<LanguageServerTemplate> templates = new ArrayList<>();

    private String lsName;

    public static LanguageServerTemplateManager getInstance() {
        return ApplicationManager.getApplication().getService(LanguageServerTemplateManager.class);
    }

    /**
     * Loads templates from resources/templates
     */
    private LanguageServerTemplateManager() {
        VirtualFile templateRoot = getTemplateRoot();
        if (templateRoot != null) {
            for (VirtualFile templateDir : templateRoot.getChildren()) {
                if (templateDir.isDirectory()) {
                    try {
                        LanguageServerTemplate template = importLsTemplate(templateDir);
                        if (template != null) {
                            templates.add(template);
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
    }

    /**
     * Load the resources/templates file as a VirtualFile from the jar
     *
     * @return template root as virtual file or null, if one couldn't be found
     */
    @Nullable
    public VirtualFile getTemplateRoot() {
        URL url = LanguageServerTemplateManager.class.getClassLoader().getResource(TEMPLATES_DIR);
        if (url == null) {
            LOGGER.warn("No " + TEMPLATES_DIR + " directory/url found");
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

    public List<LanguageServerTemplate> getTemplates() {
        return templates;
    }

    /**
     * Import language server template from a directory
     *
     * @param templateFolder directory that contains the template files
     * @return LanguageServerTemplate or null if one couldn't be created
     */
    @Nullable
    public LanguageServerTemplate importLsTemplate(@NotNull VirtualFile templateFolder) throws IOException {
        return createLsTemplate(templateFolder);
    }

    /**
     * Parses the template files to create a LanguageServerTemplate
     *
     * @param templateFolder directory that contains the template files
     * @return LanguageServerTemplate or null if one couldn't be created
     * @throws IOException if an IO error occurs when loading the text from any template file
     */
    @Nullable
    public LanguageServerTemplate createLsTemplate(@NotNull VirtualFile templateFolder) throws IOException {
        String templateJson = null;
        String settingsJson = null;
        String settingsSchemaJson = null;
        String initializationOptionsJson = null;
        String clientSettingsJson = null;
        String description = null;

        for (VirtualFile file : templateFolder.getChildren()) {
            if (file.isDirectory()) {
                continue;
            }
            switch (file.getName()) {
                case TEMPLATE_FILE_NAME:
                    templateJson = VfsUtilCore.loadText(file);
                    break;
                case SETTINGS_FILE_NAME:
                    settingsJson = VfsUtilCore.loadText(file);
                    break;
                case SETTINGS_SCHEMA_FILE_NAME:
                    settingsSchemaJson = VfsUtilCore.loadText(file);
                    break;
                case INITIALIZATION_OPTIONS_FILE_NAME:
                    initializationOptionsJson = VfsUtilCore.loadText(file);
                    break;
                case CLIENT_SETTINGS_FILE_NAME:
                    clientSettingsJson = VfsUtilCore.loadText(file);
                    break;
                case README_FILE_NAME:
                    description = VfsUtilCore.loadText(file);
                    break;
                default:
                    break;
            }
        }

        if (templateJson == null) {
            // Don't continue, if no template.json is found
            throw new FileNotFoundException("template.json is required");
        }
        if (settingsJson == null) {
            settingsJson = "{}";
        }
        if (initializationOptionsJson == null) {
            initializationOptionsJson = "{}";
        }
        if (clientSettingsJson == null) {
            clientSettingsJson = "{}";
        }

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(LanguageServerTemplate.class, new LanguageServerTemplateDeserializer());
        Gson gson = builder.create();

        LanguageServerTemplate template = gson.fromJson(templateJson, LanguageServerTemplate.class);
        template.setConfiguration(settingsJson);
        template.setConfigurationSchema(settingsSchemaJson);
        template.setInitializationOptions(initializationOptionsJson);
        template.setClientConfiguration(clientSettingsJson);
        if (StringUtils.isNotBlank(description)) {
            template.setDescription(description);
        }

        return template;
    }

    /**
     * Exports one or more language server templates to a zip file
     *
     * @param exportZip     target zip
     * @param lsDefinitions to export
     */
    public int exportLsTemplates(@NotNull VirtualFile exportZip, @NotNull List<LanguageServerDefinition> lsDefinitions) {
        return ApplicationManager.getApplication().runWriteAction((Computable<Integer>) () -> {
            try {
                SimpleEntry<Integer, byte[]> result = createZipFromLanguageServers(lsDefinitions);
                exportZip.setBinaryContent(result.getValue());
                return result.getKey();
            } catch (IOException ex) {
                LOGGER.warn(ex.getLocalizedMessage(), ex);
                return 0;
            }
        });
    }

    /**
     * Creates a zip file by handling each user defined language server definitions
     *
     * @return zip file as a byte array
     * @throws IOException if an IO error occurs when writing to the zip file
     */
    private SimpleEntry<Integer, byte[]> createZipFromLanguageServers(@NotNull List<LanguageServerDefinition> lsDefinitions) throws IOException {
        Integer count = 0;
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
            String settingsSchema = ((UserDefinedLanguageServerDefinition) lsDefinition).getConfigurationSchemaContent();
            String clientSettings = ((UserDefinedLanguageServerDefinition) lsDefinition).getClientConfigurationContent();
            lsName = lsDefinition.getDisplayName();

            writeToZip(TEMPLATE_FILE_NAME, template, zos);
            writeToZip(INITIALIZATION_OPTIONS_FILE_NAME, initializationOptions, zos);
            writeToZip(SETTINGS_FILE_NAME, settings, zos);
            writeToZip(SETTINGS_SCHEMA_FILE_NAME, settingsSchema, zos);
            writeToZip(CLIENT_SETTINGS_FILE_NAME, clientSettings, zos);
            zos.closeEntry();
            count++;
        }

        zos.close();
        return new SimpleEntry<>(count, baos.toByteArray());
    }

    /**
     * Writes a file (name + content) to a zip output stream
     *
     * @param filename name of the file to write
     * @param content  file content
     * @param zos      to write the file to
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