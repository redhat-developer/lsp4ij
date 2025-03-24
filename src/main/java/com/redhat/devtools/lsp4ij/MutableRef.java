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

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;

/**
 * Reference to a value that simplifies mutation of that value in lambdas/inner classes.
 *
 * @param <T> the value type
 */
class MutableRef<T> extends Ref<T> {
    protected MutableRef(@Nullable T value) {
        super(value);
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    @Nullable
    public T getValue() {
        return get();
    }
}
