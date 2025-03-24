/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference to a string value that simplifies mutation of that value in lambdas/inner classes.
 */
@ApiStatus.Internal
public class StringRef extends MutableRef<String> {
    @NotNull
    public static StringRef create(@NotNull String value) {
        return new StringRef(value);
    }

    @NotNull
    public static StringRef create() {
        return create("");
    }

    private StringRef(@NotNull String value) {
        super(value);
    }

    @Override
    @NotNull
    public String getValue() {
        //noinspection DataFlowIssue
        return super.getValue();
    }

    /**
     * Updates the string value to append the provided value.
     *
     * @param appendValue the value that should be appended
     */
    public void append(@Nullable String appendValue) {
        if (appendValue != null) {
            set(getValue() + appendValue);
        }
    }
}
