# LSP4IJ MCP Server Design

## Vision

Exposer les Language Servers **déjà gérés** par LSP4IJ comme outils MCP (Model Context Protocol), permettant aux assistants IA comme Claude Code d'accéder aux capacités LSP avec le contexte chaud (serveurs démarrés, fichiers ouverts, diagnostics actifs).

### Architecture Globale

```
Claude / AI Assistant
    ↕ (MCP - stdio/JSON-RPC)
LSP4IJ MCP Server (IntelliJ Plugin)
    ↕ (accès via LanguageServiceAccessor)
LanguageServerWrapper + LanguageClientImpl
    ↕ (JSON-RPC / LSP)
Language Servers gérés par LSP4IJ
    (Qute LS, Kotlin LS, TypeScript, Rust Analyzer, etc.)
```

**Avantage clé** : 
- ✅ Pas de duplication de processus LS
- ✅ Le MCP server utilise les LS **déjà lancés** par IntelliJ
- ✅ **CRITIQUE** : Le MCP n'est **pas** un nouveau client LSP
  - Il appelle les méthodes du `LanguageServerWrapper` existant
  - Qui utilise le `LanguageClientImpl` existant avec toute sa logique métier
  - Exemple : Pour Qute LS, le client gère le data model Java → le MCP en bénéficie automatiquement

### Flux Détaillé (Exemple : executeCommand)

```
┌─────────────────────────────────────────────────────────────────┐
│ Claude demande : "Expand macro in Rust file"                    │
└────────────────────────┬────────────────────────────────────────┘
                         │ MCP call: lsp/executeCommand
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ LSP4IJMCPServer (stdio transport)                               │
│  - Reçoit le JSON-RPC MCP                                       │
│  - Parse : serverId="rust-analyzer", command="expandMacro"      │
└────────────────────────┬────────────────────────────────────────┘
                         │ Java method call
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ ExecuteCommandTool.execute()                                    │
│  1. Trouve le wrapper via LanguageServiceAccessor               │
│  2. Vérifie que le serveur est started                          │
└────────────────────────┬────────────────────────────────────────┘
                         │ wrapper.getLanguageServer()
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ LanguageServerWrapper (Rust Analyzer)                           │
│  - LanguageServer proxy (déjà connecté)                         │
│  - LanguageClientImpl (custom Rust features)    ◄───────────┐   │
└────────────────────────┬────────────────────────────────────┼───┘
                         │ LSP JSON-RPC                       │
                         ▼                                    │
┌─────────────────────────────────────────────────────────────┼───┐
│ Rust Analyzer Process (déjà lancé par IntelliJ)            │   │
│  - Reçoit workspace/executeCommand                          │   │
│  - Expand le macro                                          │   │
│  - Retourne le résultat                                     │   │
└────────────────────────┬────────────────────────────────────┼───┘
                         │ LSP response                       │
                         ▼                                    │
                    CompletableFuture<Object>                 │
                         │                                    │
                         ├─ Peut déclencher des callbacks ────┘
                         │  dans LanguageClientImpl (ex: applyEdit)
                         ▼
                  MCP ToolResult.success(result)
                         │
                         ▼
                  Claude reçoit le macro expandé
```

**Points clés** :
1. Le MCP server ne communique **jamais directement** avec le LS process
2. Il passe toujours par `LanguageServerWrapper` → `LanguageClientImpl`
3. Les callbacks du LS (comme `applyEdit`, `showMessage`) passent par le client existant
4. Qute LS exemple : Quand il demande le data model Java, c'est le client existant qui répond

## Composants Principaux

### 1. LSP4IJMCPServer (stdio transport)

**Responsabilités** :
- Communiquer via stdio avec le client MCP (Claude)
- Utiliser JSON-RPC 2.0 (comme LSP)
- Logger vers stderr (jamais stdout qui est réservé au protocole MCP)
- Gérer le lifecycle (initialize/shutdown)

**Implémentation** :
- Utiliser `io.modelcontextprotocol.sdk:mcp` Java SDK
- Transport : `StdioServerTransportProvider`
- Point d'entrée : Main class ou Action IntelliJ pour lancer le serveur

