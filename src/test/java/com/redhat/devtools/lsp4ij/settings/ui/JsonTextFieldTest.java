/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The listeners of a {@link JsonTextField} hear of each change of its document: its validation handlers, and the
 * document listeners added to the field.
 */
public class JsonTextFieldTest extends BasePlatformTestCase {

    public void testAValidationHandlerHearsOfEachChangeOfTheDocument() {
        var field = new JsonTextField(getProject());
        var validations = new ArrayList<Boolean>();
        field.addValidationHandler(validations::add);

        setText(field, "{\"a\": 1}");
        setText(field, "{\"a\": 2}");

        assertEquals(2, validations.size());
    }

    public void testADocumentListenerOfTheFieldHearsOfEachChangeOfTheDocument() {
        var field = new JsonTextField(getProject());
        var texts = new ArrayList<String>();
        field.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                texts.add(event.getDocument().getText());
            }
        });

        setText(field, "{\"a\": 1}");
        setText(field, "{\"a\": 2}");

        assertEquals(List.of("{\"a\": 1}", "{\"a\": 2}"), texts);
    }

    private void setText(@NotNull JsonTextField field, @NotNull String text) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> field.getDocument().setText(text));
    }
}
