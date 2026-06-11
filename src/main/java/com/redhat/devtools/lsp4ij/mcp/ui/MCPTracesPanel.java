/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.mcp.ui;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.mcp.MCPBundle;
import com.redhat.devtools.lsp4ij.mcp.MCPServerManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Panel showing MCP request/response traces.
 * Similar to Language Server traces.
 */
public class MCPTracesPanel implements Disposable {

    private final JPanel mainPanel;
    private final MCPServerManager serverManager;
    private final ConsoleView consoleView;
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private final Project project;

    public MCPTracesPanel(@NotNull Project project, @NotNull MCPServerManager serverManager) {
        this.project = project;
        this.serverManager = serverManager;
        this.mainPanel = new JPanel(new BorderLayout());
        this.consoleView = new MCPConsoleView(project);
        Disposer.register(this, consoleView);
        initUI();
    }

    private void initUI() {
        // Console view component
        JComponent consoleComponent = consoleView.getComponent();
        mainPanel.add(consoleComponent, BorderLayout.CENTER);

        // Configure toolbar (Clear, etc.)
        configureConsoleToolbar();

        // Initial message
        addTrace(MCPBundle.message("mcp.traces.empty"));

        // Register trace listener (batching handled by MCPServerManager)
        serverManager.addTraceListener(new MCPServerManager.TraceListener() {
            @Override
            public void onTrace(String message) {
                // Message already batched by MCPServerManager
                consoleView.print(message, ConsoleViewContentType.NORMAL_OUTPUT);
            }

            @Override
            public void onRequest(String method, String params) {
                // Not used - batched into onTrace
            }

            @Override
            public void onResponse(String method, String result) {
                // Not used - batched into onTrace
            }
        });
    }

    /**
     * Configure console toolbar on the right of the console to provide actions like "Scroll to End", "Clear", etc.
     */
    private void configureConsoleToolbar() {
        DefaultActionGroup toolbarActions = new DefaultActionGroup();

        // Add console default actions (Scroll to End, Clear All, etc.)
        toolbarActions.addAll(consoleView.createConsoleActions());

        JComponent consoleComponent = consoleView.getComponent();
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("MCP Traces", toolbarActions, false);
        toolbar.setTargetComponent(consoleComponent);
        toolbar.getComponent().setBorder(JBUI.Borders.merge(
                toolbar.getComponent().getBorder(),
                JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 0, 0, 0, 1),
                true
        ));
        consoleComponent.add(toolbar.getComponent(), BorderLayout.EAST);
    }

    /**
     * Add a trace entry (for initial message).
     */
    private void addTrace(String message) {
        consoleView.print("[" + timestampFormat.format(new Date()) + "] " + message + "\n",
                ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void dispose() {
        // Disposed via Disposer.register
    }
}
