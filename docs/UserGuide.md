# User guide

## LSP console

Although not directly useful to users in most cases, the Language Server console view is extremely important 
when we need to troubleshoot issues with the language servers.

The state of the servers is visible, stop and restart is available with a right-click, and you can enable different levels of tracing:

![LSP console trace settings](./images/LSPConsoleSettings.png)

The communication details between the IDE and the language servers are seen in the "LSP consoles" pane. 
In verbose mode, the messages can be expanded for more details:

![LSP console messages](./images/LSPConsole.png)

When a language server is started, several actions are available, like stopping the language server or copying the command starting the language server :

![LSP console actions](./images/LSPConsoleActions.png)

## Language Servers preferences

The preference page under `Preferences | Languages & Frameworks | Language Servers` allows power users 
to configure language servers debugging and tracing:

![Language Server preferences](./images/LanguageServerPreferences.png)