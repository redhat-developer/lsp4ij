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
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Language-specific client-side settings for a user-defined language server configuration.
 */
public class ClientConfigurationLanguageSettings {
    // Type token to facilitate Gson (de)serialization
    static final Type TYPE_TOKEN = new TypeToken<Map<String, ClientConfigurationLanguageSettings>>() {
    }.getType();

    public boolean caseSensitive = false;
}
