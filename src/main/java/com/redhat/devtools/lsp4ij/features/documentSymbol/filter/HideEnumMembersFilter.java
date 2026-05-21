/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.features.documentSymbol.filter;

import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import org.eclipse.lsp4j.SymbolKind;

/**
 * Filter to hide enum members in the structure view.
 */
public class HideEnumMembersFilter extends LSPSymbolKindFilter {

    public static final String ID = "SHOW_ENUM_MEMBERS";

    public HideEnumMembersFilter() {
        super(ID, LanguageServerBundle.message("structure.view.filter.show.enumMembers"), SymbolKind.EnumMember);
    }
}
