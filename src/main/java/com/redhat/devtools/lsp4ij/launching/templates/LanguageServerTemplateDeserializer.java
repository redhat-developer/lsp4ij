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
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.server.definition.launching.TransportSupport;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import com.redhat.devtools.lsp4ij.templates.ServerTemplateJsonDeserializer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        String workingDir = JSONUtils.getString(jsonObject, WORKING_DIR_JSON_PROPERTY);
        if (workingDir != null) {
            languageServerTemplate.setWorkingDir(workingDir);
        }
        languageServerTemplate.setEnvData(deserializeEnvData(jsonObject));
        // What can't be used is reported, instead of a silent fallback
        var problems = new ArrayList<String>();
        languageServerTemplate.setTransport(deserializeTransport(jsonObject, problems));
        languageServerTemplate.setSupportedTransports(deserializeSupportedTransports(jsonObject, problems));
        languageServerTemplate.setProblems(problems);

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

    /**
     * Returns the transport of the "socket" object of the template, and the standard input and output without one.
     * A mode which isn't known, or none, is the standard input and output, and a port which isn't a number from 0 to
     * 65535 lets LSP4IJ choose a free one; each is added to the given problems.
     */
    private static @NotNull UserDefinedTransport deserializeTransport(@NotNull JsonObject jsonObject,
                                                                     @NotNull List<String> problems) {
        JsonObject socket = JSONUtils.getJsonObject(jsonObject, SOCKET_JSON_PROPERTY);
        if (socket == null) {
            return UserDefinedTransport.STDIO;
        }
        String modeId = JSONUtils.getString(socket, SOCKET_MODE_JSON_PROPERTY);
        var mode = UserDefinedTransport.Mode.fromId(modeId);
        if (modeId == null) {
            problems.add(LanguageServerBundle.message("language.server.template.socket.mode.missing"));
        } else if (!mode.getId().equals(modeId)) {
            problems.add(LanguageServerBundle.message("language.server.template.socket.mode", modeId));
        }
        return UserDefinedTransport.of(mode, JSONUtils.getString(socket, SOCKET_HOST_JSON_PROPERTY), port(socket, problems));
    }

    /**
     * Returns the port of the "socket" object, and 0, which lets LSP4IJ choose a free one, without a port.
     */
    private static int port(@NotNull JsonObject socket, @NotNull List<String> problems) {
        if (!socket.has(SOCKET_PORT_JSON_PROPERTY)) {
            return 0;
        }
        Integer port = JSONUtils.getInteger(socket, SOCKET_PORT_JSON_PROPERTY);
        if (port == null || port < 0 || port > 65_535) {
            problems.add(LanguageServerBundle.message("language.server.template.socket.port", socket.get(SOCKET_PORT_JSON_PROPERTY)));
            return 0;
        }
        return port;
    }

    /**
     * Returns the transports of the "transports" array of the template, and none without the array: the template
     * doesn't say. An entry of a mode which isn't known is skipped, and added to the given problems.
     */
    private static @NotNull List<TransportSupport> deserializeSupportedTransports(@NotNull JsonObject jsonObject,
                                                                                @NotNull List<String> problems) {
        JsonArray transports = JSONUtils.getJsonArray(jsonObject, TRANSPORTS_JSON_PROPERTY);
        if (transports == null) {
            return List.of();
        }
        return transports.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .flatMap(transport -> {
                    String id = JSONUtils.getString(transport, TRANSPORT_MODE_JSON_PROPERTY);
                    var mode = UserDefinedTransport.Mode.fromId(id);
                    // fromId reads an unknown mode as stdio
                    if (!mode.getId().equals(id)) {
                        problems.add(LanguageServerBundle.message("language.server.template.transports.mode", id));
                        return Stream.empty();
                    }
                    return Stream.of(new TransportSupport(mode, JSONUtils.getString(transport, TRANSPORT_ARGUMENT_JSON_PROPERTY)));
                })
                .toList();
    }

    @Override
    protected LanguageServerTemplate createServerTemplateInstance() {
        return new LanguageServerTemplate();
    }
}