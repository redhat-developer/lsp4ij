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

The `Server tab` requires the `server name` and `command` fields to be set.

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

## Using template
Template can be used to quickly create user defined language server pre-filled 
with server name, command, mappings and potential configurations.

#### Default template
The `Template combo-box` provides some default language servers templates (located in templates directory classpath), 
pre-filled with server name, command, mappings and potential configuration.

![New Language Server with Default Template](./images/user-defined-ls/NewLanguageServerWithDefaultTemplate.png)

* [Ada Language Server](./user-defined-ls/ada_language_server.md)
* [Astro Language Server](./user-defined-ls/astro-ls.md)
* [CSS Language Server](./user-defined-ls/vscode-css-language-server.md)
* [Clangd](./user-defined-ls/clangd.md) 
* [Clojure LSP](./user-defined-ls/clojure-lsp.md)
* [Dart LSP](./user-defined-ls/dart-lsp.md) 
* [Docker Language Server](./user-defined-ls/docker-language-server.md)
* [Erlang Language Server](./user-defined-ls/erlang-ls.md) 
* [Go Language Server](./user-defined-ls/gopls.md)
* [HTML Language Server](./user-defined-ls/vscode-html-language-server.md)
* [Julia Language Server](./user-defined-ls/julia.md)
* [Ruby LSP](./user-defined-ls/ruby-lsp.md)
* [Rust Language Server](./user-defined-ls/rust-analyzer.md) 
* [Scala Language Server (Metals)](./user-defined-ls/metals.md)
* [SourceKit-LSP](./user-defined-ls/sourcekit-lsp.md)
* [Svelte Language Server](./user-defined-ls/svelte-language-server.md) 
* [Terraform Language Server](./user-defined-ls/terraform-ls.md)
* [TypeScript Language Server](./user-defined-ls/typescript-language-server.md)
* [Vue Language Server](./user-defined-ls/vue-js-language-server.md)

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
