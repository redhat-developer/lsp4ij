# Luau Language Server

To enable [Luau](https://luau.org/) language support in your IDE, you can integrate the
[Luau Language Server](https://github.com/JohnnyMorganz/luau-lsp) (`luau-lsp`) by following these steps.

## Install the language server

1. **Open the New Language Server Dialog**. This can usually be found under the IDE settings related to Language Server
   Protocol (LSP). For more information, refer to the
   [New Language Server Dialog documentation](../UserDefinedLanguageServer.md#new-language-server-dialog).

2. **Select Luau Language Server as the template** from the available options. This will populate the command field
   with `"$USER_HOME$/.lsp4ij/lsp/luau-lsp/luau-lsp" lsp` (`luau-lsp.exe` on Windows), where the installer of the
   template puts `luau-lsp`, and the mappings, which map `*.luau` and `*.lua` files to the `luau` language id.

3. **Click OK** to apply the changes. You should now have Luau language support enabled in your IDE, and you should see
   the language server in the [LSP console](../UserGuide.md#lsp-console).

The first time the server starts, the installer of the template downloads the asset of the latest
[luau-lsp release](https://github.com/JohnnyMorganz/luau-lsp/releases) for your OS and architecture, and extracts
`luau-lsp` (`luau-lsp.exe` on Windows) into `$USER_HOME$/.lsp4ij/lsp/luau-lsp`. The assets which the installer
selects are:

| OS      | Architecture  | Asset                       |
|---------|---------------|-----------------------------|
| Linux   | x64           | `luau-lsp-linux-x86_64.zip` |
| Linux   | arm64         | `luau-lsp-linux-arm64.zip`  |
| macOS   | Intel         | `luau-lsp-macos.zip`        |
| macOS   | Apple Silicon | `luau-lsp-macos.zip`        |
| Windows | x64 and arm64 | `luau-lsp-win64.zip`        |

The installer has no fallback URL: when GitHub can't be reached, or its rate limit is reached, the installation fails,
see [GitHub API rate limit](../UserDefinedLanguageServerTemplate.md#github-api-rate-limit).

To install `luau-lsp` yourself, download and extract the asset of your OS from the
[luau-lsp releases](https://github.com/JohnnyMorganz/luau-lsp/releases), set the path of `luau-lsp` in the command,
keeping its `lsp` argument, and empty the `Installer` tab before you click **OK**, so that LSP4IJ doesn't install the
server.
