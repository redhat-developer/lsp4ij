/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.descriptors.templates;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplate.*;

/**
 * DAP template deserializer.
 */
public class DAPTemplateDeserializer implements JsonDeserializer<DAPTemplate> {
    @Override
    public DAPTemplate deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        DAPTemplate dapTemplate = new DAPTemplate();

        dapTemplate.setId(jsonObject.get(ID_JSON_PROPERTY).getAsString());
        dapTemplate.setName(jsonObject.get(NAME_JSON_PROPERTY).getAsString());
        JsonObject programArgs = jsonObject.get(PROGRAM_ARGS_JSON_PROPERTY).getAsJsonObject();
        if (programArgs != null) {
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> programArgsMap = gson.fromJson(programArgs, mapType);
            dapTemplate.setProgramArgs(programArgsMap);
        }

        JsonElement waitForTimeout = jsonObject.get(WAIT_FOR_TIMEOUT_JSON_PROPERTY);
        if (waitForTimeout != null) {
            dapTemplate.setWaitForTimeout(waitForTimeout.getAsString());
        }

        JsonElement waitForTrace = jsonObject.get(WAIT_FOR_TRACE_JSON_PROPERTY);
        if (waitForTrace != null) {
            dapTemplate.setWaitForTrace(waitForTrace.getAsString());
        }

        JsonArray fileTypeMappings = jsonObject.getAsJsonArray(FILE_TYPE_MAPPINGS_JSON_PROPERTY);
        if (fileTypeMappings != null && !fileTypeMappings.isEmpty()) {
            for (JsonElement ftm : fileTypeMappings) {
                List<String> patterns = new ArrayList<>();
                JsonObject fileType = ftm.getAsJsonObject().getAsJsonObject(FILE_TYPE_JSON_PROPERTY);
                JsonArray patternArray = fileType.getAsJsonArray(PATTERNS_JSON_PROPERTY);
                if (patternArray != null) {
                    for (JsonElement pattern : patternArray) {
                        patterns.add(pattern.getAsString());
                    }
                }
                if (!patterns.isEmpty()) {
                    dapTemplate.addFileTypeMapping(ServerMappingSettings.createFileNamePatternsMappingSettings(patterns, null));
                }

                JsonElement language = fileType.get(NAME_JSON_PROPERTY);
                if (language != null) {
                    dapTemplate.addFileTypeMapping(ServerMappingSettings.createFileTypeMappingSettings(language.getAsString(), null));
                }
            }
        }
        JsonArray languageMappings = jsonObject.getAsJsonArray(LANGUAGE_MAPPINGS_JSON_PROPERTY);
        if (languageMappings != null && !languageMappings.isEmpty()) {
            for (JsonElement language : languageMappings) {
                String lang = language.getAsJsonObject().get(LANGUAGE_JSON_PROPERTY) != null ? language.getAsJsonObject().get(LANGUAGE_JSON_PROPERTY).getAsString() : null;
                if (lang != null) {
                    dapTemplate.addLanguageMapping(ServerMappingSettings.createLanguageMappingSettings(lang, null));
                }
            }
        }

        return dapTemplate;
    }
}