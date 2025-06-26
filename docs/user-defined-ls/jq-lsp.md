# JQ Language Server

To enable [JQ](https://github.com/jqlang/jq) language support in your IDE, you can integrate the [JQ Language Server](https://github.com/wader/jq-lsp) by following these steps:

![JQ LS demo](../images/user-defined-ls/jq-lsp/demo_ls.gif)

---

## Step 1: Install the Language Server

1. Open an `.jq` file in your project.
2. Click on **Install JQ Language Server**:

   ![Open file](../images/user-defined-ls/jq-lsp/open_file.png)

3. This will open the [New Language Server Dialog](../UserDefinedLanguageServer.md#new-language-server-dialog) with `JQ Language Server` pre-selected:

   ![New Language Server Dialog](../images/user-defined-ls/jq-lsp/new_language_server_dialog.png)

4. Click **OK**. This will create the `JQ Language Server` definition and start the installation:

   ![Installing Language Server](../images/user-defined-ls/jq-lsp/language_server_installing.png)

5. Once the installation completes, the server should start automatically and provide JQ language support (autocomplete, diagnostics, etc.).

### Troubleshooting Installation

If the installation fails, you can customize the installation settings in the **Installer** tab,  
then click on the **Run Installation** hyperlink to reinstall the server:

![Installer tab](../images/user-defined-ls/jq-lsp/installer_tab.png)

See [Installer descriptor](../UserDefinedLanguageServerTemplate.md#installer-descriptor) for more information.

---

## Step 2: Install TextMate Bundle

Since IntelliJ does not provide native JQ TextMate support, and the language server does not handle syntax highlighting, you need to set up a TextMate bundle manually.

* Clone the [vscode-jq](https://github.com/wader/vscode-jq) repository:

  ```bash
  git clone https://github.com/wader/vscode-jq.git

* Open TextMate Bundles settings

![TextMate Bundles Settings](../images/user-defined-ls/textmate_bundles_settings.png)

* Click the `+` button and select the folder [vscode-jq](https://github.com/wader/vscode-jq) folder.
  This folder contains the modified package.json and the TextMate grammar.

Once done, IntelliJ will apply syntax highlighting, bracket matching, and other basic editor features for .jq files.