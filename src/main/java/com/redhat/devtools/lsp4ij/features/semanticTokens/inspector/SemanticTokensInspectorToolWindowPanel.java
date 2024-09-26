/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens.inspector;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

/**
 * Semantic Tokens Inspector view which shows:
 *
 * <ul>
 *     <li>on the left some configuration to show TextAttributeKey, toke,type, token modifier in the editor view which shows
 *     the tokenization of the response of the semantic tokens.</li>
 *     <li>tabs which contains a tab per file which hosts the editor content.</li>
 * </ul>
 */
public class SemanticTokensInspectorToolWindowPanel extends SimpleToolWindowPanel implements Disposable {

    private static final Object CURRENT_DATA_KEY = new Object();

    private record SemanticTokensEditorData(int tabIndex, JBTextArea editor) {
    }

    private static final Key<SemanticTokensEditorData> TAB_INDEX_KEY = Key.create("lsp.semantic.tokens.tab.index");

    private final Project project;

    private SemanticTokensInspectorDetail semanticTokensInspectorDetail;
    private JBTabbedPane semanticTokenTabbedPane;

    private SemanticTokensInspectorListener listener;


    public SemanticTokensInspectorToolWindowPanel(Project project) {
        super(false, true);
        this.project = project;
        createUI();
    }

    private void createUI() {
        // On the left, show configuration
        semanticTokensInspectorDetail = new SemanticTokensInspectorDetail(this);
        var scrollPane = new JBScrollPane(semanticTokensInspectorDetail);
        // On the right show tabs for each file which support semantic tokens
        // and show the editor content with semantic tokens information.
        semanticTokenTabbedPane = new JBTabbedPane();
        var splitPane = createSplitPanel(scrollPane, new JBScrollPane(semanticTokenTabbedPane));
        super.setContent(splitPane);
        super.revalidate();
        super.repaint();
        this.listener = data -> {
            ApplicationManager.getApplication()
                    .executeOnPooledThread(() -> {
                        // The file has been updated with new LSP semantic tokens
                        // show the tokenization response in the editor
                        show(data);
                    });
        };
        SemanticTokensInspectorManager.getInstance(project).addSemanticTokensInspectorListener(listener);
    }

    private void show(SemanticTokensInspectorData data) {
        // Use the given data document and update it to add LSP semantic tokens' information.
        String text = SemanticTokensInspectorManager.format(data,
                semanticTokensInspectorDetail.isShowTextAttributes(),
                semanticTokensInspectorDetail.isShowTokenType(),
                semanticTokensInspectorDetail.isShowTokenModifiers(),
                project);
        ApplicationManager.getApplication()
                .invokeLater(() -> {
            // get the proper editor from the file tab
            var editorData = getEditorFor(data.file());

            // Update the editor with the LSP semantic tokens' information.
            var editor = editorData.editor();
            if (editor.getText().equals(text)) {
                // Don't refresh the editor
                return;
            }
            editor.setText(text);

            // Select the proper tab of the file
            int tabIndex = editorData.tabIndex();
            JPanel tab = (JPanel) semanticTokenTabbedPane.getTabComponentAt(tabIndex);
            tab.putClientProperty(CURRENT_DATA_KEY, data);
            semanticTokenTabbedPane.setSelectedIndex(tabIndex);
        });
    }

    private SemanticTokensEditorData getEditorFor(PsiFile file) {
        SemanticTokensEditorData editorData = file.getUserData(TAB_INDEX_KEY);
        if (editorData == null) {
            synchronized (semanticTokenTabbedPane) {
                // Create a proper tab which contains the editor
                // for the given file

                JBTextArea editor = createEditor();
                var scrollPane = new JBScrollPane(editor);
                semanticTokenTabbedPane.addTab(file.getName(), file.getIcon(0), scrollPane);
                int tabIndex = semanticTokenTabbedPane.getTabCount() - 1;

                // As JTabbedPane doesn't support tab close
                // we customize the tab header to add a close icon.
                JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                tabHeader.setOpaque(false);

                JLabel label = new JLabel(file.getName());
                label.setIcon(file.getIcon(0));
                label.setBorder(JBUI.Borders.empty(1));
                label.setFont(getFont());
                tabHeader.add(label);

                JButton closeButton = new JButton(AllIcons.Actions.Close);
                closeButton.setPreferredSize(new Dimension(16, 16));
                closeButton.setContentAreaFilled(false);
                closeButton.setBorderPainted(false);
                closeButton.setFocusPainted(false);
                closeButton.setBorder(BorderFactory.createEmptyBorder());
                closeButton.addActionListener(e -> {
                    file.putUserData(TAB_INDEX_KEY, null);
                    semanticTokenTabbedPane.removeTabAt(tabIndex);
                });
                tabHeader.add(closeButton);

                semanticTokenTabbedPane.setTabComponentAt(tabIndex, tabHeader);
                label.putClientProperty(JBTabbedPane.LABEL_FROM_TABBED_PANE, Boolean.TRUE);

                editorData = new SemanticTokensEditorData(tabIndex, editor);
                file.putUserData(TAB_INDEX_KEY, editorData);
            }
        }
        return editorData;
    }

    private JBTextArea createEditor() {
        JBTextArea semanticTokenEditor = new JBTextArea(5, 0);
        semanticTokenEditor.setLineWrap(true);
        semanticTokenEditor.setWrapStyleWord(true);
        semanticTokenEditor.setFont(JBFont.regular());
        semanticTokenEditor.setEditable(false);
        return semanticTokenEditor;
    }

    private static JComponent createSplitPanel(JComponent left, JComponent right) {
        OnePixelSplitter splitter = new OnePixelSplitter(false, 0.15f);
        splitter.setShowDividerControls(true);
        splitter.setHonorComponentsMinimumSize(true);
        splitter.setFirstComponent(left);
        splitter.setSecondComponent(right);
        return splitter;
    }

    public Project getProject() {
        return project;
    }

    public void refresh() {
        if (semanticTokenTabbedPane == null) {
            return;
        }
        for (int i = 0; i < semanticTokenTabbedPane.getTabCount(); i++) {
            JPanel tab = (JPanel) semanticTokenTabbedPane.getTabComponentAt(i);
            SemanticTokensInspectorData data = (SemanticTokensInspectorData) tab.getClientProperty(CURRENT_DATA_KEY);
            if (data != null) {
                show(data);
            }
        }
    }


    @Override
    public void dispose() {
        SemanticTokensInspectorManager.getInstance(project).removeSemanticTokensInspectorListener(listener);
    }
}
