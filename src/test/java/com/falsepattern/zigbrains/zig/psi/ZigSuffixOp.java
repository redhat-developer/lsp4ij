// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ZigSuffixOp extends PsiElement {

  @NotNull
  List<ZigExpr> getExprList();

  @Nullable
  PsiElement getColon();

  @Nullable
  PsiElement getDot();

  @Nullable
  PsiElement getDot2();

  @Nullable
  PsiElement getDotasterisk();

  @Nullable
  PsiElement getDotquestionmark();

  @Nullable
  PsiElement getIdentifier();

  @Nullable
  PsiElement getLbracket();

  @Nullable
  PsiElement getRbracket();

}
