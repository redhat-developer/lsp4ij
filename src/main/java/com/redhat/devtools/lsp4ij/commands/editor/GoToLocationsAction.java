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
package com.redhat.devtools.lsp4ij.commands.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.commands.CommandExecutor;
import com.redhat.devtools.lsp4ij.commands.LSPCommand;
import com.redhat.devtools.lsp4ij.commands.LSPCommandAction;
import com.redhat.devtools.lsp4ij.usages.LSPUsageType;
import com.redhat.devtools.lsp4ij.usages.LSPUsagesManager;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.eclipse.lsp4j.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Emulates Visual Studio Code's "editor.action.goToLocations" and "editor.action.peekLocations" commands.
 *
 * This action handles navigation to LSP locations with support for different modes:
 * - "goto": navigate directly to location (single) or first location (multiple)
 * - "peek": show locations in a popup
 * - "gotoAndPeek": navigate and show popup
 */
public class GoToLocationsAction extends LSPCommandAction {

    private static final String MODE_GOTO = "goto";
    private static final String MODE_PEEK = "peek";
    private static final String MODE_GOTO_AND_PEEK = "gotoAndPeek";

    @Override
    protected void commandPerformed(@NotNull LSPCommand command, @NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // "editor.action.goToLocations" / "editor.action.peekLocations" command has 4 arguments:
        // - URI (argument 0)
        // - Position (argument 1)
        // - List of Location (argument 2)
        // - Mode: "goto" | "peek" | "gotoAndPeek" (argument 3, optional, default: "goto")

        // Example:
        /*
          "command": {
            "title": "injected: MyService (Provider.os)",
            "command": "editor.action.goToLocations",
            "arguments": [
              "file:///project/Consumer.os",
              { "line": 6, "character": 6 },
              [
                {
                  "uri": "file:///project/Provider.os",
                  "range": {
                    "start": { "line": 6, "character": 10 },
                    "end": { "line": 6, "character": 28 }
                  }
                }
              ],
              "goto"
            ]
          }
         */

        // Get the third argument (List of Location)
        JsonArray array = (JsonArray) command.getArgumentAt(2);
        if (array == null || array.isEmpty()) {
            return;
        }

        // Get the fourth argument (mode: "goto" | "peek" | "gotoAndPeek")
        String mode = resolveMode(command.getArgumentAt(3));

        DataContext dataContext = e.getDataContext();
        LanguageServerItem languageServer = dataContext.getData(CommandExecutor.LSP_COMMAND_LANGUAGE_SERVER);

        // Get LSP4J Location from the JSON locations array
        final List<LocationData> locations = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            locations.add(new LocationData(JSONUtils.toModel(array.get(i), Location.class), languageServer));
        }

        // Handle different modes
        switch (mode) {
            case MODE_GOTO:
                handleGoto(locations, project);
                break;
            case MODE_PEEK:
                handlePeek(locations, dataContext, (MouseEvent) e.getInputEvent(), project);
                break;
            case MODE_GOTO_AND_PEEK:
                handleGotoAndPeek(locations, dataContext, (MouseEvent) e.getInputEvent(), project);
                break;
            default:
                // Unknown mode, default to goto
                handleGoto(locations, project);
                break;
        }
    }

    /**
     * Resolves the optional {@code mode} argument of {@code editor.action.goToLocations} /
     * {@code editor.action.peekLocations}.
     * <p>
     * Command arguments are Gson JSON elements, so a JSON string arrives as a {@link JsonPrimitive}
     * rather than a {@link String}; a {@code null} or non-string argument falls back to
     * {@link #MODE_GOTO}.
     *
     * @param modeArg the raw fourth command argument (may be {@code null})
     * @return {@code "goto"}, {@code "peek"} or {@code "gotoAndPeek"} (or the raw string if any);
     * never {@code null}
     */
    static String resolveMode(@Nullable Object modeArg) {
        if (modeArg instanceof JsonPrimitive primitive && primitive.isString()) {
            return primitive.getAsString();
        }
        if (modeArg instanceof String stringMode) {
            // Tolerate an already-decoded String (e.g. programmatic invocation).
            return stringMode;
        }
        return MODE_GOTO;
    }

    /**
     * Navigate to the first location directly
     */
    private void handleGoto(@NotNull List<LocationData> locations, @NotNull Project project) {
        if (!locations.isEmpty()) {
            LocationData location = locations.get(0);
            ApplicationManager.getApplication().invokeLater(() ->
                LSPIJUtils.openInEditor(location.location(), location.languageServer().getClientFeatures(), project)
            );
        }
    }

    /**
     * Show locations in a popup (same as showReferences)
     */
    private void handlePeek(@NotNull List<LocationData> locations,
                           @NotNull DataContext dataContext,
                           @Nullable MouseEvent event,
                           @NotNull Project project) {
        LSPUsagesManager.getInstance(project).findShowUsagesInPopup(
            locations,
            LSPUsageType.References, // Using References as the usage type for peek
            dataContext,
            event
        );
    }

    /**
     * Navigate to the first location AND show popup
     */
    private void handleGotoAndPeek(@NotNull List<LocationData> locations,
                                   @NotNull DataContext dataContext,
                                   @Nullable MouseEvent event,
                                   @NotNull Project project) {
        // First navigate to the location
        handleGoto(locations, project);
        // Then show the popup
        handlePeek(locations, dataContext, event, project);
    }

    @Override
    protected @NotNull ActionUpdateThread getCommandPerformedThread() {
        return ActionUpdateThread.EDT;
    }
}
