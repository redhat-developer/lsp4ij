/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.openapi.options.BeanConfigurable;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;

/**
 * The LSP options of Settings | Editor | General | Smart Keys, beside the options of other languages, such as XML's
 * "Simultaneous &lt;tag&gt;&lt;/tag&gt; editing".
 */
public class LSPSmartKeysConfigurable extends BeanConfigurable<LSPEditorOptions> {

    public LSPSmartKeysConfigurable() {
        super(LSPEditorOptions.getInstance(), LanguageServerBundle.message("smart.keys.title"));
        LSPEditorOptions options = getInstance();
        checkBox(LanguageServerBundle.message("smart.keys.linked.editing"), options::isLinkedEditing, options::setLinkedEditing);
    }
}
