// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ZigAsmOutputItem extends PsiElement {

  @Nullable
  ZigExpr getExpr();

  @NotNull
  ZigStringLiteral getStringLiteral();

  @NotNull
  PsiElement getLbracket();

  @NotNull
  PsiElement getLparen();

  @Nullable
  PsiElement getMinusrarrow();

  @NotNull
  PsiElement getRbracket();

  @NotNull
  PsiElement getRparen();

}
