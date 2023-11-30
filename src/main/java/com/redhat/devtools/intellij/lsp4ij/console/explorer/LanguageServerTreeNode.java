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
package com.redhat.devtools.intellij.lsp4ij.console.explorer;

import com.redhat.devtools.intellij.lsp4ij.LanguageServersRegistry;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Language server node.
 */
public class LanguageServerTreeNode extends DefaultMutableTreeNode {

    private final LanguageServersRegistry.LanguageServerDefinition serverDefinition;

    public LanguageServerTreeNode(LanguageServersRegistry.LanguageServerDefinition serverDefinition) {
        this.serverDefinition = serverDefinition;
    }

    public LanguageServersRegistry.LanguageServerDefinition getServerDefinition() {
        return serverDefinition;
    }

    public LanguageServerProcessTreeNode getActiveProcessTreeNode() {
        for (int i = 0; i < super.getChildCount(); i++) {
            return (LanguageServerProcessTreeNode) super.getChildAt(i);
        }
        return null;
    }

    public Icon getIcon() {
        String serverId = getServerDefinition().id;
        return LanguageServersRegistry.getInstance().getServerIcon(serverId);
    }

    public String getDisplayName() {
        return serverDefinition.getDisplayName();
    }
}
