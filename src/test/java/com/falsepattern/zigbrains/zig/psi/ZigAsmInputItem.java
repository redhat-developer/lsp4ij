// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface ZigAsmInputItem extends PsiElement {

  @NotNull
  ZigExpr getExpr();

  @NotNull
  ZigStringLiteral getStringLiteral();

  @NotNull
  PsiElement getIdentifier();

  @NotNull
  PsiElement getLbracket();

  @NotNull
  PsiElement getLparen();

  @NotNull
  PsiElement getRbracket();

  @NotNull
  PsiElement getRparen();

}
