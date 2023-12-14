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

import java.util.List;

/**
 * Language server templates which hosts language server templates.
 */
public class LanguageServerTemplates {

    private List<LanguageServerTemplate> languageServers;

    public List<LanguageServerTemplate> getLanguageServers() {
        return languageServers;
    }
}
