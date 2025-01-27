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
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NotNullLazyValue;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;

/**
 * Default Debug Adapter Protocol (DAP) configuration type.
 */
final class DAPRunConfigurationType extends ConfigurationTypeBase {

    public static final String ID = "DAPRunConfiguration";

    public static DAPRunConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(DAPRunConfigurationType.class);
    }

    DAPRunConfigurationType() {
        super(ID,
                DAPBundle.message("DAPRunConfigurationType.displayName"),
                DAPBundle.message("DAPRunConfigurationType.description"),
                NotNullLazyValue.createValue(() -> AllIcons.Debugger.Console));
        addFactory(new DAPConfigurationFactory(this));
    }

}