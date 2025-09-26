/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.commands.editor;

import com.intellij.openapi.actionSystem.*;

/**
 * Emulates Visual Studio Code's "editor.action.triggerParameterHints" command, to trigger ParameterInfo.
 */
public class TriggerParameterHintsAction extends TriggerCommandAction {

    private static final String PARAMETER_INFO_ACTION = "ParameterInfo";

    public TriggerParameterHintsAction() {
        super(PARAMETER_INFO_ACTION, ActionPlaces.EDITOR_POPUP);
    }

}
