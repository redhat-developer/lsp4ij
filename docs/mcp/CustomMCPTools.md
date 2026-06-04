# Creating Custom MCP Tools

This guide explains how to create custom MCP tools for LSP4IJ, allowing you to expose
project-specific or plugin-specific capabilities to AI assistants like Claude.

## Overview

The MCP (Model Context Protocol) server in LSP4IJ uses an extension point system that
allows plugins to register custom tools. Each tool is registered individually with its
name and description defined in plugin.xml.

## Extension Point Architecture

```
MCPTool (interface)
    ├── getInputSchema() : Map
    └── execute(...) : CallToolResult
```

Plugins register tools via the extension point in plugin.xml:
```xml
<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
    <mcpTool 
        name="myPlugin-myAction"
        description="Performs a custom action on the project"
        implementation="com.example.MyCustomTool"/>
</extensions>
```

The tool name and description are defined in XML (not in Java code), making them
easy to configure without recompilation.

## Creating a Custom Tool

### 1. Implement the MCPTool interface

Create a class that implements `com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPTool`:

```java
package com.example.myplugin;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPTool;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class MyCustomTool implements MCPTool {

    @Override
    public @NotNull Map<String, Object> getInputSchema() {
        // Define the JSON Schema for your tool's input parameters
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "filePath", Map.of(
                    "type", "string",
                    "description", "Path to the file to process"
                ),
                "action", Map.of(
                    "type", "string",
                    "enum", List.of("analyze", "format", "validate"),
                    "description", "Action to perform on the file"
                )
            ),
            "required", List.of("filePath", "action")
        );
    }

    @Override
    public @NotNull McpSchema.CallToolResult execute(
            @NotNull Project project,
            @NotNull McpSyncServerExchange exchange,
            @NotNull McpSchema.CallToolRequest request) {
        
        try {
            // Extract parameters from the request
            Map<String, Object> args = request.arguments();
            String filePath = (String) args.get("filePath");
            String action = (String) args.get("action");

            // Perform your custom logic
            String result = performAction(project, filePath, action);

            // Return success result
            return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(result)))
                .build();

        } catch (IllegalArgumentException e) {
            // Return parameter validation error
            return McpSchema.CallToolResult.builder()
                .isError(true)
                .content(List.of(new McpSchema.TextContent("Invalid parameters: " + e.getMessage())))
                .build();

        } catch (Exception e) {
            // Return execution error
            return McpSchema.CallToolResult.builder()
                .isError(true)
                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                .build();
        }
    }

    private String performAction(Project project, String filePath, String action) {
        // Your custom tool logic here
        return "Performed " + action + " on " + filePath;
    }
}
```

### 2. Register the Tool in plugin.xml

Add your tool to the `com.redhat.devtools.lsp4ij` extension point:

```xml
<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
    <mcpTool 
        name="myPlugin-customAction"
        description="Performs a custom action on project files"
        implementation="com.example.myplugin.MyCustomTool"/>
</extensions>
```

**Important naming conventions:**
- Tool names should follow the pattern: `namespace-action`
- Valid characters: letters, digits, hyphens, underscores, dots
- Examples: `myPlugin-analyze`, `git-commit`, `lsp-listServers`

### 3. Build and Test

1. Build your plugin
2. Start IntelliJ with your plugin loaded
3. Start the MCP server for a project (Tools → Start MCP Server)
4. Connect Claude Code and use `--tools` to see your custom tool
5. Invoke your tool: Claude will see it and can call it based on the description

## Tool Naming Best Practices

- **Namespace prefix**: Use your plugin name to avoid conflicts
  - Good: `myPlugin-analyze`, `git-commit`, `docker-build`
  - Bad: `analyze`, `commit`, `build`

- **Action suffix**: Use a verb that clearly describes what the tool does
  - Good: `lsp-listServers`, `lsp-executeCommand`, `git-status`
  - Bad: `lsp-servers`, `lsp-command`, `git-info`

- **Character restrictions**: Only use `[A-Za-z0-9_.-]`
  - Good: `my-plugin.action_v2`
  - Bad: `my/plugin/action`, `my plugin:action`

## Input Schema Guidelines

The input schema is a JSON Schema (Draft 2020-12) that defines your tool's parameters:

```java
Map.of(
    "type", "object",
    "properties", Map.of(
        "paramName", Map.of(
            "type", "string",              // string, number, boolean, array, object
            "description", "What it does",  // Help AI understand when to use it
            "enum", List.of("opt1", "opt2") // Optional: restrict to specific values
        )
    ),
    "required", List.of("paramName")  // List of required parameter names
)
```

**Common schema patterns:**

String parameter:
```java
"serverId", Map.of(
    "type", "string",
    "description", "The language server ID (e.g., 'rust-analyzer')"
)
```

