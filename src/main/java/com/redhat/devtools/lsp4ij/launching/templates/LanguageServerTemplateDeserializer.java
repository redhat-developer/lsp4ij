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

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate.*;

public class LanguageServerTemplateDeserializer implements JsonDeserializer<LanguageServerTemplate> {
    @Override
    public LanguageServerTemplate deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        LanguageServerTemplate languageServerTemplate = new LanguageServerTemplate();

        var id = jsonObject.get(ID_JSON_PROPERTY);
        languageServerTemplate.setId(id != null ? id.getAsString() : null);
        languageServerTemplate.setName(jsonObject.get(NAME_JSON_PROPERTY).getAsString());
        JsonObject programArgs = jsonObject.get(PROGRAM_ARGS_JSON_PROPERTY).getAsJsonObject();
        if (programArgs != null) {
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> programArgsMap = gson.fromJson(programArgs, mapType);
            languageServerTemplate.setProgramArgs(programArgsMap);
        }
        JsonArray fileTypeMappings = jsonObject.getAsJsonArray(FILE_TYPE_MAPPINGS_JSON_PROPERTY);
        if (fileTypeMappings != null && !fileTypeMappings.isEmpty()) {
            for (JsonElement ftm : fileTypeMappings) {
                String languageId = ftm.getAsJsonObject().get(LANGUAGE_ID_JSON_PROPERTY) != null ? ftm.getAsJsonObject().get(LANGUAGE_ID_JSON_PROPERTY).getAsString() : null;
                List<String> patterns = new ArrayList<>();
                JsonObject fileType = ftm.getAsJsonObject().getAsJsonObject(FILE_TYPE_JSON_PROPERTY);
                JsonArray patternArray = fileType.getAsJsonArray(PATTERNS_JSON_PROPERTY);
                if (patternArray != null) {
                    for (JsonElement pattern : patternArray) {
                        patterns.add(pattern.getAsString());
                    }
                }
                if (!patterns.isEmpty() && languageId != null) {
                    languageServerTemplate.addFileTypeMapping(ServerMappingSettings.createFileNamePatternsMappingSettings(patterns, languageId));
                }

                JsonElement language = fileType.get(NAME_JSON_PROPERTY);
                if (language != null && languageId != null) {
                    languageServerTemplate.addFileTypeMapping(ServerMappingSettings.createFileTypeMappingSettings(language.getAsString(), languageId));
                }
            }
        }
        JsonArray languageMappings = jsonObject.getAsJsonArray(LANGUAGE_MAPPINGS_JSON_PROPERTY);
        if (languageMappings != null && !languageMappings.isEmpty()) {
            for (JsonElement language : languageMappings) {
                String lang = language.getAsJsonObject().get(LANGUAGE_JSON_PROPERTY) != null ? language.getAsJsonObject().get(LANGUAGE_JSON_PROPERTY).getAsString() : null;
                String languageId = language.getAsJsonObject().get(LANGUAGE_ID_JSON_PROPERTY) != null ? language.getAsJsonObject().get(LANGUAGE_ID_JSON_PROPERTY).getAsString() : null;
                if (lang != null && languageId != null) {
                    languageServerTemplate.addLanguageMapping(ServerMappingSettings.createLanguageMappingSettings(lang, languageId));
                }
            }
        }

        return languageServerTemplate;
    }
}