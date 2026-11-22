You can use the [OpenSysML language server](https://github.com/Open-MBEE/OpenSysML) for SysML v2 and KerML files by following these instructions:
* The installer of the template downloads the `sysml-lsp` release binary for your platform into
  `$USER_HOME$/.lsp4ij/lsp/sysml-lsp`, and sets the command to it. It runs the first time the server starts, and when
  you click **Run Installation** in the **Installer** tab.
* Or install it yourself and make sure `sysml-lsp` is in your OS PATH:
  * download the archive for your platform from the [releases page](https://github.com/Open-MBEE/OpenSysML/releases) and rename the extracted binary to `sysml-lsp`, or
  * with a Go toolchain, run **go install github.com/Open-MBEE/OpenSysML/cmd/sysml-lsp@latest**

The command of the template is **sysml-lsp --stdio**. The installer changes it to the downloaded binary, as
**"$USER_HOME$/.lsp4ij/lsp/sysml-lsp/sysml-lsp-linux-amd64" --stdio** on Linux x64. If you installed it manually, close
and reopen your IDE so the updated PATH is picked up.
