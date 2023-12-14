package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane;
import com.intellij.profile.codeInspection.ui.DescriptionEditorPaneKt;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

public class ShowInstructionDialog extends DialogWrapper {

    private @NotNull
    final String description;

    protected ShowInstructionDialog(@NotNull String description, @Nullable Project project) {
        super(project);
        this.description = description;
        init();
    }
    @Override
    protected @Nullable JComponent createCenterPanel() {
        var descriptionBrowser = new DescriptionEditorPane();
        final var descriptionScrollPane = ScrollPaneFactory.createScrollPane(descriptionBrowser);
        descriptionScrollPane.setBorder(null);
        DescriptionEditorPaneKt.readHTML(descriptionBrowser, description);
        descriptionBrowser.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                BrowserUtil.browse(e.getURL());
            }
        });
        return descriptionScrollPane;
    }

}
