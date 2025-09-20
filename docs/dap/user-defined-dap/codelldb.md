# CodeLLDB

To debug a [Rust application](#rust-application) or [C++ application](#c-application), you can use the [CodeLLDB](https://github.com/vadimcn/codelldb) DAP server.

# Rust Application

Let's debug the following `main.rs` Rust file:

```rust
fn main() {
  let name = "world";
  println!("Hello, {}!", name);
}
```

![Set Breakpoint](../images/codelldb/set_breakpoint.png)

You will need to install [cargo](https://doc.rust-lang.org/cargo/getting-started/installation.html), which is used to create a Rust application with `cargo init` and to build an executable with `cargo build`.  
The executable is required for debugging.

## Initialize Rust Application

```shell
cargo init
```

This command generates a Rust application with a `src/main.rs` file. Update this file with the following content:

```rust
fn main() {
  let name = "world";
  println!("Hello, {}!", name);
}
```

To debug the Rust application, you first need to build the executable that will be used by the debugger.
In the project directory, run:

```shell
cargo build
```

This will generate an executable in the `target/debug` folder.

![Target executable](../images/codelldb/generated_executable.png)

## Configure DAP server

1. Create a DAP Run/Debug configuration:

   ![DAP Configuration Type](../images/DAP_config_type.png)

2. In the `Server` tab, click on `create a new server`:

   ![Create a new server](../images/DAP_server_create_link.png)

3. A new dialog will open to create a DAP server. Select the `CodeLLDB` template:

   ![Select Template](../images/codelldb/select_template.png)

4. After clicking the `OK` button, the new server will be selected and the configuration automatically pre-filled:

![Select New server](../images/codelldb/select_new_server.png)

5. Enable DAP server traces

If you wish to show DAP request/response traces when you will debug:

![Show DAP traces](../images/codelldb/traces_in_console.png)

you need to select `Trace` with `verbose`.

![Set verbose traces](../images/codelldb/set_traces.png)

## Configure file mappings

To allows settings breakpoints to Python files, you need configure mappings in the `Mappings` tab.
As you have selected `CodeLLDB` server, it will automatically populate the file mappings like this:

![File mappings](../images/codelldb/file_mappings.png)

## Configure the executable to run/debug

As you have selected `CodeLLDB` server, it will automatically populate the `Launch` configuration like this:

1. `Launch` as `Debug mode` should be selected.
2. The DAP parameters of the launch should look like this:

```json
{
  "type": "lldb",
  "name": "Launch Rust file",
  "request": "launch",
  "program": "${file}",
  "cwd": "${workspaceFolder}"
}
```

You need to select the executable in the file field:

![DAP Configuration/Configuration](../images/codelldb/configuration_tab.png)

## Set Breakpoint

After applying the run configuration, you should set a breakpoint to files which matches file mappings.
Set a breakpoint in the `main.rs` file:

![Set Breakpoint](../images/codelldb/set_breakpoint.png)

## Debugging

You can start the run configuration in either Run or Debug mode. 

First time, [CodeLLDB](https://github.com/vadimcn/codelldb)  the DAP server will be downloaded and installed:

![Debugging / Installing](../images/codelldb/server_installing.png)

If you have some error while installation, you can go to the `Installer`, update [Installer Descriptor](https://github.com/redhat-developer/lsp4ij/blob/main/docs/UserDefinedLanguageServerTemplate.md#installer-descriptor) and run again `Run Installation`: 

![Installer](../images/codelldb/installer_tab.png)

Once started, you should see DAP traces in the console:

![Debugging / Console](../images/codelldb/traces_in_console.png)

You will also see `Threads` and `Variables`:

![Debugging / Threads](../images/codelldb/debug_threads_tab.png)

You can [open Disassembly](../UserGuide.md#disassemble) and debug step by step instructions in the Diasembly view:

![Debugging / Disassembly](../images/codelldb/disassembly.png)

# C++ Application

TODO

