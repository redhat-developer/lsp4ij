/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.templates;

import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;

import java.io.InputStreamReader;
import java.util.List;

/**
 * Language server template manager.
 */
public class LanguageServerTemplateManager {

    private final LanguageServerTemplates root;

    public static LanguageServerTemplateManager getInstance() {
        return ApplicationManager.getApplication().getService(LanguageServerTemplateManager.class);
    }

    private LanguageServerTemplateManager() {
        root = new Gson()
                .fromJson(new InputStreamReader(LanguageServerTemplateManager.class.getClassLoader().getResourceAsStream("template-ls.json")), LanguageServerTemplates.class);
    }

    public List<LanguageServerTemplate> getTemplates() {
        return root.getLanguageServers();
    }
}
