# EO Language Server

To enable [EO](https://www.eolang.org/) language support in your IDE, you can integrate the [EO LSP Server](https://github.com/objectionary/eo-lsp-server) by following these steps:

![EO LS demo](../images/user-defined-ls/eo-lsp-server/demo_ls.gif)

---

## Step 1: Install the Language Server

1. Open an `.eo` file in your project.
2. Click on **Install LSP Server for EOLANG**:

   ![Open file](../images/user-defined-ls/eo-lsp-server/open_file.png)

3. This will open the [New Language Server Dialog](../UserDefinedLanguageServer.md#new-language-server-dialog) with `LSP Server for EOLANG` pre-selected:

   ![New Language Server Dialog](../images/user-defined-ls/eo-lsp-server/new_language_server_dialog.png)

4. Click **OK**. This will create the `LSP Server for EOLANG` definition and start the installation:

   ![Installing Language Server](../images/user-defined-ls/eo-lsp-server/language_server_installing.png)

5. Once the installation completes, the server should start automatically and provide EO language support (diagnostics and semantic tokens).

The installer installs the `eo-lsp-server` npm package into `$USER_HOME$/.lsp4ij/lsp/eo-lsp-server` with
`npm install`, and sets the command to `"$USER_HOME$/.lsp4ij/lsp/eo-lsp-server/node_modules/.bin/eo-lsp-server" --stdio`
(`eo-lsp-server.cmd` on Windows). You need [Node.js](https://nodejs.org/en/download), with `npm`, on your PATH.

To install the server yourself, run `npm install -g eo-lsp-server`, keep the command of the template,
`eo-lsp-server --stdio` (`cmd /C eo-lsp-server.cmd --stdio` on Windows), and empty the `Installer` tab before you click
**OK**, so that LSP4IJ doesn't install the server.

### Troubleshooting Installation

If the installation fails, you can customize the installation settings in the **Installer** tab,  
then click on the **Run Installation** hyperlink to reinstall the server:

![Installer tab](../images/user-defined-ls/eo-lsp-server/installer_tab.png)

See [Installer descriptor](../Installer.md) for more information.

---

## Step 2: Install TextMate Bundle

Even if [EO LSP Server](https://github.com/objectionary/eo-lsp-server) can support LSP semantic tokens, You can also set up the EO TextMate bundle manually.

* Clone the [eo-vscode](https://github.com/objectionary/eo-vscode) repository:

  ```bash
  git clone https://github.com/objectionary/eo-vscode.git
  ```

* Open TextMate Bundles settings

![TextMate Bundles Settings](../images/user-defined-ls/eo-lsp-server/textmate_bundles_settings.png)

* Click the `+` button and select the folder [eo-vscode](https://github.com/objectionary/eo-vscode) folder.
  This folder contains the modified package.json and the TextMate grammar.

Once done, IntelliJ will apply syntax highlighting, bracket matching, and other basic editor features for .eo files.
