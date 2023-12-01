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
package com.redhat.devtools.lsp4ij.ui.components;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

/**
 * HyperlinkLabel that opens an inspection settings page
 */
public class InspectionHyperlink extends HyperlinkLabel {

    /**
     * Creates a new hyperlink to an inspection settings page
     * @param label the hyperlink label
     * @param inspectionGroupPath the group path to open in the inspections settings
     */
    public InspectionHyperlink(@NotNull @NlsContexts.LinkLabel String label, @NotNull String inspectionGroupPath) {
        super(label);
        addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
                @NotNull DataContext dataContext = DataManager.getInstance().getDataContext(this);
                @Nullable Settings settings = Settings.KEY.getData(dataContext);
                if (settings != null) {
                    @Nullable ErrorsConfigurable inspections = settings.find(ErrorsConfigurable.class);
                    if (inspections != null) {
                        settings.select(inspections).doWhenDone(new Runnable() {
                            @Override
                            public void run() {
                                inspections.selectInspectionGroup(new String[]{inspectionGroupPath});
                            }
                        });
                    }
                }
            }
        });
    }
}
