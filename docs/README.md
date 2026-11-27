# LSP4IJ documentation

LSP4IJ is a free and open-source client of the [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
(LSP) and the [Debug Adapter Protocol](https://microsoft.github.io/debug-adapter-protocol/) (DAP) for IntelliJ IDEA
and the other JetBrains IDEs. A language server gives the editors of its files features such as completion,
diagnostics, hover and navigation, and a debug adapter debugs a program, as in VS Code. LSP4IJ gets them either from a
plugin that integrates them with its extension points, or from a definition that a user adds in the IDE, without
writing a plugin.

This page is the starting point of the manuals. They are written for three kinds of readers:

- **Users**, who use language servers and debug adapters in the IDE, and add their own.
- **Template authors**, who write the templates that set up a language server or a debug adapter, and their installers.
- **Plugin developers**, who integrate a language server or a debug adapter in their IntelliJ plugin.

The [LSP support](./LSPSupport.md) and [DAP support](./dap/DAPSupport.md) pages describe, for all of them, which parts
of each protocol LSP4IJ implements.

## Where to start

| You want to                                                     | Read                                                                                                                                                                 |
|-----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| see what LSP4IJ does in the IDE, and find a problem             | the [User guide](./UserGuide.md)                                                                                                                                     |
| add a language server without writing a plugin                  | [User-defined language server](./UserDefinedLanguageServer.md), and the page of its [template](#language-server-templates)                                          |
| debug a program with a debug adapter                            | the [DAP user guide](./dap/UserGuide.md), and the page of its [template](#debug-adapter-templates)                                                                   |
| write a template, or its installer                              | [User-defined language server template](./UserDefinedLanguageServerTemplate.md) and [Installer descriptor](./Installer.md)                                           |
| integrate a language server in your plugin                      | the [Developer guide](./DeveloperGuide.md), then the [LSP API](./LSPApi.md)                                                                                          |
| integrate a debug adapter in your plugin                        | the [DAP developer guide](./dap/DeveloperGuide.md)                                                                                                                   |
| know which LSP or DAP requests and notifications are supported | [LSP support](./LSPSupport.md) and [DAP support](./dap/DAPSupport.md)                                                                                                |
| build LSP4IJ, or contribute to it                               | [Contributing to LSP4IJ](#contributing-to-lsp4ij)                                                                                                                    |

## Using LSP4IJ

### Language servers

- [User guide](./UserGuide.md): getting started, the LSP console, which shows the language servers and their
  messages, the `Language Servers` settings, the Structure view, actions such as formatting, Find Usages and
  refactoring, linked editing, semantic tokens, and inspections.
- [User-defined language server](./UserDefinedLanguageServer.md): the New Language Server dialog and its tabs
  (`Server`, `Mappings`, `Configuration`, `Debug`, `Installer` and `Workspace Folders`), and the templates that fill it
  in, which can also be imported and exported.

### Language server templates

Each template of the New Language Server dialog has a page that says what the language server needs, and how to set
it up. Most templates install their language server the first time it starts.

| Template                                                                                       | For                                                        |
|------------------------------------------------------------------------------------------------|------------------------------------------------------------|
| [Ada Language Server](./user-defined-ls/ada_language_server.md)                                | Ada                                                        |
| [Astro Language Server](./user-defined-ls/astro-ls.md)                                         | Astro                                                      |
| [C# Language Server (OmniSharp)](./user-defined-ls/omnisharp-roslyn.md)                        | C#                                                         |
| [CSS Language Server](./user-defined-ls/vscode-css-language-server.md)                         | CSS, SCSS and Less                                         |
| [Camel Language Server](./user-defined-ls/camel-lsp-server.md)                                 | Apache Camel                                               |
| [Candor Language Server](./user-defined-ls/candor-lsp.md)                                      | the side effects of each function                          |
| [Clangd](./user-defined-ls/clangd.md)                                                          | C, C++, CUDA and Objective-C                               |
| [Clojure LSP](./user-defined-ls/clojure-lsp.md)                                                | Clojure                                                    |
| [Cpp Language Server](./user-defined-ls/cpp_ls.md)                                             | C, C++ and CUDA                                            |
| [Dart LSP server](./user-defined-ls/dart-lsp.md)                                               | Dart                                                       |
| [Docker Language Server](./user-defined-ls/docker-language-server.md)                          | Dockerfiles, Compose files and Bake files                  |
| [ESLint Language Server](./user-defined-ls/vscode-eslint-language-server.md)                   | JavaScript and TypeScript, with ESLint                     |
| [Erlang Language Platform (ELP)](./user-defined-ls/erlang-elp.md)                              | Erlang                                                     |
| [Erlang Language Server (erlang_ls, discontinued October 2024)](./user-defined-ls/erlang-ls.md) | Erlang                                                     |
| [Go Language Server](./user-defined-ls/gopls.md)                                               | Go                                                         |
| [Harper Language Server](./user-defined-ls/harper-ls.md)                                       | English grammar, in the comments of source files and in commit messages |
| [JQ Language Server](./user-defined-ls/jq-lsp.md)                                              | jq                                                         |
| [Julia Language Server](./user-defined-ls/julia.md)                                            | Julia                                                      |
| [LSP Server for EOLANG](./user-defined-ls/eo-lsp-server.md)                                    | EO                                                         |
| [Luau Language Server](./user-defined-ls/luau-lsp.md)                                          | Luau                                                       |
| [Perl Language Server](./user-defined-ls/perl-lsp.md)                                          | Perl                                                       |
| [Ruby LSP](./user-defined-ls/ruby-lsp.md)                                                      | Ruby                                                       |
| [Rust Language Server](./user-defined-ls/rust-analyzer.md)                                     | Rust                                                       |
| [Scala Language Server (Metals)](./user-defined-ls/metals.md)                                  | Scala                                                      |
| [Smart Data Lake Language Server](./user-defined-ls/sdl-lsp.md)                                | the HOCON configuration files of Smart Data Lake Builder   |
| [SourceKit-LSP](./user-defined-ls/sourcekit-lsp.md)                                            | Swift                                                      |
| [Stylelint Language Server](./user-defined-ls/stylelint-lsp.md)                                | style sheets, with Stylelint                               |
| [Svelte Language Server](./user-defined-ls/svelte-language-server.md)                          | Svelte                                                     |
| [SysML v2 Language Server](./user-defined-ls/sysml-lsp.md)                                     | SysML v2 and KerML                                         |
| [Terraform Language Server](./user-defined-ls/terraform-ls.md)                                 | Terraform                                                  |
| [TypeScript Language Server](./user-defined-ls/typescript-language-server.md)                  | TypeScript and JavaScript                                  |
| [Vue.js Language Server](./user-defined-ls/vue-js-language-server.md)                          | Vue.js                                                     |
| [WGSL Analyzer](./user-defined-ls/wgsl-analyzer.md)                                            | WGSL, the WebGPU shading language                          |

### Debug adapters

- [DAP user guide](./dap/UserGuide.md): the `Debug Adapter Protocol` run/debug configuration, breakpoints of every
  kind, stepping, stepping back, evaluating expressions, variables and their values, the debug console, the debugged
  process, Run In Terminal, disassembly, and the templates of debug adapters.

### Debug adapter templates

Each template of the `Debug Adapter Protocol` run/debug configuration has a page that says how to install and set up
its debug adapter.

| Template                                                                     | For                         |
|------------------------------------------------------------------------------|-----------------------------|
| [CodeLLDB](./dap/user-defined-dap/codelldb.md)                               | Rust, C and C++             |
| [Docker: Dockerfile Build Debugging](./dap/user-defined-dap/buildx-dockerfile.md) | the build of a Dockerfile |
| [Erlang edb DAP server](./dap/user-defined-dap/erlang-edb.md)                | Erlang                      |
| [Go Delve DAP server](./dap/user-defined-dap/go-delve.md)                    | Go                          |
| [Julia DAP server](./dap/user-defined-dap/julia.md)                          | Julia                       |
| [Perl DAP server](./dap/user-defined-dap/perl-dap.md)                        | Perl                        |
| [Python Debugpy DAP server](./dap/user-defined-dap/python-debugpy.md)        | Python                      |
| [Ruby rdbg DAP server](./dap/user-defined-dap/ruby-rdbg.md)                  | Ruby                        |
| [Swift DAP Server](./dap/user-defined-dap/swift-lldb.md)                     | Swift                       |
| [VSCode JS Debug DAP Server](./dap/user-defined-dap/vscode-js-debug.md)      | JavaScript and TypeScript   |

## Writing templates and installers

- [User-defined language server template](./UserDefinedLanguageServerTemplate.md): the files of a template folder,
  the fields of its `template.json`, and how to contribute a template to LSP4IJ.
- [Installer descriptor](./Installer.md): the `installer.json` of a template, which checks whether the language server
  or debug adapter is installed and installs it, with its tasks (`exec`, `download`, `initialize`, ...), and what an
  earlier version of LSP4IJ does with a task it doesn't know.

A template can also have a `README.md` in its folder, under
[`src/main/resources/templates`](https://github.com/redhat-developer/lsp4ij/tree/main/src/main/resources/templates).
The New Language Server dialog shows it with its help icon: it isn't part of these manuals.

## Developing a plugin with LSP4IJ

- [Developer guide](./DeveloperGuide.md): referencing LSP4IJ in a plugin, the `LanguageServerFactory` and the
  connection to the language server, the `server` and `languageMapping` extension points, the language server manager,
  leases that keep a language server alive, LSP commands, and several instances of a language server.
- [LSP API](./LSPApi.md): `LSPClientFeatures`, with which a plugin customizes each LSP feature of its language server,
  such as completion, diagnostics, formatting or semantic tokens.
- [Workspace folder strategies](./WorkspaceFolders.md): how a plugin chooses the workspace folders of its language
  server.
- [DAP developer guide](./dap/DeveloperGuide.md): the `debugAdapterServer` extension point, how the debug adapter
  starts, launch and attach, a run/debug configuration and breakpoints of a plugin's own, and how variables and
  expressions are shown. Its API is experimental.

## What is supported

- [LSP support](./LSPSupport.md): which LSP requests and notifications LSP4IJ implements, and how each one works in
  the IDE.
- [DAP support](./dap/DAPSupport.md): which DAP events, requests and reverse requests LSP4IJ implements.

## Contributing to LSP4IJ

- [Building](../README.md#building), in the README of the repository: how to build the plugin, run it in a
  development IDE, and install a CI or nightly build.
- [CONTRIBUTING.md](../CONTRIBUTING.md): the Developer Certificate of Origin that each contribution agrees to.
- [How to contribute a template](./UserDefinedLanguageServerTemplate.md#how-to-contribute-a-user-defined-language-server-template).

A bug or a missing feature goes to [GitHub Issues](https://github.com/redhat-developer/lsp4ij/issues). The
[Feedback](../README.md#feedback) section of the README says how to report an error of a language server.