Number parameter:
```java
"timeout", Map.of(
    "type", "number",
    "description", "Timeout in seconds",
    "minimum", 1,
    "maximum", 300
)
```

Array parameter:
```java
"files", Map.of(
    "type", "array",
    "description", "List of file paths",
    "items", Map.of("type", "string")
)
```

Enum parameter:
```java
"level", Map.of(
    "type", "string",
    "enum", List.of("error", "warning", "info"),
    "description", "Diagnostic severity level"
)
```

## Error Handling

Always handle errors gracefully:

```java
@Override
public @NotNull McpSchema.CallToolResult execute(...) {
    try {
        // Your logic
        return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent(result)))
            .build();

    } catch (IllegalArgumentException e) {
        // Parameter validation errors
        return McpSchema.CallToolResult.builder()
            .isError(true)
            .content(List.of(new McpSchema.TextContent("Invalid parameters: " + e.getMessage())))
            .build();

    } catch (Exception e) {
        // Execution errors
        LOGGER.error("Tool execution failed", e);
        return McpSchema.CallToolResult.builder()
            .isError(true)
            .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
            .build();
    }
}
```

## Result Formatting

For complex results, format as JSON:

```java
import tools.jackson.databind.json.JsonMapper;

// Create result object
Map<String, Object> result = Map.of(
    "status", "success",
    "filesProcessed", 42,
    "errors", List.of()
);

// Serialize to pretty JSON
String resultJson = JsonMapper.builder()
    .build()
    .writerWithDefaultPrettyPrinter()
    .writeValueAsString(result);

return McpSchema.CallToolResult.builder()
    .content(List.of(new McpSchema.TextContent(resultJson)))
    .build();
```

## Testing Your Tool

### Manual Testing with Claude Code

1. Start the MCP server in IntelliJ:
   - Menu: **Tools → Start MCP Server**
   - Check the Event Log for: "LSP4IJ MCP Server started successfully"

2. Configure Claude Code's `.claude/settings.json`:
```json
{
  "mcpServers": {
    "lsp4ij": {
      "transport": {
        "type": "sse",
        "url": "http://localhost:9339/sse"
      }
    }
  }
}
```

3. In Claude Code, verify the tool is available:
```
--tools
```
Your tool should appear in the list with its description.

4. Test invocation:
Ask Claude to use your tool. Example:
```
Use myPlugin-customAction to analyze the file src/Main.java
```

Claude will invoke your tool with the appropriate parameters.

### Debugging

Enable debug logging to see tool invocations:

```java
private static final Logger LOGGER = LoggerFactory.getLogger(MyCustomTool.class);

@Override
public @NotNull McpSchema.CallToolResult execute(...) {
    LOGGER.info("Tool invoked with arguments: {}", request.arguments());
    // ... rest of implementation
}
```

Check IntelliJ's log file (Help → Show Log in...) for your debug messages.

## Example: Git Status Tool

Here's a complete example that exposes Git status as an MCP tool:

```java
package com.example.gitplugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPTool;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitStatusTool implements MCPTool {

    @Override
    public @NotNull Map<String, Object> getInputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public @NotNull McpSchema.CallToolResult execute(
            @NotNull Project project,
            @NotNull McpSyncServerExchange exchange,
            @NotNull McpSchema.CallToolRequest request) {
        
        try {
            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            
            Map<String, Object> status = new HashMap<>();
            status.put("modified", changeListManager.getModifiedWithoutEditing().size());
            status.put("added", changeListManager.getAffectedFiles().size());
            status.put("unversioned", changeListManager.getUnversionedFiles().size());
            
            String result = tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(status);

            return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(result)))
                .build();

        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                .isError(true)
                .content(List.of(new McpSchema.TextContent("Error getting Git status: " + e.getMessage())))
                .build();
        }
    }
}
```

plugin.xml:
```xml
<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
    <mcpTool 
        name="git-status"
        description="Get the Git status of the current project"
        implementation="com.example.gitplugin.GitStatusTool"/>
</extensions>
```

## Advanced: Async Tool Execution

For long-running operations, consider using IntelliJ's background tasks:

```java
@Override
public @NotNull McpSchema.CallToolResult execute(...) {
    CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
        // Long-running operation
        return performHeavyAnalysis(project);
    });

    try {
        String result = future.get(30, TimeUnit.SECONDS);
        return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent(result)))
            .build();
    } catch (TimeoutException e) {
        return McpSchema.CallToolResult.builder()
            .isError(true)
            .content(List.of(new McpSchema.TextContent("Operation timed out")))
            .build();
    }
}
```

## Next Steps

- See [LSP Tools Overview](LSPToolsOverview.md) for examples of LSP-specific tools
- See [MCP Server Architecture](Architecture.md) for how the MCP server works internally
- Explore the built-in tools in `com.redhat.devtools.lsp4ij.mcp.tools` for more examples
