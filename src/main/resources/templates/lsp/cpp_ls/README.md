# Cpp Language Server

You can use the [Cpp Language Server](https://github.com/CppCXY/cpp_ls) (`cpp_ls`) to get `C`, `C++` and `CUDA`
support. The parser, the preprocessor, the index and the LSP server are all part of the project, so **no clangd,
no libclang and no `compile_commands.json` are required**.

The template can install `cpp_ls` for you: select `Cpp Language Server` in the `Template` combo-box, then click the
`Check / Run Installation` link of the `Installer` tab. The installer downloads the asset which matches your OS and
architecture from the [GitHub releases](https://github.com/CppCXY/cpp_ls/releases), extracts it in
`$USER_HOME$/.lsp4ij/lsp/cpp_ls`, and updates the server command.

## Manual installation

Download the asset which matches your OS and architecture and extract it. The archive contains a single executable
at its root: `cpp_ls.exe` on Windows, `cpp_ls` on Linux and macOS.

| OS | Architecture | Asset |
| --- | --- | --- |
| Windows | x64 | `cpp_ls-win32-x64.zip` |
| Windows | x86 | `cpp_ls-win32-ia32.zip` |
| Windows | arm64 | `cpp_ls-win32-arm64.zip` |
| Linux | x64 (glibc 2.17 or newer) | `cpp_ls-linux-x64-glibc.2.17.tar.gz` |
| Linux | x64 (recent glibc) | `cpp_ls-linux-x64.tar.gz` |
| Linux | aarch64 (glibc 2.17 or newer) | `cpp_ls-linux-aarch64-glibc.2.17.tar.gz` |
| Linux | riscv64 | `cpp_ls-linux-riscv64.tar.gz` |
| Linux | musl (Alpine) | `cpp_ls-linux-musl.tar.gz` |
| macOS | Apple Silicon | `cpp_ls-darwin-arm64.tar.gz` |
| macOS | Intel | `cpp_ls-darwin-x64.tar.gz` |

The installer picks the `-glibc.2.17` assets on Linux, since they run on every distribution with glibc 2.17 or newer.
On a musl based distribution (Alpine), download `cpp_ls-linux-musl.tar.gz` yourself and set the command to the
extracted `cpp_ls` executable.

## Command

`cpp_ls` talks LSP over its standard input and output, so no argument is required:

* `cpp_ls` on Linux and macOS.
* `cpp_ls.exe` on Windows.

Useful options: `--log-level` (`error`, `warn`, `info`, `debug`, `trace`) and `--log-path` (the file to log to;
`none`, the default, disables file logging).

## Settings

The server reads its `exclude` setting from the `cppls` section, which LSP4IJ answers from the template
`settings.json`:

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

An empty list means that only the server's own rules apply. A project can also carry a `.cppls.toml` file at its
root (language standard, include paths, defines, per-path rules, diagnostic switches) and a `compile_commands.json`,
which is used for exact per-file flags when it exists. See the
[configuration documentation](https://github.com/CppCXY/cpp_ls/blob/main/docs/configuration.md).
