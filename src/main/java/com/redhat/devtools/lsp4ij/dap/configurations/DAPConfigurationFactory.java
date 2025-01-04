package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.dap.features.DAPClientFeatures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug Adapter Protocol (DAP) configuration factory.
 */
public class DAPConfigurationFactory extends ConfigurationFactory {

  private final @NotNull DAPClientFeatures clientFeatures;

  public DAPConfigurationFactory(@NotNull DAPClientFeatures clientFeatures,
                                 @NotNull ConfigurationType type) {
    super(type);
    this.clientFeatures = clientFeatures;
  }

  @Override
  public @NotNull String getId() {
    return getType().getId();
  }

  public @NotNull DAPClientFeatures getClientFeatures() {
    return clientFeatures;
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(
      @NotNull Project project) {
    return new DAPRunConfiguration(getClientFeatures(), project, this, "Demo");
  }

  @Nullable
  @Override
  public Class<? extends BaseState> getOptionsClass() {
    return DAPRunConfigurationOptions.class;
  }

}