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

        // launch
        if (jsonObject.has(LAUNCH_PROPERTY)) {
            JsonObject programArgs = jsonObject.get(LAUNCH_PROPERTY).getAsJsonObject();
            if (programArgs != null) {
                Gson gson = new Gson();
                Type mapType = new TypeToken<Map<String, String>>() {
                }.getType();
                Map<String, String> programArgsMap = gson.fromJson(programArgs, mapType);
                dapTemplate.setProgramArgs(programArgsMap);
            }
        }

        JsonElement connectTimeout = jsonObject.get(CONNECT_TIMEOUT_JSON_PROPERTY);
        if (connectTimeout != null) {
            try {
                dapTemplate.setConnectTimeout(connectTimeout.getAsInt());
            } catch (Exception e) {

            }
        }

        JsonElement debugServerReadyPattern = jsonObject.get(DEBUG_SERVER_READY_PATTERN_JSON_PROPERTY);
        if (debugServerReadyPattern != null) {
            dapTemplate.setDebugServerReadyPattern(debugServerReadyPattern.getAsString());
        }

        // Attach
        JsonElement attach = jsonObject.get(ATTACH_PROPERTY);
        if (attach != null&& attach.isJsonObject()) {
            JsonObject attachObject = (JsonObject) attach;
            if (attachObject.has(ATTACH_ADDRESS_PROPERTY)) {
                dapTemplate.setAttachAddress(attachObject.get(ATTACH_ADDRESS_PROPERTY).getAsString());
            }
            if (attachObject.has(ATTACH_PORT_PROPERTY)) {
                dapTemplate.setAttachPort(attachObject.get(ATTACH_PORT_PROPERTY).getAsString());
            }
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