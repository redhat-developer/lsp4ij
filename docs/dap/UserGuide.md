# Debug Adapter Protocol

LSP4IJ provides [Debug Adapter Protocol](https://microsoft.github.io/debug-adapter-protocol/) support. 
You can read [the DAP Support overview](./DAPSupport.md), describing which DAP features are implemented, and how.
If you need to customize the DAP support you can [register your DAP server with extension point](./DeveloperGuide.md).

The DAP support is available with the `Debug Adapter Protocol` run/debug configuration type:

![DAP Configuration Type](./images/DAP_config_type.png)

After configuring the [DAP configuration type](#dap-configuration-type), you can debug your file.

Here is an example with `JavaScript debugging`, which uses the [VSCode JS Debug DAP server](./user-defined-dap/vscode-js-debug.md):

![DAP Configuration Type](./images/DAP_debugging_overview.png)

## DAP Configuration Type:

To configure debugging with DAP, you need to fill in:

- The `Server` tab to specify the DAP server:

  ![DAP Configuration Type/Server](./images/DAP_config_type_server.png)

- The `Mappings` tab to specify the files which can be debugged to allow adding/removing breakpoints:

![DAP Configuration Type/Mappings](./images/DAP_config_type_mappings.png)

- The `Configuration` tab to specify the working directory and the file you want to run/debug:

  ![DAP Configuration Type/Configuration](./images/DAP_config_type_configuration.png)

## Inline value

The values of the variables are displayed inline, but this is not perfect because a DAP server generally cannot handle variable positions (only their values). 
To retrieve the variable positions, LSP4IJ uses the syntax highlighting information from the editor (TextMate or others).

Here a de demo with JavaScript:

![DAP inline value](./images/DAP_inline_value_demo.gif)

Theoretically, inline values should be handled by a language server via [textDocument/inlineValue](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlineValue)
but as no language servers seems implement this LSP request for the moment LSP4IJ doesn't use this strategy.

## Evaluate expression

Evaluate expression is available by consuming the [Evaluate request](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Evaluate) 

![Evaluate expression](./images/DAP_debugging_evaluate.png)

### Completion

If debug adapter [supports the `completions` request](https://microsoft.github.io/debug-adapter-protocol//specification.html#Types_Capabilities),
completion should be available in the expression editor by consuming the
[Completion request](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Completions):

![Completion](images/DAP_debugging_completion.png)

## Set value

If debug adapter [supports setting a variable to a value](https://microsoft.github.io/debug-adapter-protocol//specification.html#Types_Capabilities),
the `Set Value...` contextual menu should be available: 

![Set Value/Menu](images/DAP_debugging_setValue_menu.png)

You should edit the variable:

![Set Value/Edit](images/DAP_debugging_setValue_edit.png)

the edit apply will consume the
[SetVariable request](https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_SetVariable):

## Contextual Menu

Click on right button open existing / new DAP run configuration:

![Run/Debug menu](images/DAP_contextual_menu.png)

## DAP server traces

If you wish to show DAP request/response traces when you will debug:

![Show DAP traces](./images/vscode-js-debug/traces_in_console.png)

you need to select `Trace` with `verbose`.

![Set verbose traces](./images/vscode-js-debug/set_traces.png)

## DAP settings

You can `create/remove/update` DAP servers with `Debug Adapter Protocol` entry:

![DAP settings](./images/DAP_settings.png)

## Templates

LSP4IJ provides DAP templates that allow to initialize a given DAP server very quickly:

- [Go Delve DAP server](./user-defined-dap/go-delve.md) which allows you to debug `Go` files.
- [Julia DAP server](./user-defined-dap/julia.md) which allows you to debug `Julia` files. 
- [Python Debugpy DAP server](./user-defined-dap/python-debugpy.md) which allows you to debug `Python` files.
- [Swift DAP Server](./user-defined-dap/swift-lldb.md) which allows you to debug `Swift` files.
- [VSCode JS Debug DAP Server](./user-defined-dap/vscode-js-debug.md) which allows you to debug `JavaScript/TypeScript` files.