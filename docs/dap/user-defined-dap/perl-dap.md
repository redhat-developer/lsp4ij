# Perl DAP server

To debug [Perl](https://www.perl.org/) files, you can use the `perl-dap` DAP server of
[perl-lsp](https://github.com/EffortlessMetrics/perl-lsp). See the
[DAP user guide of perl-lsp](https://github.com/EffortlessMetrics/perl-lsp/blob/master/docs/tutorials/DAP_USER_GUIDE.md)
for its features and its launch and attach parameters.

## Configure DAP server

1. [Install Perl](https://www.perl.org/get.html), which runs the scripts that you debug.

2. Create a DAP Run/Debug configuration, and in the `Server` tab, click on `create a new server`.

3. Select the `Perl Debugging` template, and click on `OK`. This fills:

   * the command, `<<insert base directory>>/perl-dap` (`perl-dap.exe` on Windows), which the installer of the
     template sets when it installs the DAP server;
   * the file mappings, for `*.pl`, `*.pm`, `*.t` and the other Perl files, with the `perl` language id;
   * the launch configurations (`Debug Script`, `Debug with Custom Libs`, `Debug with Environment`, `Debug in WSL`) and
     the attach configurations (`Attach by PID`, `Attach by TCP`).

The first time the DAP server starts, the installer downloads the `perllsp-*-<target>` archive of the latest
[perl-lsp release](https://github.com/EffortlessMetrics/perl-lsp/releases) for your OS and architecture, which has
`perl-dap`, extracts its content into `$USER_HOME$/.lsp4ij/dap/perl-dap`, and sets the command to
`"$USER_HOME$/.lsp4ij/dap/perl-dap/perl-dap"` (`perl-dap.exe` on Windows).

To install `perl-dap` yourself, extract that archive into a folder of your choice, replace `<<insert base directory>>`
with that folder in the command, and empty the `Installer` tab, so that LSP4IJ doesn't install the DAP server.

## Language Support

If you need language support for Perl (completion, validation, etc.), you can
[configure the Perl Language Server](../../user-defined-ls/perl-lsp.md).
