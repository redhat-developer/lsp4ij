You can use the [OpenSysML language server](https://github.com/Open-MBEE/OpenSysML) for SysML v2 and KerML files by following these instructions:
* Click **Run Installation** in the **Installer** tab to download the `sysml-lsp` release binary for your platform, or
* Install it yourself and make sure `sysml-lsp` is in your OS PATH:
  * download the archive for your platform from the [releases page](https://github.com/Open-MBEE/OpenSysML/releases) and rename the extracted binary to `sysml-lsp`, or
  * with a Go toolchain, run **go install github.com/Open-MBEE/OpenSysML/cmd/sysml-lsp@latest**

The server is started with **sysml-lsp --stdio**. If you installed it manually, close and reopen your IDE so the updated PATH is picked up.
