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
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.templates.ServerTemplateJsonDeserializer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate.*;

public class LanguageServerTemplateDeserializer extends ServerTemplateJsonDeserializer<LanguageServerTemplate> {

    private static final @NotNull String BASE_URL = "https://github.com/redhat-developer/lsp4ij/tree/main/docs/user-defined-ls/";

    public LanguageServerTemplateDeserializer() {
        super(BASE_URL);
    }

    @Override
    protected void deserializeCustom(@NotNull LanguageServerTemplate languageServerTemplate,
                                     @NotNull JsonObject jsonObject,
                                     Type type,
                                     JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject programArgs = jsonObject.get(PROGRAM_ARGS_JSON_PROPERTY).getAsJsonObject();
        if (programArgs != null) {
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> programArgsMap = gson.fromJson(programArgs, mapType);
            languageServerTemplate.setProgramArgs(programArgsMap);
        }
        languageServerTemplate.setEnvData(deserializeEnvData(jsonObject));

        boolean expandConfiguration = JSONUtils.getBoolean(jsonObject, EXPAND_CONFIGURATION_JSON_PROPERTY);
        languageServerTemplate.setExpandConfiguration(expandConfiguration);

        var jsonDisablePromotionFor = JSONUtils.getJsonArray(jsonObject, DISABLE_PROMOTION_FOR);
        if (jsonDisablePromotionFor != null) {
            var disablePromotionFor =
                    jsonDisablePromotionFor
                    .asList()
                            .stream().map(JsonElement::getAsString)
                            .collect(Collectors.toSet());
            languageServerTemplate.setDisablePromotionFor(disablePromotionFor);
        }
    }

    @Override
    protected LanguageServerTemplate createServerTemplateInstance() {
        return new LanguageServerTemplate();
    }
}