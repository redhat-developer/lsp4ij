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
package com.redhat.devtools.intellij.lsp4ij.inspections;

import com.intellij.codeInspection.LocalInspectionTool;

/**
 * No-op {@link LocalInspectionTool} used as a basis for mapping inspection severities to matching LSP severities.
 */
public abstract class AbstractDelegateInspection extends LocalInspectionTool {
}
