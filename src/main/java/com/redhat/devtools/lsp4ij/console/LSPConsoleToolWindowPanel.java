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
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.CardLayoutPanel;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
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
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import static com.redhat.devtools.lsp4ij.internal.ApplicationUtils.invokeLaterIfNeeded;

/**
 * LSP consoles
 */
public class LSPConsoleToolWindowPanel extends SimpleToolWindowPanel implements Disposable {

    public static final String LANGUAGE_SERVERS_TOOL_WINDOW_ID = "Language Servers";
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
            LanguageServerView detailView = serverPanel.detailView;
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
     * Remove the detail, console panel of the given language server tree node.
     *
     * @param serverTreeNode the language server tree node.
     */
    public void removeConsolePanel(LanguageServerTreeNode serverTreeNode) {
        if (consoles == null || isDisposed()) {
            return;
        }
        consoles.dispose(serverTreeNode);
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

        /**
         * Remove the detail, console panel of the given language server tree node.
         *
         * @param key the language server or process tree node.
         */
        public void dispose(@NotNull DefaultMutableTreeNode key) {
            var value = super.getValue(key, false);
            if (value != null) {
                // Remove the detail panel of the language server
                value.dispose();
            }
            // Remove the console panel of the language server (processes)
            for (int i = 0; i < key.getChildCount(); i++) {
                if (key.getChildAt(i) instanceof DefaultMutableTreeNode treeNode) {
                    dispose(treeNode);
                }
            }
        }
    }

    private class ConsoleContentPanel extends SimpleCardLayoutPanel<JComponent> {

        private static final String NAME_VIEW_DETAIL = "detail";
        private static final String NAME_VIEW_CONSOLE = "console";
        private JBTabbedPane tabbedPane;

        private LanguageServerView detailView;

        private ConsoleView tracesConsoleView;

        private ConsoleView logsConsoleView;

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
                // Create console Traces/Logs views
                tabbedPane = new JBTabbedPane();
                add(tabbedPane, NAME_VIEW_CONSOLE);

                tracesConsoleView = createConsoleView(((LanguageServerProcessTreeNode) key).getLanguageServer().getServerDefinition(), project);
                Disposer.register(LSPConsoleToolWindowPanel.this, tracesConsoleView);
                tabbedPane.add(LanguageServerBundle.message("lsp.console.tabs.traces.title"), tracesConsoleView.getComponent());
                configureConsoleToolbar(tracesConsoleView);

                logsConsoleView = createConsoleView(((LanguageServerProcessTreeNode) key).getLanguageServer().getServerDefinition(), project);
                Disposer.register(LSPConsoleToolWindowPanel.this, logsConsoleView);
                tabbedPane.add(LanguageServerBundle.message("lsp.console.tabs.logs.title"), logsConsoleView.getComponent());

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

        public void showConsole() {
            show(NAME_VIEW_CONSOLE);
        }

        public void showDetail() {
            show(NAME_VIEW_DETAIL);
        }

        public void showTrace(String message) {
            if (tracesConsoleView == null) {
                return;
            }
            tracesConsoleView.print(message, ConsoleViewContentType.SYSTEM_OUTPUT);
        }

        public void showError(Throwable exception) {
            if (tracesConsoleView == null) {
                return;
            }
            String stacktrace = getStackTrace(exception);
            tracesConsoleView.print(stacktrace, ConsoleViewContentType.ERROR_OUTPUT);
        }

        public void showLog(MessageParams params) {
            if (logsConsoleView == null) {
                return;
            }
            ConsoleViewContentType contentType = getContentType(params.getType());
            String message = params.getMessage();
            message = message + "\n";
            logsConsoleView.print(message, contentType);
        }

        public void selectLogTab() {
            // Select "Console" tab
            showConsole();
            // Select "Log" tab
            tabbedPane.setSelectedIndex(1);
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
            if (tracesConsoleView != null) {
                tracesConsoleView.dispose();
            }
        }

    }

