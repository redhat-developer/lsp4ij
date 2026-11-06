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

import com.google.common.net.InetAddresses;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.PortField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.server.definition.launching.TransportSupport;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.awt.event.ItemEvent;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.redhat.devtools.lsp4ij.LanguageServerBundle.message;

/**
 * The transport of a user-defined language server in its Server tab: the standard input and output of its process, or
 * a socket, in one of three modes, with its port. The socket's fields show only for a socket, the host only for a
 * language server which runs already, and the choice of a free port only for a language server which LSP4IJ starts.
 */
final class TransportFields {

    private static final int GAP = 12;
    private static final int MAX_PORT = 65_535;
    private static final Pattern HOST_NAME =
            Pattern.compile("[A-Za-z0-9_]([A-Za-z0-9_-]*[A-Za-z0-9_])?(\\.[A-Za-z0-9_]([A-Za-z0-9_-]*[A-Za-z0-9_])?)*\\.?");

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
    private final JBLabel attachPortHint = new JBLabel(message("language.server.socket.port.attach.hint"));
    private final JBLabel stdioOnlyHint = new JBLabel(message("language.server.transport.stdio.only"));
    private final JBLabel templateSocketHint = new JBLabel();
    private final JPanel transportRow = row(GAP, stdio, socket);
    // Auto and Fixed have a row of their own, whose gaps hide with them, so the port lines up with the host without them
    private final JPanel portChoice = row(GAP, autoPort, fixedPort);
    private final JPanel portRow = row(0, portChoice, port);
    private final List<Runnable> changeListeners = new ArrayList<>();
    private List<TransportSupport> supportedTransports = List.of();

    TransportFields() {
        group(stdio, socket);
        group(attach, startAndConnect, startAndAccept);
        group(autoPort, fixedPort);
        host.getEmptyText().setText(InetAddress.getLoopbackAddress().getHostAddress());
        hostLabel.setLabelFor(host);
        portLabel.setLabelFor(port);
        portHint.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        attachPortHint.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        stdioOnlyHint.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        templateSocketHint.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        typedPortCommits();
        setTransport(UserDefinedTransport.STDIO);
        for (var button : List.of(stdio, socket, attach, startAndConnect, startAndAccept, autoPort, fixedPort)) {
            // a click deselects one button and selects another: update once, for the selection
            button.addItemListener(event -> {
                if (event.getStateChange() == ItemEvent.SELECTED) {
                    update();
                }
            });
        }
        port.addChangeListener(event -> update());
    }

    /**
     * Adds the rows of the transport to the given form.
     *
     * @param builder the form of the Server tab.
     */
    void addTo(@NotNull FormBuilder builder) {
        builder.addLabeledComponent(message("language.server.transport"), transportRow);
        builder.addComponent(stdioOnlyHint, 0);
        builder.addLabeledComponent(socketLabel, attach);
        builder.addLabeledComponent(startAndConnectLabel, startAndConnect);
        builder.addLabeledComponent(startAndAcceptLabel, startAndAccept);
        builder.addComponent(templateSocketHint, 0);
        builder.addLabeledComponent(hostLabel, host);
        builder.addLabeledComponent(portLabel, portRow);
        builder.addComponent(portHint, 0);
        builder.addComponent(attachPortHint, 0);
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
     * Shows what the template of the language server says of its transports: Socket is disabled for a language server
     * which talks only over its standard input and output, and a hint names the mode, and the argument, of a socket
     * which it supports. Without a word from the template, every transport can be chosen and no hint shows.
     *
     * @param supportedTransports the transports which the template lists, empty if it lists none.
     */
    void setSupportedTransports(@NotNull List<TransportSupport> supportedTransports) {
        this.supportedTransports = List.copyOf(supportedTransports);
        update();
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
     * Returns an error when the host or the port can't be used: the host of a language server which runs already is a
     * plain host name or IP address, without a scheme or a port; such a language server needs its port, since LSP4IJ
     * can't choose one for it; and a fixed port isn't 0, which would be Auto once saved.
     *
     * @return the error of the host or port, or null if they can be used.
     */
    @Nullable
    ValidationInfo validate() {
        if (!socket.isSelected()) {
            return null;
        }
        String hostText = host.getText().trim();
        if (attach.isSelected() && !hostText.isEmpty() && !isHost(hostText)) {
            return new ValidationInfo(message("language.server.validation.socket.host"), host);
        }
        if (port.getNumber() != 0) {
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
        attachPortHint.setVisible(isAttach && port.getNumber() == 0);
        var socketSupport = supportedTransports.stream()
                .filter(transport -> transport.mode() != UserDefinedTransport.Mode.STDIO)
                .findFirst();
        boolean socketSupported = supportedTransports.isEmpty() || socketSupport.isPresent();
        socket.setEnabled(socketSupported);
        stdioOnlyHint.setVisible(!socketSupported);
        socketSupport.ifPresent(transport -> templateSocketHint.setText(transport.argument() != null
                ? message("language.server.socket.template.hint", transport.argument(), modeButton(transport.mode()).getText())
                : message("language.server.socket.template.mode.hint", modeButton(transport.mode()).getText())));
        templateSocketHint.setVisible(isSocket && socketSupport.isPresent());
        var parent = transportRow.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
        changeListeners.forEach(Runnable::run);
    }

    /**
     * Makes the port field take each valid port as it is typed, not only once it loses the focus, so that validation
     * never sees a port the user has already replaced, and shows no port, 0, as an empty field rather than as a port.
     */
    private void typedPortCommits() {
        if (!(port.getEditor() instanceof JSpinner.DefaultEditor editor)) {
            return;
        }
        var formatter = new NumberFormatter(new DecimalFormat("#")) {
            @Override
            public String valueToString(Object value) throws ParseException {
                return value instanceof Number number && number.intValue() == 0 ? "" : super.valueToString(value);
            }

            @Override
            public Object stringToValue(String text) throws ParseException {
                return text == null || text.isBlank() ? 0 : super.stringToValue(text);
            }
        };
        formatter.setValueClass(Integer.class);
        formatter.setMinimum(0);
        formatter.setMaximum(MAX_PORT);
        formatter.setCommitsOnValidEdit(true);
        editor.getTextField().setFormatterFactory(new DefaultFormatterFactory(formatter));
    }

    /**
     * Returns true if the given text is an IP address, IPv6 too, or a host name: letters, digits, hyphens and
     * underscores, in labels which dots separate. A scheme such as {@code http://}, a port, or a space fails it.
     */
    private static boolean isHost(@NotNull String text) {
        return InetAddresses.isInetAddress(text) || HOST_NAME.matcher(text).matches();
    }

    private @NotNull JBRadioButton modeButton(@NotNull UserDefinedTransport.Mode mode) {
        return switch (mode) {
            case STDIO -> stdio;
            case ATTACH -> attach;
            case START_AND_CONNECT -> startAndConnect;
            case START_AND_ACCEPT -> startAndAccept;
        };
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
