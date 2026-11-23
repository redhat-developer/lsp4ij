/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.ConfigureServerTaskFactory;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.DownloadTaskFactory;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.ExecTask;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.ExecTaskFactory;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.FileExistsTaskFactory;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.InitializeTask;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.InitializeTaskFactory;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.ShowMessageTaskFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link InstallerTaskRegistry} parsing of the {@code initialize} task type, and for the bundled installers
 * which check with it (#1957).
 */
public class InstallerTaskRegistryInitializeTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    // The download task of the bundled installers looks up an application service
    @BeforeClass
    public static void startTheApplication() {
        TestApplicationManager.getInstance();
    }

    private static @NotNull InstallerTaskRegistry createRegistry(boolean withInitialize) {
        InstallerTaskRegistry registry = new InstallerTaskRegistry();
        registry.registerFactory("fileExists", new FileExistsTaskFactory());
        registry.registerFactory("exec", new ExecTaskFactory());
        registry.registerFactory("download", new DownloadTaskFactory());
        registry.registerFactory("configureServer", new ConfigureServerTaskFactory());
        registry.registerFactory("showMessage", new ShowMessageTaskFactory());
        if (withInitialize) {
            registry.registerFactory("initialize", new InitializeTaskFactory());
        }
        return registry;
    }

    @Test
    public void testInitializeCheckParsedWithItsTimeout() {
        var descriptor = createRegistry(true).loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "initialize": {
                      "name": "Check that ruby-lsp starts",
                      "timeout": 10000
                    }
                  }
                }
                """);
        var check = assertInstanceOf(InitializeTask.class, descriptor.getCheck());
        assertEquals("Check that ruby-lsp starts", check.getName());
        assertEquals(10000, check.getTimeout());
    }

    @Test
    public void testInitializeCheckWithoutTimeoutWaitsTheDefaultTimeout() {
        var descriptor = createRegistry(true).loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "initialize": {
                      "name": "Check that the server starts"
                    }
                  }
                }
                """);
        var check = assertInstanceOf(InitializeTask.class, descriptor.getCheck());
        assertEquals(InitializeTask.DEFAULT_TIMEOUT, check.getTimeout());
    }

    @Test
    public void testInitializeCheckWithOnFailAndOnSuccess() {
        var descriptor = createRegistry(true).loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "initialize": {
                      "name": "Check that gopls starts",
                      "onFail": {
                        "exec": {
                          "name": "Check Go runtime",
                          "command": "which go",
                          "onSuccess": {
                            "initialize": {
                              "name": "Check that gopls starts"
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """);
        var check = assertInstanceOf(InitializeTask.class, descriptor.getCheck());
        var onFail = assertInstanceOf(ExecTask.class, check.getOnFail());
        assertInstanceOf(InitializeTask.class, onFail.getOnSuccess());
    }

    /**
     * An LSP4IJ which doesn't know the {@code initialize} task, as one older than it, ignores it, as any task it
     * doesn't know: the installer then has no check.
     */
    @Test
    public void testARegistryWithoutTheInitializeTaskIgnoresIt() {
        var descriptor = createRegistry(false).loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "initialize": {
                      "name": "Check that the server starts"
                    }
                  },
                  "run": {
                    "exec": {
                      "name": "Install",
                      "command": "npm install test-server"
                    }
                  }
                }
                """);
        assertNull(descriptor.getCheck());
        assertNotNull(descriptor.getRun());
    }

    /**
     * The bundled installers which checked with an {@code exec} of {@code ${server.command}}, which a server that hangs
     * passes, still do, and then check with {@code initialize}, as its {@code onSuccess}. {@code initialize} is nowhere
     * else: the debug adapters which LSP4IJ attaches to, as debugpy and rdbg, have no command, and check that their
     * debugger is installed with its own {@code --version}.
     */
    @Test
    public void testTheBundledInstallersCheckWithInitializeOnceTheServerCommandRuns() throws IOException {
        Map<String, List<String>> checks = new TreeMap<>();
        try (Stream<Path> installers = Files.walk(TEMPLATES)) {
            installers.filter(path -> path.getFileName().toString().equals("installer.json"))
                    .forEach(installer -> {
                        String template = TEMPLATES.relativize(installer.getParent()).toString().replace('\\', '/');
                        List<String> tasks = serverChecks(readJson(installer)).toList();
                        if (!tasks.isEmpty()) {
                            checks.put(template, tasks);
                        }
                    });
        }
        var expected = new TreeMap<String, List<String>>();
        for (var template : List.of("lsp/astro-ls", "lsp/candor-lsp", "lsp/dart-lsp", "lsp/eo-lsp-server", "lsp/metals",
                "lsp/ruby-lsp", "lsp/sdl-lsp", "lsp/sourcekit-lsp", "lsp/stylelint-lsp", "lsp/svelte-language-server",
                "lsp/sysml-lsp", "lsp/typescript-language-server", "lsp/vscode-css-language-server",
                "lsp/vscode-eslint-language-server", "lsp/vue-js-language-server", "dap/go-delve")) {
            expected.put(template, List.of(EXEC_THEN_INITIALIZE));
        }
        expected.put("lsp/gopls", List.of(EXEC_THEN_INITIALIZE, EXEC_THEN_INITIALIZE));
        expected.put("lsp/jq-lsp", List.of(EXEC_THEN_INITIALIZE, EXEC_THEN_INITIALIZE));
        assertEquals(expected, checks);
    }

    /**
     * An LSP4IJ which doesn't know the {@code initialize} task, as one older than it, loads the check of a bundled
     * installer as the {@code exec} it had before, without {@code onSuccess}, and this LSP4IJ loads it with
     * {@code initialize} as its {@code onSuccess}.
     */
    @Test
    public void testAnLSP4IJWithoutTheInitializeTaskLoadsTheCheckOfTheBundledInstallersAsBefore() throws IOException {
        for (var template : List.of("lsp/typescript-language-server", "lsp/ruby-lsp", "lsp/gopls", "dap/go-delve")) {
            String json = Files.readString(TEMPLATES.resolve(template).resolve("installer.json"));

            var withInitialize = assertInstanceOf(ExecTask.class, createRegistry(true).loadInstaller(json).getCheck(), template);
            assertInstanceOf(InitializeTask.class, withInitialize.getOnSuccess(), template);

            var withoutInitialize = assertInstanceOf(ExecTask.class, createRegistry(false).loadInstaller(json).getCheck(), template);
            assertEquals("Trying current command", withoutInitialize.getName(), template);
            assertNull(withoutInitialize.getOnSuccess(), template);
            assertEquals(withInitialize.getOnFail() != null, withoutInitialize.getOnFail() != null, template);
        }
    }

    private static final String EXEC = "exec ${server.command}";
    private static final String EXEC_THEN_INITIALIZE = "exec ${server.command}, then initialize";

    /**
     * Returns the checks of the server in the given installer, at any depth: the {@code exec} tasks of
     * {@code ${server.command}}, with or without an {@code initialize} as their {@code onSuccess}, and any other
     * {@code initialize}.
     */
    private static @NotNull Stream<String> serverChecks(@NotNull JsonElement json) {
        if (json.isJsonArray()) {
            return StreamSupport.stream(json.getAsJsonArray().spliterator(), false)
                    .flatMap(InstallerTaskRegistryInitializeTest::serverChecks);
        }
        if (!json.isJsonObject()) {
            return Stream.empty();
        }
        return json.getAsJsonObject().entrySet().stream().flatMap(entry -> {
            JsonElement value = entry.getValue();
            if (entry.getKey().equals("exec") && value.isJsonObject() && isServerCommand(value.getAsJsonObject())) {
                JsonObject exec = value.getAsJsonObject();
                JsonElement onSuccess = exec.get("onSuccess");
                boolean thenInitialize = onSuccess != null && onSuccess.isJsonObject()
                        && onSuccess.getAsJsonObject().keySet().equals(Set.of("initialize"));
                // What the initialize task triggers, and the rest of the exec
                JsonObject rest = exec.deepCopy();
                if (thenInitialize) {
                    rest.remove("onSuccess");
                }
                return Stream.concat(Stream.of(thenInitialize ? EXEC_THEN_INITIALIZE : EXEC),
                        Stream.concat(serverChecks(rest), thenInitialize
                                ? serverChecks(onSuccess.getAsJsonObject().get("initialize"))
                                : Stream.empty()));
            }
            if (entry.getKey().equals("initialize")) {
                return Stream.concat(Stream.of("initialize"), serverChecks(value));
            }
            return serverChecks(value);
        });
    }

    private static boolean isServerCommand(@NotNull JsonObject exec) {
        JsonElement command = exec.get("command");
        return command != null && command.isJsonPrimitive() && command.getAsString().equals("${server.command}");
    }

    private static @NotNull JsonObject readJson(@NotNull Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
