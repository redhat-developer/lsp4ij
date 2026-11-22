You can use the [Eclipse JDT.LS](https://github.com/eclipse-jdtls/eclipse.jdt.ls) to test support for Java files, by
following these instructions:

* The first time the server starts, the installer of the template downloads
  [jdt-language-server-latest.tar.gz](https://www.eclipse.org/downloads/download.php?file=/jdtls/snapshots/jdt-language-server-latest.tar.gz)
  and extracts it into `$USER_HOME$/.lsp4ij/lsp/jdt-ls`, where the command starts **Eclipse JDT LS**:
  `$USER_HOME$/.lsp4ij/lsp/jdt-ls/bin/jdtls` (`bin/jdtls.bat` on Windows). If you extracted
  **jdt-language-server-latest.tar.gz** somewhere else, change that directory in the command, and empty the
  **Installer** tab before you click **OK**, so that LSP4IJ doesn't install the server.
* Eclipse JDT.LS stores workspace specific information in its data directory, `$PROJECT_DIR$/jdtls-data`, which is
  unique per project.

⚠️Eclipse JDT.LS requires Java 17 or higher to run, so make sure JAVA_HOME is set to a proper JDK before launching your
IDE.
