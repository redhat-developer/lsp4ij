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

import com.google.gson.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.DebuggingType;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplate.*;

/**
 * DAP (Debug Adapter Protocol) template manager.
 */
public class DAPTemplateManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DAPTemplateManager.class);

    private static final String TEMPLATES_DIR = "dap/templates";

    private final List<DAPTemplate> templates = new ArrayList<>();

    public static DAPTemplateManager getInstance() {
        return ApplicationManager.getApplication().getService(DAPTemplateManager.class);
    }

    /**
     * Loads templates from resources/templates
     */
    private DAPTemplateManager() {
        VirtualFile templateRoot = getTemplateRoot();
        if (templateRoot != null) {
            for (VirtualFile templateDir : templateRoot.getChildren()) {
                if (templateDir.isDirectory()) {
                    try {
                        DAPTemplate template = importDapTemplate(templateDir);
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
     * Load the resources/dap/templates file as a VirtualFile from the jar
     *
     * @return template root as virtual file or null, if one couldn't be found
     */
    @Nullable
    public VirtualFile getTemplateRoot() {
        URL url = DAPTemplateManager.class.getClassLoader().getResource(TEMPLATES_DIR);
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

    public List<DAPTemplate> getTemplates() {
        return templates;
    }

    /**
     * Import DAP template from a directory
     *
     * @param templateFolder directory that contains the template files
     * @return DAPTemplate or null if one couldn't be created
     */
    @Nullable
    public DAPTemplate importDapTemplate(@NotNull VirtualFile templateFolder) throws IOException {
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
            List<LaunchConfiguration> launchConfigurations = new ArrayList<>();

            for (VirtualFile file : templateFolder.getChildren()) {
                if (file.isDirectory()) {
                    continue;
                }
                String fileName = file.getName();
                if (TEMPLATE_FILE_NAME.equals(fileName)) {
                    templateJson = VfsUtilCore.loadText(file);
                } else {
                    DebuggingType type = getDebuggingType(fileName);
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
            return template;
        }
        catch(Exception e) {
            LOGGER.warn("Error while loading DAP template", e);
            return null;
        }
    }

    @Nullable
    private DebuggingType getDebuggingType(String fileName) {
        if (fileName.startsWith(LAUNCH_FILE_START_NAME)) {
            return DebuggingType.LAUNCH;
        }
        if (fileName.startsWith(ATTACH_FILE_START_NAME)) {
            return DebuggingType.ATTACH;
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