// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ZigIfStatement extends PsiElement {

  @Nullable
  ZigExpr getExpr();

  @NotNull
  ZigIfPrefix getIfPrefix();

  @Nullable
  ZigPayload getPayload();

  @Nullable
  ZigStatement getStatement();

  @Nullable
  PsiElement getKeywordElse();

  @Nullable
  PsiElement getSemicolon();

}
