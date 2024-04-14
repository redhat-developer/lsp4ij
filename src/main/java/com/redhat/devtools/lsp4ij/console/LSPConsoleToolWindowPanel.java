/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.console;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.CardLayoutPanel;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.console.actions.ApplyLanguageServerSettingsAction;
import com.redhat.devtools.lsp4ij.console.actions.ResetLanguageServerSettingsAction;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerExplorer;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerProcessTreeNode;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerTreeNode;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import com.redhat.devtools.lsp4ij.settings.LanguageServerView;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettingsListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * LSP consoles
 */
public class LSPConsoleToolWindowPanel extends SimpleToolWindowPanel implements Disposable {

    private final Project project;

    private LanguageServerExplorer explorer;

    private ConsolesPanel consoles;
    private boolean disposed;

    public LSPConsoleToolWindowPanel(Project project) {
        super(false, true);
        this.project = project;
        createUI();
    }

    private void createUI() {
        explorer = new LanguageServerExplorer(this);
        var scrollPane = new JBScrollPane(explorer);
        this.consoles = new ConsolesPanel();
        var splitPane = createSplitPanel(scrollPane, consoles);
        super.setContent(splitPane);
        super.revalidate();
        super.repaint();
        explorer.load();
    }

    public Project getProject() {
        return project;
    }

    private static JComponent createSplitPanel(JComponent left, JComponent right) {
        OnePixelSplitter splitter = new OnePixelSplitter(false, 0.15f);
        splitter.setShowDividerControls(true);
        splitter.setHonorComponentsMinimumSize(true);
        splitter.setFirstComponent(left);
        splitter.setSecondComponent(right);
        return splitter;
    }

    public void selectDetail(LanguageServerTreeNode treeNode) {
        if (consoles == null || isDisposed()) {
            return;
        }
        consoles.select(treeNode, true);
    }

    /**
     * Returns true if the command of the given language server node is editing and false otherwise.
     *
     * @return true if the command of the given language server node is editing and false otherwise.
     */
    public boolean isEditingCommand(LanguageServerTreeNode serverNode) {
        if (consoles == null || isDisposed()) {
            return false;
        }
        var serverPanel = consoles.getValue(serverNode, false);
        if (serverPanel != null && serverPanel.isShowing()) {
            LanguageServerView detailView= serverPanel.detailView;
            if (detailView != null && detailView.getComponent().isShowing()) {
                return detailView.isEditingCommand();
            }
        }
        return false;
    }


    public void selectConsole(LanguageServerProcessTreeNode processTreeNode) {
        if (consoles == null || isDisposed()) {
            return;
        }
        consoles.select(processTreeNode, true);
    }

    /**
     * A card-panel that displays panels for each language server instances.
     */
    private class ConsolesPanel extends CardLayoutPanel<DefaultMutableTreeNode, DefaultMutableTreeNode, ConsoleContentPanel> {

        @Override
        protected DefaultMutableTreeNode prepare(DefaultMutableTreeNode key) {
            return key;
        }

        @Override
        protected ConsoleContentPanel create(DefaultMutableTreeNode key) {
            if (isDisposed() || LSPConsoleToolWindowPanel.this.isDisposed()) {
                return null;
            }
            return new ConsoleContentPanel(key);
        }

        @Override
        public void dispose() {
            removeAll();
        }

        @Override
        protected void dispose(DefaultMutableTreeNode key, ConsoleContentPanel value) {
            if (value != null) {
                value.dispose();
            }
        }
    }

    private class ConsoleContentPanel extends SimpleCardLayoutPanel<JComponent> {

        private static final String NAME_VIEW_DETAIL = "detail";
        private static final String NAME_VIEW_CONSOLE = "console";

        private LanguageServerView detailView;

        private ConsoleView consoleView;

        private final Set<UserDefinedLanguageServerSettingsListener> settingsChangeListeners = new HashSet<UserDefinedLanguageServerSettingsListener>();

        private final Set<LanguageServerDefinitionListener> serverDefinitionListeners = new HashSet<>();

