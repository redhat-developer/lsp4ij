package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

public class UserDefinedLanguageClientImpl extends LanguageClientImpl {
    private final LanguageServerLifecycleListener l =new LanguageServerLifecycleListener() {
        @Override
        public void handleStatusChanged(LanguageServerWrapper languageServer) {
            if (languageServer.getServerStatus() == ServerStatus.started) {
                languageServer.getInitializedServer()
                        .thenAccept(ls -> {
                            Object params = load();
                            ls.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(params));
                        });
            }
        }

        private Object load() {
            try (Reader templateReader = new InputStreamReader(new BufferedInputStream(UserDefinedLanguageClientImpl.class.getResourceAsStream("/templates/jdt.ls.json")))) {
                return JsonParser.parseReader(templateReader);
            } catch (IOException e) {

            }
            return null;
        }

        @Override
        public void handleLSPMessage(Message message, MessageConsumer consumer, LanguageServerWrapper languageServer) {

        }

        @Override
        public void handleError(LanguageServerWrapper languageServer, Throwable exception) {

        }

        @Override
        public void dispose() {

        }
    };

    public UserDefinedLanguageClientImpl(Project project) {
        super(project);
        LanguageServerLifecycleManager.getInstance(project).addLanguageServerLifecycleListener(l);
    }

    @Override
    public void dispose() {
        super.dispose();
        LanguageServerLifecycleManager.getInstance(getProject()).removeLanguageServerLifecycleListener(l);
    }
}
