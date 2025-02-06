# Julia Language Server

To enable [Julia](https://julialang.org/) language support in your IDE, you can integrate [Julia Language Server](https://github.com/julia-vscode/LanguageServer.jl) by following these steps:

![Julia LS demo](../images/user-defined-ls/julia/demo_ls.gif)

## Install the language server

1. [install Julia](https://julialang.org/downloads/). After that open a terminal and type `julia`:
   ![Julia command](../images/user-defined-ls/julia/julia_command.png)

2. switch to Julia’s REPL by typing `]` to install the DAP server with the command `add LanguageServer`
   ![Install Julia language server](../images/user-defined-ls/julia/julia_ls_install.png)

3. **Open the New Language Server Dialog**. This can usually be found under the IDE settings related to Language Server Protocol (LSP). For more information, refer to the [New Language Server Dialog documentation](../UserDefinedLanguageServer.md#new-language-server-dialog).

4. **Select Julia Language Server as the template** from the available options.
   
   ![Julia template](../images/user-defined-ls/julia/select_template.png)

6. **Optional**: You may also customize the mappings section according to your preferences.

   ![Julia LS template mappings](../images/user-defined-ls/julia/configure_file_mappings.png)

7. **Click OK** to apply the changes. You should now have [Julia](https://julialang.org/) language support enabled in your IDE:

   ![Julia LS in LSP Console](../images/user-defined-ls/julia/ls_in_console.png)

You could also configure server (you should have completion which will help you to configure server):

![Julia LS configuration](../images/user-defined-ls/julia/configure_server_configuration.png)

## Debugging

If you need to Run/Debug Julia program, you can [configure the Julia DAP server](../dap/user-defined-dap/julia.md).

![Debugging / Threads](../dap/images/julia/debug_threads_tab.png)