You can use [Rust Analyzer](https://rust-analyzer.github.io/) to edit Rust files.

The first time the server starts, the installer of the template downloads the asset of the latest
[rust-analyzer release](https://github.com/rust-lang/rust-analyzer/releases) for your OS and architecture into
`$USER_HOME$/.lsp4ij/lsp/rust-analyzer`, and sets the command to the executable, as
`"$USER_HOME$/.lsp4ij/lsp/rust-analyzer/rust-analyzer-x86_64-unknown-linux-gnu"` on Linux x64 or
`"$USER_HOME$/.lsp4ij/lsp/rust-analyzer/rust-analyzer.exe"` on Windows.

To install it yourself:

 * [Install the rust-analyzer language server binary](https://rust-analyzer.github.io/book/rust_analyzer_binary.html)
 * Set the command to it, and empty the **Installer** tab before you click **OK**, so that LSP4IJ doesn't install the server.
