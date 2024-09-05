/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.telemetry;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.redhat.devtools.intellij.telemetry.core.service.TelemetryMessageBuilder;
import com.redhat.devtools.intellij.telemetry.core.util.Lazy;
import com.redhat.devtools.lsp4ij.telemetry.TelemetryEventName;
import com.redhat.devtools.lsp4ij.telemetry.TelemetryService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Red Hat Telemetry Service, used when <a href="https://github.com/redhat-developer/intellij-redhat-telemetry">intellij-redhat-telemetry</a> is available
 */
public class RedHatTelemetryService implements TelemetryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedHatTelemetryService.class);

    private final Lazy<TelemetryMessageBuilder> builder = new Lazy<>(() -> new TelemetryMessageBuilder(PluginManager.getPluginByClass(this.getClass())));
    private boolean hasError;

    /**
     * Sends a tracking event with additional properties.
     * @param eventName the name of the event
     * @param properties the properties of the event and null otherwise
     * @param error the error of the event and null otherwise
     */
    public void send(@NotNull TelemetryEventName eventName,
                     @Nullable Map<String, String> properties,
                     @Nullable Exception error) {
        TelemetryMessageBuilder.ActionMessage action = action(eventName, properties, error);
        if (action == null) {
            return;
        }
        asyncSend(action);
    }

    @Nullable
    private TelemetryMessageBuilder.ActionMessage action(@NotNull TelemetryEventName eventName,
                                                         @Nullable Map<String, String> properties,
                                                         @Nullable Exception error) {
        TelemetryMessageBuilder builder = getMessageBuilder();
        if (builder == null) {
            return null;
        }
        try {
            TelemetryMessageBuilder.ActionMessage action = error != null ?
                    builder.action(eventName.getEventName()).error(error) :
                    builder.action(eventName.getEventName());
            if (properties != null) {
                properties.forEach((k, v) -> action.property(k, v));
            }
            return action;
        }
        catch(Exception e) {
            LOGGER.warn("Error while creating telemetry message.", e);
            return null;
        }
    }

    @Nullable
    private TelemetryMessageBuilder getMessageBuilder() {
        if (hasError) {
            return null;
        }
        try {
            return builder.get();
        }
        catch(Exception e) {
            LOGGER.warn("Error while creating TelemetryMessageBuilder instance.", e);
            hasError = true;
            return null;
        }
    }

    private void asyncSend(@NotNull TelemetryMessageBuilder.ActionMessage message) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                message.send();
            } catch (Exception e) {
                LOGGER.warn("Failed to send Telemetry data : {}", e.getMessage());
            }
        });
    }

}
