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
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LanguageServerTemplateDeserializer implements JsonDeserializer<LanguageServerTemplate> {
    @Override
    public LanguageServerTemplate deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        LanguageServerTemplate languageServerTemplate = new LanguageServerTemplate();

        languageServerTemplate.setName(jsonObject.get("name").getAsString());
        JsonObject programArgs = jsonObject.get("programArgs").getAsJsonObject();
        if (programArgs != null) {
            languageServerTemplate.setDefaultProgramArg(programArgs.get("default").getAsString());
        }
        JsonArray fileTypeMappings = jsonObject.getAsJsonArray("fileTypeMappings");
        if (fileTypeMappings != null && !fileTypeMappings.isEmpty()) {
            for (JsonElement ftm : fileTypeMappings) {
                String languageId = ftm.getAsJsonObject().get("languageId").getAsString();
                List<String> patterns = new ArrayList<>();
                JsonObject fileType = ftm.getAsJsonObject().getAsJsonObject("fileType");
                JsonArray patternArray = fileType.getAsJsonArray("patterns");
                for (JsonElement pattern : patternArray) {
                    patterns.add(pattern.getAsString());
                }
                if (!patterns.isEmpty()) {
                    languageServerTemplate.addFileTypeMapping(ServerMappingSettings.createFileNamePatternsMappingSettings(patterns, languageId));
                }

                JsonElement language = fileType.get("name");
                if (language != null) {
                    languageServerTemplate.addFileTypeMapping(ServerMappingSettings.createFileTypeMappingSettings(language.getAsString(), languageId));
                }
            }
        }
        JsonArray languageMappings = jsonObject.getAsJsonArray("languageMappings");
        if (languageMappings != null && !languageMappings.isEmpty()) {
            for (JsonElement language : languageMappings) {
                String lang = language.getAsJsonObject().get("language").getAsString();
                String languageId = language.getAsJsonObject().get("languageId").getAsString();
                languageServerTemplate.addLanguageMapping(ServerMappingSettings.createLanguageMappingSettings(lang, languageId));
            }
        }

        return languageServerTemplate;
    }
}