# Erlang - edb

To debug [Erlang](https://www.erlang.org/) code, you can use [edb](https://github.com/WhatsApp/edb), the debugger of
WhatsApp, which speaks DAP: `edb dap` talks DAP over its standard input and output. When the debugged node hits a
breakpoint, edb pauses the whole node. See the [DAP guide of edb](https://github.com/WhatsApp/edb/blob/main/docs/DAP.md)
for its launch and attach parameters.

## Configure DAP server

1. [Install Erlang/OTP](https://www.erlang.org/downloads) 28 or later, and make sure that `erl` and `escript` are on
   your PATH: the `edb` launcher runs `erl` to find the version of Erlang/OTP, and then the escript of edb for it.
   edb runs on Linux and macOS; it doesn't support Windows yet.

2. Compile the code to debug with the options `debug_info`, `beam_debug_info` and `beam_debug_stack`, as in the `test`
   profile of your `rebar.config`:

   ```erlang
   {profiles, [
       {test, [
           {erl_opts, [debug_info, beam_debug_info, beam_debug_stack]}
       ]}
   ]}.
   ```

3. Create a DAP Run/Debug configuration, and in the `Server` tab, click on `create a new server`.

4. Select the `Erlang - edb` template, and click on `OK`. This fills:

   * the command, `edb dap`;
   * the file mappings, for `*.erl` and `*.hrl`, with the `erlang` language id;
   * the launch configuration `Launch rebar3 shell`, which edb's README gives: edb asks LSP4IJ to start
     `rebar3 as test shell` in a terminal of the IDE, with the
     [Run In Terminal](../UserGuide.md#run-in-terminal) request, and the shell node then connects to edb.

The first time the DAP server starts, the installer downloads the `edb-<version>.tar.gz` archive of the latest
[edb release](https://github.com/WhatsApp/edb/releases), extracts its content into `$USER_HOME$/.lsp4ij/dap/erlang-edb`,
and sets the command to `"$USER_HOME$/.lsp4ij/dap/erlang-edb/edb" dap`. The archive has the `edb` launcher and an
escript of edb for Erlang/OTP 28 and one for 29 and later, in `otp-28` and `otp-29`, which the launcher finds next to
itself: keep them together. If GitHub can't be reached, the installer downloads edb 1.0.0 instead.

To install edb yourself, extract that archive into a folder of your choice, set the command to the `edb` launcher in it
followed by `dap`, or keep `edb dap` if the launcher is on your PATH, and empty the `Installer` tab, so that LSP4IJ
doesn't install the DAP server.

## Attach to a running node

edb attaches to a running node with an `attach` request, which it gets from the DAP server that the IDE started over
its standard input and output. LSP4IJ's `Attach` debug mode instead connects to a DAP server which already runs, at an
address and a port, and starts none, so the template has no attach configuration. To debug a node, launch it from the
debug session, as `Launch rebar3 shell` does.

## Language Support

If you need language support for Erlang (completion, validation, etc.), you can
[configure the Erlang Language Platform (ELP)](../../user-defined-ls/erlang-elp.md).
