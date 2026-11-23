You can use the [Erlang Language Platform (ELP)](https://whatsapp.github.io/erlang-language-platform/) to edit Erlang
files. It is the language server that the archived [erlang_ls](https://github.com/erlang-ls/erlang_ls) recommends.

1. [Install Erlang/OTP](https://www.erlang.org/downloads), and make sure that `erl` and `escript` are on your PATH:
   ELP runs them to find the Erlang/OTP libraries and to parse and check Erlang code. ELP's releases have builds for
   Erlang/OTP 27, 28 and 29.

2. **Select Erlang Language Platform (ELP) as the template**. The command is `elp server`.

3. **Click OK**. The first time the server starts, the installer of the template downloads the
   `elp-<os>-<arch>-…-otp-<version>.tar.gz` asset of the latest
   [ELP release](https://github.com/WhatsApp/erlang-language-platform/releases) for your OS and architecture, extracts
   `elp` (`elp.exe` on Windows) into `$USER_HOME$/.lsp4ij/lsp/erlang-elp`, and sets the command to
   `"$USER_HOME$/.lsp4ij/lsp/erlang-elp/elp" server`.

ELP publishes one build per Erlang/OTP version: the version that ELP was built with, not one that you must run.
According to [ELP's install guide](https://whatsapp.github.io/erlang-language-platform/docs/get-started/install/), a
build runs on that Erlang/OTP version and usually on later ones. So the installer takes the build for the oldest
Erlang/OTP version of the release, as ELP's VS Code extension does: OTP 27 at the time of writing. To take another
build, change `otp-*` in the `asset` of the `Installer` tab, for instance to `otp-28*`, before you click **OK**, or
later in the `Installer` tab of the language server in the LSP console, and click **Run Installation** there.

To install ELP yourself, as with `brew install erlang-language-platform` on macOS, or from a
[release](https://github.com/WhatsApp/erlang-language-platform/releases), keep the command `elp server` if `elp` is on
your PATH, or set the path of `elp` in it, and empty the **Installer** tab before you click **OK**, so that LSP4IJ
doesn't install the server.
