/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.definitions.extension;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ResourceBundle;

/**
 * Debug Adapter descriptor factory extension point bean.
 *
 * <pre>
 *   <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
 *     <debugAdapterServer
 *         id="myDAPServer"
 *         name="My DAP Server"
 *         factoryClass="my.dap.server.MyDebugAdapterDescriptorFactory">
 *     <description><![CDATA[
 *      Some description written in HTML Debug Adapter Protocol settings.
 *      ]]>
 *     </description>
 *   </debugAdapterServer>
 * </extensions>
 * </pre>
 */
public class DebugAdapterServerExtensionPointBean extends BaseKeyedLazyInstance<DebugAdapterDescriptorFactory> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugAdapterServerExtensionPointBean.class);

    public static final ExtensionPointName<DebugAdapterServerExtensionPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.lsp4ij.debugAdapterServer");

    /**
     * The DAP server id.
     */
    @Attribute("id")
    @RequiredElement
    private String id;

    /**
     * This attribute specifies the resource bundle that contains the specified {@link #nameKey} / {@link #descriptionKey}.
     * This is another way to specify the {@link #name server label} / {@link #description server description}.
     */
    @Attribute("bundle")
    private String bundle;

    /**
     * This attribute specifies the resource key in the specified {@link #bundle}.
     * This is another way to specify the {@link #name server name}.
     */
    @Attribute("nameKey")
    @Nls(capitalization = Nls.Capitalization.Title)
    private String nameKey;

    /**
     * The language server label displayed on the LSP console and Language Servers preferences.
     */
    @Attribute("name")
    private String name;

    /**
     * This attribute specifies the resource key in the specified {@link #bundle}.
     * This is another way to specify the {@link #description server description}.
     */
    @Attribute("descriptionKey")
    @Nls(capitalization = Nls.Capitalization.Sentence)
    private String descriptionKey;

    /**
     * The language server description displayed on the LSP console and Language Servers preferences.
     */
    @Tag("description")
    private String description;

    /**
     * The resource path of the icon displayed on the LSP console and Language Servers preferences.
     * Language server icons must have the size of 13x13 pixels.
     */
    @Attribute("icon")
    private String icon;

    /**
     * The {@link LanguageServerFactory} implementation used to create connection, language client and server interface.
     */
    @Attribute("factoryClass")
    @RequiredElement
    private String factoryClass;

    @Override
    protected @Nullable String getImplementationClassName() {
        return factoryClass;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getName() {
        if (name != null) {
            return name;
        }
        name = getLocalizedString(bundle, nameKey);
        if (name == null) {
            name = id;
        }
        return name;
    }

    @Nullable
    public String getDescription() {
        if (description != null) {
            return description;
        }
        if (bundle != null && descriptionKey != null) {
            return getLocalizedString(bundle, descriptionKey);
        }
        return null;
    }

    @Nullable
    public String getIcon() {
        return icon;
    }

    private @Nullable @Nls String getLocalizedString(@Nullable String bundleName, String key) {
        PluginDescriptor descriptor = getPluginDescriptor();
        String baseName = bundleName != null ? bundleName :
                bundle != null ? bundle :
                        descriptor.getResourceBundleBaseName();
        if (baseName == null || key == null) {
            if (bundleName != null) {
                LOGGER.warn("Bundle key missed for " + id);
            }
            return null;
        }
        ResourceBundle resourceBundle = DynamicBundle.getResourceBundle(descriptor.getClassLoader(), baseName);
        return AbstractBundle.message(resourceBundle, key);
    }

}
