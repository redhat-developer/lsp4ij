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
package com.redhat.devtools.lsp4ij.dap.descriptors.templates;

import static com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplate.ATTACH_FILE_START_NAME;
import static com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplate.LAUNCH_FILE_START_NAME;
import static com.redhat.devtools.lsp4ij.templates.ServerTemplate.INSTALLER_FILE_NAME;
import static com.redhat.devtools.lsp4ij.templates.ServerTemplate.TEMPLATE_FILE_NAME;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
import com.redhat.devtools.lsp4ij.templates.ServerTemplateManager;

/**
 * DAP (Debug Adapter Protocol) template manager.
 */
public class DAPTemplateManager extends ServerTemplateManager<DAPTemplate> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DAPTemplateManager.class);

    private static final String TEMPLATES_DIR = "templates/dap";

    public static DAPTemplateManager getInstance() {
        return ApplicationManager.getApplication().getService(DAPTemplateManager.class);
    }

    /**
     * Loads templates from resources/templates
     */
    private DAPTemplateManager() {
        // Loads templates from resources/dap/templates
        super(TEMPLATES_DIR);
    }

    /**
     * Import DAP template from a directory
     *
     * @param templateFolder directory that contains the template files
     * @return DAPTemplate or null if one couldn't be created
     */
    @Override
    @Nullable
    public DAPTemplate importServerTemplate(@NotNull VirtualFile templateFolder) throws IOException {
        return createDapTemplate(templateFolder);
    }

    /**
     * Parses the template files to create a DAPTemplate
     *
     * @param templateFolder directory that contains the template files
     * @return DAPTemplate or null if one couldn't be created
     * @throws IOException if an IO error occurs when loading the text from any template file
     */
    @Nullable
    public DAPTemplate createDapTemplate(@NotNull VirtualFile templateFolder) throws IOException {
        try {
            String templateJson = null;
            String installerJson = null;
            List<LaunchConfiguration> launchConfigurations = new ArrayList<>();

            for (VirtualFile file : templateFolder.getChildren()) {
                if (file.isDirectory()) {
                    continue;
                }
                String fileName = file.getName();
                if (TEMPLATE_FILE_NAME.equals(fileName)) {
                    templateJson = VfsUtilCore.loadText(file);
                } else if (INSTALLER_FILE_NAME.equals(fileName)) {
                    installerJson = VfsUtilCore.loadText(file);
                } else {
                    DebugMode type = getDebugMode(fileName);
                    if (type != null) {
                        String launchJson = VfsUtilCore.loadText(file);
                        JsonObject launch = (JsonObject) JsonParser.parseString(launchJson);
                        JsonElement jsonName = launch.get("name");
                        String name = jsonName != null ? jsonName.getAsString() : "undefined";
                        LaunchConfiguration launchConfiguration = new LaunchConfiguration(fileName, name, launchJson, type);
                        launchConfigurations.add(launchConfiguration);
                    }
                }
            }

            if (templateJson == null) {
                // Don't continue, if no template.json is found
                throw new FileNotFoundException("template.json is required");
            }

            GsonBuilder builder = new GsonBuilder();
            builder.registerTypeAdapter(DAPTemplate.class, new DAPTemplateDeserializer());
            Gson gson = builder.create();

            DAPTemplate template = gson.fromJson(templateJson, DAPTemplate.class);
            template.setLaunchConfigurations(launchConfigurations);
            template.setInstallerConfiguration(installerJson);
            return template;
        } catch (Exception e) {
            LOGGER.warn("Error while loading DAP template", e);
            return null;
        }
    }

    @Nullable
    private DebugMode getDebugMode(String fileName) {
        if (fileName.startsWith(LAUNCH_FILE_START_NAME)) {
            return DebugMode.LAUNCH;
        }
        if (fileName.startsWith(ATTACH_FILE_START_NAME)) {
            return DebugMode.ATTACH;
        }
        return null;
    }

    /**
     * Exports one or more DAP templates to a zip file
     *
     * @param exportZip     target zip
     * @param lsDefinitions to export
     */
/*    public int exportDapTemplates(@NotNull VirtualFile exportZip, @NotNull List<DAPDefinition> lsDefinitions) {
        return ApplicationManager.getApplication().runWriteAction((Computable<Integer>) () -> {
            try {
                SimpleEntry<Integer, byte[]> result = createZipFromDAPs(lsDefinitions);
                exportZip.setBinaryContent(result.getValue());
                return result.getKey();
            } catch (IOException ex) {
                LOGGER.warn(ex.getLocalizedMessage(), ex);
                return 0;
            }
        });
    }
*/
    /**
     * Creates a zip file by handling each user defined DAP definitions
     *
     * @return zip file as a byte array
     * @throws IOException if an IO error occurs when writing to the zip file
     */
  /*  private SimpleEntry<Integer, byte[]> createZipFromDAPs(@NotNull List<DAPDefinition> lsDefinitions) throws IOException {
        Integer count = 0;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (DAPDefinition lsDefinition : lsDefinitions) {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(UserDefinedDAPDefinition.class, new DAPDefinitionSerializer())
                    .setPrettyPrinting()
                    .create();
            String template = gson.toJson(lsDefinition);
            String initializationOptions = ((UserDefinedDAPDefinition) lsDefinition).getInitializationOptionsContent();
            String settings = ((UserDefinedDAPDefinition) lsDefinition).getConfigurationContent();
            String settingsSchema = ((UserDefinedDAPDefinition) lsDefinition).getConfigurationSchemaContent();
            String clientSettings = ((UserDefinedDAPDefinition) lsDefinition).getClientConfigurationContent();
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
*/
    /**
     * Writes a file (name + content) to a zip output stream
     *
     * @param filename name of the file to write
     * @param content  file content
     * @param zos      to write the file to
     */
  /*  private void writeToZip(String filename, String content, ZipOutputStream zos) throws IOException {
        if (StringUtils.isBlank(content)) {
            content = "{}";
        }

        ZipEntry entry = new ZipEntry(lsName + "/" + filename);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }*/
}