# SysML v2 Language Server

To enable [SysML v2](https://www.omg.org/spec/SysML/2.0/) and [KerML](https://www.omg.org/spec/KerML/1.0/) language support in your IDE, you can integrate the [OpenSysML language server](https://github.com/Open-MBEE/OpenSysML) (`sysml-lsp`) by following these steps:

---

## Install the Language Server

1. Open a `.sysml` or `.kerml` file in your project.
2. Click on **Install SysML v2 Language Server**.
3. This will open the [New Language Server Dialog](../UserDefinedLanguageServer.md#new-language-server-dialog) with `SysML v2 Language Server` pre-selected.
4. Click **OK**. This will create the `SysML v2 Language Server` definition and download the `sysml-lsp` release binary for your platform into `$USER_HOME$/.lsp4ij/lsp/sysml-lsp`.
5. Once the installation completes, the server should start automatically and provide SysML v2 / KerML language support (diagnostics, completion, hover, navigation, rename, formatting, semantic highlighting, etc.).

If `sysml-lsp` is already on your PATH (for example after `go install github.com/Open-MBEE/OpenSysML/cmd/sysml-lsp@latest`), the installer detects it and keeps the `sysml-lsp --stdio` command as is.

### Troubleshooting Installation

If the installation fails or if you need another version of the language server, you can customize the installation settings in the **Installer** tab,
then click on the **Run Installation** hyperlink to reinstall the server.

See [Installer descriptor](../UserDefinedLanguageServerTemplate.md#installer-descriptor) for more information.

---

## Syntax highlighting

The language server provides semantic tokens, which LSP4IJ uses for [semantic highlighting](../LSPSupport.md#semantic-tokens). For TextMate-based highlighting you can additionally register the grammar shipped with the [OpenSysML VS Code extension](https://github.com/Open-MBEE/OpenSysML/tree/main/editors/vscode) as a TextMate bundle.
