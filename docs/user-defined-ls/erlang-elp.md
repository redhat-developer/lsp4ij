# Erlang Language Platform (ELP)

To enable [Erlang](https://www.erlang.org/) language support in your IDE, you can integrate the
[Erlang Language Platform (ELP)](https://whatsapp.github.io/erlang-language-platform/), the language server of
WhatsApp, by following these steps. ELP is the language server that the archived
[Erlang LS](https://github.com/erlang-ls/erlang_ls) recommends; its template,
[Erlang Language Server (erlang_ls, discontinued October 2024)](./erlang-ls.md), is still there for the projects that
need it.

## Install the language server

1. [Install Erlang/OTP](https://www.erlang.org/downloads), and make sure that `erl` and `escript` are on your PATH: ELP
   runs them to find the Erlang/OTP libraries and to parse and check Erlang code. ELP's releases have builds for
   Erlang/OTP 27, 28 and 29. For a rebar3 project, ELP also needs `rebar3` 3.24.0 or later on the PATH, see
   [rebar3](https://whatsapp.github.io/erlang-language-platform/docs/get-started/configure-project/rebar3/).

2. **Open the New Language Server Dialog**. This can usually be found under the IDE settings related to Language Server
   Protocol (LSP). For more information, refer to the
   [New Language Server Dialog documentation](../UserDefinedLanguageServer.md#new-language-server-dialog).

3. **Select Erlang Language Platform (ELP) as the template** from the available options. This will populate the
   command field with `elp server`, the mappings and the installer.

4. **Optional**: You may also customize the mappings section according to your preferences. The template maps these
   files to the `erlang` language id, as ELP's VS Code extension does: `*.erl`, `*.hrl`, `*.escript`, `*.app.src`,
   `*.app`, `rebar.config`, `rebar.config.script`, `rebar.lock`, `sys.config`, `sys.config.src`, `sys.ct.config`,
   `sys.shell.config`, `*.yrl` and `*.xrl`.

5. **Click OK** to apply the changes. You should now have Erlang language support enabled in your IDE, with ELP
   integrated, and you should see the language server in the [LSP console](../UserGuide.md#lsp-console).

The first time the server starts, the installer of the template downloads the asset of the latest
[ELP release](https://github.com/WhatsApp/erlang-language-platform/releases) for your OS and architecture, extracts
`elp` (`elp.exe` on Windows) into `$USER_HOME$/.lsp4ij/lsp/erlang-elp`, and sets the command to:

```
"$USER_HOME$/.lsp4ij/lsp/erlang-elp/elp" server
```

A language server created from the template once ELP is installed there gets that command from the check of the
installer. If GitHub can't be reached, the installer downloads the release `2026-11-20` instead.

The assets which the installer selects are:

| OS      | Architecture  | Asset                                                |
|---------|---------------|------------------------------------------------------|
| Linux   | x64           | `elp-linux-x86_64-unknown-linux-gnu-otp-*.tar.gz`    |
| Linux   | arm64         | `elp-linux-aarch64-unknown-linux-gnu-otp-*.tar.gz`   |
| macOS   | Intel         | `elp-macos-x86_64-apple-darwin-otp-*.tar.gz`         |
| macOS   | Apple Silicon | `elp-macos-aarch64-apple-darwin-otp-*.tar.gz`        |
| Windows | x64 and arm64 | `elp-windows-x86_64-pc-windows-msvc-otp-*.tar.gz`    |

### The Erlang/OTP version of the build

Each ELP release has one build per Erlang/OTP version, as `elp-macos-aarch64-apple-darwin-otp-27.3.tar.gz`. According
to [ELP's install guide](https://whatsapp.github.io/erlang-language-platform/docs/get-started/install/), that is the
Erlang/OTP version that ELP was built with, not one that you must run, and a build runs on that Erlang/OTP version
and usually on later ones. So the installer takes the build for the oldest Erlang/OTP version of the release, which is
the build that ELP's VS Code extension ships: OTP 27 at the time of writing, which runs on Erlang/OTP 27, 28 and 29.

To take the build for another Erlang/OTP version, change `otp-*` in the `asset` of the `Installer` tab, for instance to
`otp-28*` for `elp-macos-aarch64-apple-darwin-otp-28*.tar.gz`: before you click **OK** in the New Language Server
dialog, or later in the `Installer` tab of the language server in the LSP console, and click **Run Installation**
there.

### Install ELP yourself

On macOS, `brew install erlang-language-platform` installs ELP and puts `elp` on the PATH. On the other platforms,
see [Install ELP](https://whatsapp.github.io/erlang-language-platform/docs/get-started/install/). Keep the command of
the template, `elp server`, if `elp` is on your PATH, or set the path of `elp` in it, and empty the `Installer` tab
before you click **OK**, so that LSP4IJ doesn't install the server.

## Settings

ELP reads its settings from the `elp` section, which LSP4IJ answers from the settings of the `Configuration` tab
(`Server` sub-tab). The template sets no settings, so ELP uses its defaults, and the settings schema of the template
completes the names of the settings, as `elp.diagnostics.disabled` to turn off some
[diagnostics](https://whatsapp.github.io/erlang-language-platform/docs/erlang-error-index/):

```json
{
  "elp.diagnostics.disabled": [
    "W0030",
    "W0031"
  ]
}
```

The structure of a project, its applications, dependencies and include paths, comes from rebar3, or from a
[`.elp.toml`](https://whatsapp.github.io/erlang-language-platform/docs/get-started/configure-project/elp-toml/) file
at its root. See [Configure Your Project](https://whatsapp.github.io/erlang-language-platform/docs/get-started/configure-project/).
