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
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.redhat.devtools.lsp4ij.internal.telemetry.TelemetryManager;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Initializes the Telemetry service once the project is opened
 */
public class LSPPostStartupActivity implements ProjectActivity, DumbAware {

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        if (!ApplicationManager.getApplication().isUnitTestMode() && !project.isDisposed()) {
            // On the first project which is loaded
            // we register a LanguageServerDefinitionListener to the LanguageServersRegistry,
            // to report telemetry events for added/removed LanguageServerDefinition.
            TelemetryManager.instance().initialize();
        }
        // Force the load of the user defined language server settings for the given project
        // to avoid initializing it when LSP message are logged (which could block the IJ startup or crash the language server)
        UserDefinedLanguageServerSettings.getInstance(project);
        return null;
    }
}
