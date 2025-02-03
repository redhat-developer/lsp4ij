/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.settings.ui;

import com.intellij.ui.JBIntSpinner;
import com.redhat.devtools.lsp4ij.dap.DebugServerWaitStrategy;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Connection server configuration panel to choose the strategy to connect to the DAP server:
 *
 *  <ul>
 *      <li>timeout: Wait for a timeout before assuming the server is ready</li>
 *      <li>timeout: Wait for a specific log message before proceeding</li>
 *  </ul>
 *
 */
public class DAPDebugServerWaitStrategyPanel extends JPanel {

    private JRadioButton timeoutRadio;
    private JRadioButton traceRadio;

    private JBIntSpinner connectTimeoutField;
    private JTextField traceField;

    public DAPDebugServerWaitStrategyPanel() {
        super.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        ButtonGroup buttonGroup = new ButtonGroup();
        // - Timeout strategy
        createTimeoutStrategyContent(buttonGroup);
        // - Trace strategy
        createTraceStrategyContent(buttonGroup);
    }

    private void createTimeoutStrategyContent(ButtonGroup buttonGroup) {
        JPanel timeoutStrategyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        timeoutRadio = new JRadioButton(DAPBundle.message("dap.settings.editor.server.connecting.strategy.timeout"));
        buttonGroup.add(timeoutRadio);
        connectTimeoutField = new JBIntSpinner(0, 0, Integer.MAX_VALUE);
        timeoutStrategyPanel.add(timeoutRadio);
        timeoutStrategyPanel.add(connectTimeoutField);
        super.add(timeoutStrategyPanel);
    }

    private void createTraceStrategyContent(ButtonGroup buttonGroup) {
        JPanel traceStrategyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        traceRadio = new JRadioButton(DAPBundle.message("dap.settings.editor.server.connecting.strategy.trace"));
        buttonGroup.add(traceRadio);
        traceField = new JTextField(30);
        traceStrategyPanel.add(traceRadio);
        traceStrategyPanel.add(traceField);
        super.add(traceStrategyPanel);
    }

    public void update(@Nullable DebugServerWaitStrategy debugServerWaitStrategy,
                       int connectTimeout,
                       @Nullable String trace) {
        connectTimeoutField.setNumber(connectTimeout);
        traceField.setText(trace != null ? trace : "");
        if (debugServerWaitStrategy == null) {
            debugServerWaitStrategy = DebugServerWaitStrategy.TIMEOUT;
            if (connectTimeout > 0) {
                debugServerWaitStrategy = DebugServerWaitStrategy.TIMEOUT;
            } else if (StringUtils.isNotBlank(trace)) {
                debugServerWaitStrategy = DebugServerWaitStrategy.TRACE;
            }
        }
        switch(debugServerWaitStrategy) {
            case TIMEOUT -> timeoutRadio.setSelected(true);
            case TRACE -> traceRadio.setSelected(true);
        }
    }

    public DebugServerWaitStrategy getDebugServerWaitStrategy() {
        if (timeoutRadio.isSelected()) {
            return DebugServerWaitStrategy.TIMEOUT;
        }
        if (traceRadio.isSelected()) {
            return DebugServerWaitStrategy.TRACE;
        }
        return DebugServerWaitStrategy.TIMEOUT;
    }

    public int getConnectTimeout() {
        return connectTimeoutField.getNumber();
    }

    public String getTrace() {
        return traceField.getText();
    }


}
