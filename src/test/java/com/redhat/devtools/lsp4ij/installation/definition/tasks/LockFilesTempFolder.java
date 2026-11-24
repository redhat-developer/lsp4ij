/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.intellij.openapi.util.io.NioFiles;
import org.jetbrains.annotations.Nullable;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Keeps the lock files of the working directories of exec tasks in a temp folder while the tests run, and deletes it
 * at the end, instead of in {@code ~/.lsp4ij/locks}, where the IDE keeps them: the working directories of the tests
 * are new temp folders, so their lock files would pile up there, one for each (#2140).
 * <p>
 * JUnit registers it for every test run, through
 * {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}, as {@code HangingTestWatchdog}, so a
 * test which runs an exec task needs nothing for it, whether Gradle or the IDE runs it.
 */
public final class LockFilesTempFolder implements TestExecutionListener {

    private @Nullable Path folder;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        try {
            folder = Files.createTempDirectory("lsp4ij-exec-locks");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the temp folder of the lock files of the tests", e);
        }
        ExecTask.setLockFilesDir(folder);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        ExecTask.setLockFilesDir(null);
        if (folder == null) {
            return;
        }
        try {
            NioFiles.deleteRecursively(folder);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the temp folder of the lock files of the tests, " + folder, e);
        } finally {
            folder = null;
        }
    }
}
