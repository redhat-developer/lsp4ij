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
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Launch configuration snippet.
 */
public class LaunchConfiguration {

    @Attribute("id")
    private String id;
    @Attribute("name")
    private String name;
    @Attribute("content")
    private String content;
    @Attribute("type")
    private String type;

    public LaunchConfiguration() {

    }

    public LaunchConfiguration(@NotNull String id,
                               @NotNull String name,
                               @NotNull String content,
                               @NotNull DebuggingType type) {
        this.id = id;
        this.name = name;
        this.content = content;
        this.type = type.name();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public DebuggingType getType() {
        return DebuggingType.get(type);
    }

    @Nullable
    public static LaunchConfiguration findLaunchConfiguration(@Nullable List<LaunchConfiguration> launchConfigurations) {
        return findConfiguration(launchConfigurations, DebuggingType.LAUNCH);
    }

    @Nullable
    public static LaunchConfiguration findAttachConfiguration(@Nullable List<LaunchConfiguration> launchConfigurations) {
        return findConfiguration(launchConfigurations, DebuggingType.ATTACH);
    }

    private static LaunchConfiguration findConfiguration(@Nullable List<LaunchConfiguration> launchConfigurations,
                                                         @NotNull DebuggingType debuggingType) {
        if (launchConfigurations == null || launchConfigurations.isEmpty()) {
            return null;
        }
        var result = launchConfigurations
                .stream()
                .filter((snippet -> snippet.getType() == debuggingType))
                .findFirst();
        return result.isEmpty() ? null : result.get();
    }

}
