/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Semantic tokens colors provider extension point.
 *
 * <pre>
 *   <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
 *
 *     <semanticTokensColorsProvider
 *                      serverId="myLanguageServerId"
 *                      class="my.language.server.MySemanticTokensColorsProvider" />
 *
 *   </extensions>
 * </pre>
 */
public class SemanticTokensColorsProviderExtensionPointBean extends BaseKeyedLazyInstance<SemanticTokensColorsProvider> {

    public static final ExtensionPointName<SemanticTokensColorsProviderExtensionPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.lsp4ij.semanticTokensColorsProvider");

    /**
     * The language server.
     */
    @Attribute("serverId")
    @RequiredElement
    public String serverId;

    /**
     * The custom {@link SemanticTokensColorsProvider semantic tokens colors provider}.
     */
    @Attribute("class")
    public String className;

    public @NotNull SemanticTokensColorsProvider getSemanticTokensColorsProvider() {
        return super.getInstance();
    }

    @Override
    protected @Nullable String getImplementationClassName() {
        return className;
    }
}