**Exemple de structure** :
```java
package com.redhat.devtools.lsp4ij.mcp;

import io.modelcontextprotocol.kotlin.sdk.server.*;

public class LSP4IJMCPServer {
    private final Project project;
    private final LanguageServiceAccessor lspAccessor;
    
    public void start() {
        var transport = new StdioServerTransportProvider(objectMapper);
        var capabilities = ServerCapabilities.builder()
            .tools(true) // On expose des outils
            .build();
        
        var server = MCPServer.builder()
            .transport(transport)
            .capabilities(capabilities)
            .toolRegistry(createToolRegistry())
            .build();
            
        server.start();
    }
    
    private ToolRegistry createToolRegistry() {
        var registry = new ToolRegistry();
        
        // Outils génériques LSP
        registry.register(new ListLanguageServers(lspAccessor));
        registry.register(new ExecuteCommandTool(lspAccessor));
        registry.register(new GetDocumentSymbolsTool(lspAccessor));
        registry.register(new GetWorkspaceSymbolsTool(lspAccessor));
        
        // Outils spécifiques par LS (via extension point)
        for (var provider : MCPToolProvider.EP_NAME.getExtensions()) {
            provider.registerTools(registry, lspAccessor);
        }
        
        return registry;
    }
}
```

### 2. MCPToolProvider Extension Point

**But** : Permettre à chaque plugin LS d'enregistrer ses propres outils MCP.

**Extension Point** :
```xml
<extensionPoints>
    <extensionPoint name="mcpToolProvider"
                    interface="com.redhat.devtools.lsp4ij.mcp.MCPToolProvider"
                    dynamic="true"/>
</extensionPoints>
```

**Interface** :
```java
package com.redhat.devtools.lsp4ij.mcp;

public interface MCPToolProvider {
    ExtensionPointName<MCPToolProvider> EP_NAME = 
        ExtensionPointName.create("com.redhat.devtools.lsp4ij.mcpToolProvider");
    
    /**
     * Le language server id auquel ce provider s'applique
     */
    @NotNull
    String getLanguageServerId();
    
    /**
     * Enregistre les outils MCP spécifiques à ce LS
     */
    void registerTools(@NotNull ToolRegistry registry, 
                      @NotNull LanguageServiceAccessor accessor);
}
```

**Exemple d'utilisation** (plugin Rust) :
```java
public class RustMCPToolProvider implements MCPToolProvider {
    @Override
    public String getLanguageServerId() {
        return "rust-analyzer";
    }
    
    @Override
    public void registerTools(ToolRegistry registry, LanguageServiceAccessor accessor) {
        // Outils spécifiques à Rust Analyzer
        registry.register(new CargoCheckTool(accessor));
        registry.register(new ExpandMacroTool(accessor));
        registry.register(new ViewCrateTool(accessor));
    }
}
```

### 3. Outils MCP Génériques LSP

Tous les LS bénéficient automatiquement de ces outils :

#### 3.1 `lsp/listServers`
Liste les LS actuellement démarrés dans le projet.

**Input** : aucun  
**Output** :
```json
{
  "servers": [
    {
      "id": "rust-analyzer",
      "status": "started",
      "capabilities": {
        "documentSymbol": true,
        "codeAction": true,
        "executeCommand": true,
        "commands": ["rust-analyzer.expandMacro", ...]
      }
    }
  ]
}
```

**Implémentation** :
```java
public class ListLanguageServers implements MCPTool {
    @Override
    public ToolResult execute(Map<String, Object> params) {
        var servers = accessor.getStartedServers();
        var result = servers.stream()
            .map(wrapper -> {
                var caps = wrapper.getServerCapabilities();
                return Map.of(
                    "id", wrapper.getServerDefinition().getId(),
                    "status", wrapper.getServerStatus().name(),
                    "capabilities", extractCapabilities(caps)
                );
            })
            .toList();
        return ToolResult.success(Map.of("servers", result));
    }
}
```

#### 3.2 `lsp/executeCommand`
Exécute une commande LSP sur un serveur donné.

**Input** :
```json
{
  "serverId": "rust-analyzer",
  "command": "rust-analyzer.expandMacro",
  "arguments": [...]
}
```

**Output** : Le résultat de la commande

**Implémentation** :
```java
public class ExecuteCommandTool implements MCPTool {
    @Override
    public ToolResult execute(Map<String, Object> params) {
        String serverId = (String) params.get("serverId");
        String command = (String) params.get("command");
        List<?> arguments = (List<?>) params.get("arguments");
        
        // Trouve le wrapper existant (avec son client configuré)
        var wrapper = accessor.getStartedServers().stream()
            .filter(w -> w.getServerDefinition().getId().equals(serverId))
            .findFirst()
            .orElseThrow();
        
        // ⚠️ IMPORTANT : On appelle via le wrapper, pas directement le LS
        // Le wrapper gère :
        // - Le lifecycle (serveur démarré ?)
        // - Les timeouts
        // - Les erreurs
        // - Le client LSP4IJ avec toute sa logique métier
        var executeParams = new ExecuteCommandParams(command, arguments);
        
        // Le wrapper utilise son LanguageServer + LanguageClientImpl existants
        // Exemple : Pour Qute LS, LanguageClientImpl gère le data model Java
        var result = wrapper.getLanguageServer()
            .getWorkspaceService()
            .executeCommand(executeParams)
            .get(10, TimeUnit.SECONDS);
            
        return ToolResult.success(result);
    }
}
```

