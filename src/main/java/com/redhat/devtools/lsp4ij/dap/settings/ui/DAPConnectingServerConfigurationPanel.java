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
import com.redhat.devtools.lsp4ij.dap.ConnectingServerStrategy;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Connection server configuration panel to choose the strategy to connect to the DAP server:
 *
 *  <ul>
 *      <li>none: connect after starting the server.</li>
 *      <li>timeout: connect after waiting for a given timeout (ms).</li>
 *      <li>timeout: connect after finding some trace (ex: listening...).</li>
 *  </ul>
 *
 */
public class DAPConnectingServerConfigurationPanel extends JPanel {

    private JRadioButton noneRadio;
    private JRadioButton timeoutRadio;
    private JRadioButton traceRadio;

    private JBIntSpinner connectTimeoutField;
    private JTextField traceField;

    public DAPConnectingServerConfigurationPanel() {
        super.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        ButtonGroup buttonGroup = new ButtonGroup();
        // - None strategy
        createNoneStrategyContent(buttonGroup);
        // - Timeout strategy
        createTimeoutStrategyContent(buttonGroup);
        // - Trace strategy
        createTraceStrategyContent(buttonGroup);
    }

    private void createNoneStrategyContent(ButtonGroup buttonGroup) {
        JPanel noneStrategyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        noneRadio = new JRadioButton(DAPBundle.message("dap.settings.editor.server.connecting.strategy.none"));
        noneRadio.setSelected(true);
        buttonGroup.add(noneRadio);
        noneStrategyPanel.add(noneRadio);
        super.add(noneStrategyPanel);
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

    public void update(@Nullable ConnectingServerStrategy connectingServerStrategy,
                       int connectTimeout,
                       String trace) {
        connectTimeoutField.setNumber(connectTimeout);
        traceField.setText(trace);
        if (connectingServerStrategy == null) {
            connectingServerStrategy = ConnectingServerStrategy.NONE;
            if (connectTimeout > 0) {
                connectingServerStrategy = ConnectingServerStrategy.TIMEOUT;
            } else if (StringUtils.isNotBlank(trace)) {
                connectingServerStrategy = ConnectingServerStrategy.TRACE;
            }
        }
        switch(connectingServerStrategy) {
            case NONE -> noneRadio.setSelected(true);
            case TIMEOUT -> timeoutRadio.setSelected(true);
            case TRACE -> traceRadio.setSelected(true);
        }
    }

    public ConnectingServerStrategy getConnectingServerStrategy() {
        if (timeoutRadio.isSelected()) {
            return ConnectingServerStrategy.TIMEOUT;
        }
        if (traceRadio.isSelected()) {
            return ConnectingServerStrategy.TRACE;
        }
        return ConnectingServerStrategy.NONE;
    }

    public int getConnectTimeout() {
        return connectTimeoutField.getNumber();
    }

    public String getTrace() {
        return traceField.getText();
    }


}
