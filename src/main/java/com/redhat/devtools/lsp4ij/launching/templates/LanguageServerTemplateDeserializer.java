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

import static com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate.*;

public class LanguageServerTemplateDeserializer implements JsonDeserializer<LanguageServerTemplate> {
    @Override
    public LanguageServerTemplate deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        LanguageServerTemplate languageServerTemplate = new LanguageServerTemplate();

        languageServerTemplate.setName(jsonObject.get(NAME).getAsString());
        JsonObject programArgs = jsonObject.get(PROGRAM_ARGS).getAsJsonObject();
        if (programArgs != null) {
            languageServerTemplate.setDefaultProgramArg(programArgs.get(DEFAULT).getAsString());
        }
        JsonArray fileTypeMappings = jsonObject.getAsJsonArray(FILE_TYPE_MAPPINGS);
        if (fileTypeMappings != null && !fileTypeMappings.isEmpty()) {
            for (JsonElement ftm : fileTypeMappings) {
                String languageId = ftm.getAsJsonObject().get(LANGUAGE_ID).getAsString();
                List<String> patterns = new ArrayList<>();
                JsonObject fileType = ftm.getAsJsonObject().getAsJsonObject(FILE_TYPE);
                JsonArray patternArray = fileType.getAsJsonArray(PATTERNS);
                for (JsonElement pattern : patternArray) {
                    patterns.add(pattern.getAsString());
                }
                if (!patterns.isEmpty()) {
                    languageServerTemplate.addFileTypeMapping(ServerMappingSettings.createFileNamePatternsMappingSettings(patterns, languageId));
                }

                JsonElement language = fileType.get(NAME);
                if (language != null) {
                    languageServerTemplate.addFileTypeMapping(ServerMappingSettings.createFileTypeMappingSettings(language.getAsString(), languageId));
                }
            }
        }
        JsonArray languageMappings = jsonObject.getAsJsonArray(LANGUAGE_MAPPINGS);
        if (languageMappings != null && !languageMappings.isEmpty()) {
            for (JsonElement language : languageMappings) {
                String lang = language.getAsJsonObject().get(LANGUAGE).getAsString();
                String languageId = language.getAsJsonObject().get(LANGUAGE_ID).getAsString();
                languageServerTemplate.addLanguageMapping(ServerMappingSettings.createLanguageMappingSettings(lang, languageId));
            }
        }

        return languageServerTemplate;
    }
}