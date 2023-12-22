# Developer guide

This section provide step-by-step instructions how to contribute your own LSP language server in your IntelliJ plugin.

## Reference LSP4IJ

### plugin.xml

The first step is to reference LSP4IJ. LSP4IJ uses `com.redhat.devtools.lsp4ij` as plugin Id.

You need [to declare dependency in your plugin.xml](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html#3-dependency-declaration-in-pluginxml) like this:

```xml
<idea-plugin>
  ...

  <depends>com.redhat.devtools.lsp4ij</depends>

  ...
</idea-plugin>
```

### Exclude all LSP4J dependencies

LSP4IJ depends on [Eclipse LSP4J](https://github.com/eclipse-lsp4j/lsp4j) (Java binding for the [Language Server Protocol](https://microsoft.github.io/language-server-protocol) and the [Debug Adapter Protocol](https://microsoft.github.io/debug-adapter-protocol).). It provides its own version of LSP4J and its classes are loaded in the LSP4IJ plugin class loader.

Your IntelliJ Plugin must not embed its own version of LSP4J, in order to avoid conflicts with the version provided by LSP4IJ. Failing to do so will result in `ClassCastException` errors to be thrown. 
Make sure that the LSP4J dependency in your plugin is either declared with a `runtimeOnly` scope or excluded entirely, if it's included as a transitive dependency.

Here is a sample used in [Quarkus Tools](https://github.com/redhat-developer/intellij-quarkus) in [build.gradle.kts](https://github.com/redhat-developer/intellij-quarkus/blob/main/build.gradle.kts) to exclude LSP4J dependency from the [Qute Language Server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls):

```
implementation("com.redhat.microprofile:com.redhat.qute.ls:0.17.0) {
  exclude("org.eclipse.lsp4j")
}
```

## Declare server

## LanguageServerFactory

Create an implementation of [LanguageServerFactory](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/LanguageServerFactory.java) to expose your `my.language.server.MyLanguageServer`, implementing [`StreamConnectionProvider`](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/server/StreamConnectionProvider.java) :

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

If you need to provide client specific features (e.g. commands), you can override the `createLanguageClient` method to return your custom LSP client implementation.
If you need to expose a custom server API, i.e. custom commands supported by your language server, you can override the `createLanguageClient` method to return a custom interface extending LSP4J's LanguageServer API.

### StreamConnectionProvider Implementation

Your `MyLanguageServer` needs to implement the [StreamConnectionProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/server/StreamConnectionProvider.java) API which manages:

 * the language server lifecycle (start/stop)
 * returns the input/error stream of LSP requests, responses, notifications.

Generally, the language server is started with a process by using a runtime like Java, NodeJS, etc. In this case you need to extend [ProcessStreamConnectionProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/server/ProcessStreamConnectionProvider.java)

Here is a basic sample which starts the `path/to/my/language/server/main.js` language server written in JavaScript, with the NodeJS runtime found in "path/to/nodejs/node.exe":

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

If your language server is written in Java, to build the command, you can use [JavaProcessCommandBuilder](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/server/JavaProcessCommandBuilder.java):

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

This builder takes care of filling command with Java runtime and generate the command with debug if the settings of the language server `myLanguageServerId` defines a debug port.

You can see a full sample with [QuteServer](https://github.com/redhat-developer/intellij-quarkus/blob/main/src/main/java/com/redhat/devtools/intellij/qute/lsp/QuteServer.java)

### LanguageClientImpl

It is not required but you can override the [LanguageClientImpl](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/client/LanguageClientImpl.java) to, for instance:

 * add some IJ listeners when language client is created.
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
 
If your language server manages custom LSP requests, it is advised to extend [IndexAwareLanguageClient](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/client/IndexAwareLanguageClient.java)

You can see a full sample with [QuteLanguageClient](https://github.com/redhat-developer/intellij-quarkus/blob/main/src/main/java/com/redhat/devtools/intellij/qute/lsp/QuteLanguageClient.java)


## Declare server with extension point

The last step is to declare the server in your plugin.xml with `com.redhat.devtools.lsp4ij.server` extension point 
to use your `my.language.server.MyLanguageServerFactory`:

```xml
<idea-plugin>

  <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
    <server id="myLanguageServerId"
            label="My Language Server"
            factoryClass="my.language.server.MyLanguageServerFactory">
      <description><![CDATA[
        Some description written in HTML to display it in LSP consoles and Language Servers settings.
        ]]>
      </description>
    </server>
  </extensions>
  
</idea-plugin>
```

Once the declaration is done, your server should appear in the LSP console:

![My LanguageServer in LSP Console](./images/MyLanguageServerInLSPConsole.png)

## Declare language mapping with extension point

Once the server is defined, you need to associate an IntelliJ language with the `server` defined by the id attribute 
with the `com.redhat.devtools.lsp4ij.languageMapping` extension point.

Here is sample snippet to associate the `XML` language with the `myLanguageServerId` server:

```xml
</idea-plugin>

  <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">

    <languageMapping language="XML"
                     serverId="myLanguageServerId" />

  </extensions>
```

Some language servers use the [TextDocumentItem#languageId](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentItem) field to identify the document on the server side. 
For instance the [vscode-css-languageservice](https://github.com/microsoft/vscode-css-languageservice) (used by the vscode CSS language server) expects the `languageId` to be `css` or `less`. 
To do that, you can declare it with the `languageId` attribute:

```xml
</idea-plugin>

  <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">

    <languageMapping language="CSS"
                     serverId="myLanguageServerId"
                     languageId="css" />

  </extensions>
```

If the language check is not enough, you can implement a custom [DocumentMatcher](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/DocumentMatcher.java).
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
</idea-plugin>

  <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">

    <languageMapping language="XML"
                     serverId="myLanguageServerId"
                     documentMatcher="my.language.server.MyDocumentMatcher" />

  </extensions>
```

## Declare fileType mapping with extension point

If your plugin does not define an IntelliJ `language` but just a `File Type`, you can use `fileTypeMapping` 
instead of using `languageMapping`. A good sample is if you want to map a CSS file to the CSS language server in 
IntelliJ Community which only defines the CSS file type but not the CSS language.

```xml
</idea-plugin>

  <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">

    <fileTypeMapping fileType="CSS"
                     serverId="myLanguageServerId"
                     languageId="css" />

  </extensions>
```

## Special cases

In general, LSP4IJ maps language server features to specific Intellij APIs. However, in some cases, the mapping needs to be declared explicitly in your plugin.xml.

### Hover support

In case the language server provides hover support for a language already supported by Intellij or another plugin, you'll need to add a special mapping between that language and LSP4IJ, in your plugin.xml:
 
```xml 
<lang.documentationProvider 
  language="MyLanguage" 
  implementationClass="com.redhat.devtools.lsp4ij.operations.documentation.LSPDocumentationProvider" 
  order="first"/>
```

See specific [hover implementation details](./LSPSupport.md#hover) for more details.

```