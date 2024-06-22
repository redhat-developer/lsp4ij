/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens.inspector;

import com.intellij.openapi.editor.colors.TextAttributesKey;

import java.util.List;

/**
 * Semantic tokens highlight information.
 *
 * @param tokenType
 * @param tokenModifiers
 * @param start
 * @param end
 * @param colorKey
 */
public record SemanticTokensHighlightInfo(String tokenType, List<String> tokenModifiers, int start, int end, TextAttributesKey colorKey) {
}
