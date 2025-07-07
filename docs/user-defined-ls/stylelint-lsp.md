# StyleLint Language Server

To enable [StyleLint](https://stylelint.io/) language support in your IDE, you can integrate the [StyleLint-LSP](https://github.com/bmatcuk/stylelint-lsp) by following these steps:

## Step 1: Install the Language Server

1. Open a `.css` file in your project.
2. Click on **Install StyleLint Language Server**:

   ![Open file](../images/user-defined-ls/stylelint-lsp/open_file.png)

3. This will open the [New Language Server Dialog](../UserDefinedLanguageServer.md#new-language-server-dialog) with `StyleLint Language Server` pre-selected:

   ![New Language Server Dialog](../images/user-defined-ls/stylelint-lsp/new_language_server_dialog.png)

4. Click **OK**. This will create the `StyleLint Language Server` definition and start the installation:

   ![Installing Language Server](../images/user-defined-ls/stylelint-lsp/language_server_installing.png)

5. Once the installation completes, the server should start automatically and provide StyleLint language diagnostics directly within the IDE.

## Step 2: Configure StyleLint

* If necessary, execute `npm install stylelint` in your project root.
* Create a [configuration file](https://stylelint.io/user-guide/configure).

### Troubleshooting Installation

If the installation fails, you can customize the installation settings in the **Installer** tab,  
then click on the **Run Installation** hyperlink to reinstall the server:

![Installer tab](../images/user-defined-ls/stylelint-lsp/installer_tab.png)

See [Installer descriptor](../UserDefinedLanguageServerTemplate.md#installer-descriptor) for more information.
