You can use the [Eclipse JDT.LS](https://github.com/eclipse-jdtls/eclipse.jdt.ls) to test support for Java files, by
following these instructions:

* [Download and extract jdt-language-server-latest.tar.gz](https://www.eclipse.org/downloads/download.php?file=/jdtls/snapshots/jdt-language-server-latest.tar.gz)
* The command starts **Eclipse JDT LS** where the installer extracts it, in `$USER_HOME$/.lsp4ij/lsp/jdt-ls`. If you
  extracted **jdt-language-server-latest.tar.gz** somewhere else, change that directory in the command.
* Eclipse JDT.LS stores workspace specific information in its data directory, `$PROJECT_DIR$/jdtls-data`, which is
  unique per project.

⚠️Eclipse JDT.LS requires Java 17 or higher to run, so make sure JAVA_HOME is set to a proper JDK before lauching your
IDE.
