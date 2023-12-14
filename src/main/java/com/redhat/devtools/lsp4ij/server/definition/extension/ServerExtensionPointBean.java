/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.server.definition.extension;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ResourceBundle;

/**
 * Server extension point bean.
 *
 * <pre>
 *   <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
 *     <server id="myLanguageServerId"
 *         label="My Language Server"
 *         factoryClass="my.language.server.MyLanguageServerFactory">
 *     <description><![CDATA[
 *      Some description written in HTML to display it in LSP consoles and Language Servers settings.
 *      ]]>
 *     </description>
 *   </server>
 * </extensions>
 * </pre>
 */
public class ServerExtensionPointBean extends BaseKeyedLazyInstance<LanguageServerFactory> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerExtensionPointBean.class);

    public static final ExtensionPointName<ServerExtensionPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.lsp4ij.server");

    /**
     * The language server id used to associate language
     * with {@link LanguageMappingExtensionPointBean language mapping} extension point.
     */
    @Attribute("id")
    @RequiredElement
    public String id;

    /**
     * This attribute specifies the resource bundle that contains the specified {@link #labelKey} / {@link #descriptionKey}.
     * This is another way to specify the {@link #label server label} / {@link #description server description}.
     */
    @Attribute("bundle")
    public String bundle;

    /**
     * This attribute specifies the resource key in the specified {@link #bundle}.
     * This is another way to specify the {@link #label server label}.
     */
    @Attribute("labelKey")
    @Nls(capitalization = Nls.Capitalization.Title)
    public String labelKey;

    /**
     * The language server label displayed on the LSP console and Language Servers preferences.
     */
    @Attribute("label")
    public String label;

    /**
     * This attribute specifies the resource key in the specified {@link #bundle}.
     * This is another way to specify the {@link #description server description}.
     */
    @Attribute("descriptionKey")
    @Nls(capitalization = Nls.Capitalization.Sentence)
    public String descriptionKey;

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
    @RequiredElement
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

    @NotNull
    public String getLabel() {
        if (label != null) {
            return label;
        }
        label = getLocalizedString(bundle, labelKey);
        if (label == null) {
            label = id;
        }
        return label;
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
