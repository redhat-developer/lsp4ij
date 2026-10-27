# Perl Language Server

To enable [Perl](https://www.perl.org/) language support in your IDE, you can integrate the [Rust Perl Language Server](https://github.com/EffortlessMetrics/perl-lsp) by following these steps:

![Perl LS demo](../images/user-defined-ls/perl-lsp/demo_ls.gif)

---

## Step 1: Install the Language Server

1. Open an `.pl` file in your project.
2. Click on **Install Perl Language Server**:

   ![Open file](../images/user-defined-ls/perl-lsp/open_file.png)

3. This will open the [New Language Server Dialog](../UserDefinedLanguageServer.md#new-language-server-dialog) with `Perl Language Server` pre-selected:

   ![New Language Server Dialog](../images/user-defined-ls/perl-lsp/new_language_server_dialog.png)

4. Click **OK**. This will create the `Perl Language Server` definition and start the installation:

   ![Installing Language Server](../images/user-defined-ls/perl-lsp/language_server_installing.png)

5. Once the installation completes, the server should start automatically and provide [Perl](https://www.perl.org/) language support (autocomplete, diagnostics, etc.).

## Step 2: Configure the server

You can also configure the server with the same settings as VSCode:

![Configure Language Server](../images/user-defined-ls/perl-lsp/configuration_server_configuration_tab.png)

### Troubleshooting Installation

If the installation fails, you can customize the installation settings in the **Installer** tab,  
then click on the **Run Installation** hyperlink to reinstall the server:

![Installer tab](../images/user-defined-ls/perl-lsp/installer_tab.png)

See [Installer descriptor](../UserDefinedLanguageServerTemplate.md#installer-descriptor) for more information.