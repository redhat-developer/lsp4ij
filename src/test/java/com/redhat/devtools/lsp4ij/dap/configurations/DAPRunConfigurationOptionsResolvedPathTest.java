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
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.SystemProperties;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

/**
 * Tests the {@code Working directory} and the {@code File} of a DAP run configuration as a run uses them
 * ({@link DAPRunConfigurationOptions#getResolvedWorkingDirectory} and
 * {@link DAPRunConfigurationOptions#getResolvedFile}): a leading {@code ~} is the user's home directory, a relative path
 * is relative to the project's directory, and an absolute path stays as it is. What is stored, which the settings
 * editor shows, doesn't change. The {@code ~} is expanded as IntelliJ's {@code FileUtil.expandUserHome} does
 * ({@link DAPRunConfigurationOptions#expandUserHome}).
 */
public class DAPRunConfigurationOptionsResolvedPathTest extends LightPlatformTestCase {

    public void testATildeIsTheHomeDirectory() {
        var options = new DAPRunConfigurationOptions();
        options.setWorkingDirectory("~");
        options.setFile("~/docs/ruby-test/hello.rb");

        assertEquals(home().toString(), options.getResolvedWorkingDirectory(getProject()));
        assertEquals(home().resolve("docs").resolve("ruby-test").resolve("hello.rb").toString(),
                options.getResolvedFile(getProject()));
        // what is stored, and shown in the settings editor
        assertEquals("~", options.getWorkingDirectory());
        assertEquals("~/docs/ruby-test/hello.rb", options.getFile());
    }

    public void testARelativePathIsRelativeToTheProjectDirectory() {
        var options = new DAPRunConfigurationOptions();
        Path projectDirectory = projectDirectory();

        options.setWorkingDirectory(".");
        assertEquals(projectDirectory.normalize().toString(), options.getResolvedWorkingDirectory(getProject()));

        options.setWorkingDirectory("src/main");
        assertEquals(projectDirectory.resolve("src").resolve("main").normalize().toString(),
                options.getResolvedWorkingDirectory(getProject()));

        options.setWorkingDirectory("../other-project");
        assertEquals(projectDirectory.resolveSibling("other-project").normalize().toString(),
                options.getResolvedWorkingDirectory(getProject()));

        // A file, without a working directory, in which the Debug Adapter then runs
        options.setWorkingDirectory("");
        options.setFile("src/hello.rb");
        assertEquals(projectDirectory.resolve("src").resolve("hello.rb").normalize().toString(),
                options.getResolvedFile(getProject()));
        assertEquals("src/hello.rb", options.getFile());
    }

    public void testARelativeFileIsRelativeToTheWorkingDirectory() {
        // As the Debug Adapter resolves it, in its working directory
        var options = new DAPRunConfigurationOptions();
        options.setFile("x.rb");

        String absolute = new File(System.getProperty("java.io.tmpdir"), "a").getAbsolutePath();
        options.setWorkingDirectory(absolute);
        assertEquals(Path.of(absolute).resolve("x.rb").normalize().toString(), options.getResolvedFile(getProject()));

        // A relative working directory is relative to the project's directory
        options.setWorkingDirectory("sub");
        assertEquals(projectDirectory().resolve("sub").resolve("x.rb").normalize().toString(),
                options.getResolvedFile(getProject()));

        options.setWorkingDirectory("~");
        assertEquals(home().resolve("x.rb").toString(), options.getResolvedFile(getProject()));
        assertEquals("x.rb", options.getFile());
    }

    public void testAnAbsolutePathStaysAsItIs() {
        var options = new DAPRunConfigurationOptions();
        // Not normalized either: with its .. and its separator at the end
        String absolute = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath()
                + File.separator + "a" + File.separator + ".." + File.separator + "b" + File.separator;
        options.setWorkingDirectory(absolute);
        options.setFile(absolute + "hello.rb");

        assertEquals(absolute, options.getResolvedWorkingDirectory(getProject()));
        assertEquals(absolute + "hello.rb", options.getResolvedFile(getProject()));

        // On Windows, a path which starts with a separator has no drive, so isn't absolute: it stays as it is too
        options.setWorkingDirectory("/working/directory");
        options.setFile("/working/directory/hello.rb");
        assertEquals("/working/directory", options.getResolvedWorkingDirectory(getProject()));
        assertEquals("/working/directory/hello.rb", options.getResolvedFile(getProject()));
        options.setWorkingDirectory("\\working\\directory");
        assertEquals("\\working\\directory", options.getResolvedWorkingDirectory(getProject()));
    }

    public void testNoPathStaysAsItIs() {
        var options = new DAPRunConfigurationOptions();
        assertTrue(StringUtils.isBlank(options.getWorkingDirectory()));
        assertEquals(options.getWorkingDirectory(), options.getResolvedWorkingDirectory(getProject()));
        assertEquals(options.getFile(), options.getResolvedFile(getProject()));

        options.setWorkingDirectory("  ");
        options.setFile(null);
        assertEquals(options.getWorkingDirectory(), options.getResolvedWorkingDirectory(getProject()));
        assertEquals(options.getFile(), options.getResolvedFile(getProject()));
    }

    public void testWithoutAProjectARelativePathStaysAsItIs() {
        var options = new DAPRunConfigurationOptions();
        options.setWorkingDirectory("src/main");
        options.setFile("~/hello.rb");

        assertEquals("src/main", options.getResolvedWorkingDirectory(null));
        assertEquals(home().resolve("hello.rb").toString(), options.getResolvedFile(null));
    }

    public void testOnlyALeadingTildeAloneOrBeforeASeparatorIsTheHomeDirectory() {
        String home = SystemProperties.getUserHome();
        assertEquals(home, DAPRunConfigurationOptions.expandUserHome("~"));
        assertEquals(home + "/docs/hello.rb", DAPRunConfigurationOptions.expandUserHome("~/docs/hello.rb"));
        assertEquals(home + "\\docs\\hello.rb", DAPRunConfigurationOptions.expandUserHome("~\\docs\\hello.rb"));

        // As IntelliJ's FileUtil.expandUserHome: the home of another user, and a tilde anywhere else, stay as they are
        assertEquals("~user/hello.rb", DAPRunConfigurationOptions.expandUserHome("~user/hello.rb"));
        assertEquals("~hello.rb", DAPRunConfigurationOptions.expandUserHome("~hello.rb"));
        assertEquals("docs/~/hello.rb", DAPRunConfigurationOptions.expandUserHome("docs/~/hello.rb"));
        assertEquals("hello.rb~", DAPRunConfigurationOptions.expandUserHome("hello.rb~"));
        assertEquals("", DAPRunConfigurationOptions.expandUserHome(""));
    }

    private @NotNull Path projectDirectory() {
        String basePath = getProject().getBasePath();
        assertNotNull("The project of the tests has a directory", basePath);
        return Path.of(basePath);
    }

    private static @NotNull Path home() {
        return Path.of(SystemProperties.getUserHome()).normalize();
    }
}