#### 3.3 `lsp/getDocumentSymbols`
Récupère les symboles d'un document.

**Input** :
```json
{
  "serverId": "typescript-language-server",
  "uri": "file:///path/to/file.ts"
}
```

#### 3.4 `lsp/getWorkspaceSymbols`
Recherche des symboles dans tout le workspace.

**Input** :
```json
{
  "serverId": "rust-analyzer",
  "query": "MyStruct"
}
```

#### 3.5 `lsp/getCodeActions`
Récupère les code actions disponibles pour une plage donnée.

**Input** :
```json
{
  "serverId": "typescript-language-server",
  "uri": "file:///path/to/file.ts",
  "range": {
    "start": {"line": 10, "character": 5},
    "end": {"line": 10, "character": 20}
  }
}
```

#### 3.6 `lsp/getDiagnostics`
Récupère les diagnostics actifs d'un fichier.

**Input** :
```json
{
  "serverId": "rust-analyzer",
  "uri": "file:///path/to/main.rs"
}
```

### 4. Configuration & Lancement

#### 4.1 Lancement du MCP Server

**Option A : Action IntelliJ**
```java
public class StartMCPServerAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        var project = e.getProject();
        var server = new LSP4IJMCPServer(project);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            server.start(); // Bloque sur stdio
        });
    }
}
```

**Option B : Service automatique** (si MCP doit être toujours disponible)
```xml
<projectService serviceImplementation="com.redhat.devtools.lsp4ij.mcp.LSP4IJMCPService"/>
```

#### 4.2 Configuration Claude Code

Dans `.claude/settings.json` :
```json
{
  "mcpServers": {
    "lsp4ij": {
      "command": "idea",
      "args": ["run-mcp-server", "--project", "/path/to/project"],
      "env": {}
    }
  }
}
```

Ou plus simplement, un script wrapper :
```bash
#!/bin/bash
# ~/.claude/mcp-servers/lsp4ij-server.sh
cd "$1"
idea --mcp-server
```

## Cas d'Usage Concrets

### Exemple 1 : Rust Analyzer + Claude

Claude demande : "Montre-moi tous les usages de la struct User"

1. Claude appelle `lsp/getWorkspaceSymbols` avec `query: "User"`
2. Le MCP server utilise le **Rust Analyzer déjà lancé** dans IntelliJ
3. Retourne les symboles trouvés
4. Claude peut ensuite appeler `rust-analyzer/findReferences` (outil custom)

### Exemple 2 : TypeScript + Organization

Claude : "Organise les imports de ce fichier"

1. Claude appelle `lsp/executeCommand` avec :
   ```json
   {
     "serverId": "typescript-language-server",
     "command": "_typescript.organizeImports",
     "arguments": [{"uri": "file:///..."}]
   }
   ```
2. Le LS exécute l'organisation
3. Le fichier est modifié dans IntelliJ

### Exemple 3 : Multi-LS Diagnostics

Claude : "Quels sont tous les problèmes dans mon projet ?"

1. Claude appelle `lsp/listServers`
2. Pour chaque serveur, appelle `lsp/getDiagnostics` sur les fichiers pertinents
3. Agrège les résultats de tous les LS (Rust, TypeScript, Java, etc.)

## Avantages par Rapport aux Alternatives

### vs. Lancer un LS séparé
❌ **Duplication** : Deux processus Rust Analyzer  
❌ **Pas de contexte** : Le LS MCP ne voit pas les fichiers ouverts dans IntelliJ  
❌ **Incohérence** : Diagnostics différents entre IDE et IA  

✅ **LSP4IJ MCP** : Un seul processus, contexte partagé, diagnostics cohérents

### vs. MCP natif IntelliJ (depuis 2025.2)
Le MCP natif IntelliJ expose des outils **IDE génériques** (fichiers, build, terminal).  
**LSP4IJ MCP** expose les **capacités spécifiques des Language Servers** (symboles, diagnostics, commandes custom).

**Complémentaires** : On peut avoir les deux !

