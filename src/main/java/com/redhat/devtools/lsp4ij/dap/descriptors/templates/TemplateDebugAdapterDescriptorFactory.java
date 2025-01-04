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
package com.redhat.devtools.lsp4ij.dap.descriptors.templates;

import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.descriptors.userdefined.UserDefinedDebugAdapterDescriptorFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Template {@link DebugAdapterDescriptorFactory}.
 */
public class TemplateDebugAdapterDescriptorFactory extends UserDefinedDebugAdapterDescriptorFactory {

    public TemplateDebugAdapterDescriptorFactory(@NotNull DAPTemplate template) {
        super(template.getId(), template.getName(), template.getOSProgramArgs(),
                template.getLanguageMappings(), template.getFileTypeMappings());
        super.setWaitForTimeout(template.getWaitForTimeout());
        super.setWaitForTrace(template.getWaitForTrace());
        super.setLaunchConfiguration(template.getLaunchConfiguration());
        super.setLaunchConfigurationSchema(template.getLaunchConfigurationSchema());
        super.setAttachConfiguration(template.getAttachConfiguration());
        super.setAttachConfigurationSchema(template.getAttachConfigurationSchema());
    }
}
