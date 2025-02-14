There is already the [clojure-lsp-intellij](https://github.com/clojure-lsp/clojure-lsp-intellij) dedicated plugin that fully integrates with lsp4ij and [Clojure LSP](https://clojure-lsp.io) providing more features, make sure to check it out first.

In case you want to integrate manually, follow these steps: 

1. **Download the appropriate release for your operating system** from the [Clojure LSP releases page](https://github.com/clojure-lsp/clojure-lsp/releases).

2. **Extract the downloaded asset**. For example, on Windows, the extracted files will include `clojure-lsp.exe`.

3. **Open the New Language Server Dialog**. This can usually be found under the IDE settings related to Language Server Protocol (LSP). For more information, refer to the [New Language Server Dialog documentation](../UserDefinedLanguageServer.md#new-language-server-dialog).

4. **Select Clojure LSP as the template** from the available options. This will populate the command field with a default command. You need to adjust this command to point to the location of the extracted Clojure LSP asset.

   ![Clojure LS template](../images/user-defined-ls/clojure-lsp/select_template.png)

5. **Adjust the command** in the template to reference the location of the extracted Clojure LSP executable.

6. **Optional**: You may also customize the mappings section according to your preferences.

   ![Clojure LS template mappings](../images/user-defined-ls/clojure-lsp/configure_file_mappings.png)

7. **Click OK** to apply the changes. You should now have Clojure language support enabled in your IDE, with the Clojure LSP integrated.

   ![Clojure LSP in LSP Console](../images/user-defined-ls/clojure-lsp/ClojureLSPInLSPConsole.png)
