To enable Swift language support in your IDE, you can integrate [SourceKit-LSP](https://github.com/swiftlang/sourcekit-lsp) by following these steps:

1. Install Swift. Please see the [Getting Started Guide on Swift.org](https://www.swift.org/getting-started/) for details on how to install Swift on your system.
   This installation will install the [SourceKit-LSP](https://github.com/swiftlang/sourcekit-lsp) language server.

2. **Open the New Language Server Dialog**. This can usually be found under the IDE settings related to Language Server Protocol (LSP).
   For more information, refer to the [New Language Server Dialog documentation](../UserDefinedLanguageServer.md#new-language-server-dialog).

3**Select SourceKit-LSP as the template** from the available options.
This will populate the command field with a default command `sourcekit-lsp`

![SourceKit-LSP template](../images/user-defined-ls/SourceKitLSPTemplate.png)

4. **Optional**: You may also customize the mappings section according to your preferences.

   ![SourceKit-LSP template mappings](../images/user-defined-ls/SourceKitLSPTemplateMappings.png)

5. **Click OK** to apply the changes. You should now have Swift language support enabled in your IDE.

   ![SourceKit-LSP in LSP Console](../images/user-defined-ls/SourceKitLSPInLSPConsole.png)
 