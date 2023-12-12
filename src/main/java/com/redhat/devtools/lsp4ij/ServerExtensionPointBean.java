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
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Server extension point.
 */
public class ServerExtensionPointBean extends BaseKeyedLazyInstance<StreamConnectionProvider> implements PluginAware {
    public static final ExtensionPointName<ServerExtensionPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.lsp4ij.server");

    @Attribute("id")
    public String id;

    @Attribute("label")
    public String label;

    @Tag("description")
    public String description;

    /**
     * The resource path of the icon displayed on the LSP console and Language Servers preferences.
     * Language server icons must have the size of 13x13 pixels.
     */
    @Attribute("icon")
    public String icon;

    @Attribute("class")
    public String serverImpl;
    private Class<?> serverImplClass;

    @Attribute("clientImpl")
    public String clientImpl;
    private Class clientClass;

    @Attribute("serverInterface")
    public String serverInterface;
    private Class serverClass;

    /**
     *  Valid values are <code>project</code> and <code>application</code><br/>
     *  When <code>project</code> scope is selected, the implementation of {@link StreamConnectionProvider} requires a
     *  constructor with a single {@link com.intellij.openapi.project.Project} parameter
     */
    @Attribute("scope")
    public String scope;

    @Attribute("singleton")
    public boolean singleton;

    @Attribute("supportsLightEdit")
    public boolean supportsLightEdit;

    @Attribute("lastDocumentDisconnectedTimeout")
    public Integer lastDocumentDisconnectedTimeout;

    public Class getClientImpl() throws ClassNotFoundException {
        if (clientClass == null) {
            clientClass = getPluginDescriptor().getPluginClassLoader().loadClass(clientImpl);
        }
        return clientClass;
    }

    public Class getServerImpl() throws ClassNotFoundException {
        if (serverImplClass == null) {
            serverImplClass = getPluginDescriptor().getPluginClassLoader().loadClass(serverImpl);
        }
        return serverImplClass;
    }

    public Class getServerInterface() throws ClassNotFoundException {
        if (serverClass == null) {
            serverClass = getPluginDescriptor().getPluginClassLoader().loadClass(serverInterface);
        }
        return serverClass;
    }

    @Override
    protected @Nullable String getImplementationClassName() {
        return serverImpl;
    }
}
