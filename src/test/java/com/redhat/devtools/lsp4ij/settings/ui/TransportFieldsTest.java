/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.ui.PortField;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.redhat.devtools.lsp4ij.LanguageServerBundle.message;

/**
 * Tests the transport in the Server tab of a user-defined language server, as the user sees and clicks it: the socket's
 * fields show only for a socket, the host only for a language server which runs already, and the free port is a
 * choice of its own.
 */
public class TransportFieldsTest extends LightPlatformTestCase {

    private TransportFields fields;
    private JPanel form;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        fields = new TransportFields();
        var builder = FormBuilder.createFormBuilder();
        fields.addTo(builder);
        form = builder.getPanel();
    }

    public void testTheStandardInputAndOutputShowsNoSocket() {
        assertTrue(radio("language.server.transport.stdio").isSelected());
        assertFalse("the socket's modes are hidden", radio("language.server.socket.attach").isVisible());
        assertFalse(port().getParent().isVisible());
        assertSame(UserDefinedTransport.STDIO, fields.getTransport());
        assertNull(fields.validate());
    }

    public void testASocketStartsTheLanguageServerOnAFreePortByDefault() {
        var changes = new AtomicInteger();
        fields.addChangeListener(changes::incrementAndGet);

        radio("language.server.transport.socket").doClick();

        assertTrue(changes.get() > 0);
        assertTrue(radio("language.server.socket.startAndAccept").isVisible());
        assertTrue(radio("language.server.socket.startAndAccept").isSelected());
        assertTrue(radio("language.server.socket.port.auto").isSelected());
        assertFalse("the port is chosen", port().isEnabled());
        assertFalse("LSP4IJ starts the language server, so it has no host", host().isVisible());
        assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0), fields.getTransport());
    }

    public void testAFixedPortIsNotZero() {
        radio("language.server.transport.socket").doClick();
        radio("language.server.socket.port.fixed").doClick();

        // 0 would be Auto once saved
        var error = fields.validate();
        assertNotNull(error);
        assertEquals(message("language.server.validation.socket.port.fixed"), error.message);

        port().setNumber(4389);
        assertNull(fields.validate());
    }

    public void testAFixedPortIsTheOneTyped() {
        radio("language.server.transport.socket").doClick();
        radio("language.server.socket.startAndConnect").doClick();
        radio("language.server.socket.port.fixed").doClick();
        assertTrue(port().isEnabled());
        port().setNumber(4389);

        assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 4389), fields.getTransport());
    }

    public void testALanguageServerThatRunsAlreadyHasAHostAndItsPortButNoFreePort() {
        radio("language.server.transport.socket").doClick();
        radio("language.server.socket.attach").doClick();

        assertTrue(host().isVisible());
        assertFalse("Auto and Fixed are hidden", radio("language.server.socket.port.auto").getParent().isVisible());
        assertTrue(port().isEnabled());
        assertNotNull("it needs its port", fields.validate());

        host().setText("127.0.0.1");
        port().setNumber(6005);
        assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005), fields.getTransport());
        assertNull(fields.validate());
    }

    public void testATransportIsShownAsItIs() {
        for (var transport : new UserDefinedTransport[]{
                UserDefinedTransport.STDIO,
                new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "localhost", 6005),
                new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, null, 6005),
                new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 0),
                new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 4389)}) {
            fields.setTransport(transport);
            assertEquals(transport, fields.getTransport());
        }
    }

    private @NotNull JBRadioButton radio(@NotNull String key) {
        String text = message(key);
        return UIUtil.findComponentsOfType(form, JBRadioButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No radio button " + text));
    }

    private @NotNull PortField port() {
        return UIUtil.findComponentOfType(form, PortField.class);
    }

    private @NotNull JBTextField host() {
        return UIUtil.findComponentOfType(form, JBTextField.class);
    }
}
