To enable Scala language support in your IDE, you can integrate [Scala Language Server (Metals)](https://scalameta.org/metals/) by following these steps:

1. Install [Coursier](https://get-coursier.io/docs/cli-installation), and make sure its `cs` command is on your PATH.
   Close and reopen your IDE if you changed the PATH.

2. **Select Scala Language Server (Metals) as the template** from the available options.
   This will populate the command field with `"$USER_HOME$/.lsp4ij/lsp/metals/metals"` (`metals.bat` on Windows),
   where the installer of the template puts Metals.

3. **Click OK**. The first time the server starts, the installer runs `cs install metals --install-dir .` in
   `$USER_HOME$/.lsp4ij/lsp/metals`, which creates the `metals` launcher there (`metals.bat` on Windows).

To install Metals yourself, read [How to start Metals?](https://scalameta.org/metals/docs/integrations/new-editor#starting-the-server),
set the command to the launcher you created, and empty the **Installer** tab before you click **OK**, so that LSP4IJ
doesn't install the server.
