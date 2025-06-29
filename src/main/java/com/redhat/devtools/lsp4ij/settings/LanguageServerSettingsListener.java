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
public interface LanguageServerSettingsListener {

    record LanguageServerSettingsChangedEvent(@NotNull String languageServerId,
                                              @NotNull LanguageServerSettings.LanguageServerDefinitionSettings settings,
                                              boolean configurationContentChanged,
                                              boolean expandConfigurationChanged,
                                              boolean configurationSchemaContentChanged,
                                              boolean initializationOptionsContentChanged,
                                              boolean experimentalContentChanged,
                                              boolean debugPortChanged,
                                              boolean debugSuspendChanged,
                                              boolean errorReportingKindChanged,
                                              boolean serverTraceChanged,
                                              boolean useIntegerIdsChanged) {
        }

    void handleChanged(@NotNull LanguageServerSettingsListener.LanguageServerSettingsChangedEvent event);
}
