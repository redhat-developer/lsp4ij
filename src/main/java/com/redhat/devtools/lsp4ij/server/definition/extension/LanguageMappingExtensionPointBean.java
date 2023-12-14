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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import com.redhat.devtools.lsp4ij.DocumentMatcher;
import org.eclipse.lsp4j.TextDocumentItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Language mapping extension point.
 *
 * <pre>
 *   <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
 *
 *     <languageMapping language="XML"
 *                      serverId="myLanguageServerId"
 *                      languageId="xml" />
 *
 *   </extensions>
 * </pre>
 */
public class LanguageMappingExtensionPointBean extends BaseKeyedLazyInstance<DocumentMatcher> {

    public static final DocumentMatcher DEFAULT_DOCUMENT_MATCHER = (file,project) -> true;

    public static final ExtensionPointName<LanguageMappingExtensionPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.lsp4ij.languageMapping");

    /**
     * The language server mapped with the language {@link #language IntelliJ language}.
     */
    @Attribute("serverId")
    @RequiredElement
    public String serverId;

    /**
     * The IntelliJ language mapped with the language server {@link #serverId server id}.
     */
    @Attribute("language")
    @RequiredElement
    public String language;

    /**
     * The LSP language ID which must be used in the LSP {@link TextDocumentItem#getLanguageId()}. If it is not defined
     * the languageId used will be the IntelliJ {#link language}.
     */
    @Attribute("languageId")
    public String languageId;

    /**
     * The {@link DocumentMatcher document matcher}.
     */
    @Attribute("documentMatcher")
    public String documentMatcher;

    public @NotNull DocumentMatcher getDocumentMatcher() {
        try {
            return super.getInstance();
        }
        catch(Exception e) {
            return DEFAULT_DOCUMENT_MATCHER;
        }
    }

    @Override
    protected @Nullable String getImplementationClassName() {
        return documentMatcher;
    }
}
