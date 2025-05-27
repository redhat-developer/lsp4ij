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
package com.redhat.devtools.lsp4ij.dap.settings.ui;

import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplate;
import com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplateManager;
import com.redhat.devtools.lsp4ij.ui.ChooseItemPopupUI;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A UI component that displays a searchable popup for selecting a {@link DAPTemplate}
 * (Debug Adapter Protocol template) when creating a new debug adapter configuration.
 * <p>
 * This class is a specialization of {@link ChooseItemPopupUI} configured with
 * {@link DAPTemplate} instances, using their ID and name for display and filtering.
 * </p>
 *
 */
public class ChooseDebugServerTemplatePopupUI extends ChooseItemPopupUI<DAPTemplate> {

    /**
     * Creates a new popup UI for choosing a DAP template.
     *
     * @param onItemSelected the callback invoked when a template is selected
     */
    public ChooseDebugServerTemplatePopupUI(@NotNull Consumer<DAPTemplate> onItemSelected) {
        super(DAPBundle.message("new.debug.adapter.dialog.choose.template.title"),
                DAPTemplateManager.getInstance().getTemplates(),
                DAPTemplate::getId,
                DAPTemplate::getName,
                onItemSelected);
    }

}