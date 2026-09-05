// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface ZigGroupedExpr extends ZigExpr {

  @NotNull
  ZigExpr getExpr();

  @NotNull
  PsiElement getLparen();

  @NotNull
  PsiElement getRparen();

}
