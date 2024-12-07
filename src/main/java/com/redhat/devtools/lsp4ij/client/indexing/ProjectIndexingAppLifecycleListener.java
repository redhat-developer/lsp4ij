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

import com.intellij.ide.AppLifecycleListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>The proxy that implements ProjectIndexingActivityHistoryListener is registered via topic subscribe
 * in the {@link ProjectIndexingDumbAndScanningStrategy}.
 * </p>
 *
 * <p>
 * This registration must be done as early as possible (when the IDE is starting)
 * so that it is registered before the Scanning files task is executed.
 * </p>
 *
 * <p>This class allows to perform this registration as early as possible.
 * For example if this registration is done in a ProjectActivity,
 * the proxy that implements ProjectIndexingActivityHistoryListener will be
 * registered after the Scanning task is executed
 * </p>
 */
@ApiStatus.Internal
public class ProjectIndexingAppLifecycleListener implements AppLifecycleListener {

    @Override
    public  void appFrameCreated(@NotNull List<String> commandLineArgs) {
        ProjectIndexingDumbAndScanningStrategy.getInstance();
    }
}
