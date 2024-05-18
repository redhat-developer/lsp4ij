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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LanguageServerDefinitionSerializer implements JsonSerializer<UserDefinedLanguageServerDefinition> {
    @Override
    public JsonElement serialize(UserDefinedLanguageServerDefinition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject userDefinedLSDefinitionJson = new JsonObject();
        userDefinedLSDefinitionJson.addProperty("name", src.getDisplayName());

        // User defined ls only supports a single command, defined as default
        JsonObject programArgs = new JsonObject();
        programArgs.addProperty("default", src.getCommandLine());
        userDefinedLSDefinitionJson.add("programArgs", programArgs);

        JsonArray languageMappings = new JsonArray();
        for (var languageEntry : src.getLanguageMappings().entrySet()) {
            JsonObject language = new JsonObject();
            language.addProperty("language", languageEntry.getKey().getID());
            String languageId = languageEntry.getValue();
            if (languageId != null) {
                language.addProperty("languageId", languageEntry.getValue());
            }

            languageMappings.add(language);
        }
        if (!languageMappings.isEmpty()) {
            userDefinedLSDefinitionJson.add("languageMappings", languageMappings);
        }

        JsonArray fileTypeMappings = new JsonArray();
        for (var fileTypeEntry : src.getFileTypeMappings().entrySet()) {
            JsonObject fileTypeContainer = new JsonObject();
            JsonObject fileType = new JsonObject();
            fileType.addProperty("name", fileTypeEntry.getKey().getName());
            fileTypeContainer.add("fileType", fileType);
            fileTypeContainer.addProperty("languageId", fileTypeEntry.getValue());
            fileTypeMappings.add(fileTypeContainer);
        }

        for (Pair<List<FileNameMatcher>, String> filenameMatcherEntry : src.getFilenameMatcherMappings()) {
            if (fileTypeMappings.isEmpty()) {
                boolean exists = false;
                for (JsonElement fileType : fileTypeMappings.asList()) {
                    if (fileType.getAsJsonObject().get("fileType").getAsJsonObject().get("languageId").toString().equals(filenameMatcherEntry.getSecond())) {
                        var fileTypeElement = fileType.getAsJsonObject();
                        exists = true;
                        JsonObject filenameMatcher = new JsonObject();
                        JsonArray patterns = new JsonArray();
                        for (var fNameMatcher : filenameMatcherEntry.getFirst()) {
                            patterns.add(fNameMatcher.getPresentableString());
                        }
                        fileTypeElement.add("patterns", patterns);
                        filenameMatcher.addProperty("languageId", filenameMatcherEntry.getSecond());
                    }
                }

                if (!exists) {
                    JsonObject fileTypeContainer = new JsonObject();
                    JsonObject fileType = new JsonObject();
                    JsonArray patterns = new JsonArray();
                    for (var fNameMatcher : filenameMatcherEntry.getFirst()) {
                        patterns.add(fNameMatcher.getPresentableString());
                    }
                    fileType.add("patterns", patterns);
                    fileTypeContainer.add("fileType", fileType);
                    fileTypeContainer.addProperty("languageId", filenameMatcherEntry.getSecond());
                    fileTypeMappings.add(fileTypeContainer);
                }
            } else {
                JsonObject fileTypeContainer = new JsonObject();
                JsonObject fileType = new JsonObject();
                JsonArray patterns = new JsonArray();
                for (var fNameMatcher : filenameMatcherEntry.getFirst()) {
                    patterns.add(fNameMatcher.getPresentableString());
                }
                fileType.add("patterns", patterns);
                fileTypeContainer.add("fileType", fileType);
                fileTypeContainer.addProperty("languageId", filenameMatcherEntry.getSecond());
                fileTypeMappings.add(fileTypeContainer);
            }
        }
        if (!fileTypeMappings.isEmpty()) {
            userDefinedLSDefinitionJson.add("fileTypeMappings", fileTypeMappings);
        }

        return userDefinedLSDefinitionJson;
    }
}