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
package com.redhat.devtools.lsp4ij.server.capabilities;

import com.redhat.devtools.lsp4ij.features.files.PathPatternMatcher;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.DocumentFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Extended documentSelector.
 */
public class ExtendedDocumentSelector {

    interface DocumentFilersProvider {
        List<ExtendedDocumentFilter> getFilters();
    }

    @NotNull
    private final List<ExtendedDocumentFilter> filters;

    public class ExtendedDocumentFilter extends DocumentFilter {

        private PathPatternMatcher patternMatcher;

        public ExtendedDocumentFilter(DocumentFilter filter) {
            super.setLanguage(filter.getLanguage());
            super.setScheme(filter.getScheme());
            super.setPattern(filter.getPattern());
        }

        @Nullable
        public PathPatternMatcher getPathPattern() {
            if (patternMatcher != null) {
                return patternMatcher;
            }
            String pattern = super.getPattern();
            if (StringUtils.isEmpty(pattern)) {
                return null;
            }
            patternMatcher = new PathPatternMatcher(pattern);
            return patternMatcher;
        }
    }

    public ExtendedDocumentSelector(List<DocumentFilter> documentSelector) {
        this.filters = documentSelector != null ?
                documentSelector
                        .stream()
                        .filter(f -> !(StringUtils.isEmpty(f.getLanguage()) && StringUtils.isEmpty(f.getPattern()) && StringUtils.isEmpty(f.getScheme())))
                        .map(f -> new ExtendedDocumentFilter(f))
                        .toList() :
                Collections.emptyList();
    }

    public @NotNull List<ExtendedDocumentFilter> getFilters() {
        return filters;
    }
}
