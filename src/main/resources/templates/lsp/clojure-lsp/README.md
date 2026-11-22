You can use [Clojure LSP](https://clojure-lsp.io/) to edit Clojure files.

The command starts Clojure LSP where the installer of the template puts it:
`$USER_HOME$/.lsp4ij/lsp/clojure-lsp/clojure-lsp` (`clojure-lsp.exe` on Windows). The first time the server starts,
the installer downloads the `clojure-lsp-native-<os>-<arch>.zip` asset of the latest
[Clojure LSP release](https://github.com/clojure-lsp/clojure-lsp/releases) and extracts it into
`$USER_HOME$/.lsp4ij/lsp/clojure-lsp`.

To install Clojure LSP yourself:
 * [Download the asset compatible with your OS](https://github.com/clojure-lsp/clojure-lsp/releases)
 * After unzipping the asset, reference the file (ex : `clojure-lsp.exe` for Windows OS) in the Command prompt.
 * Empty the **Installer** tab before you click **OK**, so that LSP4IJ doesn't install the server.
