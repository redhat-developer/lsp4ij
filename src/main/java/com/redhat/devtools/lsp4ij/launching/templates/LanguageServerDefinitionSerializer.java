/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mitja Leino <mitja.leino@hotmail.com> - Initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.templates;

import com.google.gson.*;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.util.Pair;
import com.redhat.devtools.lsp4ij.server.definition.launching.TransportSupport;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.List;

import static com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate.*;

public class LanguageServerDefinitionSerializer implements JsonSerializer<UserDefinedLanguageServerDefinition> {
    @Override
    public JsonElement serialize(UserDefinedLanguageServerDefinition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject userDefinedLSDefinitionJson = new JsonObject();
        userDefinedLSDefinitionJson.addProperty(NAME_JSON_PROPERTY, src.getDisplayName());

        // User defined ls only supports a single command, defined as default
        JsonObject programArgs = new JsonObject();
        programArgs.addProperty(DEFAULT_JSON_PROPERTY, src.getCommandLine());
        userDefinedLSDefinitionJson.add(PROGRAM_ARGS_JSON_PROPERTY, programArgs);

        // Working directory
        if (src.getWorkingDir() != null && !src.getWorkingDir().isBlank()) {
            userDefinedLSDefinitionJson.addProperty(WORKING_DIR_JSON_PROPERTY, src.getWorkingDir());
        }

        // Socket, for a language server which LSP4IJ doesn't reach over its standard input and output
        var transport = src.getTransport();
        if (transport.mode() != UserDefinedTransport.Mode.STDIO) {
            JsonObject socket = new JsonObject();
            socket.addProperty(SOCKET_MODE_JSON_PROPERTY, transport.mode().getId());
            if (transport.host() != null) {
                socket.addProperty(SOCKET_HOST_JSON_PROPERTY, transport.host());
            }
            socket.addProperty(SOCKET_PORT_JSON_PROPERTY, transport.port());
            userDefinedLSDefinitionJson.add(SOCKET_JSON_PROPERTY, socket);
        }

        // Transports: the language server keeps only its own, so these are those its template lists, which its
        // settings apply too
        var supportedTransports = getSupportedTransports(src);
        if (!supportedTransports.isEmpty()) {
            JsonArray transports = new JsonArray();
            for (var supported : supportedTransports) {
                JsonObject transportJson = new JsonObject();
                transportJson.addProperty(TRANSPORT_MODE_JSON_PROPERTY, supported.mode().getId());
                if (supported.argument() != null) {
                    transportJson.addProperty(TRANSPORT_ARGUMENT_JSON_PROPERTY, supported.argument());
                }
                transports.add(transportJson);
            }
            userDefinedLSDefinitionJson.add(TRANSPORTS_JSON_PROPERTY, transports);
        }

        JsonArray languageMappings = new JsonArray();
        for (var languageEntry : src.getLanguageMappings().entrySet()) {
            JsonObject language = new JsonObject();
            language.addProperty(LANGUAGE_JSON_PROPERTY, languageEntry.getKey().getID());
            String languageId = languageEntry.getValue();
            if (languageId != null) {
                language.addProperty(LANGUAGE_ID_JSON_PROPERTY, languageEntry.getValue());
            }

            languageMappings.add(language);
        }
        if (!languageMappings.isEmpty()) {
            userDefinedLSDefinitionJson.add(LANGUAGE_MAPPINGS_JSON_PROPERTY, languageMappings);
        }

        JsonArray fileTypeMappings = getJsonElements(src);

        for (Pair<List<FileNameMatcher>, String> filenameMatcherEntry : src.getFilenameMatcherMappings()) {
            if (fileTypeMappings.isEmpty()) {
                boolean exists = false;
                for (JsonElement fileType : fileTypeMappings.asList()) {
                    if (fileType.getAsJsonObject().get(FILE_TYPE_JSON_PROPERTY).getAsJsonObject().get(LANGUAGE_ID_JSON_PROPERTY).toString().equals(filenameMatcherEntry.getSecond())) {
                        exists = true;

                        var fileTypeElement = fileType.getAsJsonObject();
                        JsonObject filenameMatcher = new JsonObject();
                        JsonArray patterns = new JsonArray();
                        for (var fNameMatcher : filenameMatcherEntry.getFirst()) {
                            patterns.add(fNameMatcher.getPresentableString());
                        }
                        fileTypeElement.add(PATTERNS_JSON_PROPERTY, patterns);
                        filenameMatcher.addProperty(LANGUAGE_ID_JSON_PROPERTY, filenameMatcherEntry.getSecond());
                    }
                }

                if (!exists) {
                    addNewFileTypePattern(filenameMatcherEntry, fileTypeMappings);
                }
            } else {
                addNewFileTypePattern(filenameMatcherEntry, fileTypeMappings);
            }
        }
        if (!fileTypeMappings.isEmpty()) {
            userDefinedLSDefinitionJson.add(FILE_TYPE_MAPPINGS_JSON_PROPERTY, fileTypeMappings);
        }

        return userDefinedLSDefinitionJson;
    }

    /**
     * Returns the transports which the template of the given language server lists. The template is found as the
     * settings of the language server find it, by its id among the bundled templates, so a language server without a
     * template, or whose template LSP4IJ doesn't have, has none.
     */
    private static @NotNull List<TransportSupport> getSupportedTransports(@NotNull UserDefinedLanguageServerDefinition definition) {
        String templateId = definition.getTemplateId();
        if (templateId == null) {
            return List.of();
        }
        var template = LanguageServerTemplateManager.getInstance().findTemplateById(templateId);
        return template != null ? template.getSupportedTransports() : List.of();
    }

    private static @NotNull JsonArray getJsonElements(UserDefinedLanguageServerDefinition src) {
        JsonArray fileTypeMappings = new JsonArray();
        for (var fileTypeEntry : src.getFileTypeMappings().entrySet()) {
            JsonObject fileTypeContainer = new JsonObject();
            JsonObject fileType = new JsonObject();
            fileType.addProperty(NAME_JSON_PROPERTY, fileTypeEntry.getKey().getName());
            fileTypeContainer.add(FILE_TYPE_JSON_PROPERTY, fileType);
            fileTypeContainer.addProperty(LANGUAGE_ID_JSON_PROPERTY, fileTypeEntry.getValue());
            fileTypeMappings.add(fileTypeContainer);
        }
        return fileTypeMappings;
    }

    private void addNewFileTypePattern(Pair<List<FileNameMatcher>, String> filenameMatcherEntry,
                                       JsonArray fileTypeMappings) {
        JsonObject fileTypeContainer = new JsonObject();
        JsonObject fileType = new JsonObject();
        JsonArray patterns = new JsonArray();
        for (var fNameMatcher : filenameMatcherEntry.getFirst()) {
            patterns.add(fNameMatcher.getPresentableString());
        }
        fileType.add(PATTERNS_JSON_PROPERTY, patterns);
        fileTypeContainer.add(FILE_TYPE_JSON_PROPERTY, fileType);
        fileTypeContainer.addProperty(LANGUAGE_ID_JSON_PROPERTY, filenameMatcherEntry.getSecond());
        fileTypeMappings.add(fileTypeContainer);
    }
}