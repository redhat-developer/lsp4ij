# Python (debugpy) DAP server

To debug [Python](https://www.python.org/) files, you can use the [Debugpy](https://github.com/microsoft/debugpy) DAP server.

Let’s debugging the following `test.py` file:

```python
def main():
    user = "hello world"
    print(user)

if __name__ == "__main__":
    main()
```

![Set Breakpoint](../images/python-debugpy/set_breakpoint.png)

## Configure DAP server

1. [install Python](https://www.python.org/downloads/). After that open a terminal and type `python`:
   ![Python command](../images/python-debugpy/python_command.png)

2. Use `pip install debugpy` to install the required `debugpy package`.

3. Create a test file named `test.py` with a simple Python script that includes a `main()` function printing `"hello world"`

```python
def main():
    user = "hello world"
    print(user)

if __name__ == "__main__":
    main()
```

4. Create a DAP Run/Debug configuration:

   ![DAP Configuration Type](../images/DAP_config_type.png)

5. In the `Server` tab, click on `create a new server`:

   ![Create a new server](../images/DAP_server_create_link.png)

6. It opens a new dialog to create DAP server, select `Python- Debugpy` template:
   ![Select Template](../images/python-debugpy/select_template.png)

7. After clicking on `OK` button, it will select the new server and pre-fill configurations:

 ![Select New server](../images/python-debugpy/select_new_server.png)

As [Debugpy](https://github.com/microsoft/debugpy) DAP server is consumed with [attach request](#configure-the-python-file-to-rundebug-with-attach),
the command is empty.

8. Enable DAP server traces

If you wish to show DAP request/response traces when you will debug:

![Show DAP traces](../images/python-debugpy/traces_in_console.png)

you need to select `Trace` with `verbose`.

![Set verbose traces](../images/python-debugpy/set_traces.png)

## Configure file mappings

To allows settings breakpoints to Python files, you need configure mappings in the `Mappings` tab.
As you have selected `Python- Debugpy` server, it will automatically populate the file mappings like this:

![File mappings](../images/python-debugpy/file_mappings.png)

## Configure the Python file to run/debug with attach

As you have selected `Python- Debugpy` server, it will automatically populate the `Attach` configuration like this:

![DAP Configuration/Configuration](../images/python-debugpy/configuration_tab.png)

1. `Attach` as `Debug mode` should be selected. 
2. The DAP parameters of the launch should look like this:

```json
{
   "name": "Attach",
   "type": "python",
   "request": "attach",
   "redirectOutput": true,
   "connect": {
      "host": "127.0.0.1",
      "port": 5678
   }
}
```

`Address input` is pre-filled with `$connect.host` and `Port input` is pre-filled with `$connect.port` which means that JSON configuration is used
to retrieve the address and the port. You can see on the right of the inputs the real value of the address and port:

![Attach fields preview](../images/python-debugpy/attach_fields_preview.png)

If you don't want to use JSON configuration, you can fill directly the real value:

![Attach fields forced](../images/python-debugpy/attach_fields_forced.png)

The pre-filled configuration uses `"redirectOutput": true` to show output (like print(....))
in the IntelliJ console. Read [Python debugging in VS Code](https://code.visualstudio.com/docs/python/debugging) for more information.

## Set Breakpoint

After applying the run configuration, you should set a breakpoint to files which matches file mappings.
Set a breakpoint in the `test.py` file:

![Set Breakpoint](../images/python-debugpy/set_breakpoint.png)

## Start Python program in a Terminal

Open an `IntelliJ Terminal` (or other terminal), navigate to the directory containing your test file 
and execute the following command to start the debug server:

```
python -m debugpy --wait-for-client --listen 0.0.0.0:5678 test.py
```

This will make the script wait for a debugger to attach. 

![Start program](../images/python-debugpy/start_program.png)

## Debugging

Before starting the configuration, check that your address and port are correct:

![Start program and attach](../images/python-debugpy/start_program_and_attach.png)

You can start the run configuration in either Run or Debug mode. Once started, you should see DAP traces in the console:

![Debugging / Console](../images/python-debugpy/traces_in_console.png)

You will also see `Threads` and `Variables`:

![Debugging / Threads](../images/python-debugpy/debug_threads_tab.png)

As [Debugpy](https://github.com/microsoft/debugpy) can support completion, you should benefit it:

![Completion](../images/python-debugpy/completion.png)

