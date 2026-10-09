// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface ZigLabeledStatement extends PsiElement {

  @Nullable
  ZigBlock getBlock();

  @Nullable
  ZigBlockLabel getBlockLabel();

  @Nullable
  ZigLoopStatement getLoopStatement();

  @Nullable
  ZigSwitchExpr getSwitchExpr();

}
