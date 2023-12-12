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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Server extension point.
 */
public class ServerExtensionPointBean extends BaseKeyedLazyInstance<LanguageServerFactory> {
    public static final ExtensionPointName<ServerExtensionPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.lsp4ij.server");

    /**
     * The language server id used to associate language
     * with 'com.redhat.devtools.lsp4ij.languageMapping' extension point.
     */
    @Attribute("id")
    public String id;

    /**
     * The language server label displayed on the LSP console and Language Servers preferences.
     */
    @Attribute("label")
    public String label;

    /**
     * The language server description displayed on the LSP console and Language Servers preferences.
     */
    @Tag("description")
    public String description;

    /**
     * The resource path of the icon displayed on the LSP console and Language Servers preferences.
     * Language server icons must have the size of 13x13 pixels.
     */
    @Attribute("icon")
    public String icon;

    /**
     * The {@link LanguageServerFactory} implementation used to create connection, language client and server interface.
     */
    @Attribute("factoryClass")
    public String factoryClass;

    /**
     * true if language server is a singleton and false otherwise.
     */
    @Attribute("singleton")
    public boolean singleton;

    /**
     * true if language server supports light edit and false otherwise.
     */
    @Attribute("supportsLightEdit")
    public boolean supportsLightEdit;

    /**
     * Timeout used when all files are closed before stopping the language server.
     */
    @Attribute("lastDocumentDisconnectedTimeout")
    public Integer lastDocumentDisconnectedTimeout;

    @Override
    protected @Nullable String getImplementationClassName() {
        return factoryClass;
    }
}