        public ConsoleContentPanel(DefaultMutableTreeNode key) {
            if (key instanceof LanguageServerTreeNode) {
                // Create detail view
                detailView = createDetailView((LanguageServerTreeNode) key);
                Disposer.register(LSPConsoleToolWindowPanel.this, detailView);
                add(detailView.getComponent(), NAME_VIEW_DETAIL);
                configureDetailToolbar(detailView);
                showDetail();
            } else if (key instanceof LanguageServerProcessTreeNode) {
                // Create console view
                consoleView = createConsoleView(((LanguageServerProcessTreeNode) key).getLanguageServer().getServerDefinition(), project);
                Disposer.register(LSPConsoleToolWindowPanel.this, consoleView);
                add(consoleView.getComponent(), NAME_VIEW_CONSOLE);
                configureConsoleToolbar(consoleView);
                showConsole();
            }
        }

        private LanguageServerView createDetailView(LanguageServerTreeNode key) {
            LanguageServerDefinition serverDefinition = key.getServerDefinition();
            Project project = LSPConsoleToolWindowPanel.this.project;
            // Create the language server panel with 'Server', 'Mappings', 'Configuration', 'Debug' tabs
            LanguageServerView languageServerView = new LanguageServerView(serverDefinition, null, project);
            loadDetailPanel(languageServerView);

            // Track changes of definition + settings to reload the language server detail (command, mappings, etc):

            // - 1. track changes of language server definition (command, mappings, etc) + associated settings (trace, error reporting kind)
            var serverDefinitionListener = createServerDefinitionListener(serverDefinition, languageServerView);
            LanguageServersRegistry.getInstance().addLanguageServerDefinitionListener(serverDefinitionListener);
            serverDefinitionListeners.add(serverDefinitionListener);

            // - 2. track changes of associated settings (trace, error reporting kind)
            UserDefinedLanguageServerSettingsListener settingsChangeListener = createSettingsChangeListener(serverDefinition, languageServerView);
            com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.getInstance(getProject()).addSettingsListener(settingsChangeListener);
            settingsChangeListeners.add(settingsChangeListener);

            return languageServerView;
        }


        private void showConsole() {
            show(NAME_VIEW_CONSOLE);
        }

        private void showDetail() {
            show(NAME_VIEW_DETAIL);
        }

        public void showMessage(String message) {
            if (consoleView == null) {
                return;
            }
            consoleView.print(message, ConsoleViewContentType.SYSTEM_OUTPUT);
        }

        public void showError(Throwable exception) {
            if (consoleView == null) {
                return;
            }
            String stacktrace = getStackTrace(exception);
            consoleView.print(stacktrace, ConsoleViewContentType.ERROR_OUTPUT);
        }

        @Override
        public void dispose() {
            for (UserDefinedLanguageServerSettingsListener settingsChangeListener : settingsChangeListeners) {
                com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.getInstance(getProject()).removeChangeHandler(settingsChangeListener);
            }
            settingsChangeListeners.clear();
            for (var serverDefinitionListener : serverDefinitionListeners) {
                LanguageServersRegistry.getInstance().removeLanguageServerDefinitionListener(serverDefinitionListener);
            }
            serverDefinitionListeners.clear();
            super.dispose();
            if (consoleView != null) {
                consoleView.dispose();
            }
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        if (consoles != null) {
            consoles.dispose();
        }
        explorer.dispose();
    }

    private boolean isDisposed() {
        return disposed || project.isDisposed();
    }

