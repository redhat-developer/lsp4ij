# User-defined language server

LSP4IJ provides the capability to consume any language server without developing
an IntelliJ plugin via a `User-defined language server`.

![New Language Server Dialog with TypeScript](images/user-defined-ls/typescript-language-server/select_template.png)

The main idea is to: 

 * install the language server and its requirements(ex : `Node.js` to 
execute a language server written in JavaScript/TypeScript), 
 * declare the command that starts the language server.
 * associate the language server with the proper files (identified by IntelliJ Language, File Type or file name pattern)

## New Language Server dialog

In order to create a new `User-defined language server`, you need to open the `New Language Server` dialog, either:

 * from the menu on the right of the LSP console:

![New Language Server From Console](./images/user-defined-ls/NewLanguageServerFromConsole.png)

* or with the `[+]` on the top of the language server settings:

![New Language Server From Settings](./images/user-defined-ls/NewLanguageServerFromSettings.png)

Once you clicked on either of them, the dialog will appear:

![New Language Server Dialog](./images/user-defined-ls/NewLanguageServerDialogEmpty.png)

When you click the `OK` button, the language server will be created.

If the language server definition [declares an installer](UserDefinedLanguageServerTemplate.md#installer-descriptor)  
in the [Installer tab](#installer-tab), the installer will run once when the language server starts.

You can reinstall the server at any time (e.g. to fetch the latest version) using the `Reinstall` action:

![Install actions](./images/InstallActions.png)

### Server tab

The `Server tab` requires the `server name` and `command` fields to be set. A language server which runs already, and
to which LSP4IJ attaches over a socket, needs no command, but its port (see [Transport](#transport)).

Below the `Working Directory` field, a line shows the directory with its macros resolved, and says when no such directory
exists yet: the language server can only start in one that exists, which its installer can still create.

Here is a sample with the [typescript-language-server](https://github.com/typescript-language-server/typescript-language-server):

![New Language Server Dialog with TypeScript](images/user-defined-ls/typescript-language-server/select_template.png)

#### Environment variables

The environment variables accessible by the process are populated with 
[EnvironmentUtil.getEnvironmentMap()](https://github.com/JetBrains/intellij-community/blob/3a527a2c9b56209c09852ba7bc89d80bc31e1c04/platform/util/src/com/intellij/util/EnvironmentUtil.java#L85) 
which retrieves system variables.

It is also possible to add custom environment variables via the `Environment variables` field:

![Environment Variables](./images/user-defined-ls/EnvironmentVariables.png)

Depending on your OS, the environment variables may not be accessible. To make sure they are accessible, you can fill out the order fields:

* with `Windows OS`: `cmd /c command_to_start_your_ls`
* with `Linux`, `Mac OS`: `sh -c command_to_start_your_ls`

##### Directory-specific environment variables

Since IntelliJ 2025.1, a plugin can set up the environment of a process for its working directory (the
`commandLineEnvCustomizer` extension point), as the [mise](https://mise.jdx.dev/) plugin does from a `mise.toml`, or a
[direnv](https://direnv.net/) plugin from an `.envrc`. The language server's process gets that environment when it starts.
When a working directory is set, LSP4IJ also looks up the language server's command in the `PATH` of that environment, so
that the tool which the directory selects, such as its version of `node`, is the one that starts.

> [!NOTE]
> - **Set the working directory**: the environment is the one of that directory. Without one, the command is looked up in the IDE's `PATH`.
> - **Precedence**: the directory's environment is applied when the process starts, after the environment variables configured here, so it wins over a variable that both set.
> - **Windows**: a command without its extension, such as `node` or `npx`, is looked up with the extensions in `PATHEXT`, as Windows does, so that the directory's `node.exe` or `npx.cmd` starts.
> - **Tools not bundled**: LSP4IJ doesn't bundle mise or direnv. Install the tool and its IntelliJ plugin. The mise plugin customizes the processes of other plugins only with its `useMiseInAllCommandLines` setting, which is off by default.
> - **IntelliJ 2024.x** has no such extension point: the environment and the lookup are as before.

#### Macro syntax

You can use [built-in macros](https://www.jetbrains.com/help/idea/built-in-macros.html) in your command. You could, for instance, store the language server in your project (to share it with your team) 
and write a command that references it in a portable way to start it.

That command might look like this:

`$PROJECT_DIR$/path/to/your/start/command`

Here are some useful standard [built-in macros](https://www.jetbrains.com/help/idea/built-in-macros.html) that you can use:

 * `$WORKSPACE_DIR$`: The path to the workspace where the current project belongs. The workspace is the root of the open file hierarchy and can include multiple projects.
 * `$PROJECT_DIR$`: The root of the project where run.json is located. A project is typically a collection of files for developing and building an application such as a Maven or Node.js project.
 * `$USER_HOME$`: User home directory.

Here is an example with Scala Language Server's `metals.bat` stored at the root of the project:

![Macro syntax](images/user-defined-ls/Macro.png)

When commands contain macros, their resolved value is visible below the `Command` field.

#### Transport

By default, LSP4IJ talks to the language server over the standard input and output (stdio) of its process. A language
server which talks LSP over a TCP socket instead is configured with `Transport: Socket (experimental)`, in one of three
ways, which are experimental:

![Transport](./images/user-defined-ls/SocketTransportModes.png)

* `Attach to a running language server`: the language server runs already and listens on `Host` and `Port`, as when it
  runs in a debugger or inside another application. LSP4IJ doesn't start it, so the environment variables, the
  `Command` and the working directory are hidden, and stopping the language server only closes the connection. Without a host, LSP4IJ connects to the loopback address; a host is a plain host name or IP address, without a scheme,
  and the port has its own field. An attach that gets no answer within 10 seconds fails.
  The port is needed, since LSP4IJ can't choose it.
* `Start the language server, LSP4IJ connects to it`: LSP4IJ starts the language server with the `Command`, and
  connects to the port once the language server accepts connections.
* `Start the language server, it connects to LSP4IJ`: LSP4IJ listens on the port and starts the language server with
  the `Command`, which connects to it, as VS Code's socket transport does for a language server started with
  `--socket=<port>`.

When LSP4IJ starts the language server, the `Command` takes the port as `${port}`, and a `Port` of `Auto` lets LSP4IJ
choose a free port each time: the `Command` must then have `${port}`, or the language server can't know the port. A
start that gets no connection within 60 seconds fails. For instance, with a language server built on `vscode-languageserver`:

`node path/to/server.js --socket=${port}`

![Socket transport](./images/user-defined-ls/SocketTransport.png)

An [installer](#installer-tab) which sets the `Command` writes the command of its template, which is for the standard
input and output unless it takes the port as `${port}`. A language server which LSP4IJ starts over a socket is then
switched back to the standard input and output; set the socket again with a command that takes the port.

LSP4IJ listens on the loopback address only. The output of a language server which LSP4IJ starts is shown in the `Log`
tab of the [LSP console](./UserGuide.md#lsp-console), since the socket carries the protocol: its standard output as
information, and its standard error as errors. As with any TCP transport
of LSP, which has no handshake to authenticate its peer, any local process can connect to a loopback port: on a machine
shared with other users, prefer the standard input and output.

### Mappings tab

The `Mappings tab` provides the capability to `associate the language server with the proper files` identified by: 

 * [IntelliJ  Language](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html) 
 * [IntelliJ File type](https://www.jetbrains.com/help/idea/creating-and-registering-file-types.html) 
 * `File name pattern`

Here are mappings samples with the [typescript-language-server](https://github.com/typescript-language-server/typescript-language-server):

 * The existing `JavaScript` file type is used to associate the file to the language server: 

![TypeScript file type](images/user-defined-ls/typescript-language-server/TypeScriptServerDialog_FileType.png)

* Since IntelliJ (Community) doesn't provide file type by default `TypeScript`, `React` file name patterns are used:

![TypeScript file name patterns](images/user-defined-ls/typescript-language-server/TypeScriptServerDialog_FileNamePatterns.png)

NOTE: it is better to use file name pattern instead of creating custom file type for TypeScript, since by default 
IntelliJ Community support `TypeScript syntax coloration` with `TextMate`. If you define a file type, you will
lose syntax coloration.

#### Language ID

When you declare mapping, you can fill the `Language ID` column which is used to declare the LSP [TextDocumentItem#languageId](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentItem)
to identify the document on the server side.

For instance the [vscode-css-languageservice](https://github.com/microsoft/vscode-css-languageservice) (used by the vscode CSS language server) expects the `languageId` to be `css` or `less`.
To do that, you can declare it with the `languageId` attribute:

### Configuration tab

The `Configuration tab` allows to configure the language server with the expected (generally JSON format) configuration. 

Here are configuration sample with the [typescript-language-server](https://github.com/typescript-language-server/typescript-language-server):

![Configuration with TypeScript](images/user-defined-ls/typescript-language-server/TypeScriptServerDialog_Configuration.png)

A property set to `null` in `Configuration` or `Initialization Options` reaches the language server as `null`, so that
it can reset that setting to its default.

LSP4IJ must be able to read the JSON fields: `Configuration`, `Initialization Options` and `Experimental` in the
`Server` tab of the `Configuration` tab, the configuration in its `Client` tab, and the JSON of the `Installer` and
`Workspace Folders` tabs. It reads them leniently, so comments (`//`, `/* */` and `#`), unquoted names and
single-quoted strings are accepted, but a missing brace or a trailing comma is not. `Configuration`,
`Initialization Options` and `Experimental` must stay readable once their macros, such as `$PROJECT_DIR$`, are
expanded, which a Windows path in a string may not. A language server with a field that can't be read is not saved:
the `New Language Server` dialog lists the errors and disables `OK`, and the settings page and the `Apply` action of
the LSP console show the error.

### Debug tab

The `Debug tab` is available when you have created the language server definition. It allows to customize the 
level Trace used in LSP console.

### Installer tab

The `Installer tab` provides the capability to declare with JSON, the language server installation
with task to execute like:

 * `exec` to execute a command (ex: `npm install typescript-language-server` to install the [typescript-language-server](https://github.com/typescript-language-server/typescript-language-server)).
 * `download` to download the language server from a given url according the OS.

![Installer tab](images/user-defined-ls/InstallUserDefinedLanguageServer.png)

This installer content will be used when you will create the language server. See [here](UserDefinedLanguageServerTemplate.md#installer-descriptor)
to understand how to write this JSON installer descriptor.

### Workspace Folders tab

[Workspace folders](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspaceFolder) define the scope of files that the language server will analyze. They can be sent to the language server:

 * At startup via the [initialize](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#initialize) request
 * Dynamically via [workspace/didChangeWorkspaceFolders](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didChangeWorkspaceFolders) notifications

This tab allows you to configure and preview how workspace folders will be discovered and sent to your language server.

#### Configuration strategies

##### Root type

The `rootType` defines which IntelliJ project structure to use as workspace folders:

 * `PROJECT_BASE` (default): Uses [content roots](https://www.jetbrains.com/help/idea/content-roots.html#adding_content_root)
 * `SOURCE_ROOTS`: Uses [source roots](https://www.jetbrains.com/help/idea/content-roots.html#folder-categories)  
 * `NONE`: No workspace folders

```json
{
    "rootType": "SOURCE_ROOTS"
}
```

This configuration will populate `InitializeParams.workspaceFolders` when the language server starts:

```json
{
  "workspaceFolders": [
    {
      "name": "my-module",
      "uri": "file:///path/to/my-module"
    }
  ]
}
```

##### Marker-based discovery

If `rootType` strategies don't match your project structure, use markers to define workspace folders based on specific files. This is useful for:

- Mono-repos with multiple independent modules
- Python projects with `pyproject.toml` or `setup.py`
- Projects where workspace boundaries are defined by configuration files

```json
{
  "markers": [
    "pyproject.toml",
    "pom.xml",
    ".git"
  ]
}
```

When a file is opened, LSP4IJ walks up the directory tree to find the closest marker file and uses its directory as the workspace folder.

**Note:** Marker-based discovery always uses [lazy mode](#lazy-mode).

#### Lazy mode

By default (eager mode), all workspace folders are sent at startup via the `initialize` request. For large projects, this can cause the language server to load and analyze many files unnecessarily.

**Lazy mode** defers workspace folder discovery: folders are sent progressively via `workspace/didChangeWorkspaceFolders` notifications as you open files.

```json
{
    "rootType": "PROJECT_BASE",
    "lazy": true
}
```

**Example:** Consider a project with 2 [content roots](https://www.jetbrains.com/help/idea/content-roots.html#adding_content_root): `client` and `common`.

![Project content roots](./images/workspace-folders/ProjectContentRoots.png)

**Eager mode** (`lazy: false`):
- All folders sent at startup in the `initialize` request:
  ```json
  {
    "workspaceFolders": [
      { "uri": "file:///.../client", "name": "client" },
      { "uri": "file:///.../common", "name": "common" }
    ]
  }
  ```
- Language server loads **all** files from both `client` and `common`, even if you only open a file in `common`

You can see this behavior with the following configuration:

![Root type - PROJECT_BASE](./images/workspace-folders/RootType_PROJECT_BASE.png)

**Lazy mode** (`lazy: true`):
- Empty workspace folders at startup:
  ```json
  {
    "workspaceFolders": []
  }
  ```
- When you open a file from `common`, only that folder is sent via `workspace/didChangeWorkspaceFolders`:
  ```json
  {
    "event": {
      "added": [{ "uri": "file:///.../common", "name": "common" }],
      "removed": []
    }
  }
  ```

#### Several language server instances

By default, one language server instance serves all workspace folders of a project. If parts of a project need a server process of their own, for example because each one has its own environment, `serverInstances` says which instance serves a file. It takes `rootType` or `markers`, as described above:

```json
{
    "serverInstances": {
        "markers": ["pyproject.toml"]
    }
}
```

Files with the same root share an instance:

 * An instance starts when the first of its files is opened. The Language Servers view shows it as a process of its own, with the path of its root in the project. Its node stays when the instance stops or is replaced, with its traces and logs, for the next instance of that root.
 * It's initialized with its root as `rootUri`.
 * A file without a root gets no instance, so this language server provides no features for it. LSP4IJ says so once, when such a file is open in an editor.
 * With markers, the search stops at the project: a file without a marker up to its topmost content root has that content root as its root, so no instance runs above the project. In a project with content roots, a file outside all of them has no root then.
 * All instances use the same command. If no working directory is configured, an instance runs in its root.
 * Stop on an instance in the Language Servers view, or too many failed starts of it, switch off that instance only, and the others keep working. Restart switches it on again, and so does a changed configuration.
 * Each instance needs a connection of its own. Over a socket, use an automatic port, and let LSP4IJ start the language server: the settings don't save `serverInstances` with a language server that runs already, or with a fixed port.

Without `rootType` and `markers` next to `serverInstances`, the root is the only workspace folder of an instance. With them, they describe the workspace folders inside an instance:

```json
{
    "serverInstances": {
        "markers": ["pyproject.toml"]
    },
    "rootType": "SOURCE_ROOTS"
}
```

Here, each folder with a `pyproject.toml` has an instance, and that instance gets the source roots inside it. With `"lazy": true`, it gets them through `workspace/didChangeWorkspaceFolders` as their files are opened. A workspace folder reaches the instance that its own directory has as root: a folder of another instance doesn't, nor does a folder above the root, such as one that contains several instances. An instance that would start without a folder gets its root, unless `rootType` next to `serverInstances` is `NONE`. In lazy mode, an instance starts without folders and gets those of its files as they are opened, and a file outside all of them adds none.

In this example, `backend` and `frontend` each contain a `pyproject.toml` and source roots, so each has its own instance. `scripts/tool.py` has no marker up to the project's folder, so that folder is its root, with an instance of its own:

![Several language server instances](./images/workspace-folders/ServerInstances.png)

Changing this configuration, the command, the working directory, the environment variables or the mappings replaces the instances. Those with an open file start again, the others when one of their files is opened.

#### Testing your configuration

The **preview panel** on the right shows how workspace folders will be sent based on your configuration.

**Checkbox unchecked** - Shows all discovered workspace folders (discovery mode):

![Root type - PROJECT_BASE - lazy](./images/workspace-folders/RootType_PROJECT_BASE_lazy.png)

**Checkbox checked** - Shows only folders sent at initialization (empty in lazy mode):

![Root type - PROJECT_BASE - lazy](./images/workspace-folders/RootType_PROJECT_BASE_lazy_checkInit_empty.png)

**Drag & drop or click "Open file..."** to simulate opening a file and see which workspace folder it belongs to:

![Root type - PROJECT_BASE - lazy](./images/workspace-folders/RootType_PROJECT_BASE_lazy_checkInit_openFile.png)

With `serverInstances`, the preview shows each language server instance with its workspace folders below it, so a file you open shows which instance serves it.

If a file is not included in any workspace folder, it will appear under a "(No root)" node:

![Root type - PROJECT_BASE - lazy](./images/workspace-folders/RootType_PROJECT_BASE_noRoot.png)

## Using template
Template can be used to quickly create user defined language server pre-filled 
with server name, command, mappings and potential configurations.

#### Default template
The `Template combo-box` provides some default language servers templates (located in templates directory classpath), 
pre-filled with server name, command, mappings and potential configuration.

![New Language Server with Default Template](./images/user-defined-ls/NewLanguageServerWithDefaultTemplate.png)

* [Ada Language Server](./user-defined-ls/ada_language_server.md)
* [Apache Camel Language Server](./user-defined-ls/camel-lsp-server.md)
* [Astro Language Server](./user-defined-ls/astro-ls.md)
* [CSS Language Server](./user-defined-ls/vscode-css-language-server.md)
* [Clangd](./user-defined-ls/clangd.md) 
* [Clojure LSP](./user-defined-ls/clojure-lsp.md)
* [Dart LSP](./user-defined-ls/dart-lsp.md) 
* [Docker Language Server](./user-defined-ls/docker-language-server.md)
* [EO LSP Server](./user-defined-ls/eo-lsp-server.md) 
* [Erlang Language Server](./user-defined-ls/erlang-ls.md)
* [ESLint Language Server](./user-defined-ls/vscode-eslint-language-server.md) 
* [Go Language Server](./user-defined-ls/gopls.md)
* [Harper Language Server](./user-defined-ls/harper-ls.md)
* [JQ Language Server](./user-defined-ls/jq-lsp.md)
* [Julia Language Server](./user-defined-ls/julia.md)
* [Perl Language Server](./user-defined-ls/perl-lsp.md)
* [Ruby LSP](./user-defined-ls/ruby-lsp.md)
* [Rust Language Server](./user-defined-ls/rust-analyzer.md) 
* [Scala Language Server (Metals)](./user-defined-ls/metals.md)
* [SourceKit-LSP](./user-defined-ls/sourcekit-lsp.md)
* [Stylelint-LSP](./user-defined-ls/stylelint-lsp.md)
* [Svelte Language Server](./user-defined-ls/svelte-language-server.md) 
* [SysML v2 Language Server](./user-defined-ls/sysml-lsp.md)
* [Terraform Language Server](./user-defined-ls/terraform-ls.md)
* [TypeScript Language Server](./user-defined-ls/typescript-language-server.md)
* [Vue Language Server](./user-defined-ls/vue-js-language-server.md)
* [WGSL Analyzer](./user-defined-ls/wgsl-analyzer.md)

If the template directory contains a `README.md` file, you can open the instructions by pressing the help icon.

#### Custom template

The `Import from custom template...` item from the `Template combo-box` can be used to select a directory from 
the file system to load a custom language server template, 
these templates can be pre-filled with server name, command, mappings and potential configuration.

The selected directory contents should match the [custom template structure](#custom-template-structure).
If the template directory contains a `README.md` file, you can open the instructions by pressing the help icon.

Custom templates can be created by [exporting templates](#exporting-templates).

![New Language Server with Custom Template](./images/user-defined-ls/NewLanguageServerWithCustomTemplate.png)

### Exporting templates

Users can export their own language servers to a zip file, where each language server is a separate directory. 
This can be done from the LSP console, by selecting one or more language servers and selecting the export option from the context menu.

These directories can then be used as a template for a new language server by [importing a custom template](#custom-template).

![Export Language Servers to a Zip](./images/user-defined-ls/ExportUserDefinedLanguageServer.png)

#### Custom template structure
By default, each directory contains the following files, but only `template.json` is required.
- `template.json`
- `settings.json`
- `initializationOptions.json`

A `README.md` file can be added manually to each of the language server directories to provide instructions 
for the corresponding language server.

See [here](./UserDefinedLanguageServerTemplate.md) for more information about those JSON structures.
