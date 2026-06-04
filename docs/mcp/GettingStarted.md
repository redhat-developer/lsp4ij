# Getting Started with LSP4IJ MCP Server

This guide explains how to start the LSP4IJ MCP (Model Context Protocol) server and connect it to Claude Code CLI.

## Overview

The LSP4IJ MCP server exposes language servers running in IntelliJ IDEA as MCP tools, allowing AI assistants like Claude Code to interact with them. This gives Claude access to:

- List all started language servers
- Execute commands on language servers
- Access LSP capabilities (code actions, diagnostics, symbols, etc.)

## Prerequisites

- IntelliJ IDEA with LSP4IJ plugin installed
- Claude Code CLI installed
- A project with language servers configured in IntelliJ

## Step 1: Start the MCP Server in IntelliJ

1. Open your project in IntelliJ IDEA
2. Make sure your language servers are started (they auto-start when you open relevant files)
3. Go to **Tools → Start MCP Server**
4. Check the Event Log for the confirmation message:
   ```
   LSP4IJ MCP Server started successfully
     SSE endpoint: http://localhost:9339/sse
     Message endpoint: http://localhost:9339/mcp/message
   ```

The server is now running on port 9339 and waiting for client connections.

## Step 2: Register the MCP Server with Claude CLI

Claude CLI needs to know about the MCP server. Use the `claude mcp add` command:

```bash
claude mcp add --transport sse lsp4ij http://localhost:9339/sse
```

**What this does:**
- Registers the LSP4IJ MCP server with Claude CLI
- Updates `~/.claude.json` (your Claude CLI config file) with the server configuration
- The server is scoped to the current project directory

**Output:**
```
Added SSE MCP server lsp4ij with URL: http://localhost:9339/sse to local config
File modified: C:\Users\YourName\.claude.json [project: C:\path\to\your\project]
```

## Step 3: Verify the Connection

Check that the MCP server is connected:

```bash
claude mcp list
```

**Expected output:**
```
Checking MCP server health…

lsp4ij: http://localhost:9339/sse (SSE) - ✓ Connected
```

If you see **✓ Connected**, the setup is complete!

## Step 4: Use MCP Tools in Claude

Start a Claude CLI session in your project directory and check available tools:

```bash
--tools
```

You should see the LSP4IJ tools listed:
- **lsp-listServers** - List all started language servers in the project with their capabilities
- **lsp-executeCommand** - Execute a command on a specific language server

### Example Usage

Ask Claude to use the tools:

```
List all language servers running in this project
```

Claude will use the `lsp-listServers` tool to query IntelliJ's running language servers.

```
Execute the "rust-analyzer.expandMacro" command on the rust-analyzer server
```

Claude will use the `lsp-executeCommand` tool to invoke the command.

## Configuration Details

### Where is the MCP server registered?

When you run `claude mcp add`, it updates your `~/.claude.json` file. The configuration is stored **per project**:

**Linux/Mac:** `~/.claude.json`  
**Windows:** `C:\Users\YourName\.claude.json`

Example entry in `.claude.json`:

```json
{
  "projects": {
    "C:/Users/YourName/git/my-project": {
      "mcpServers": {
        "lsp4ij": {
          "type": "sse",
          "url": "http://localhost:9339/sse"
        }
      }
    }
  }
}
```

This means:
- ✅ **One-time setup per project**: You only need to run `claude mcp add` once for each project
- ✅ **Persists across sessions**: The configuration is saved and will work in future Claude CLI sessions
- ✅ **Project-scoped**: Each project can have its own MCP server configuration

### Do I need to register for every project?

**Yes**, if you want to use the LSP4IJ MCP server in multiple projects, you need to register it once per project:

```bash
cd /path/to/project-a
claude mcp add --transport sse lsp4ij http://localhost:9339/sse

cd /path/to/project-b
claude mcp add --transport sse lsp4ij http://localhost:9339/sse
```

However, the **MCP server itself** (running in IntelliJ) is the same - it serves whichever IntelliJ project is currently open.

## Troubleshooting

### Tools don't appear in `--tools`

**Solution 1: Restart Claude CLI**
```bash
# Exit current session
exit

# Start a new session
claude
--tools
```

**Solution 2: Verify server is running**
```bash
curl -H 'Accept: text/event-stream' http://localhost:9339/sse
```

Expected response (within a few seconds):
```
event: endpoint
data: /mcp/message?sessionId=xxx-xxx-xxx
```

If you get a connection error, the MCP server is not running in IntelliJ. Go back to Step 1.

**Solution 3: Check registration**
```bash
claude mcp list
```

If `lsp4ij` doesn't appear, run `claude mcp add` again.

### "Connection refused" error

The MCP server is not running. Make sure:
1. IntelliJ is open
2. You've clicked **Tools → Start MCP Server**
3. The Event Log shows "LSP4IJ MCP Server started successfully"

### "Health check failed" in `claude mcp list`

The MCP server was registered but is no longer running. Restart it:
1. In IntelliJ: **Tools → Start MCP Server**
2. Verify: `claude mcp list` should show **✓ Connected**

### Tools work but show empty results

The language servers might not be started yet:
1. In IntelliJ, open a file that triggers the language server (e.g., a `.rs` file for rust-analyzer)
2. Wait a few seconds for the server to initialize
3. Ask Claude to list servers again

## Next Steps

- **[Custom MCP Tools](CustomMCPTools.md)** - Create your own MCP tools
- **[LSP Tools Overview](LSPToolsOverview.md)** - Learn about available LSP-based tools
- **[MCP Server Architecture](Architecture.md)** - Understand how the MCP server works

## Advanced: Manual Configuration

If you prefer not to use `claude mcp add`, you can manually edit `~/.claude.json`:

```json
{
  "projects": {
    "/absolute/path/to/your/project": {
      "mcpServers": {
        "lsp4ij": {
          "type": "sse",
          "url": "http://localhost:9339/sse"
        }
      }
    }
  }
}
```

**Note:** Use the `claude mcp add` command instead - it's simpler and handles the path correctly.

## Stopping the MCP Server

To stop the MCP server in IntelliJ:
- There's currently no explicit "Stop" action
- The server stops when you close IntelliJ or the project
- Alternatively, close the MCP server's log/status window if one appears

Claude CLI will automatically detect the disconnection and show the server as offline in `claude mcp list`.
