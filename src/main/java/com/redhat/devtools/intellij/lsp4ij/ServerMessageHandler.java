package com.redhat.devtools.intellij.lsp4ij;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;

import javax.swing.Icon;
import java.util.concurrent.CompletableFuture;

public class ServerMessageHandler {
    private ServerMessageHandler() {
        // this class shouldn't be instantiated
    }

    private static final String NAME_PATTERN = "%s (%s)"; //$NON-NLS-1$


    public static void logMessage(LanguageServerWrapper wrapper, MessageParams params) {
        //TODO: implements message to console
    }

    private static Icon messageTypeToIcon(MessageType type) {
        Icon result = null;

        switch (type) {
            case Error:
                result = AllIcons.General.Error;
                break;
            case Info:
            case Log:
                result =  AllIcons.General.Information;
                break;
            case Warning:
                result = AllIcons.General.Warning;
        }
        return result;
    }

    private static NotificationType messageTypeToNotificationType(MessageType type) {
        NotificationType result = switch (type) {
            case Error -> NotificationType.ERROR;
            case Info, Log -> NotificationType.INFORMATION;
            case Warning -> NotificationType.WARNING;
        };
        return result;
    }


    public static void showMessage(String title, MessageParams params) {
        Notification notification = new Notification("Language Server Protocol", title, params.getMessage(), messageTypeToNotificationType(params.getType()));
        notification.setIcon(messageTypeToIcon(params.getType()));
        Notifications.Bus.notify(notification);
    }

    public static CompletableFuture<MessageActionItem> showMessageRequest(LanguageServerWrapper wrapper, ShowMessageRequestParams params) {
        String[] options = params.getActions().stream().map(MessageActionItem::getTitle).toArray(String[]::new);
        CompletableFuture<MessageActionItem> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            MessageActionItem result = new MessageActionItem();
            int dialogResult = Messages.showIdeaMessageDialog(null, params.getMessage(), wrapper.serverDefinition.label, options, 0, Messages.getInformationIcon(), null);
            if (dialogResult != -1) {
                result.setTitle(options[dialogResult]);
            }
            future.complete(result);
        });
        return future;
    }
}
