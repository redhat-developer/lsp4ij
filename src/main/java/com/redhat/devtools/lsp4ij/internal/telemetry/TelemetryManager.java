/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import com.redhat.devtools.lsp4ij.server.definition.extension.ExtensionLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.telemetry.TelemetryService;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper to initialize Telemetry. A Telemetry service based on <a href="https://github.com/redhat-developer/intellij-redhat-telemetry">intellij-redhat-telemetry</a>,
 * will be instantiated if that plugin is installed, a No-Op Telemetry stub will be used otherwise.
 */
public class TelemetryManager implements Disposable {

    private final Project project;
    private TelemetryService telemetryService;
    private LanguageServerDefinitionListener telemetryListener;

    public TelemetryManager(Project project){
        this.project = project;
        try {
            Class.forName("com.redhat.devtools.intellij.telemetry.core.service.TelemetryService");
            telemetryService = new RedHatTelemetryService();
        } catch (Exception ignore) {
            telemetryService = new NoOpTelemetryService();
        }
    }

    public static TelemetryManager getInstance(@NotNull Project project) {
        return project.getService(TelemetryManager.class);
    }

    /**
     * Initializes Telemetry by:
     *  <ul>
     *      <li>registering a LanguageServerDefinitionListener to the LanguageServersRegistry,
     *          to report telemetry events for added/removed LanguageServerDefinition.
     *      </li>
     *  </ul>
     */
    public void initialize() {
        telemetryListener = createListener();
        LanguageServersRegistry.getInstance().addLanguageServerDefinitionListener(telemetryListener);
    }

    /**
     * Creates a LanguageServerDefinitionListener that will report telemetry events for added/removed LanguageServerDefinition
     */
    private LanguageServerDefinitionListener createListener() {

        return new LanguageServerDefinitionListener() {

            @Override
            public void handleAdded(@NotNull LanguageServerAddedEvent event) {
                if (project.equals(event.getProject())) {
                    event.serverDefinitions.forEach(sd -> send("lsp.server.added", sd));
                }
            }

            @Override
            public void handleRemoved(@NotNull LanguageServerRemovedEvent event) {
                if (project.equals(event.getProject())) {
                    event.serverDefinitions.forEach(sd -> send("lsp.server.removed", sd));
                }
            }

            @Override
            public void handleChanged(@NotNull LanguageServerChangedEvent event) {
            }

            private void send(String event, LanguageServerDefinition lsDef) {
                Map<String, String> eventProperties;
                if (lsDef instanceof UserDefinedLanguageServerDefinition) {
                    eventProperties = new HashMap<>(2);
                    eventProperties.put("user_defined", "true");
                    eventProperties.put("ls_label", getTemplateNames().contains(lsDef.getDisplayName())? lsDef.getDisplayName(): "User Custom LS");
                } else if (lsDef instanceof ExtensionLanguageServerDefinition) {
                    //Currently can not happen
                    eventProperties = new HashMap<>(2);
                    eventProperties.put("user_defined", "false");
                    eventProperties.put("ls_label", lsDef.getDisplayName());
                } else {
                    return;
                }
                getTelemetryService().send(event, eventProperties);
            }
        };
    }

    public TelemetryService getTelemetryService() {
        return telemetryService;
    }

    private List<String> getTemplateNames() {
        return LanguageServerTemplateManager.getInstance().getTemplates().stream().map(LanguageServerTemplate::getName).toList();
    }

    @Override
    public void dispose() {
        if (telemetryListener != null) {
            LanguageServersRegistry.getInstance().removeLanguageServerDefinitionListener(telemetryListener);
        }
    }
}
