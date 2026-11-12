# Cpp Language Server

You can use [Cpp Language Server](https://github.com/CppCXY/cpp_ls) (`cpp_ls`) to get `C`, `C++` and `CUDA` support:

* **no build system is required**: a `compile_commands.json` is used for exact per-file flags when it exists,
  and a `.cppls.toml` file in the workspace root can name the language standard, the include paths and the defines;
* completion, hover, signature help, definition, references, rename, call hierarchy, workspace symbols, inlay hints,
  semantic tokens, folding, selection ranges, formatting and diagnostics are provided by the server.

## Install the language server

`cpp_ls` is distributed as one asset per OS and architecture in the
[GitHub releases](https://github.com/CppCXY/cpp_ls/releases), and the LSP4IJ template installs the right one for you.

1. **Select `Cpp Language Server` as the template** from the available options. This will populate the command
   field with a default command (`cpp_ls`, or `cpp_ls.exe` on Windows), the file type mappings, the `cppls` settings
   and the installer.

2. **Install the language server** from the `Installer` tab, by clicking the `Check / Run Installation` link:

   * `Check Installation` tests whether `cpp_ls` has already been installed in `$USER_HOME$/.lsp4ij/lsp/cpp_ls`.
   * `Run Installation` downloads the asset which matches your OS and architecture, extracts it in
     `$USER_HOME$/.lsp4ij/lsp/cpp_ls`, and updates the server command to the extracted executable.
   * `Check / Run Installation` does both, which is the simplest choice on a fresh installation.

   The assets which the installer selects are:

   | OS | Architecture | Asset |
   | --- | --- | --- |
   | Windows | x64 | `cpp_ls-win32-x64.zip` |
   | Windows | x86 | `cpp_ls-win32-ia32.zip` |
   | Windows | arm64 | `cpp_ls-win32-arm64.zip` |
   | Linux | x64 (glibc 2.17 or newer) | `cpp_ls-linux-x64-glibc.2.17.tar.gz` |
   | Linux | aarch64 (glibc 2.17 or newer) | `cpp_ls-linux-aarch64-glibc.2.17.tar.gz` |
   | Linux | riscv64 | `cpp_ls-linux-riscv64.tar.gz` |
   | macOS | Apple Silicon | `cpp_ls-darwin-arm64.tar.gz` |
   | macOS | Intel | `cpp_ls-darwin-x64.tar.gz` |

   The archives contain a single executable at their root: `cpp_ls.exe` on Windows, `cpp_ls` on Linux and macOS.

   If you prefer to install it yourself, download the asset which matches your OS and architecture, extract it, and
   set the command in the `Server` tab to the extracted executable.

   > The installer selects the `-glibc.2.17` assets on Linux, since they run on every distribution which provides
   > glibc 2.17 or newer. On a musl based distribution (Alpine), download `cpp_ls-linux-musl.tar.gz` yourself, and
   > set the command to the extracted `cpp_ls` executable.

3. **Optional**: customize the `Mappings` tab. The template maps:

   * `*.c`, `*.i` to the `c` language id,
   * `*.cpp`, `*.h`, `*.hpp`, `*.cc`, `*.cxx`, `*.inl`, `*.ipp`, `*.ixx`, `*.mpp`, `*.mxx`, etc to the `cpp`
     language id,
   * `*.cu`, `*.cuh` to the `cuda-cpp` language id.

4. **Optional**: customize the settings in the `Configuration` tab (`Server` sub-tab), see [Settings](#settings)
   below.

5. **Click OK** to apply the changes. You should now have `C`, `C++` and `CUDA` support enabled in your IDE, with the
   [Cpp Language Server](https://github.com/CppCXY/cpp_ls) integrated, and you should see the language server in the
   [LSP console](../UserGuide.md#lsp-console).

## Command

`cpp_ls` talks LSP over its standard input and output, so the command needs no argument:

* `cpp_ls` on Linux and macOS.
* `cpp_ls.exe` on Windows.

Useful options, to add to the command if needed:

* `--log-level` (`error`, `warn`, `info` (the default), `debug`, `trace`) and `--log-path` (the file to log to;
  `none`, the default, disables file logging) to diagnose a problem;
* `--communication stdio` (the default) and `--communication tcp --port ${port}` to choose how the server talks LSP.
  For TCP, also set `Transport` to `Socket` with `Start the language server, LSP4IJ connects to it`, since `cpp_ls`
  listens on the port, and `Port` to `Auto` or a port; see [Transport](../UserDefinedLanguageServer.md#transport).

LSP4IJ sends the name of the IDE as its client info, which the server recognizes for the IntelliJ based IDEs it
knows; `--editor intellij` can be added to the command to force this mode.

## Settings

The server reads its settings from the `cppls` section, which LSP4IJ answers from the template `settings.json`:

```json
{
  "cppls": {
    "exclude": [
      "**/build/**",
      "**/third_party/**"
    ]
  }
}
```

`cppls.exclude` is the list of glob patterns of the files and folders which the server must not analyse (a build
tree, a vendored copy, generated sources). An empty list means that only the server's own rules apply.

A project can also provide a `.cppls.toml` file at its root for the language standard, the include paths, the
defines, per-path rules and the diagnostic switches, and a `compile_commands.json` file for the exact per-file
flags. See the [configuration documentation](https://github.com/CppCXY/cpp_ls/blob/main/docs/configuration.md) for
the details.
