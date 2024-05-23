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
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.List;

import static com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate.*;

public class LanguageServerDefinitionSerializer implements JsonSerializer<UserDefinedLanguageServerDefinition> {
    @Override
    public JsonElement serialize(UserDefinedLanguageServerDefinition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject userDefinedLSDefinitionJson = new JsonObject();
        userDefinedLSDefinitionJson.addProperty(NAME, src.getDisplayName());

        // User defined ls only supports a single command, defined as default
        JsonObject programArgs = new JsonObject();
        programArgs.addProperty(DEFAULT, src.getCommandLine());
        userDefinedLSDefinitionJson.add(PROGRAM_ARGS, programArgs);

        JsonArray languageMappings = new JsonArray();
        for (var languageEntry : src.getLanguageMappings().entrySet()) {
            JsonObject language = new JsonObject();
            language.addProperty(LANGUAGE, languageEntry.getKey().getID());
            String languageId = languageEntry.getValue();
            if (languageId != null) {
                language.addProperty(LANGUAGE_ID, languageEntry.getValue());
            }

            languageMappings.add(language);
        }
        if (!languageMappings.isEmpty()) {
            userDefinedLSDefinitionJson.add(LANGUAGE_MAPPINGS, languageMappings);
        }

        JsonArray fileTypeMappings = getJsonElements(src);

        for (Pair<List<FileNameMatcher>, String> filenameMatcherEntry : src.getFilenameMatcherMappings()) {
            if (fileTypeMappings.isEmpty()) {
                boolean exists = false;
                for (JsonElement fileType : fileTypeMappings.asList()) {
                    if (fileType.getAsJsonObject().get(FILE_TYPE).getAsJsonObject().get(LANGUAGE_ID).toString().equals(filenameMatcherEntry.getSecond())) {
                        exists = true;

                        var fileTypeElement = fileType.getAsJsonObject();
                        JsonObject filenameMatcher = new JsonObject();
                        JsonArray patterns = new JsonArray();
                        for (var fNameMatcher : filenameMatcherEntry.getFirst()) {
                            patterns.add(fNameMatcher.getPresentableString());
                        }
                        fileTypeElement.add(PATTERNS, patterns);
                        filenameMatcher.addProperty(LANGUAGE_ID, filenameMatcherEntry.getSecond());
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
            userDefinedLSDefinitionJson.add(FILE_TYPE_MAPPINGS, fileTypeMappings);
        }

        return userDefinedLSDefinitionJson;
    }

    private static @NotNull JsonArray getJsonElements(UserDefinedLanguageServerDefinition src) {
        JsonArray fileTypeMappings = new JsonArray();
        for (var fileTypeEntry : src.getFileTypeMappings().entrySet()) {
            JsonObject fileTypeContainer = new JsonObject();
            JsonObject fileType = new JsonObject();
            fileType.addProperty(NAME, fileTypeEntry.getKey().getName());
            fileTypeContainer.add(FILE_TYPE, fileType);
            fileTypeContainer.addProperty(LANGUAGE_ID, fileTypeEntry.getValue());
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
        fileType.add(PATTERNS, patterns);
        fileTypeContainer.add(FILE_TYPE, fileType);
        fileTypeContainer.addProperty(LANGUAGE_ID, filenameMatcherEntry.getSecond());
        fileTypeMappings.add(fileTypeContainer);
    }
}