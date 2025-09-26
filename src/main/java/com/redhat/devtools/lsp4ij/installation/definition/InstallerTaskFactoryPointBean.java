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
package com.redhat.devtools.lsp4ij.installation.definition;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * Server extension point bean.
 *
 * <pre>
 *   <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
 *     <installerTaskFactory
 *         type="exec"
 *         factoryClass="com.redhat.devtools.lsp4ij.installation.definition.tasks.ExecTaskFactory">
 *     </description>
 *   </installerTaskFactory>
 * </extensions>
 * </pre>
 */
public class InstallerTaskFactoryPointBean extends BaseKeyedLazyInstance<InstallerTaskFactory> {

    public static final ExtensionPointName<InstallerTaskFactoryPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.lsp4ij.installerTaskFactory");

    /**
     * The installer task factory type.
     */
    @Attribute("type")
    @RequiredElement
    public String type;

    /**
     * The {@link InstallerTaskFactory} implementation used to create task.
     */
    @Attribute("factoryClass")
    @RequiredElement
    public String factoryClass;

    @Override
    protected @Nullable String getImplementationClassName() {
        return factoryClass;
    }

}
