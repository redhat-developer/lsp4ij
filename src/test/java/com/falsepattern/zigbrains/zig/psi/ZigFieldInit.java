// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface ZigFieldInit extends PsiElement {

  @NotNull
  ZigExpr getExpr();

  @NotNull
  PsiElement getDot();

  @NotNull
  PsiElement getEqual();

  @NotNull
  PsiElement getIdentifier();

}
