# Buildx Debugging for Dockerfiles

To debug [Dockerfiles](https://docs.docker.com/build/concepts/dockerfile/), you can use the DAP implementation provided by [Buildx](https://github.com/docker/buildx).

## Requirements

You will need Buildx v0.28.0. You can get Buildx by [installing Docker Desktop](https://docs.docker.com/install/) or by [installing Buildx manually](https://github.com/docker/buildx?tab=readme-ov-file#manual-download).

1. Run `docker buildx version` to check your Buildx version.
2. Run `BUILDX_EXPERIMENTAL=1 docker buildx dap` to check that the `dap` subcommand is available in your Buildx installation.

## Setup

1. Create a Debug Adapter Protocol launch configuration.

![Use the plus button to create a new Debug Adapter Protocol configuration](../images/buildx-dockerfile/setup-01-create-config.png)

2. Click on the 'Create a new server' hyperlink.

![Create a new server instead of using an existing Debug Adapter Server](../images/buildx-dockerfile/setup-02-create-server.png)

3. Click 'Choose template...' and select the 'Docker: Dockerfile Build Debugging' template.

![Select the Dockerfile Build Debugging template](../images/buildx-dockerfile/setup-03-template-selection.png)

4. In 'Environment variables:', enter in `BUILDX_EXPERIMENTAL=1`.

![Set the environment variable in the dialog](../images/buildx-dockerfile/setup-04-env-var.png)

5. Click the 'OK' button to save the server configuration.
6. Now go to the 'Configuration' tab and find select a Dockerfile in the 'File:' field.

![Set the Dockerfile to debug](../images/buildx-dockerfile/setup-05-file-selection.png)

7. Click the 'OK' button to save the launch configuration.
8. Open your Dockerfile and place a breakpoint.
9. Launch your configuration in debug mode to pause the Dockerfile build!

![Debug Dockerfiles with LSP4IJ](../images/buildx-dockerfile/setup-06-start-debug.png)

## Configuration

If you would like to make further configurations to how the build is launched, you can refer to [this document](https://github.com/docker/buildx/blob/77315f947ea4f800639bbd0132ff3012780ed6fc/docs/reference/buildx_dap_build.md#examples) to modify the DAP JSON parameters that are passed.

![Debug Dockerfiles with LSP4IJ](../images/buildx-dockerfile/config-01-dap-parameters.png)

## Features

### View the environment of the image being built

While your build is in a suspended state, you can review variables, the working directory, and the files and folders of the image.

![See variables and files while the build is suspended](../images/buildx-dockerfile/features-01-environment.png)

### Open a shell inside the image

Evaluate the `exec` expression and Buildx will open a terminal for you to type commands into the image directly.

![Run the exec command in the expressions input field](../images/buildx-dockerfile/features-02-exec-command.png)

![Modify and experiment with the contents of the image in its suspended state](../images/buildx-dockerfile/features-02-modify-contents.png)

Please note however that any changes you make will be wiped when you advance the debugger.

![Observe that modifications are not permanent and will be reset when the build continues](../images/buildx-dockerfile/features-02-shell-reset.png)
