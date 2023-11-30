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
package com.redhat.devtools.intellij.lsp4ij.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.codeInspection.ui.ListEditForm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Base {@link LocalInspectionTool} providing the possibility to define exclusions.
 */
public abstract class AbstractDelegateInspectionWithExclusions extends LocalInspectionTool {

    private final String exclusionsLabel;

    /**
     * Inspection constructor
     * @param exclusionsLabel the label to use for the exclusion component in the options panel
     */
    public AbstractDelegateInspectionWithExclusions(@NotNull String exclusionsLabel) {
        this.exclusionsLabel = exclusionsLabel;
    }

    //Field is public, so it can be serialized as XML
    public final @NotNull List<String> excludeList = new ArrayList<>();

    public JComponent createOptionsPanel() {
        InspectionOptionsPanel panel = new InspectionOptionsPanel();

        var injectionListTable = new ListEditForm("", exclusionsLabel, excludeList);

        panel.addGrowing(injectionListTable.getContentPanel());

        return panel;
    }
}