    private static ConsoleViewContentType getContentType(@Nullable MessageType type) {
        if (type == null) {
            return ConsoleViewContentType.LOG_INFO_OUTPUT;
        }
        return switch (type) {
            case Error -> ConsoleViewContentType.LOG_ERROR_OUTPUT;
            case Info -> ConsoleViewContentType.LOG_INFO_OUTPUT;
            case Log -> ConsoleViewContentType.LOG_VERBOSE_OUTPUT;
            case Warning -> ConsoleViewContentType.LOG_WARNING_OUTPUT;
        };
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

    public void showTrace(LanguageServerProcessTreeNode processTreeNode, String message) {
        if (isDisposed()) {
            return;
        }
        var consoleOrErrorPanel = consoles.getValue(processTreeNode, true);
        if (consoleOrErrorPanel != null) {
            consoleOrErrorPanel.showTrace(message);
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
     * Show the given message parameters in the tab "Logs" of the LSP console of the given server definition.
     *
     * @param serverDefinition the language server definition.
     * @param params           the message parameters.
     * @param project          the project.
     */
    public static void showLog(@NotNull LanguageServerDefinition serverDefinition,
                               @NotNull MessageParams params,
                               @NotNull Project project) {
        // Get the LSP tool window
        var toolWindow = ToolWindowManager.getInstance(project).getToolWindow(LANGUAGE_SERVERS_TOOL_WINDOW_ID);
        if (toolWindow == null) {
            return;
        }
        invokeLaterIfNeeded(() -> {
            // Get the panel of the LSP tool window
            var contentManager = toolWindow.getContentManager();
            var content = contentManager.getContent(0);
            if (content != null && content.getComponent() instanceof LSPConsoleToolWindowPanel panel) {
              // Show log...
              panel.showLog(params, serverDefinition);
            }
        });
    }

    private void showLog(@NotNull MessageParams params,
                         @NotNull LanguageServerDefinition serverDefinition) {
        if (isDisposed()) {
            return;
        }
        LanguageServerTreeNode serverNode = explorer.findNodeForServer(serverDefinition);
        if (serverNode == null) {
            return;
        }
        var processTreeNode = serverNode.getActiveProcessTreeNode();
        if (processTreeNode == null) {
            return;
        }
        var consoleOrErrorPanel = consoles.getValue(processTreeNode, true);
        if (consoleOrErrorPanel != null) {
            consoleOrErrorPanel.showLog(params);
        }
    }

    /**
     * Select the "Log tab" the LSP console of the given server definition.
     *
     * @param serverDefinition the language server definition.
     * @param project          the project.
     */
    public static void selectLogTab(@NotNull LanguageServerDefinition serverDefinition,
                               @NotNull Project project) {
        // Get the LSP tool window
        var toolWindow = ToolWindowManager.getInstance(project).getToolWindow(LANGUAGE_SERVERS_TOOL_WINDOW_ID);
        if (toolWindow == null) {
            return;
        }
        invokeLaterIfNeeded(() -> {
            // Get the panel of the LSP tool window
            var contentManager = toolWindow.getContentManager();
            var content = contentManager.getContent(0);
            if (content != null && content.getComponent() instanceof LSPConsoleToolWindowPanel panel) {
                // Show log...
                panel.selectLogTab(serverDefinition);
            }
        });
    }

    /**
     * Select "Log" tab for the given language server definition.
     *
     * @param serverDefinition the language server deinition.
     */
    private void selectLogTab(@NotNull LanguageServerDefinition serverDefinition) {
        if (isDisposed()) {
            return;
        }
        LanguageServerTreeNode serverNode = explorer.findNodeForServer(serverDefinition);
        if (serverNode == null) {
            return;
        }
        var processTreeNode = serverNode.getActiveProcessTreeNode();
        if (processTreeNode == null) {
            return;
        }
        // Select the language server process node
        explorer.selectAndExpand(processTreeNode);
        // Select the "Log" tab
        var consoleOrErrorPanel = consoles.getValue(processTreeNode, true);
        if (consoleOrErrorPanel != null) {
            consoleOrErrorPanel.selectLogTab();
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
