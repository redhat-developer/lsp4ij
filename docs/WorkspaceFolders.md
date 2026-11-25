# Workspace folder strategies

This page belongs to the [developer guide](./DeveloperGuide.md#workspace-folders): it describes how an IntelliJ plugin
chooses the workspace folders of its language server.

You can customize workspace folder discovery by using one of the built-in strategies, configuring a strategy with JSON, or implementing your own [WorkspaceFolderStrategy](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/workspaceFolder/WorkspaceFolderStrategy.java).

## Built-in Strategies

LSP4IJ provides several built-in strategies:

### ProjectWorkspaceFolderStrategy (Default)

Uses the IntelliJ [ProjectWorkspaceFolderStrategy](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/workspaceFolder/ProjectWorkspaceFolderStrategy.java) project base directories as workspace folders. By default, all folders are sent during initialization.

To customize the workspace folder strategy, you need to:

1. Create a class that extends `LSPWorkspaceFolderFeature` and overrides `createStrategy()`:

```java
package my.language.server;

import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ProjectWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import org.jetbrains.annotations.NotNull;

public class MyWorkspaceFolderFeature extends LSPWorkspaceFolderFeature {
    
    @Override
    protected @NotNull WorkspaceFolderStrategy createStrategy() {
        return new ProjectWorkspaceFolderStrategy();
    }
}
```

2. In your `LanguageServerFactory`, create `LSPClientFeatures` and set the workspace folder feature:

```java
package my.language.server;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.jetbrains.annotations.NotNull;

public class MyLanguageServerFactory implements LanguageServerFactory {
    
    @Override
    public @NotNull LSPClientFeatures createClientFeatures() {
        LSPClientFeatures clientFeatures = new LSPClientFeatures();
        clientFeatures.setWorkspaceFolderFeature(new MyWorkspaceFolderFeature());
        return clientFeatures;
    }
}
```

**Enable lazy loading:**

You can extend the strategy to enable lazy loading, sending workspace folders progressively via `workspace/didChangeWorkspaceFolders` as files are opened:

```java
package my.language.server;

import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ProjectWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import org.jetbrains.annotations.NotNull;

public class MyWorkspaceFolderFeature extends LSPWorkspaceFolderFeature {
    
    @Override
    protected @NotNull WorkspaceFolderStrategy createStrategy() {
        return new ProjectWorkspaceFolderStrategy(true /* Enable lazy loading */);
    }
}
```

This is useful for large projects where you want to avoid sending all workspace folders upfront.

### SourceRootsWorkspaceFolderStrategy

Uses IntelliJ module [SourceRootsWorkspaceFolderStrategy](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/workspaceFolder/SourceRootsWorkspaceFolderStrategy.java) source roots as workspace folders. By default, all folders are sent during initialization.

Useful when you want to expose only source directories (not the entire project root) to the language server.

```java
package my.language.server;

import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.SourceRootsWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import org.jetbrains.annotations.NotNull;

public class MyWorkspaceFolderFeature extends LSPWorkspaceFolderFeature {
    
    @Override
    protected @NotNull WorkspaceFolderStrategy createStrategy() {
        return new SourceRootsWorkspaceFolderStrategy();
    }
}
```

**Enable lazy loading for source roots:**

You can extend the strategy to enable lazy loading, sending workspace folders on-demand as files are opened:

```java
package my.language.server;

import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.SourceRootsWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import org.jetbrains.annotations.NotNull;

public class MyWorkspaceFolderFeature extends LSPWorkspaceFolderFeature {
    
    @Override
    protected @NotNull WorkspaceFolderStrategy createStrategy() {
        return new SourceRootsWorkspaceFolderStrategy(true /* Enable lazy loading */);
    }
}
```

### MarkersWorkspaceFolderStrategy

Use [MarkersWorkspaceFolderStrategy](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/workspaceFolder/MarkersWorkspaceFolderStrategy.java)
to discover workspace folders dynamically by walking up the directory tree looking for marker files. 
Folders are discovered lazily as files are opened.

```java
package my.language.server;

import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.MarkersWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import org.jetbrains.annotations.NotNull;

public class MyWorkspaceFolderFeature extends LSPWorkspaceFolderFeature {
    
    @Override
    protected @NotNull WorkspaceFolderStrategy createStrategy() {
        return new MarkersWorkspaceFolderStrategy("pyproject.toml", "setup.py");
    }
}
```

This is particularly useful for:
- **Monorepos** with multiple projects
- **Python projects** with `pyproject.toml` or `setup.py`
- **Node.js projects** with multiple `package.json` files

For example, when opening `/monorepo/backend/src/app.py` with markers `["pyproject.toml"]`:
1. Checks `/monorepo/backend/src/` for `pyproject.toml`
2. Checks `/monorepo/backend/` for `pyproject.toml` ← **Found!**
3. Returns `/monorepo/backend/` as workspace folder

### ConfigurableWorkspaceFolderStrategy

[ConfigurableWorkspaceFolderStrategy](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/workspaceFolder/ConfigurableWorkspaceFolderStrategy.java) is a flexible strategy that can be configured with JSON to support different root types, markers, and lazy loading options:

```java
package my.language.server;

import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ConfigurableWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import org.jetbrains.annotations.NotNull;

public class MyWorkspaceFolderFeature extends LSPWorkspaceFolderFeature {
    
    @Override
    protected @NotNull WorkspaceFolderStrategy createStrategy() {
        // language=json
        String config = """
            {
              "markers": ["pyproject.toml", "setup.py"],
              "lazy": true
            }
            """;
        ConfigurableWorkspaceFolderStrategy strategy = new ConfigurableWorkspaceFolderStrategy();
        strategy.configure(config);
        return strategy;
    }
}
```

## Lazy vs Eager Loading

All workspace folder strategies support two loading modes:

### Eager Loading (default: `lazy: false`)

All workspace folders are sent **once during initialization**:

```
Client → Server: initialize(workspaceFolders: ["/project/backend", "/project/frontend"])
Server → Client: initialized
```

**Pros:**
- Server has complete workspace context from the start
- Simpler implementation

**Cons:**
- Slower IDE startup for large projects
- Sends folders that may never be used

### Lazy Loading (`lazy: true`)

Workspace folders are sent **progressively via notifications** as files are opened.

**Initialization with empty workspace folders:**

```text
Client → Server: initialize
{
  "workspaceFolders": []
}

Server → Client: initialized
```

**When a file is opened, the workspace folder is discovered and sent:**

```
User opens: C:/Users/Foo/lsp4ij-demo/src/Main.java

[Trace] Sending notification 'workspace/didChangeWorkspaceFolders'
Params: {
  "event": {
    "added": [
      {
        "uri": "file:///C:/Users/Foo/lsp4ij-demo",
        "name": "lsp4ij-demo"
      }
    ],
    "removed": []
  }
}
```

**Subsequent files in the same workspace folder don't trigger notifications:**

```
User opens: C:/Users/Foo/lsp4ij-demo/src/Utils.java
// No notification sent - workspace folder already known
```

**Opening a file from a different workspace folder:**

```
User opens: C:/Users/Foo/lsp4ij-demo/another-project/src/App.java

[Trace] Sending notification 'workspace/didChangeWorkspaceFolders'
Params: {
  "event": {
    "added": [
      {
        "uri": "file:///C:/Users/Foo/lsp4ij-demo/another-project",
        "name": "another-project"
      }
    ],
    "removed": []
  }
}
```

**Pros:**
- Faster IDE startup (no full project scan)
- Reduced memory usage (only active workspace folders)
- Ideal for monorepos

**Cons:**
- Server must support `workspace/didChangeWorkspaceFolders`
- More complex notification flow

**When to use lazy loading:**
- Large monorepos with multiple projects
- Projects with many modules (only load modules being edited)
- Marker-based strategies (avoid scanning entire filesystem)

## Configuration Options

The [ConfigurableWorkspaceFolderStrategy](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/workspaceFolder/ConfigurableWorkspaceFolderStrategy.java) supports the following JSON configuration:

### Root Type

Determines how workspace folders are discovered:

- **`PROJECT_BASE`** (default): Uses IntelliJ project base directories
- **`SOURCE_ROOTS`**: Uses module source roots
- **`NONE`**: No workspace folders

```json
{
  "rootType": "SOURCE_ROOTS"
}
```

### Server Instances

Spreads the files of a project over several language server instances, see [Several Language Server Instances](./DeveloperGuide.md#several-language-server-instances):

```json
{
  "serverInstances": { "markers": ["pyproject.toml"] }
}
```

### Marker-based Discovery

When `markers` is specified, LSP4IJ walks up the directory tree from each opened file looking for the specified marker files:

```json
{
  "markers": ["pyproject.toml", "setup.py", "requirements.txt"]
}
```

For example, when opening `/project/src/main/app.py`, LSP4IJ will:
1. Check if `/project/src/main/` contains a marker file
2. Check if `/project/src/` contains a marker file
3. Check if `/project/` contains a marker file
4. Return the first directory containing any of the marker files

This is useful for monorepos with multiple language server roots.

### Lazy Loading

Controls when and how workspace folders are sent to the server:

- **`lazy: false`** (default): All workspace folders are sent **during initialization** via the `initialize` request
- **`lazy: true`**: Workspace folders are discovered and sent **on-demand** as files are opened via `workspace/didChangeWorkspaceFolders` notification

**How lazy loading works:**

When `lazy: true`, the language server receives workspace folders progressively:

1. **Initialization**: Empty or minimal workspace folders list sent in `initialize` request
2. **File opened**: User opens `/project/backend/src/app.py`
3. **Discovery**: LSP4IJ discovers the workspace folder (e.g., `/project/backend/`)
4. **Notification**: Sends `workspace/didChangeWorkspaceFolders` with the new folder added
5. **Repeat**: Each time a file from a new workspace folder is opened, a notification is sent

**Benefits:**
- Faster IDE startup (no full project scan)
- Reduced memory usage (server only loads active workspace folders)
- Better for large monorepos with many projects

**Example - Lazy loading with markers:**
```json
{
  "markers": ["pyproject.toml"]
}
```

**Example - Lazy loading with source roots:**
```json
{
  "rootType": "SOURCE_ROOTS",
  "lazy": true
}
```

## Configuration Examples

**Python project with pyproject.toml:**
```json
{
  "markers": ["pyproject.toml"]
}
```

**Multi-module project using source roots:**
```json
{
  "rootType": "SOURCE_ROOTS",
  "lazy": false
}
```

**Monorepo with multiple package.json:**
```json
{
  "markers": ["package.json"]
}
```

## Custom Strategy

For complete control, implement your own `WorkspaceFolderStrategy`:

1. Create your custom strategy implementation:

```java
package my.language.server;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MyWorkspaceFolderStrategy implements WorkspaceFolderStrategy {
    
    @Override
    public @NotNull List<WorkspaceFolder> getWorkspaceFolders(
            @NotNull Project project, 
            @NotNull FileUriSupport fileUriSupport) {
        // Return all the workspace folders of the project, which getInitialWorkspaceFolders sends during
        // initialization when sendAllFoldersOnInitialization() returns true
        List<WorkspaceFolder> folders = new ArrayList<>();
        // ... custom logic ...
        return folders;
    }
    
    @Override
    public @Nullable WorkspaceFolder getWorkspaceFolderForFile(
            @NotNull VirtualFile file,
            @NotNull Project project,
            @NotNull FileUriSupport fileUriSupport) {
        // Return the workspace folder for a given file
        // ... custom logic ...
        return null;
    }
    
    @Override
    public boolean sendAllFoldersOnInitialization() {
        // Return true to send all folders at init, false for lazy loading
        return true;
    }
}
```

2. Create a workspace folder feature that uses your custom strategy:

```java
package my.language.server;

import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import org.jetbrains.annotations.NotNull;

public class MyWorkspaceFolderFeature extends LSPWorkspaceFolderFeature {
    
    @Override
    protected @NotNull WorkspaceFolderStrategy createStrategy() {
        return new MyWorkspaceFolderStrategy();
    }
}
```
