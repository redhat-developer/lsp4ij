// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ZigSwitchExpr extends ZigExpr {

  @Nullable
  ZigExpr getExpr();

  @Nullable
  ZigSwitchProngList getSwitchProngList();

  @NotNull
  PsiElement getKeywordSwitch();

  @Nullable
  PsiElement getLbrace();

  @Nullable
  PsiElement getLparen();

  @Nullable
  PsiElement getRbrace();

  @Nullable
  PsiElement getRparen();

}
