/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.templates;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.util.system.CpuArch;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskFactoryBase;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the commands of the bundled templates and installers keep their arguments.
 * <p>
 * LSP4IJ splits a command at its spaces, so {@code sh -c typescript-language-server --stdio} gives {@code sh} three
 * arguments, and {@code sh -c} runs only the first one: the language server starts without {@code --stdio}. And
 * {@code sh -s ruby-lsp} reads its commands from the standard input, which carries the LSP messages, so it never starts
 * {@code ruby-lsp}.
 * <p>
 * LSP4IJ also expands the macros of the command of a language server or debug adapter, as {@code $USER_HOME$}, before
 * it splits the command, so a path under a home directory with a space, as {@code C:\Users\Jane Doe} on Windows, must
 * be in quotes.
 */
public class BundledTemplateCommandsTest {

    private static final String HOME = "C:/Users/Jane Doe";
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([\\w.]+)}");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    /**
     * A folder under {@code ~/.lsp4ij}: {@code lsp} or {@code dap}, and the folder of the server in it, if there is one.
     */
    // The templates which share the folder of one install, as the npm package which has both language servers
    private static final Map<String, String> SHARED_FOLDERS = Map.of(
            "lsp/vscode-css-language-server", "vscode-langservers-extracted",
            "lsp/vscode-eslint-language-server", "vscode-langservers-extracted");

    private static final Pattern INSTALL_FOLDER = Pattern.compile("\\.lsp4ij/([^/\"\\\\$]*)(?:/([^/\"\\\\$]*))?");

    /**
     * The templates whose command has no fixed path, which the check of their installer gives them once it finds the
     * server installed (#2011), with the root folder of the archive of their download, if it has one, which the download
     * adds to {@code ${output.dir}}, as {@code clangd_snapshot_20250518/} in {@code clangd-mac-snapshot_20250518.zip}.
     */
    private static final Map<String, String> CHECKS_WHICH_GIVE_THE_COMMAND = Map.of(
            "lsp/clangd", "clangd_snapshot_20250518",
            "lsp/camel-lsp-server", "",
            "lsp/ada_language_server", "integration",
            "lsp/rust-analyzer", "",
            "lsp/wgsl-analyzer", "",
            "dap/codelldb", "",
            "dap/perl-dap", "",
            "dap/vscode-js-debug", "js-debug");

    @Test
    void shRunsTheWholeCommandWhereABundledTemplateUsesIt() throws IOException, URISyntaxException {
        var files = bundledFiles();
        List<String> problems = new ArrayList<>();
        files.forEach((file, json) -> checkStrings(json, file, problems));
        assertTrue(files.size() > 40, "the templates and installers of the language servers and debug adapters: "
                + files.keySet());
        assertEquals(List.of(), problems,
                "sh runs only the argument right after -c: start the command without a shell, or give sh -c the whole command in double quotes");
    }

    @Test
    void aHomeDirectoryWithASpaceStaysOneArgumentInTheCommandsOfTheTemplatesAndTheInstallers()
            throws IOException, URISyntaxException {
        List<String> commands = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        bundledFiles().forEach((file, json) -> {
            List<String> fileCommands = new ArrayList<>();
            Map<String, String> properties = new HashMap<>();
            if (file.endsWith("/template.json")) {
                collectByOs(json.get("programArgs"), fileCommands);
            } else {
                // The command which the installer gives the language server or debug adapter once it has installed
                // it, with the properties of the installer, and the directory where it put it
                var declared = json.getAsJsonObject("properties");
                if (declared != null) {
                    declared.entrySet().forEach(property -> properties.put(property.getKey(),
                            property.getValue().getAsString()));
                }
                collectInstallation(json, properties, fileCommands);
                properties.put("output.file.name", "server");
                // The file that the check found
                properties.put("check.file", "$USER_HOME$/.lsp4ij/server");
            }
            for (String command : fileCommands) {
                String expanded = resolve(command, properties)
                        .replace("$USER_HOME$", HOME)
                        .replace("$PROJECT_DIR$", HOME + "/project");
                if (!expanded.contains(HOME)) {
                    continue;
                }
                commands.add(file + ": " + command);
                List<String> arguments = CommandUtils.createCommands(expanded);
                if (arguments.stream().anyMatch(argument -> argument.endsWith("/Jane") || argument.startsWith("Doe/"))) {
                    problems.add(file + ": " + command + " -> " + arguments);
                }
            }
        });
        assertTrue(commands.size() > 30, "the commands with a path under the home directory: " + commands);
        assertEquals(List.of(), problems, "put the paths of the commands in double quotes");
    }

    /**
     * A template whose installer checks for the language server where its download puts it starts the language server
     * there, as the command that the installer gives it once it has downloaded it: when the check finds the language
     * server installed, the download doesn't run, and the language server keeps the command of its template (#2011).
     * Not the templates that start a language server on the PATH, as cpp_ls, nor those whose installer puts it in a
     * folder named after its version, as clangd. The CSS and ESLint language servers start from the npm package that
     * their installers share (#1960).
     */
    @Test
    void aTemplateStartsItsLanguageServerWhereItsInstallerPutsIt() throws IOException, URISyntaxException {
        var files = bundledFiles();
        List<String> problems = new ArrayList<>();
        for (String server : List.of("clojure-lsp", "erlang-ls", "harper-ls", "jdtls", "lemminx", "luau-lsp", "perl-lsp",
                "vscode-css-language-server", "vscode-eslint-language-server")) {
            var template = files.get("lsp/" + server + "/template.json");
            var installer = files.get("lsp/" + server + "/installer.json");
            assertNotNull(template, server);
            assertNotNull(installer, server);
            for (String os : List.of("windows", "default")) {
                String command = byOs(template.get("programArgs"), os);
                String installed = installedCommand(installer, os);
                if (!command.equals(installed)) {
                    problems.add(server + " (" + os + "): " + command + ", installed: " + installed);
                }
            }
        }
        assertEquals(List.of(), problems, "start the language server where its installer puts it");
    }

    /**
     * A template whose server has no fixed path, as clangd, whose archive has a folder named after the version of clangd,
     * or rust-analyzer, a file by architecture, keeps the command of its template when the check of its installer finds
     * the server installed, since the download doesn't run: so the check gives it the command that the download gives,
     * with the file that it found, {@code ${check.file}}, for each OS and architecture. Only while the server has the
     * command of its template, as when the user didn't change it (#2011).
     */
    @Test
    void aPassingCheckGivesTheServerTheCommandThatItsDownloadGivesIt() throws IOException, URISyntaxException {
        var files = bundledFiles();
        List<String> checked = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        CHECKS_WHICH_GIVE_THE_COMMAND.forEach((server, archiveRoot) -> {
            var template = files.get(server + "/template.json");
            var installer = files.get(server + "/installer.json");
            assertNotNull(template, server);
            assertNotNull(installer, server);
            Map<String, String> properties = new HashMap<>();
            installer.getAsJsonObject("properties").entrySet()
                    .forEach(property -> properties.put(property.getKey(), property.getValue().getAsString()));
            var fileExists = installer.getAsJsonObject("check").getAsJsonObject("fileExists");
            var onSuccess = fileExists.getAsJsonObject("onSuccess");
            if (onSuccess == null || !onSuccess.has("replaceTemplateCommand")) {
                problems.add(server + ": the check gives no command");
                return;
            }
            var checkConfigure = onSuccess.getAsJsonObject("replaceTemplateCommand");
            var download = installer.getAsJsonObject("run").getAsJsonObject("download");
            var output = download.getAsJsonObject("output");
            var outputFile = output.getAsJsonObject("file");
            var downloadConfigure = download.getAsJsonObject("onSuccess").getAsJsonObject("configureServer");
            var templateCommand = template.get(server.startsWith("dap/") ? "launch" : "programArgs");
            for (String os : List.of("windows", "mac", "unix")) {
                Set<String> architectures = new TreeSet<>(architectures(fileExists.get("file"), os));
                architectures.addAll(architectures(outputFile != null ? outputFile.get("name") : null, os));
                architectures.addAll(architectures(download.get("url"), os));
                for (String arch : architectures) {
                    String platform = server + " (" + os + (arch.isEmpty() ? "" : ", " + arch) + ")";
                    String file = forPlatform(fileExists.get("file"), os, arch);
                    String fileName = outputFile != null ? forPlatform(outputFile.get("name"), os, arch) : null;
                    String url = forPlatform(download.get("url"), os, arch);
                    if (fileName == null && url != null && !url.matches(".*\\.(zip|vsix|tar|gz|tgz|txz|xz)")) {
                        // Without a file name, the download keeps the name of the file of its URL, which isn't unpacked
                        fileName = url.substring(url.lastIndexOf('/') + 1);
                    }
                    if (file == null) {
                        if (fileName != null) {
                            problems.add(platform + ": the check looks for no file, the download installs " + fileName);
                        }
                        continue;
                    }
                    checked.add(platform);
                    Map<String, String> downloaded = new HashMap<>(properties);
                    downloaded.put("output.dir", resolve(output.get("dir").getAsString(), properties)
                            + (archiveRoot.isEmpty() ? "" : "/" + archiveRoot));
                    if (fileName != null) {
                        downloaded.put("output.file.name", fileName);
                    }
                    String downloadCommand = resolve(forPlatform(downloadConfigure.get("command"), os, arch), downloaded);
                    // The server that the download installed, which its command starts
                    Matcher quoted = QUOTED.matcher(downloadCommand);
                    if (!quoted.find()) {
                        problems.add(platform + ": the download gives no path in quotes: " + downloadCommand);
                        continue;
                    }
                    String installed = quoted.group(1);
                    String lookedFor = resolve(file, properties);
                    if (!matchesGlob(lookedFor, installed)) {
                        problems.add(platform + ": the check looks for " + lookedFor + ", the download installs " + installed);
                    }

                    Map<String, String> found = new HashMap<>(properties);
                    found.put("check.file", installed);
                    String checkCommand = resolve(forPlatform(checkConfigure.get("command"), os, arch), found);
                    if (!checkCommand.equals(downloadCommand)) {
                        problems.add(platform + ": the check gives " + checkCommand + ", the download " + downloadCommand);
                    }

                    String replaced = forPlatform(checkConfigure.get("templateCommand"), os, arch);
                    String ofTemplate = forPlatform(templateCommand, os, arch);
                    if (!Objects.equals(replaced, ofTemplate)) {
                        problems.add(platform + ": the check replaces the command " + replaced
                                + ", the template has " + ofTemplate);
                    }
                }
            }
        });
        assertTrue(checked.size() > 20, "the OS and architectures of the templates: " + checked);
        assertEquals(List.of(), problems, "the check of an installer should give the command that its download gives");
    }

    /**
     * An installer puts its language server or debug adapter in {@code ~/.lsp4ij/lsp} or {@code ~/.lsp4ij/dap}, in a
     * folder of its own, named after its template, as {@code ~/.lsp4ij/dap/perl-dap} for the debug adapter
     * {@code perl-dap}, and the files of the template, as its command or its initialization options, take it from
     * there: so two templates that download the same archive, as {@code perl-lsp} and {@code perl-dap}, don't share a
     * folder, and the folder of a server is found from its template (#1960). The name is the id of the template, or its
     * directory, as {@code jdt-ls} for the template in {@code jdtls}. Not clangd, which unpacks its archive, a folder
     * named after the version of clangd, into {@code ~/.lsp4ij/lsp} itself (#2011). The CSS and ESLint language servers
     * share the folder of the npm package which has both, {@code vscode-langservers-extracted} (#1960).
     */
    @Test
    void anInstallerPutsItsServerInTheFolderOfItsTemplate() throws IOException, URISyntaxException {
        Map<String, String> templateIds = new HashMap<>();
        bundledFiles().forEach((file, json) -> {
            if (file.endsWith("/template.json")) {
                templateIds.put(file.substring(0, file.lastIndexOf('/')), json.get("id").getAsString());
            }
        });
        var texts = bundledTexts(".*\\.json");
        List<String> folders = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        texts.forEach((file, text) -> {
            // As lsp/perl-lsp
            String template = file.substring(0, file.lastIndexOf('/'));
            String kind = template.substring(0, template.indexOf('/'));
            Set<String> names = Set.copyOf(List.of(template.substring(template.indexOf('/') + 1),
                    templateIds.getOrDefault(template, ""), SHARED_FOLDERS.getOrDefault(template, "")));
            Matcher matcher = INSTALL_FOLDER.matcher(text);
            while (matcher.find()) {
                folders.add(file + ": " + matcher.group());
                if (template.equals("lsp/clangd")) {
                    continue;
                }
                if (!kind.equals(matcher.group(1)) || matcher.group(2) == null || !names.contains(matcher.group(2))) {
                    problems.add(file + ": " + matcher.group());
                }
            }
        });
        assertTrue(folders.size() > 30, "the folders of the installers and of the templates: " + folders);
        assertEquals(List.of(), problems, "install a server in ~/.lsp4ij/lsp or ~/.lsp4ij/dap, in the folder of its template");
    }

    /**
     * An {@code npm install} of an installer runs in the {@code node_modules} folder of its server, as
     * {@code ~/.lsp4ij/lsp/typescript-language-server/node_modules}. npm installs into the first folder, from its
     * working directory up, that has a {@code package.json} or a {@code node_modules} folder: from a
     * {@code node_modules} folder, that's the folder of the server. From the folder of the server itself, a
     * {@code package.json} in the home directory takes the install, and the command that the installer gives the
     * language server doesn't find it (#1960).
     */
    @Test
    void anNpmInstallRunsInTheNodeModulesFolderOfItsServer() throws IOException, URISyntaxException {
        List<String> installs = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        bundledFiles().forEach((file, json) -> {
            if (!file.endsWith("/installer.json")) {
                return;
            }
            Map<String, String> properties = new HashMap<>();
            var declared = json.getAsJsonObject("properties");
            if (declared != null) {
                declared.entrySet().forEach(property -> properties.put(property.getKey(),
                        property.getValue().getAsString()));
            }
            collectNpmInstalls(json, file, properties, installs, problems);
        });
        assertTrue(installs.size() > 5, "the npm installs of the installers: " + installs);
        assertEquals(List.of(), problems, "run npm install in the node_modules folder of the server");
    }

    private static void collectNpmInstalls(@NotNull JsonElement json, @NotNull String file,
                                           @NotNull Map<String, String> properties, @NotNull List<String> installs,
                                           @NotNull List<String> problems) {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has("command") && object.has("workingDir")) {
            List<String> commands = new ArrayList<>();
            collectByOs(object.get("command"), commands);
            if (commands.stream().anyMatch(command -> command.matches("npm(\\.cmd)? install .*"))) {
                String workingDir = resolve(object.get("workingDir").getAsString(), properties);
                installs.add(file + ": " + workingDir);
                if (!workingDir.endsWith("/node_modules")) {
                    problems.add(file + ": " + workingDir);
                }
            }
        }
        object.entrySet().forEach(entry -> collectNpmInstalls(entry.getValue(), file, properties, installs, problems));
    }

    /**
     * An install step that runs a package manager, as {@code go install}, {@code gem install}, {@code pip install},
     * {@code npm install}, {@code cs install} or {@code coursier install}, ignores its standard error. Package managers
     * write their progress there, as {@code go: downloading …}, and an {@code exec} that writes to it fails unless it
     * ignores it: the installation would fail although the server was installed. The exit code of the package manager
     * says whether it installed the server.
     */
    @Test
    void anInstallStepOfAPackageManagerIgnoresItsStandardError() throws IOException, URISyntaxException {
        Pattern install = Pattern.compile("\\b(go|gem|pip3?|npm(\\.cmd)?|cs|coursier) install\\b");
        List<String> installs = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        bundledFiles().forEach((file, json) -> {
            if (file.endsWith("/installer.json") && json.has("run")) {
                collectPackageManagerInstalls(json.get("run"), file, install, installs, problems);
            }
        });
        assertTrue(installs.size() > 10, "the installs of a package manager in the installers: " + installs);
        assertEquals(List.of(), problems, "set \"ignoreStderr\": true on the exec of the install of a package manager");
    }

    private static void collectPackageManagerInstalls(@NotNull JsonElement json, @NotNull String file,
                                                      @NotNull Pattern install, @NotNull List<String> installs,
                                                      @NotNull List<String> problems) {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has("command")) {
            List<String> commands = new ArrayList<>();
            collectByOs(object.get("command"), commands);
            if (commands.stream().anyMatch(command -> install.matcher(command).find())) {
                installs.add(file + ": " + commands);
                JsonElement ignoreStderr = object.get("ignoreStderr");
                if (ignoreStderr == null || !ignoreStderr.getAsBoolean()) {
                    problems.add(file + ": " + commands);
                }
            }
        }
        object.entrySet().forEach(entry -> collectPackageManagerInstalls(entry.getValue(), file, install, installs,
                problems));
    }

    /**
     * Returns the command that the download or the install command of the given installer gives the language server on
     * the given OS, {@code windows} or {@code default}, with the directory and the file of the download, or the
     * properties of the installer, as the working directory of an {@code npm install}.
     */
    private static @NotNull String installedCommand(@NotNull JsonObject installer, @NotNull String os) {
        Map<String, String> properties = new HashMap<>();
        var declared = installer.getAsJsonObject("properties");
        if (declared != null) {
            declared.entrySet().forEach(property -> properties.put(property.getKey(), property.getValue().getAsString()));
        }
        var run = installer.getAsJsonObject("run");
        var exec = run.getAsJsonObject("exec");
        if (exec != null) {
            var command = exec.getAsJsonObject("onSuccess").getAsJsonObject("configureServer").get("command");
            return resolve(byOs(command, os), properties);
        }
        var download = run.getAsJsonObject("download");
        var output = download.getAsJsonObject("output");
        properties.put("output.dir", resolve(output.get("dir").getAsString(), properties));
        var file = output.getAsJsonObject("file");
        if (file != null) {
            properties.put("output.file.name", byOs(file.get("name"), os));
        } else {
            // Without a file name, the download keeps the name of the file of its URL
            String url = byOs(download.get("url"), os);
            properties.put("output.file.name", url.substring(url.lastIndexOf('/') + 1));
        }
        var command = download.getAsJsonObject("onSuccess").getAsJsonObject("configureServer").get("command");
        return resolve(byOs(command, os), properties);
    }

    /**
     * Returns the value for the given OS, {@code windows} or {@code default}, of a value that can differ by OS: a
     * string, or an object by OS, whose {@code default}, or else {@code unix}, is for the OS other than Windows.
     */
    private static @NotNull String byOs(@NotNull JsonElement value, @NotNull String os) {
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        var byOs = value.getAsJsonObject();
        for (String key : os.equals("windows") ? List.of("windows", "default") : List.of("default", "unix")) {
            if (byOs.has(key)) {
                return byOs.get(key).getAsString();
            }
        }
        throw new AssertionError("No value for " + os + " in " + value);
    }

    /**
     * Returns the value for the given OS, {@code windows}, {@code mac} or {@code unix}, and architecture, as
     * {@code arm64}, or empty, of a value that can differ by OS and architecture, as an installer takes it: for the OS,
     * or else {@code default}, then for the architecture, or else {@code default}.
     */
    private static @Nullable String forPlatform(@Nullable JsonElement value, @NotNull String os, @NotNull String arch) {
        JsonElement forArch = forKey(forKey(value, os), arch);
        return forArch != null && forArch.isJsonPrimitive() ? forArch.getAsString() : null;
    }

    /**
     * Returns the architectures for which a value that can differ by OS and architecture differs on the given OS, or
     * only the empty architecture if it doesn't differ by architecture there.
     */
    private static @NotNull Set<String> architectures(@Nullable JsonElement value, @NotNull String os) {
        JsonElement forOs = forKey(value, os);
        return forOs != null && forOs.isJsonObject() ? forOs.getAsJsonObject().keySet() : Set.of("");
    }

    /**
     * Returns the value for the given key, as an OS, or else for {@code default}, of a value by key, or the value itself
     * if it isn't by key.
     */
    private static @Nullable JsonElement forKey(@Nullable JsonElement value, @NotNull String key) {
        if (value == null || !value.isJsonObject()) {
            return value;
        }
        var byKey = value.getAsJsonObject();
        return byKey.has(key) ? byKey.get(key) : byKey.get("default");
    }

    /**
     * Returns true if the given path matches the given path of the check of an installer, whose globs {@code *} and
     * {@code ?} match within one segment.
     */
    private static boolean matchesGlob(@NotNull String glob, @NotNull String path) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            regex.append(switch (c) {
                case '*' -> "[^/]*";
                case '?' -> "[^/]";
                default -> Pattern.quote(String.valueOf(c));
            });
        }
        return path.matches(regex.toString());
    }

    private static @NotNull String resolve(@NotNull String text, @NotNull Map<String, String> properties) {
        String resolved = text;
        for (int i = 0; i < 5; i++) {
            Matcher matcher = PROPERTY.matcher(resolved);
            resolved = matcher.replaceAll(match -> Matcher.quoteReplacement(
                    properties.getOrDefault(match.group(1), match.group())));
        }
        return resolved;
    }

    private static void collectInstallation(@NotNull JsonElement json, @NotNull Map<String, String> properties,
                                            @NotNull List<String> commands) {
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getKey().equals("configureServer") || entry.getKey().equals("replaceTemplateCommand")) {
                collectByOs(entry.getValue().getAsJsonObject().get("command"), commands);
            } else if (entry.getKey().equals("output") && entry.getValue().isJsonObject()
                    && entry.getValue().getAsJsonObject().has("dir")) {
                // A download puts the language server there
                properties.put("output.dir", resolve(entry.getValue().getAsJsonObject().get("dir").getAsString(),
                        properties));
            } else {
                collectInstallation(entry.getValue(), properties, commands);
            }
        });
    }

    private static void collectByOs(JsonElement command, @NotNull List<String> commands) {
        if (command == null) {
            return;
        }
        if (command.isJsonPrimitive()) {
            commands.add(command.getAsString());
        } else if (command.isJsonObject()) {
            command.getAsJsonObject().entrySet().forEach(os -> collectByOs(os.getValue(), commands));
        }
    }

    /**
     * Returns the templates and installers of the bundled language servers and debug adapters, by their path, as
     * {@code lsp/jdtls/installer.json}.
     */
    private static @NotNull Map<String, JsonObject> bundledFiles() throws IOException, URISyntaxException {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        bundledTexts("template\\.json|installer\\.json")
                .forEach((file, text) -> files.put(file, JsonParser.parseString(text).getAsJsonObject()));
        return files;
    }

    /**
     * Returns the text of the files of the bundled language servers and debug adapters whose name matches the given
     * regular expression, by their path, as {@code lsp/jdtls/installer.json}.
     */
    private static @NotNull Map<String, String> bundledTexts(@NotNull String fileNames)
            throws IOException, URISyntaxException {
        Map<String, String> texts = new LinkedHashMap<>();
        for (String kind : List.of("templates/lsp", "templates/dap")) {
            var url = BundledTemplateCommandsTest.class.getClassLoader().getResource(kind);
            assertNotNull(url, kind);
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                // As in the sandbox of the tests, which has the plugin's jar
                try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                    read(jar.provider().getPath(uri), fileNames, texts);
                }
            } else {
                read(Path.of(uri), fileNames, texts);
            }
        }
        return texts;
    }

    private static void read(@NotNull Path templates, @NotNull String fileNames, @NotNull Map<String, String> texts)
            throws IOException {
        try (Stream<Path> paths = Files.walk(templates)) {
            for (Path path : paths.filter(p -> p.getFileName() != null
                    && p.getFileName().toString().matches(fileNames)).toList()) {
                texts.put(templates.getFileName() + "/" + path.getParent().getFileName() + "/" + path.getFileName(),
                        Files.readString(path));
            }
        }
    }

    private static void checkStrings(@NotNull JsonElement json, @NotNull String file, @NotNull List<String> problems) {
        if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(entry -> checkStrings(entry.getValue(), file, problems));
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> checkStrings(element, file, problems));
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String command = json.getAsString();
            List<String> arguments = CommandUtils.createCommands(command);
            if (!arguments.isEmpty() && List.of("sh", "/bin/sh").contains(arguments.get(0))
                    && !(arguments.size() == 3 && arguments.get(1).equals("-c"))) {
                problems.add(file + ": " + command);
            }
        }
    }

    /**
     * The {@code os.arch} that the JVM reports on each OS, by the key of the OS in an installer: OpenJDK sets it in
     * {@code make/autoconf/platform.m4}, as {@code amd64} for x86_64 on every OS but macOS, where it stays
     * {@code x86_64}, {@code i386} for x86 on Linux, and {@code aarch64} for arm64, and on Windows in
     * {@code java_props_md.c}. LSP4IJ takes the value of an installer for the CPU of the IDE, as {@link CpuArch} names it,
     * {@code x86_64}, {@code arm64} or {@code x86}, or else for the {@code os.arch}, or else for {@code default}.
     */
    private static final Map<String, List<String>> OS_ARCHS = Map.of(
            "mac", List.of("x86_64", "aarch64"),
            "windows", List.of("amd64", "aarch64", "x86"),
            "unix", List.of("amd64", "aarch64", "i386", "arm", "riscv64", "ppc64le", "s390x"));

    /**
     * The platforms for which the installers download a server.
     */
    private static final List<Platform> PLATFORMS = List.of(
            new Platform("macOS Apple Silicon", "mac", "aarch64"),
            new Platform("macOS Intel", "mac", "x86_64"),
            new Platform("Linux x64", "unix", "amd64"),
            new Platform("Linux arm64", "unix", "aarch64"),
            new Platform("Windows x64", "windows", "amd64"),
            new Platform("Windows arm64", "windows", "aarch64"));

    private static final Pattern ARM64 = words("aarch64|arm64");
    private static final Pattern X64 = words("x86_64|x64|amd64");
    /**
     * The names of the OSes other than the given one, by its key in an installer.
     */
    private static final Map<String, Pattern> OTHER_OSES = Map.of(
            "mac", words("linux|windows|win32|win64|msvc"),
            "unix", words("darwin|apple|macos|windows|win32|win64|msvc"),
            "windows", words("linux|darwin|apple|macos"));

    /**
     * A step which installs, downloads or configures a server, with what follows the verb.
     */
    private static final Pattern SERVER_STEP = Pattern.compile("(?:Install|Download|Configure) (.+)");
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+");

    /**
     * The dot files that the templates map, which are file names: a pattern without {@code *} or {@code ?} is the exact
     * name of a file for IntelliJ's {@code FileNameMatcherFactoryImpl.createMatcher}.
     */
    private static final Set<String> DOT_FILES = Set.of(".eslintignore", ".eslintrc.json", ".envrc", ".hushlogin");

    /**
     * A value of an installer for a CPU, as {@code "mac": {"x86_64": ..., "arm64": ...}}, is one that LSP4IJ picks on
     * some platform of its OS: the JVM reports {@code x86_64} on an Intel Mac, so {@code amd64} is never picked on macOS,
     * {@code 386} never anywhere, and {@code aarch64} never where {@code arm64} is there too.
     */
    @Test
    void everyValueOfAnInstallerForACpuIsOneThatLsp4ijPicks() throws IOException, URISyntaxException {
        List<String> values = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        bundledFiles().forEach((file, json) -> {
            if (file.endsWith("/installer.json")) {
                checkValuesByCpu(json, file, values, problems);
            }
        });
        assertTrue(values.size() > 50, "the values of the installers for a CPU: " + values);
        assertEquals(List.of(), problems,
                "give a value for a CPU that LSP4IJ picks: x86_64, arm64 or x86, or an os.arch of the OS");
    }

    /**
     * On macOS on Apple Silicon and on Intel, and on Linux and Windows on x64 and on arm64, the download of an installer
     * has a URL or a GitHub asset, and what an installer takes there by OS and CPU, the asset, the URL and the file of a
     * download and the file that a check looks for, names no other OS, nor a CPU that the platform can't run: Windows and
     * macOS on arm64 run x64 programs, Linux on arm64 doesn't.
     */
    @Test
    void anInstallerDownloadsItsServerForEachPlatform() throws IOException, URISyntaxException {
        List<String> downloads = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        bundledFiles().forEach((file, json) -> {
            if (!file.endsWith("/installer.json")) {
                return;
            }
            for (Platform platform : PLATFORMS) {
                String where = file + " (" + platform.name() + ")";
                for (var task : tasks(json, "check", "run")) {
                    JsonObject body = task.getValue();
                    if (task.getKey().equals("download")) {
                        downloads.add(where);
                        var github = body.getAsJsonObject("github");
                        String asset = github != null ? platform.get(github, "asset") : null;
                        String url = platform.get(body, "url");
                        if (asset == null && url == null && !body.has("maven")) {
                            problems.add(where + ": no URL nor asset");
                        }
                        var output = body.getAsJsonObject("output");
                        var outputFile = output != null ? output.getAsJsonObject("file") : null;
                        String fileName = outputFile != null ? platform.get(outputFile, "name") : null;
                        Stream.of(asset, url, fileName).forEach(value -> platform.check(value, where, problems));
                    } else if (task.getKey().equals("fileExists")) {
                        platform.check(platform.get(body, "file"), where, problems);
                    }
                }
            }
        });
        assertTrue(downloads.size() > 100, "the downloads of the installers on each platform: " + downloads);
        assertEquals(List.of(), problems, "download the server of each platform");
    }

    /**
     * A step of an installer, its check and its run, and the onSuccess and the onFail of a task, has one task: LSP4IJ
     * loads only the first key of a step, so an onSuccess beside the exec of a run never runs.
     */
    @Test
    void aStepOfAnInstallerHasOneTask() throws IOException, URISyntaxException {
        List<String> steps = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        bundledFiles().forEach((file, json) -> {
            if (file.endsWith("/installer.json")) {
                for (String step : List.of("check", "run")) {
                    checkStep(json.getAsJsonObject(step), file + ": " + step, steps, problems);
                }
            }
        });
        assertTrue(steps.size() > 60, "the steps of the installers: " + steps);
        assertEquals(List.of(), problems, "give a step one task, and its onSuccess and onFail in the task");
    }

    /**
     * The check of an installer runs the command of its server, {@code ${server.command}}, only if its template gives the
     * server a command: a debug adapter that LSP4IJ attaches to, as debugpy, which the user starts, has none, so the
     * check would never pass, and the installer would install it again at the first debug of each IDE session.
     */
    @Test
    void aCheckRunsTheCommandOfItsServerOnlyIfItsTemplateHasOne() throws IOException, URISyntaxException {
        var files = bundledFiles();
        List<String> checks = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        files.forEach((file, json) -> {
            if (!file.endsWith("/installer.json")) {
                return;
            }
            var template = files.get(file.replace("/installer.json", "/template.json"));
            assertNotNull(template, file);
            List<String> templateCommands = new ArrayList<>();
            collectByOs(template.get(file.startsWith("dap/") ? "launch" : "programArgs"), templateCommands);
            for (var task : tasks(json, "check")) {
                List<String> commands = new ArrayList<>();
                if (task.getKey().equals("exec")) {
                    collectByOs(task.getValue().get("command"), commands);
                }
                if (commands.contains("${server.command}")) {
                    checks.add(file);
                    if (templateCommands.stream().allMatch(String::isBlank)) {
                        problems.add(file + ": " + task.getValue().get("name").getAsString());
                    }
                }
            }
        });
        assertTrue(checks.size() > 10, "the checks which run the command of their server: " + checks);
        assertEquals(List.of(), problems, "check for the server with a command that ends, as python -m debugpy --version");
    }

    /**
     * The file name patterns of a template match the files that they are for: a pattern without {@code *} or {@code ?}
     * is the exact name of a file, so an extension is {@code *.tmpl}, as {@code .tmpl} matches only a file named
     * {@code .tmpl}; and a mapping has a pattern once.
     */
    @Test
    void theFileNamePatternsOfATemplateMatchTheFilesThatTheyAreFor() throws IOException, URISyntaxException {
        List<String> patterns = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        forEachFileTypeMapping((file, fileType, languageId) -> {
            var mappingPatterns = fileType.getAsJsonArray("patterns");
            if (mappingPatterns == null) {
                return;
            }
            List<String> seen = new ArrayList<>();
            mappingPatterns.forEach(element -> {
                String pattern = element.getAsString();
                patterns.add(file + ": " + pattern);
                boolean fileName = !pattern.contains("*") && !pattern.contains("?");
                if (fileName && pattern.startsWith(".") && !DOT_FILES.contains(pattern)) {
                    problems.add(file + ": " + pattern + " is the name of a file, not an extension");
                }
                if (seen.contains(pattern)) {
                    problems.add(file + ": " + pattern + " twice");
                }
                seen.add(pattern);
            });
        });
        assertTrue(patterns.size() > 200, "the file name patterns of the templates: " + patterns);
        assertEquals(List.of(), problems,
                "write an extension as *.ext, and add the dot files that a template maps to DOT_FILES");
    }

    /**
     * A file type that a template names goes to one language: in an IDE which has a file type of that name, LSP4IJ maps
     * the file type, and not the patterns of the mapping, so the shell scripts of a mapping named Scala would go to the
     * Scala files.
     */
    @Test
    void aFileTypeOfATemplateGoesToOneLanguage() throws IOException, URISyntaxException {
        Map<String, String> languages = new HashMap<>();
        List<String> problems = new ArrayList<>();
        forEachFileTypeMapping((file, fileType, languageId) -> {
            var name = fileType.get("name");
            if (name == null) {
                return;
            }
            String previous = languages.putIfAbsent(file + ": " + name.getAsString(), languageId);
            if (previous != null && !previous.equals(languageId)) {
                problems.add(file + ": " + name.getAsString() + " goes to " + previous + " and to " + languageId);
            }
        });
        assertTrue(languages.size() > 50, "the file types of the templates: " + languages);
        assertEquals(List.of(), problems, "name the file type of each language of a template");
    }

    /**
     * gopls reads a file that it gets as {@code gotmpl} or {@code tmpl} as a template, but finds the templates that
     * aren't open, which the definitions, references and completions in a template look in, by the extensions of its
     * {@code templateExtensions} setting, which has none by default. So the bundled settings of gopls give the
     * extensions of the files that its template sends it as templates, without the dot: gopls compares them with the
     * part of a file name after its last dot.
     */
    @Test
    void goplsFindsTheTemplatesThatItsTemplateSendsItAlsoWhenTheyAreNotOpen() throws IOException, URISyntaxException {
        Pattern extension = Pattern.compile("\\*\\.(\\w+)");
        List<String> extensions = new ArrayList<>();
        forEachFileTypeMapping((file, fileType, languageId) -> {
            var patterns = fileType.getAsJsonArray("patterns");
            if (file.equals("lsp/gopls/template.json") && Set.of("gotmpl", "tmpl").contains(languageId)
                    && patterns != null) {
                patterns.forEach(pattern -> {
                    Matcher matcher = extension.matcher(pattern.getAsString());
                    extensions.add(matcher.matches() ? matcher.group(1) : pattern.getAsString());
                });
            }
        });
        var settings = JsonParser.parseString(bundledTexts("settings\\.json").get("lsp/gopls/settings.json"))
                .getAsJsonObject().getAsJsonObject("gopls");
        List<String> templateExtensions = new ArrayList<>();
        settings.getAsJsonArray("build.templateExtensions")
                .forEach(element -> templateExtensions.add(element.getAsString()));
        assertEquals(List.of("tmpl", "gotmpl"), extensions, "the extensions of the Go templates in the gopls template");
        assertEquals(extensions, templateExtensions,
                "give build.templateExtensions of the gopls settings the extensions of its Go template mapping");
    }

    /**
     * A step that installs, downloads or configures a server names it as its template or its installer does: the word
     * after Install, Download or Configure is the first word of the name or of the id of the template or of the
     * installer, as Svelte in "Configure Svelte Language Server command", and not a server that it was copied from.
     */
    @Test
    void aStepNamesTheServerOfItsInstaller() throws IOException, URISyntaxException {
        var files = bundledFiles();
        List<String> names = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        files.forEach((file, json) -> {
            if (!file.endsWith("/installer.json")) {
                return;
            }
            var template = files.get(file.replace("/installer.json", "/template.json"));
            assertNotNull(template, file);
            List<String> serverWords = Stream.of(template.get("name"), template.get("id"), json.get("name"), json.get("id"))
                    .filter(value -> value != null && value.isJsonPrimitive())
                    .map(value -> firstWord(value.getAsString()))
                    .toList();
            for (var task : tasks(json, "check", "run")) {
                var name = task.getValue().get("name");
                Matcher step = name != null && name.isJsonPrimitive() ? SERVER_STEP.matcher(name.getAsString()) : null;
                if (step != null && step.matches()) {
                    names.add(file + ": " + name.getAsString());
                    if (!serverWords.contains(firstWord(step.group(1)))) {
                        problems.add(file + ": " + name.getAsString() + ", not " + serverWords);
                    }
                }
            }
        });
        assertTrue(names.size() > 50, "the steps that install, download or configure a server: " + names);
        assertEquals(List.of(), problems, "name the server of the installer in its steps");
    }

    /**
     * A platform for which an installer downloads a server.
     *
     * @param name   the name of the platform.
     * @param os     the key of its OS in an installer.
     * @param osArch the {@code os.arch} that the JVM reports there.
     */
    private record Platform(@NotNull String name, @NotNull String os, @NotNull String osArch) {

        /**
         * Returns the value that an installer takes on this platform for the given property, by OS and CPU.
         */
        @Nullable String get(@NotNull JsonObject json, @NotNull String property) {
            return InstallerTaskFactoryBase.getStringFromOs(json, property, os, CpuArch.fromString(osArch), osArch);
        }

        /**
         * Adds a problem if the given value, which an installer takes on this platform, names another OS, or a CPU that
         * this platform can't run.
         */
        void check(@Nullable String value, @NotNull String where, @NotNull List<String> problems) {
            if (value == null) {
                return;
            }
            boolean arm64 = CpuArch.fromString(osArch) == CpuArch.ARM64;
            if (!arm64 && ARM64.matcher(value).find()
                    || arm64 && os.equals("unix") && X64.matcher(value).find()
                    || OTHER_OSES.get(os).matcher(value).find()
                    || !os.equals("windows") && value.endsWith(".exe")) {
                problems.add(where + ": " + value);
            }
        }
    }

    @FunctionalInterface
    private interface FileTypeMappingConsumer {
        void accept(@NotNull String file, @NotNull JsonObject fileType, @NotNull String languageId);
    }

    /**
     * Calls the given consumer with each file type mapping of the bundled templates.
     */
    private static void forEachFileTypeMapping(@NotNull FileTypeMappingConsumer consumer)
            throws IOException, URISyntaxException {
        bundledFiles().forEach((file, json) -> {
            if (file.endsWith("/template.json") && json.has("fileTypeMappings")) {
                json.getAsJsonArray("fileTypeMappings").forEach(mapping -> consumer.accept(file,
                        mapping.getAsJsonObject().getAsJsonObject("fileType"),
                        mapping.getAsJsonObject().get("languageId").getAsString()));
            }
        });
    }

    /**
     * Returns the tasks of the given steps of the given installer that LSP4IJ loads, by their type, as {@code exec}:
     * the first key of each step, and of the onSuccess and the onFail of each task, as {@code InstallerTaskRegistry}
     * loads them.
     */
    private static @NotNull List<Map.Entry<String, JsonObject>> tasks(@NotNull JsonObject installer,
                                                                      @NotNull String... steps) {
        List<Map.Entry<String, JsonObject>> tasks = new ArrayList<>();
        for (String step : steps) {
            addTasks(installer.getAsJsonObject(step), tasks);
        }
        return tasks;
    }

    private static void addTasks(@Nullable JsonObject step, @NotNull List<Map.Entry<String, JsonObject>> tasks) {
        if (step == null || step.size() == 0) {
            return;
        }
        String type = step.keySet().iterator().next();
        JsonObject task = step.getAsJsonObject(type);
        tasks.add(Map.entry(type, task));
        addTasks(task.getAsJsonObject("onSuccess"), tasks);
        addTasks(task.getAsJsonObject("onFail"), tasks);
    }

    private static void checkStep(@Nullable JsonObject step, @NotNull String path, @NotNull List<String> steps,
                                  @NotNull List<String> problems) {
        if (step == null) {
            return;
        }
        steps.add(path);
        if (step.size() != 1) {
            problems.add(path + ": " + step.keySet());
        }
        for (var task : step.entrySet()) {
            if (task.getValue().isJsonObject()) {
                for (String next : List.of("onSuccess", "onFail")) {
                    checkStep(task.getValue().getAsJsonObject().getAsJsonObject(next),
                            path + "/" + task.getKey() + "/" + next, steps, problems);
                }
            }
        }
    }

    /**
     * Adds a problem for each value of the given JSON by CPU, as {@code "mac": {"x86_64": ..., "arm64": ...}}, that
     * LSP4IJ picks for none of the {@code os.arch} of its OS.
     */
    private static void checkValuesByCpu(@NotNull JsonElement json, @NotNull String file, @NotNull List<String> values,
                                         @NotNull List<String> problems) {
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            String os = entry.getKey();
            List<String> osArchs = OS_ARCHS.get(os);
            if (osArchs == null || !entry.getValue().isJsonObject()) {
                checkValuesByCpu(entry.getValue(), file, values, problems);
                return;
            }
            // The values that LSP4IJ picks on this OS, for each of its os.arch
            JsonObject byOs = new JsonObject();
            byOs.add(os, entry.getValue());
            JsonObject property = new JsonObject();
            property.add("value", byOs);
            List<String> picked = osArchs.stream()
                    .map(osArch -> InstallerTaskFactoryBase.getStringFromOs(property, "value", os,
                            CpuArch.fromString(osArch), osArch))
                    .toList();
            entry.getValue().getAsJsonObject().entrySet().forEach(cpu -> {
                values.add(file + ": " + os + "/" + cpu.getKey());
                if (!cpu.getValue().isJsonPrimitive() || !picked.contains(cpu.getValue().getAsString())) {
                    problems.add(file + ": " + os + "/" + cpu.getKey() + ": " + cpu.getValue());
                }
            });
        });
    }

    private static @NotNull String firstWord(@NotNull String text) {
        Matcher word = WORD.matcher(text);
        return word.find() ? word.group().toLowerCase() : "";
    }

    private static @NotNull Pattern words(@NotNull String words) {
        return Pattern.compile("(?<![a-z0-9])(?:" + words + ")(?![a-z0-9])", Pattern.CASE_INSENSITIVE);
    }
}
