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
package com.redhat.devtools.lsp4ij.client.indexing;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Project indexing strategy which supports only dumb indexing.
 */
@ApiStatus.Internal
public class ProjectIndexingOnlyDumbStrategy extends ProjectIndexingStrategyBase implements DumbService.DumbModeListener {

    private final @NotNull Project project;

    public ProjectIndexingOnlyDumbStrategy(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void enteredDumbMode() {
        if (isEnabled()) {
            onStartedDumbIndexing(project);
        }
    }

    @Override
    public void exitDumbMode() {
        if (isEnabled()) {
            onFinishedDumbIndexing(project);
        }
    }

    public boolean isEnabled() {
        return !ProjectIndexingDumbAndScanningStrategy.getInstance().isEnabled();
    }
}
