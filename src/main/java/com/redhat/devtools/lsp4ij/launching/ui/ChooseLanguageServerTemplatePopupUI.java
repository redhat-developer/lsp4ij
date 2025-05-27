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
package com.redhat.devtools.lsp4ij.launching.ui;

import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.ui.ChooseItemPopupUI;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * UI component that displays a popup to let the user select a {@link LanguageServerTemplate}.
 * <p>
 * This class is a specialization of {@link ChooseItemPopupUI} that preconfigures
 * the list of items to show using {@link LanguageServerTemplateManager#getTemplates()},
 * and defines how each {@link LanguageServerTemplate} is displayed and identified.
 * </p>
 *
 * <p>
 * When the user selects an item (via double-click or pressing Enter), the provided
 * {@code onItemSelected} {@link Consumer} is invoked with the selected template.
 * </p>
 *
 * <p>
 * This popup is typically used when configuring or creating a new language server
 * from predefined templates.
 * </p>
 *
 */
public class ChooseLanguageServerTemplatePopupUI extends ChooseItemPopupUI<LanguageServerTemplate> {

    /**
     * Creates a new popup UI for choosing a Language Server template.
     *
     * @param onItemSelected the callback invoked when a template is selected
     */
    public ChooseLanguageServerTemplatePopupUI(@NotNull Consumer<LanguageServerTemplate> onItemSelected) {
        super(LanguageServerBundle.message("new.language.server.dialog.choose.template.title"),
                LanguageServerTemplateManager.getInstance().getTemplates(),
                LanguageServerTemplate::getId,
                LanguageServerTemplate::getName,
                onItemSelected);
    }

}