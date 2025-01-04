# VSCode JS Debug

To debug JavaScript or TypeScript files, you can use the [VSCode JS Debug](https://github.com/microsoft/vscode-js-debug) DAP server.

Example: debugging the `test.js` file:

```js
const s = "foo";
console.log(s);
```

![Set Breakpoint](../images/vscode-js-debug_set_breakpoint.png)

## Configure DAP server

1. Download the `js-debug-dap-v*.tar.gz` asset from the [VSCode JS Debug releases](https://github.com/microsoft/vscode-js-debug/releases).  
   For example, download [js-debug-dap-v1.96.0.tar.gz](https://github.com/microsoft/vscode-js-debug/releases/download/v1.96.0/js-debug-dap-v1.96.0.tar.gz), which is the latest version at the time of writing.

2. Extract the archive into any folder (e.g., `/home/path/to/dap`). The extracted folder should contain the DAP server at `/js-debug/src/dapDebugServer.js`.

3. Create a DAP Run/Debug configuration:

   ![DAP Configuration Type](../images/DAP_config_type.png)

4. In the `Server` tab, select `VSCode JS Debug`. This will automatically populate the name, command, and DAP parameters:

   ![DAP Configuration Type/Program](../images/vscode-js-debug_template.png)

   The command should look like this:

```
node ${BASE_DIR}/js-debug/src/dapDebugServer.js ${port}
```

Replace `${BASE_DIR}` with the directory where you extracted the DAP server. For example:

```
node /home/path/to/dap/js-debug/src/dapDebugServer.js ${port}
```

 - The `${port}` argument will be replaced with a free port when the run configuration starts.
 - The (wait for) trace `Debug server listening at` indicates when the DAP client can connect to the DAP server. 
This trace will appear in the console as follows:

```
node /home/path/to/dap/js-debug-dap-v1.96.0/js-debug/src/dapDebugServer.js 56425
Debug server listening at 127.0.0.1:56425
```

5. enable server trace

## Configure file mappings

TODO

## Configure the JavaScript file to run/debug

1. Fill in the `Configuration` tab to specify the `working directory` (usually the project's root directory) 
and the path to the `test.js` file.
2. Select `Launch` as debugging type.
2. The DAP parameters of the launch should look like this:

```json
{
   "type": "pwa-node",
   "request": "launch",
   "program": "${file}",
   "cwd": "${workspaceFolder}"
}
```

When the run configuration starts:

- `${workspaceFolder}` will be replaced with the working directory you specified.
- `${file}` will be replaced with the full path to `test.js`.

## Set Breakpoint

After applying the run configuration, you should set a breakpoint to files which matches file mappings.
Set a breakpoint in the `test.js` file:

![Set Breakpoint](../images/vscode-js-debug_set_breakpoint.png)

# Debugging

You can start the run configuration in either Run or Debug mode. Once started, you should see DAP traces in the console:

![Debugging / Console](../images/vscode-js-debug_console.png)

You will also see `Threads` and `Variables`:

![Debugging / Threads](../images/vscode-js-debug_threads.png)

## Configure the TypeScript file to run/debug

TODO : how to configure source maps?