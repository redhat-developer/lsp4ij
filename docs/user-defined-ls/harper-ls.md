# Harper Language Server

[Harper](https://github.com/Automattic/harper) is an English grammar checker designed to be _just right._
To enable [Harper](https://github.com/Automattic/harper) language support in your IDE, 
you can integrate the [Harper Language Server](https://writewithharper.com/docs/integrations/language-server) by following these steps:

![Harper LS demo](../images/user-defined-ls/harper-ls/demo_ls.gif)

---

## Install the Language Server

1. Open the [New Language Server Dialog](../UserDefinedLanguageServer.md#new-language-server-dialog) and select `Harper Language Server`:

   ![New Language Server Dialog](../images/user-defined-ls/harper-ls/new_language_server_dialog.png)

2. Click **OK**. This will create the `Harper Language Server` definition with predefined file mappings (that you can update):

   ![File mappings](../images/user-defined-ls/harper-ls/language_server_mappings.png)

3. Open a file (ex: a text file) which belongs to the file mapping. It will start the installation

   ![Installing Language Server](../images/user-defined-ls/harper-ls/language_server_installing.png)

4. Once the installation completes, the server should start automatically and provide Harper language support (diagnostics and code actions).

   ![Installing Language Server](../images/user-defined-ls/harper-ls/language_server_diagnostics.png)

The installer downloads the `harper-ls-<target>` archive of the latest
[Harper release](https://github.com/Automattic/harper/releases) for your OS and architecture, extracts it into
`$USER_HOME$/.lsp4ij/lsp/harper-ls`, and sets the command to `"$USER_HOME$/.lsp4ij/lsp/harper-ls/harper-ls" --stdio`
(`harper-ls.exe` on Windows). This is also the command of the template.

To install the server yourself, see [Installation](https://writewithharper.com/docs/integrations/language-server),
set the command in the `Server` tab to `"path/to/harper-ls" --stdio`, and empty the `Installer` tab before you click
**OK**, so that LSP4IJ doesn't install the server.

### Troubleshooting Installation

If the installation fails, you can customize the installation settings in the **Installer** tab,  
then click on the **Run Installation** hyperlink to reinstall the server:

![Installer tab](../images/user-defined-ls/harper-ls/installer_tab.png)

See [Installer descriptor](../UserDefinedLanguageServerTemplate.md#installer-descriptor) for more information.

---
