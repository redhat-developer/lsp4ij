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
package com.redhat.devtools.lsp4ij.commands.editor;

import com.intellij.openapi.actionSystem.ActionPlaces;

/**
 * Emulates Visual Studio Code's "editor.action.triggerSuggest" command, to trigger code completion after selecting a completion item.
 */
public class TriggerSuggestAction extends TriggerCommandAction {


    private static final String CODE_COMPLETION_ACTION = "CodeCompletion";

    public TriggerSuggestAction() {
        super(CODE_COMPLETION_ACTION, ActionPlaces.UNKNOWN);
    }
}
