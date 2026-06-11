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

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.mcp.MCPBundle;
import com.redhat.devtools.lsp4ij.mcp.MCPServerManager;
import com.redhat.devtools.lsp4ij.mcp.settings.MCPTrace;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Server configuration panel with port, start/stop buttons.
 */
public class MCPServerConfigPanel {

    private final MCPServerManager serverManager;
    private final JPanel mainPanel;

    private JBIntSpinner portSpinner;
    private ComboBox<MCPTrace> traceComboBox;
    private JButton startButton;
    private JButton stopButton;
    private JButton restartButton;
    private JBLabel statusLabel;

    public MCPServerConfigPanel(@NotNull MCPServerManager serverManager) {
        this.serverManager = serverManager;
        this.mainPanel = new JPanel(new BorderLayout());

        initUI();
        updateUI();

        // Listen to status changes
        serverManager.addStatusListener((status, message) -> {
            SwingUtilities.invokeLater(this::updateUI);
        });
    }

    private void initUI() {
        // Port configuration
        portSpinner = new JBIntSpinner(serverManager.getPort(), 1024, 65535);
        portSpinner.addChangeListener(e -> {
            if (serverManager.getStatus() == MCPServerManager.ServerStatus.STOPPED) {
                serverManager.setPort((Integer) portSpinner.getValue());
            }
        });

        // Trace level combo box
        traceComboBox = new ComboBox<>(MCPTrace.values());
        traceComboBox.setSelectedItem(serverManager.getTrace());
        traceComboBox.addActionListener(e -> {
            serverManager.setTrace((MCPTrace) traceComboBox.getSelectedItem());
        });

        // Status label
        statusLabel = new JBLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

        // Buttons
        startButton = new JButton(MCPBundle.message("mcp.server.start"));
        startButton.addActionListener(e -> {
            new Thread(() -> serverManager.start()).start();
        });

        stopButton = new JButton(MCPBundle.message("mcp.server.stop"));
        stopButton.addActionListener(e -> {
            new Thread(() -> serverManager.stop()).start();
        });

        restartButton = new JButton(MCPBundle.message("mcp.server.restart"));
        restartButton.addActionListener(e -> {
            new Thread(() -> serverManager.restart()).start();
        });

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(restartButton);

        // Build form
        FormBuilder formBuilder = FormBuilder.createFormBuilder()
                .setFormLeftIndent(10)
                .addLabeledComponent(MCPBundle.message("mcp.server.port.label"), portSpinner)
                .addLabeledComponent(MCPBundle.message("mcp.server.trace.label"), traceComboBox)
                .addComponent(statusLabel)
                .addComponent(buttonPanel);

        JPanel formPanel = formBuilder.getPanel();
        formPanel.setBorder(JBUI.Borders.empty(10));

        mainPanel.add(formPanel, BorderLayout.NORTH);
    }

    private void updateUI() {
        MCPServerManager.ServerStatus status = serverManager.getStatus();

        // Update status label
        String statusText = switch (status) {
            case RUNNING -> MCPBundle.message("mcp.server.status.running");
            case STARTING -> MCPBundle.message("mcp.server.status.starting");
            case ERROR -> MCPBundle.message("mcp.server.status.error");
            default -> MCPBundle.message("mcp.server.status.stopped");
        };
        statusLabel.setText("Status: " + statusText);

        // Update buttons
        boolean isRunning = status == MCPServerManager.ServerStatus.RUNNING;
        boolean isStopped = status == MCPServerManager.ServerStatus.STOPPED;

        startButton.setEnabled(isStopped);
        stopButton.setEnabled(isRunning);
        restartButton.setEnabled(isRunning);
        portSpinner.setEnabled(isStopped);
    }

    public JComponent getComponent() {
        return mainPanel;
    }
}
