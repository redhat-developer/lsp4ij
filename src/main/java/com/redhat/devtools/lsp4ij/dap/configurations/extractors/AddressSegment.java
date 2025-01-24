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
package com.redhat.devtools.lsp4ij.dap.configurations.extractors;

/**
 * The address segment ${address}.
 */
public class AddressSegment extends DynamicSegment {

    public AddressSegment() {
        super("${address}", "([\\w\\.]+)", DynamicSegmentType.ADDRESS);
    }

}
