/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.commands.editor;

import com.google.gson.JsonArray;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.commands.CommandExecutor;
import com.redhat.devtools.lsp4ij.commands.LSPCommand;
import com.redhat.devtools.lsp4ij.commands.LSPCommandAction;
import com.redhat.devtools.lsp4ij.usages.LSPUsageType;
import com.redhat.devtools.lsp4ij.usages.LSPUsagesManager;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.eclipse.lsp4j.Location;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Emulates Visual Studio Code's "editor.action.showReferences" command, to show the LSP references in a popup.
 */
public class ShowReferencesAction extends LSPCommandAction {

    @Override
    protected void commandPerformed(@NotNull LSPCommand command, @NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // "editor.action.showReferences" command which have 3 arguments:
        // - URI
        // - Position
        // - List of Location

        // Here a sample of "editor.action.showReferences" command:
        /*
          "command": {
            "title": "3 references",
            "command": "editor.action.showReferences",
            "arguments": [
              "file:///c%3A/.../test.tsx",
              {
                "line": 0,
                "character": 9
              },
              [
                {
                  "uri": "file:///c%3A/.../test.tsx",
                  "range": {
                    "start": {
                      "line": 5,
                      "character": 8
                    },
                    "end": {
                      "line": 5,
                      "character": 11
                    }
                  }
                }
              ]
            ]
          }
         */

        // Get the third argument (List of Location)
        JsonArray array = (JsonArray) command.getArgumentAt(2);
        if (array == null) {
            return;
        }

        DataContext dataContext = e.getDataContext();
        LanguageServerItem languageServer = dataContext.getData(CommandExecutor.LSP_COMMAND_LANGUAGE_SERVER);

        // Get LSP4J Location from the JSON locations array
        final List<LocationData> locations = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            locations.add(new LocationData(JSONUtils.toModel(array.get(i), Location.class), languageServer));
        }

        // Call "Find Usages" in popup mode.
        LSPUsagesManager.getInstance(project).findShowUsagesInPopup(locations,
                LSPUsageType.References,
                dataContext,
                (MouseEvent) e.getInputEvent());
    }

    @Override
    protected @NotNull ActionUpdateThread getCommandPerformedThread() {
        return ActionUpdateThread.EDT;
    }
}