## Implémentation par Phases

### Phase 1 : MVP (Minimum Viable Product)
- [ ] MCP server stdio de base
- [ ] Outil `lsp/listServers`
- [ ] Outil `lsp/executeCommand`
- [ ] Documentation de configuration

### Phase 2 : Outils LSP génériques
- [ ] `lsp/getDocumentSymbols`
- [ ] `lsp/getWorkspaceSymbols`
- [ ] `lsp/getCodeActions`
- [ ] `lsp/getDiagnostics`

### Phase 3 : Système d'extension
- [ ] Extension point `MCPToolProvider`
- [ ] Documentation pour contributeurs
- [ ] Exemple avec un LS populaire (Rust Analyzer)

### Phase 4 : Polish & Intégration
- [ ] Gestion d'erreurs robuste
- [ ] Logs structurés (stderr)
- [ ] Tests d'intégration avec Claude Code
- [ ] Performance monitoring

## Considérations Techniques

### ⚠️ CRITIQUE : Ne PAS créer un nouveau client LSP

**FAUX** ❌ :
```java
// NE JAMAIS FAIRE ÇA
var newClient = new LanguageClientImpl(project);
var ls = wrapper.getLanguageServer();
// Problème : On bypass le client existant avec toute sa logique métier
```

**CORRECT** ✅ :
```java
// Utiliser le wrapper existant qui contient déjà son client
var wrapper = accessor.getStartedServers().stream()...
var ls = wrapper.getLanguageServer(); // Utilise le client déjà connecté
```

**Pourquoi c'est critique** :
- **Qute LS** : Le `LanguageClientImpl` custom gère le data model Java pour les templates
- **Kotlin LS** : Le client custom gère les capacités spécifiques Kotlin
- **Tout LS** : Le client peut avoir des customizations (traces, diagnostics, workspaceFolders, etc.)

→ Le MCP doit **réutiliser** le client existant, **pas en créer un nouveau**.

### Thread Safety
- `LanguageServiceAccessor.getStartedServers()` : Thread-safe (CopyOnWriteArrayList)
- Appels LSP : Déjà async via CompletableFuture
- MCP server : Doit gérer les requêtes concurrentes
- **LanguageServerWrapper** : Déjà thread-safe pour les appels concurrents

### Gestion du Lifecycle
- Le MCP server vit pendant toute la session IntelliJ
- Si un LS crash/restart : Le MCP continue, retourne une erreur gracieuse
- Si IntelliJ ferme : Le MCP server se termine proprement (dispose)

### Logging
⚠️ **CRITIQUE** : Jamais de `System.out.println()` (corrompt le protocole stdio)
✅ Utiliser SLF4J qui log vers stderr ou fichier

### Performance
- Appels LSP déjà optimisés par LSP4IJ
- MCP ajoute une latence minimale (sérialisation JSON)
- Pas de copie de données : références directes aux wrappers

## Documentation pour Utilisateurs

### Configuration Minimale

1. Installer LSP4IJ dans IntelliJ
2. Configurer les LS souhaités (Rust, TypeScript, etc.)
3. Ajouter dans `.claude/settings.json` :
```json
{
  "mcpServers": {
    "lsp4ij": {
      "command": "lsp4ij-mcp-server",
      "args": ["--project", "${workspaceFolder}"]
    }
  }
}
```

4. Utiliser dans Claude :
```
Claude: "Liste les serveurs disponibles"
[MCP appelle lsp/listServers]

Claude: "Trouve tous les usages de MyClass"
[MCP appelle lsp/getWorkspaceSymbols puis findReferences]
```

## Références

- [MCP Specification](https://modelcontextprotocol.io/specification/2025-11-25)
- [MCP Java SDK](https://java.sdk.modelcontextprotocol.io/latest/server/)
- [LSP Specification](https://microsoft.github.io/language-server-protocol/)
- [LSP4IJ Developer Guide](./DeveloperGuide.md)

## Sources

- [Model Context Protocol Specification](https://modelcontextprotocol.io/specification/2025-11-25)
- [MCP Cheat Sheet (2026)](https://www.webfuse.com/mcp-cheat-sheet)
- [How to Build an MCP Server with Java SDK](https://imhoratiu.wordpress.com/2025/07/17/how-to-build-an-mcp-server-with-java-sdk/)
- [Understanding MCP Through Raw STDIO Communication](https://foojay.io/today/understanding-mcp-through-raw-stdio-communication/)
- [MCP Java SDK Documentation](https://java.sdk.modelcontextprotocol.io/latest/server/)
