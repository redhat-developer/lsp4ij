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
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.PortField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import static com.redhat.devtools.lsp4ij.LanguageServerBundle.message;

/**
 * The transport of a user-defined language server in its Server tab: the standard input and output of its process, or
 * a socket, in one of three modes, with its port. The socket's fields show only for a socket, the host only for a
 * language server which runs already, and the choice of a free port only for a language server which LSP4IJ starts.
 */
final class TransportFields {

    private static final int GAP = 12;

    private final JBRadioButton stdio = new JBRadioButton(message("language.server.transport.stdio"));
    private final JBRadioButton socket = new JBRadioButton(message("language.server.transport.socket"));
    private final JBRadioButton attach = new JBRadioButton(message("language.server.socket.attach"));
    private final JBRadioButton startAndConnect = new JBRadioButton(message("language.server.socket.startAndConnect"));
    private final JBRadioButton startAndAccept = new JBRadioButton(message("language.server.socket.startAndAccept"));
    private final JBTextField host = new JBTextField();
    private final JBRadioButton autoPort = new JBRadioButton(message("language.server.socket.port.auto"));
    private final JBRadioButton fixedPort = new JBRadioButton(message("language.server.socket.port.fixed"));
    private final PortField port = new PortField(0, 0);
    private final JBLabel socketLabel = new JBLabel(message("language.server.socket"));
    // the other modes' rows have no label, so "Socket:" stands on the row of the first mode
    private final JBLabel startAndConnectLabel = new JBLabel();
    private final JBLabel startAndAcceptLabel = new JBLabel();
    private final JBLabel hostLabel = new JBLabel(message("language.server.socket.host"));
    private final JBLabel portLabel = new JBLabel(message("language.server.socket.port"));
    private final JBLabel portHint = new JBLabel(message("language.server.socket.port.hint", UserDefinedTransport.PORT_MACRO));
    private final JPanel transportRow = row(GAP, stdio, socket);
    // Auto and Fixed have a row of their own, whose gaps hide with them, so the port lines up with the host without them
    private final JPanel portChoice = row(GAP, autoPort, fixedPort);
    private final JPanel portRow = row(0, portChoice, port);
    private final List<Runnable> changeListeners = new ArrayList<>();

    TransportFields() {
        group(stdio, socket);
        group(attach, startAndConnect, startAndAccept);
        group(autoPort, fixedPort);
        host.getEmptyText().setText(InetAddress.getLoopbackAddress().getHostAddress());
        hostLabel.setLabelFor(host);
        portLabel.setLabelFor(port);
        portHint.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        setTransport(UserDefinedTransport.STDIO);
        for (var button : List.of(stdio, socket, attach, startAndConnect, startAndAccept, autoPort, fixedPort)) {
            button.addItemListener(event -> update());
        }
    }

    /**
     * Adds the rows of the transport to the given form.
     *
     * @param builder the form of the Server tab.
     */
    void addTo(@NotNull FormBuilder builder) {
        builder.addLabeledComponent(message("language.server.transport"), transportRow);
        builder.addLabeledComponent(socketLabel, attach);
        builder.addLabeledComponent(startAndConnectLabel, startAndConnect);
        builder.addLabeledComponent(startAndAcceptLabel, startAndAccept);
        builder.addLabeledComponent(hostLabel, host);
        builder.addLabeledComponent(portLabel, portRow);
        builder.addComponent(portHint, 0);
    }

    /**
     * Adds a listener which runs when the transport changes.
     *
     * @param listener the listener.
     */
    void addChangeListener(@NotNull Runnable listener) {
        changeListeners.add(listener);
    }

    /**
     * Returns the transport which the fields show.
     *
     * @return the transport which the fields show.
     */
    @NotNull
    UserDefinedTransport getTransport() {
        if (!socket.isSelected()) {
            return UserDefinedTransport.STDIO;
        }
        if (attach.isSelected()) {
            String text = host.getText().trim();
            return new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, text.isEmpty() ? null : text, port.getNumber());
        }
        var mode = startAndConnect.isSelected()
                ? UserDefinedTransport.Mode.START_AND_CONNECT
                : UserDefinedTransport.Mode.START_AND_ACCEPT;
        return new UserDefinedTransport(mode, null, autoPort.isSelected() ? 0 : port.getNumber());
    }

    /**
     * Shows the given transport. For the standard input and output, the socket's fields keep their defaults: a
     * language server which connects to LSP4IJ, on a free port.
     *
     * @param transport the transport.
     */
    void setTransport(@NotNull UserDefinedTransport transport) {
        switch (transport.mode()) {
            case STDIO -> {
                stdio.setSelected(true);
                startAndAccept.setSelected(true);
            }
            case ATTACH -> {
                socket.setSelected(true);
                attach.setSelected(true);
            }
            case START_AND_CONNECT -> {
                socket.setSelected(true);
                startAndConnect.setSelected(true);
            }
            case START_AND_ACCEPT -> {
                socket.setSelected(true);
                startAndAccept.setSelected(true);
            }
        }
        host.setText(transport.host() != null ? transport.host() : "");
        (transport.port() == 0 ? autoPort : fixedPort).setSelected(true);
        port.setNumber(transport.port());
        update();
    }

    /**
     * Returns an error when the port can't be used: a language server which runs already needs its port, since LSP4IJ
     * can't choose one for it, and a fixed port isn't 0, which would be Auto once saved.
     *
     * @return the error of the port, or null if it can be used.
     */
    @Nullable
    ValidationInfo validate() {
        if (!socket.isSelected() || port.getNumber() != 0) {
            return null;
        }
        if (attach.isSelected()) {
            return new ValidationInfo(message("language.server.validation.socket.port"), port);
        }
        if (fixedPort.isSelected()) {
            return new ValidationInfo(message("language.server.validation.socket.port.fixed"), port);
        }
        return null;
    }

    /**
     * Shows the fields which the selected transport uses, and tells the listeners.
     */
    private void update() {
        boolean isSocket = socket.isSelected();
        boolean isAttach = isSocket && attach.isSelected();
        for (var component : List.of(socketLabel, attach, startAndConnectLabel, startAndConnect, startAndAcceptLabel,
                startAndAccept)) {
            component.setVisible(isSocket);
        }
        hostLabel.setVisible(isAttach);
        host.setVisible(isAttach);
        portLabel.setVisible(isSocket);
        portRow.setVisible(isSocket);
        // LSP4IJ can't choose the port of a language server which runs already
        portChoice.setVisible(!isAttach);
        port.setEnabled(isAttach || fixedPort.isSelected());
        portHint.setVisible(isSocket && !isAttach);
        var parent = transportRow.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
        changeListeners.forEach(Runnable::run);
    }

    private static void group(@NotNull AbstractButton... buttons) {
        var group = new ButtonGroup();
        for (var button : buttons) {
            group.add(button);
        }
    }

    /**
     * Returns a row of the given components, each followed by the given gap.
     */
    private static @NotNull JPanel row(int gap, @NotNull JComponent... components) {
        var row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        for (var component : components) {
            component.setMaximumSize(component.getPreferredSize());
            row.add(component);
            row.add(Box.createHorizontalStrut(JBUI.scale(gap)));
        }
        row.add(Box.createHorizontalGlue());
        return row;
    }
}
