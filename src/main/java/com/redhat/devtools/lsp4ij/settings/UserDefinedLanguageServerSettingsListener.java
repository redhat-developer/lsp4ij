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
package com.redhat.devtools.lsp4ij.settings;

import org.jetbrains.annotations.NotNull;

/**
 * Language server settings listener API.
 */
@FunctionalInterface
public interface UserDefinedLanguageServerSettingsListener {

    record LanguageServerSettingsChangedEvent(String languageServerId,
                                              UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings,
                                              boolean debugPortChanged, boolean debugSuspendChanged,
                                              boolean errorReportingKindChanged, boolean serverTraceChanged) {

            public LanguageServerSettingsChangedEvent(@NotNull String languageServerId,
                                                      @NotNull UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings,
                                                      boolean debugPortChanged,
                                                      boolean debugSuspendChanged,
                                                      boolean errorReportingKindChanged,
                                                      boolean serverTraceChanged) {
                this.languageServerId = languageServerId;
                this.settings = settings;
                this.debugPortChanged = debugPortChanged;
                this.debugSuspendChanged = debugSuspendChanged;
                this.errorReportingKindChanged = errorReportingKindChanged;
                this.serverTraceChanged = serverTraceChanged;
            }
        }

    void handleChanged(@NotNull UserDefinedLanguageServerSettingsListener.LanguageServerSettingsChangedEvent event);
}
