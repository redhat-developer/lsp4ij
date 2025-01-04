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
import com.intellij.openapi.util.NotNullLazyValue;
import com.redhat.devtools.lsp4ij.dap.features.DAPClientFeatures;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Base class Debug Adapter Protocol (DAP) configuration type.
 */
public abstract class DAPRunConfigurationTypeBase extends ConfigurationTypeBase {

    protected DAPRunConfigurationTypeBase(@NotNull DAPClientFeatures clientFeatures,
                                          @NonNls String id,
                                          @Nls String displayName,
                                          @Nls String description,
                                          @Nullable NotNullLazyValue<Icon> icon) {
        super(id, displayName, description, icon);
        addFactory(clientFeatures.createConfigurationFactory(this));
    }

}