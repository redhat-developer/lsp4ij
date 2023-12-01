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
import org.jetbrains.annotations.Nullable;

/**
 * Server icon provider extension.
 */
public class ServerIconProviderExtensionPointBean extends BaseKeyedLazyInstance<ServerIconProvider> {

    public static final ExtensionPointName<ServerIconProviderExtensionPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.lsp4ij.serverIconProvider");

    @Attribute("serverId")
    public String serverId;

    @Attribute("class")
    public String clazz;

    @Override
    protected @Nullable String getImplementationClassName() {
        return clazz;
    }

}
