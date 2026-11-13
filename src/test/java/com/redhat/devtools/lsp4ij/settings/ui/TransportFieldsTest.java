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
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.server.definition.launching.TransportSupport;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
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

    public void testAClickUpdatesTheFieldsOnce() {
        radio("language.server.transport.socket").doClick();
        var changes = new AtomicInteger();
        fields.addChangeListener(changes::incrementAndGet);

        radio("language.server.socket.startAndConnect").doClick();

        assertEquals("a click deselects one button and selects another, but updates once", 1, changes.get());
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

    public void testATypedPortCountsBeforeTheFieldLosesTheFocus() {
        radio("language.server.transport.socket").doClick();
        radio("language.server.socket.port.fixed").doClick();
        assertNotNull(fields.validate());

        portText().setText("50070");

        // no focus change commits it
        assertNull(fields.validate());
        assertEquals(50070, fields.getTransport().port());
    }

    public void testARunningLanguageServerWithoutAPortShowsAnEmptyFieldAndAHint() {
        radio("language.server.transport.socket").doClick();
        radio("language.server.socket.attach").doClick();

        // no port isn't shown as the port 0
        assertEquals("", portText().getText());
        assertTrue(label("language.server.socket.port.attach.hint").isVisible());

        portText().setText("6005");
        assertFalse("the hint goes once there is a port", label("language.server.socket.port.attach.hint").isVisible());

        portText().setText("");
        assertEquals(0, port().getNumber());
        assertTrue(label("language.server.socket.port.attach.hint").isVisible());
    }

    public void testALanguageServerWhichTalksOnlyOverStdioCanNotBeGivenASocket() {
        assertFalse(label("language.server.transport.stdio.only").isVisible());

        fields.setSupportedTransports(List.of(new TransportSupport(UserDefinedTransport.Mode.STDIO, "--stdio")));

        assertFalse(radio("language.server.transport.socket").isEnabled());
        assertTrue(label("language.server.transport.stdio.only").isVisible());
    }

    public void testASocketWhichTheTemplateListsHasAHint() {
        fields.setSupportedTransports(List.of(new TransportSupport(UserDefinedTransport.Mode.STDIO, "--stdio"),
                new TransportSupport(UserDefinedTransport.Mode.START_AND_ACCEPT, "--socket=${port}")));
        assertTrue(radio("language.server.transport.socket").isEnabled());
        assertFalse(label("language.server.transport.stdio.only").isVisible());
        String hint = message("language.server.socket.template.hint", "--socket=${port}",
                message("language.server.socket.startAndAccept"));
        assertFalse("only a socket has the hint", labelWithText(hint).isVisible());

        radio("language.server.transport.socket").doClick();

        assertTrue(labelWithText(hint).isVisible());
    }

    public void testWithoutAWordFromTheTemplateEveryTransportCanBeChosenWithoutAHint() {
        fields.setSupportedTransports(List.of());
        radio("language.server.transport.socket").doClick();

        assertTrue(radio("language.server.transport.socket").isEnabled());
        assertFalse(label("language.server.transport.stdio.only").isVisible());
        assertTrue(UIUtil.findComponentsOfType(form, JBLabel.class).stream()
                .noneMatch(label -> label.isVisible() && label.getText().startsWith("This language server takes")));
    }

    public void testTheHostOfARunningLanguageServerIsAPlainHostNameOrIpAddress() {
        radio("language.server.transport.socket").doClick();
        radio("language.server.socket.attach").doClick();
        port().setNumber(6005);

        // they would only fail when the language server starts
        for (String wrong : List.of("http://localhost", "tcp://127.0.0.1", "localhost:6005", "local host", "999.1.1.1", "127.0.0.256")) {
            host().setText(wrong);
            var error = fields.validate();
            assertNotNull(wrong, error);
            assertEquals(message("language.server.validation.socket.host"), error.message);
        }
        for (String right : List.of("localhost", "127.0.0.1", "::1", "my-server.example.com", "my_service", "3d-printer")) {
            host().setText(right);
            assertNull(right, fields.validate());
        }
    }

    public void testATemplateLeavesASocketModeChosenAlready() {
        var running = new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005);
        fields.setTransport(running);

        // as when the settings of a language server open: the saved transport first, then its template's transports
        fields.setSupportedTransports(List.of(
                new TransportSupport(UserDefinedTransport.Mode.STDIO, "--stdio"),
                new TransportSupport(UserDefinedTransport.Mode.START_AND_ACCEPT, "--socket=${port}")));

        assertEquals(running, fields.getTransport());
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

    private @NotNull JFormattedTextField portText() {
        return ((JSpinner.DefaultEditor) port().getEditor()).getTextField();
    }

    private @NotNull JBLabel label(@NotNull String key) {
        return labelWithText(message(key));
    }

    private @NotNull JBLabel labelWithText(@NotNull String text) {
        return UIUtil.findComponentsOfType(form, JBLabel.class).stream()
                .filter(label -> text.equals(label.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No label " + text));
    }

    private @NotNull JBTextField host() {
        return UIUtil.findComponentOfType(form, JBTextField.class);
    }
}
