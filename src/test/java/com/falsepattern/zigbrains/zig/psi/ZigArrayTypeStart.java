// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ZigArrayTypeStart extends PsiElement {

  @NotNull
  List<ZigExpr> getExprList();

  @Nullable
  PsiElement getColon();

  @NotNull
  PsiElement getLbracket();

  @NotNull
  PsiElement getRbracket();

}
