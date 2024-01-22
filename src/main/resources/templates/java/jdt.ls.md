You can use the [Eclipse JDT.LS](https://github.com/eclipse-jdtls/eclipse.jdt.ls) to test support for Java files, by following these instructions:

* [Download and extract jdt-language-server-latest.tar.gz](https://www.eclipse.org/downloads/download.php?file=/jdtls/snapshots/jdt-language-server-latest.tar.gz)
* Once **jdt-language-server-latest.tar.gz** has been extracted, you need to replace **${BASE_DIR}**.
  with the directory where **Eclipse JDT LS** is stored.
* Replace **${DATA_DIR}** with the absolute path to your data directory. Eclipse JDT.LS stores workspace 
specific information in it. This should be unique per workspace/project, e.g. `/tmp/jdtls/my-project-name`.

⚠️Eclipse JDT.LS requires Java 17 or higher to run, so make sure JAVA_HOME is set to a proper JDK before lauching your IDE.
