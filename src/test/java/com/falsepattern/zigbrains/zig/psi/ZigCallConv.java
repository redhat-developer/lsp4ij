// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface ZigCallConv extends PsiElement {

  @NotNull
  ZigExpr getExpr();

  @NotNull
  PsiElement getKeywordCallconv();

  @NotNull
  PsiElement getLparen();

  @NotNull
  PsiElement getRparen();

}
