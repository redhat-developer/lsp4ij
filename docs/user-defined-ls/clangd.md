# Clangd

## Install the language server

1. **Open the New Language Server Dialog**. This can usually be found under the IDE settings related to Language Server Protocol (LSP). For more information, refer to the [New Language Server Dialog documentation](../UserDefinedLanguageServer.md#new-language-server-dialog).

2. **Select `Clangd` as the template** from the available options.
   This will populate the command field with a default command, `clangd`. When a `*.c`, `*.h` (etc) file is opened the
   first time, the installer downloads the asset of the latest [clangd snapshot](https://github.com/clangd/clangd/releases)
   for your OS, extracts it into a `clangd_snapshot_<date>` folder of `$USER_HOME$/.lsp4ij/lsp`, and sets the command to
   its `bin/clangd` executable (`bin/clangd.exe` on Windows). Then `*.c`, `*.h` support should be available.

   To install clangd yourself, see [Getting started](https://clangd.llvm.org/installation), keep the command `clangd`
   if `clangd` is on your PATH, and empty the `Installer` tab before you click **OK**, so that LSP4IJ doesn't install
   the server.

   ![Clangd template](../images/user-defined-ls/clangd/select_template.png)

## Tell clangd how your project is built

clangd needs the flags each file is compiled with, such as include paths (`-I`), macros (`-D`) and the language standard.
Without them, it reports the headers of your project and of its dependencies as not found, and completion and
diagnostics miss what they declare. clangd reads the flags from the project's files, not from LSP4IJ:

1. **A `compile_commands.json`**, a compilation database with the command of each file. clangd looks for it in the
   folder of the file you edit and in its parent folders, and in a `build/` subfolder of each.
   - **CMake** writes one: `cmake -S . -B build -DCMAKE_EXPORT_COMPILE_COMMANDS=ON`.
   - **Meson** writes one in its build folder.
   - **Make and other build systems:** [Bear](https://github.com/rizsotto/Bear) records one while you build:
     `bear -- make`.
2. **A `compile_flags.txt`**, for a simple project: a file with one flag per line, used for every file in its folder and
   below:

   ```
   -std=c11
   -I./include
   -I./vendored/some-dep/include
   -DDEBUG
   ```
3. **A `.clangd` file** in the project's root adds flags to those of the compilation database, or replaces some:

   ```yaml
   CompileFlags:
     Add: [-I./vendored/some-dep/include]
   ```

When the compilation database is in another folder, give that folder to clangd in the command of the language server,
in the **Server** tab: `clangd --compile-commands-dir=out/debug`.

If clangd doesn't pick up a change of these files, restart the language server from the
[LSP console](../UserGuide.md#lsp-console).

For more, see [Project setup](https://clangd.llvm.org/installation#project-setup) and
[Configuration](https://clangd.llvm.org/config) in the clangd documentation.
