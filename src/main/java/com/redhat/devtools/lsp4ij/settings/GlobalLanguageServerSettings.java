/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

/**
 * Language server settings for a given Language server definition stored in Global scope.
 *
 * <ul>
 *     <li>Debug port</li>
 *     <li>Suspend and wait for a debugger</li>
 *     <li>Trace LSP requests/responses/notifications</li>
 * </ul>
 */
@State(
        name = "LanguageServerSettingsState",
        storages = @Storage("LanguageServersSettings.xml")
)
public class GlobalLanguageServerSettings extends LanguageServerSettings {

    public static GlobalLanguageServerSettings getInstance() {
        return ApplicationManager.getApplication().getService(GlobalLanguageServerSettings.class);
    }
}
