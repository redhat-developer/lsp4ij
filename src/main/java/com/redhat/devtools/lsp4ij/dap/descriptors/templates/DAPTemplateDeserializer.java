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
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.templates.ServerTemplate;
import com.redhat.devtools.lsp4ij.templates.ServerTemplateJsonDeserializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplate.*;

/**
 * DAP template deserializer.
 */
public class DAPTemplateDeserializer extends ServerTemplateJsonDeserializer<DAPTemplate> {

    private static final @NotNull String BASE_URL = "https://github.com/redhat-developer/lsp4ij/tree/main/docs/dap/user-defined-dap/";

    public DAPTemplateDeserializer() {
        super(BASE_URL);
    }

    @Override
    protected void deserializeCustom(@NotNull DAPTemplate dapTemplate,
                                     @NotNull JsonObject jsonObject,
                                     Type type,
                                     JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
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
                // Do nothing
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
    }

    @Override
    protected DAPTemplate createServerTemplateInstance() {
        return new DAPTemplate();
    }

}