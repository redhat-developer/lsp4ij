# Developer guide

This section provides step-by-step instructions for contributing an LSP language server in your IntelliJ plugin.

## Reference LSP4IJ

### plugin.xml

The first step is to reference LSP4IJ. LSP4IJ uses `com.redhat.devtools.lsp4ij` as plugin Id.

You
need [to declare dependency in your plugin.xml](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html#3-dependency-declaration-in-pluginxml)
like this:

```xml

<idea-plugin>
    ...

    <depends>com.redhat.devtools.lsp4ij</depends>

    ...
</idea-plugin>
```

### Exclude all LSP4J dependencies

LSP4IJ depends on [Eclipse LSP4J](https://github.com/eclipse-lsp4j/lsp4j) (Java binding for
the [Language Server Protocol](https://microsoft.github.io/language-server-protocol) and
the [Debug Adapter Protocol](https://microsoft.github.io/debug-adapter-protocol).). It provides its own version of LSP4J
and its classes are loaded in the LSP4IJ plugin class loader.

If your IntelliJ Plugin contributes a Java-based language server, it must not embed its own version of LSP4J, in order to avoid conflicts with the version provided by
LSP4IJ. Failing to do so will result in `ClassCastException` errors to be thrown.
Make sure that the LSP4J dependency in your plugin is either declared with a `runtimeOnly` scope or excluded entirely,
if it's referenced as a transitive dependency.

Here is an example from the [build.gradle.kts](https://github.com/redhat-developer/intellij-quarkus/blob/main/build.gradle.kts) of the [Quarkus Tools](https://github.com/redhat-developer/intellij-quarkus) project,
excluding the LSP4J
dependency from the [Qute Language Server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls):

```kotlin
implementation("com.redhat.microprofile:com.redhat.qute.ls:0.17.0) {
  exclude("org.eclipse.lsp4j")
}
```

## Write Server-related code

## LanguageServerFactory

Create an implementation
of [LanguageServerFactory](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/LanguageServerFactory.java)
to expose your `my.language.server.MyLanguageServer`,
implementing [`StreamConnectionProvider`](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/server/StreamConnectionProvider.java) :

```java
package my.language.server;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.jetbrains.annotations.NotNull;

public class MyLanguageServerFactory implements LanguageServerFactory {

    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
        return new MyLanguageServer(project);
    }

    @Override //If you need to provide client specific features
    public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
        return new MyLanguageClient(project);
    }

    @Override //If you need to expose a custom server API
    public @NotNull Class<? extends LanguageServer> getServerInterface() {
        return MyCustomServerAPI.class;
    }

}
```

If you need to provide client specific features (e.g. commands), you can override the `createLanguageClient` method to
return your custom LSP client implementation.

If you need to expose a custom server API, i.e. custom commands supported by your language server, you can override
the `createLanguageClient` method to return a custom interface extending LSP4J's LanguageServer API.

### StreamConnectionProvider Implementation

Your `MyLanguageServer` needs to implement
the [StreamConnectionProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/server/StreamConnectionProvider.java)
API which :

* manages the language server lifecycle (start/stop)
* returns the input/error stream of LSP requests, responses, notifications.

Frequently, a language server process is started through a runtime like Java, Node.js, etc. In this case you
need to
extend [ProcessStreamConnectionProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/server/ProcessStreamConnectionProvider.java)

Here is a basic sample which starts the `path/to/my/language/server/main.js` language server written in JavaScript, with
the Node.js runtime found in "path/to/nodejs/node.exe":

```java
package my.language.server;

import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;

import java.util.Arrays;
import java.util.List;

public class MyLanguageServer extends ProcessStreamConnectionProvider {

    public MyLanguageServer() {
        List<String> commands = Arrays.asList("path/to/nodejs/node.exe", "path/to/my/language/server/main.js");
        super.setCommands(commands);
    }
}
```

If your language server is written in Java, you can
use [JavaProcessCommandBuilder](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/server/JavaProcessCommandBuilder.java) to build the launch command:

```java
package my.language.server;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.server.JavaProcessCommandBuilder;
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;

import java.util.Arrays;
import java.util.List;

public class MyLanguageServer extends ProcessStreamConnectionProvider {

    public MyLanguageServer(Project project) {
        List<String> commands = new JavaProcessCommandBuilder(project, "myLanguageServerId")
                .setJar("path/to/my/language/server/server.jar")
                .create();
        super.setCommands(commands);
    }
}
```

This builder takes care of filling command with the current Java runtime, and adds additional debug flags if the settings of the
language server `myLanguageServerId` defines a debug port.

You can see a complete example
with the [QuteServer](https://github.com/redhat-developer/intellij-quarkus/blob/main/src/main/java/com/redhat/devtools/intellij/qute/lsp/QuteServer.java) implementation.

### LanguageClientImpl

It is not required, but you can override the [LanguageClientImpl](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/client/LanguageClientImpl.java) to, for instance:

* add some IJ listeners when the language client is created.
* override some LSP methods.

```java
package my.language.server;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;

public class MyLanguageClient extends LanguageClientImpl {
    public MyLanguageClient(Project project) {
        super(project);
    }
}
```

If your language server manages custom LSP requests, it is recommended to extend [IndexAwareLanguageClient](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/client/IndexAwareLanguageClient.java), to ensure it won't be adversely affected by indexing operations.

You can see a full example
with [QuteLanguageClient](https://github.com/redhat-developer/intellij-quarkus/blob/main/src/main/java/com/redhat/devtools/intellij/qute/lsp/QuteLanguageClient.java)

## workspace/didChangeConfiguration

If you need to send a `workspace/didChangeConfiguration` with your settings, you can:

 * override and implement `LanguageClientImpl#createSettings()` to create the settings to send
 * call `LanguageClientImpl#triggerChangeConfiguration()` to send the settings from your custom listener (ex : track the change of your settings)

if you need to send a `workspace/didChangeConfiguration` when server is started, you can override and 
implement `LanguageClientImpl#handleServerStatusChanged(ServerStatus serverStatus)` like this:

```java
@Override
public void handleServerStatusChanged(ServerStatus serverStatus) {
  if(serverStatus == ServerStatus.started) {
  triggerChangeConfiguration();    
}
```

## Extension point declaration

The next step is to declare the server in your plugin.xml with the `com.redhat.devtools.lsp4ij.server` extension point
referencing your `my.language.server.MyLanguageServerFactory`:

```xml

<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
    <server id="myLanguageServerId"
            name="My Language Server"
            factoryClass="my.language.server.MyLanguageServerFactory">
        <description><![CDATA[
        Some description written in HTML to display it in the LSP consoles view and Language Servers settings.
        ]]>
        </description>
    </server>
</extensions>

```

A that point, once the declaration is done, your server should appear in the `LSP Consoles` view:

![My LanguageServer in LSP Console](./images/MyLanguageServerInLSPConsole.png)

## Declare file mappings

Once the server is defined in your `plugin.xml`, you still need to associate an IntelliJ language with the `server` defined by the id attribute.
You can use three kinds of mappings:

* [Language mapping](#language-mapping) with the `com.redhat.devtools.lsp4ij.languageMapping` extension point
  used to associate a [Language](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html) with a
  language server.
* [File type mapping](#file-type-mapping) with the `com.redhat.devtools.lsp4ij.fileTypeMapping` extension point
  used to associate a [File type](https://www.jetbrains.com/help/idea/creating-and-registering-file-types.html) with a
  language server.
* [File name pattern mapping](#file-name-pattern-mapping) with the `com.redhat.devtools.lsp4ij.fileNamePatternMapping`
  extension point
  used to associate a simple pattern file name (ex: `*.less`) with a language server.
  This mapping can be very helpful if you need to support syntax coloration with TextMate.
  Indeed, when you manually define
  a [File type](https://www.jetbrains.com/help/idea/creating-and-registering-file-types.html) for some file name
  patterns, **you loose the TextMate syntax coloration**.

### Language mapping

Here is a sample snippet to associate the `XML` language with the `myLanguageServerId` server:

```xml

<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">

    <languageMapping language="XML"
                     serverId="myLanguageServerId"/>

</extensions>
```

Some language servers use
the [TextDocumentItem#languageId](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentItem)
field to identify the document on the server side.
For instance the [vscode-css-languageservice](https://github.com/microsoft/vscode-css-languageservice) (used by the
vscode CSS language server) expects the `languageId` to be `css` or `less`.
To do that, you can declare it with the `languageId` attribute:

```xml

<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">

    <languageMapping language="CSS"
                     serverId="myLanguageServerId"
                     languageId="css"/>

</extensions>
```

If the language check is not enough, you can implement a
custom [DocumentMatcher](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/DocumentMatcher.java).
For instance your language server could be mapped to the `Java` language, and you could implement a DocumentMatcher
to check if the module containing the file contains certain Java classes in its classpath.

The DocumentMatcher is executed in a non blocking read action.

A document matcher looks like this:

```java
package my.language.server;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.AbstractDocumentMatcher;
import org.jetbrains.annotations.NotNull;

public class MyDocumentMatcher extends AbstractDocumentMatcher {

    @Override
    public boolean match(@NotNull VirtualFile virtualFile, @NotNull Project project) {
        return true;
    }
}
```

and it must be registered as language mapping, with the `documentMatcher` attribute:

```xml

<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">

    <languageMapping language="XML"
                     serverId="myLanguageServerId"
                     documentMatcher="my.language.server.MyDocumentMatcher"/>

</extensions>
```

### File type mapping

If your plugin does not define an
IntelliJ [Language](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html)
but just a [File type](https://www.jetbrains.com/help/idea/creating-and-registering-file-types.html), you can
use `fileTypeMapping`
instead of using `languageMapping`.

A good example is if you want to associate the existing `CSS` file type to the
`CSS language server` in `IntelliJ Community`, which only defines the CSS file type, but not the CSS language.

```xml

<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">

    <fileTypeMapping fileType="CSS"
                     serverId="myLanguageServerId"
                     languageId="css"/>

</extensions>
```

### File name pattern mapping

If your plugin does not define
a [File type](https://www.jetbrains.com/help/idea/creating-and-registering-file-types.html),
you can use `fileNamePatternMapping` instead of using `fileTypeMapping`.

A good example is if you want to associate `*.less` files to the `CSS language server`
where `LESS file type` doesn't exist in `IntelliJ Community`.

Using `fileNamePatternMapping` is recommended if you want to keep the `TextMate`-based syntax coloration.
(using [File type](https://www.jetbrains.com/help/idea/creating-and-registering-file-types.html) will override and
disable the syntax coloration with TextMate).

A good example is if you want to associate
the [TypeScript Language Server](./user-defined-ls/typescript-language-server.md)
without breaking existing syntax coloration managed with `TextMate` in `IntelliJ Community`.

```xml

<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">

    <fileNamePatternMapping patterns="*.less;*.scss"
                            serverId="myLanguageServerId"
                            languageId="css"/>

</extensions>
```

## Special cases

In general, LSP4IJ maps language server features to specific Intellij APIs. However, in some cases, the mapping needs to
be declared explicitly in your plugin.xml.

### Hover support

In case the language server provides hover support for a language already supported by Intellij or another plugin,
you'll need to add a special mapping between that language and LSP4IJ, in your plugin.xml:

```xml 

<lang.documentationProvider
        language="MyLanguage"
        implementationClass="com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationProvider"
        order="first"/>
```

See specific [hover implementation details](./LSPSupport.md#hover) for more details.

## Language Server Manager

If you need to `enable/disable` and / or `start/stop` your language server, LSP4IJ provides the [LanguageServerManager](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/LanguageServerManager.java) API which provides this support:

### Enable / Disable

By default, a language server is enabled : in other words, when the IDE starts, LSP4IJ tracks the opened files and starts the matching language servers.
If you need to manage this `enabled` state programmatically, your [LanguageServerFactory](#languageserverfactory) must implement [LanguageServerEnablementSupport](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/LanguageServerEnablementSupport.java):

```java
package my.language.server;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.LanguageServerEnablementSupport;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.jetbrains.annotations.NotNull;

public class MyLanguageServerFactory implements LanguageServerFactory, LanguageServerEnablementSupport {

    ...
  
    @Override
    public boolean isEnabled(@NotNull Project project) {
        // Get enabled state from your settings
      boolean enabled = ...
      return enabled;
    }

    @Override
    public void setEnabled(boolean enabled, Project project) { {
      // Update enabled state of your settings 
    }
  
}
```

### Start / Stop

To `start` your language server you can use the [LanguageServerManager](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/LanguageServerManager.java) API.

```java
Project project = ...
LanguageServerManager.getInstance(project).start("myLanguageServerId");
```

Here the language server will only start if there is an open file corresponding to your language server.

You can force the start of the language server with:

```java
LanguageServerManager.StartOptions options = new LanguageServerManager.StartOptions();
options.setForceStart(true);
Project project = ...
LanguageServerManager.getInstance(project).start("myLanguageServerId", options);
```

To `stop` your language server you can use the [LanguageServerManager](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/LanguageServerManager.java) API.

```java
Project project = ...
LanguageServerManager.getInstance(project).stop("myLanguageServerId");
```

Here the language server will be stopped and disabled.

If you just want to stop the language server without disabling it, you can write:

```java
LanguageServerManager.StopOptions options = new LanguageServerManager.StopOptions();
options.setWillDisable(false);
Project project = ...
LanguageServerManager.getInstance(project).stop("myLanguageServerId", options);
```

## LSP commands

### LSPCommandAction

If the language server support requires to implement a custom client command, you can extend
[LSPCommandAction.java](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/commands/LSPCommandAction.java) and register it 
in `plugin.xml` with a `standard` action element.

### Default commands

LSP4IJ provides default LSP commands that you language servers leverage.

#### editor.action.triggerSuggest

[TriggerSuggestAction.java](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/commands/editor/TriggerSuggestAction.java) emulates Visual Studio Code's `editor.action.triggerSuggest` command, 
to trigger code completion after selecting a completion item.

This command is used for instance with the [CSS Language Server](./user-defined-ls/vscode-css-language-server.md)
to reopen completion after `applying color completion item`:

![editor.action.triggerSuggest](./images/commands/TriggerSuggestAction.gif)

#### editor.action.showReferences

[ShowReferencesAction.java](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/commands/editor/ShowReferencesAction.java) emulates Visual Studio Code's `editor.action.showReferences` command, 
to show the LSP references in a popup.

This command is used for instance with the [TypeScript Language Server](./user-defined-ls/typescript-language-server.md) 
to open `references/implementations` in a popup when  clicking on a `Codelens` :

![editor.action.showReferences](./images/commands/ShowReferencesAction.png)