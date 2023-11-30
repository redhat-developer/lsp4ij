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
package com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.handler;

import java.util.List;

public class LinkedPositionResult {

    private final String templateContent;

    private final List<LinkedPosition> linkedPositions;

    public LinkedPositionResult(String templateContent, List<LinkedPosition> linkedPositions) {
        super();
        this.templateContent = templateContent;
        this.linkedPositions = linkedPositions;
    }

    public String getTemplateContent() {
        return templateContent;
    }


    public LinkedPosition[] getLinkedPositions() {
        return linkedPositions.toArray(new LinkedPosition[linkedPositions.size()]);
    }

}