    /**
     * Code copied from <a href="https://github.com/apache/commons-lang/blob/24744a40b2c094945e542b71cc1fbf59caa0d70b/src/main/java/org/apache/commons/lang3/exception/ExceptionUtils.java#L400C5-L407C6">...</a>
     *
     * @param throwable the error exception.
     * @return the stack trace as sting.
     */
    @NotNull
    private static String getStackTrace(@Nullable Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        final StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw, true));
        return sw.toString();
    }

    // -------------------------------------------------------------
    // ---------------- Methods used by the Detail view ------------
    // -------------------------------------------------------------

    private UserDefinedLanguageServerSettingsListener createSettingsChangeListener(LanguageServerDefinition serverDefinition, LanguageServerView languageServerView) {
        return (event) -> {
            if (isDisposed()) {
                return;
            }
            if (event.languageServerId().equals(serverDefinition.getId())) {
                loadDetailPanel(languageServerView);
            }
        };
    }

    @NotNull
    private LanguageServerDefinitionListener createServerDefinitionListener(LanguageServerDefinition serverDefinition, LanguageServerView languageServerView) {
        return new LanguageServerDefinitionListener() {
            @Override
            public void handleAdded(@NotNull LanguageServerDefinitionListener.LanguageServerAddedEvent event) {
                // Do nothing
            }

            @Override
            public void handleRemoved(@NotNull LanguageServerDefinitionListener.LanguageServerRemovedEvent event) {
                // Do nothing
            }

            @Override
            public void handleChanged(@NotNull LanguageServerDefinitionListener.LanguageServerChangedEvent event) {
                if (isDisposed()) {
                    return;
                }
                if (event.serverDefinition == serverDefinition) {
                    loadDetailPanel(languageServerView);
                }
            }
        };
    }

    /**
     * Configure language server detail toolbar on the left to provide some action like "Apply", "Reset", etc
     *
     * @param languageServerView the language server view
     */
    private static void configureDetailToolbar(LanguageServerView languageServerView) {
        DefaultActionGroup myToolbarActions = new DefaultActionGroup();
        myToolbarActions.add(new ApplyLanguageServerSettingsAction(languageServerView));
        myToolbarActions.add(new ResetLanguageServerSettingsAction(languageServerView));

        JComponent detailComponent = languageServerView.getComponent();
        ActionToolbar tb = ActionManager.getInstance().createActionToolbar("LSP Detail", myToolbarActions, false);
        tb.setTargetComponent(detailComponent);
        tb.getComponent().setBorder(JBUI.Borders.merge(tb.getComponent().getBorder(), JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 0, 0, 0, 1), true));
        tb.getComponent().setName(ApplyLanguageServerSettingsAction.ACTION_TOOLBAR_COMPONENT_NAME);
        detailComponent.add(tb.getComponent(), BorderLayout.WEST);
    }

    /**
     * Load fields (command, mappings, etc) from each tabs from the given language server definition
     *
     * @param languageServerView the language server view which hosts fields.
     */
    private static void loadDetailPanel(LanguageServerView languageServerView) {
        languageServerView.reset();
    }

    // -------------------------------------------------------------
    // ---------------- Methods used by the Console view -----------
    // -------------------------------------------------------------

    private static ConsoleView createConsoleView(@NotNull LanguageServerDefinition serverDefinition, @NotNull Project project) {
        var builder = new LSPTextConsoleBuilderImpl(serverDefinition, project);
        builder.setViewer(true);
        return builder.getConsole();
    }

    public void showMessage(LanguageServerProcessTreeNode processTreeNode, String message) {
        if (isDisposed()) {
            return;
        }
        var consoleOrErrorPanel = consoles.getValue(processTreeNode, true);
        if (consoleOrErrorPanel != null) {
            consoleOrErrorPanel.showMessage(message);
        }
    }

    public void showError(LanguageServerProcessTreeNode processTreeNode, Throwable exception) {
        if (isDisposed()) {
            return;
        }
        var consoleOrErrorPanel = consoles.getValue(processTreeNode, true);
        if (consoleOrErrorPanel != null) {
            consoleOrErrorPanel.showError(exception);
        }
    }


    /**
     * Configure console toolbar on the right of the console to provide some action like "Scroll to End", "Clean", etc
     *
     * @param consoleView the console view.
     */
    private static void configureConsoleToolbar(ConsoleView consoleView) {
        DefaultActionGroup myToolbarActions = new DefaultActionGroup();
        myToolbarActions.addAll(consoleView.createConsoleActions());

        JComponent consoleComponent = consoleView.getComponent();
        ActionToolbar tb = ActionManager.getInstance().createActionToolbar("LSP Console", myToolbarActions, false);
        tb.setTargetComponent(consoleComponent);
        tb.getComponent().setBorder(JBUI.Borders.merge(tb.getComponent().getBorder(), JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 0, 0, 0, 1), true));
        consoleComponent.add(tb.getComponent(), BorderLayout.EAST);
    }
}
