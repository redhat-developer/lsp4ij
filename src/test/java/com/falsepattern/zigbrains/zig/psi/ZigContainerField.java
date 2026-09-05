// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ZigContainerField extends PsiElement {

  @Nullable
  ZigByteAlign getByteAlign();

  @NotNull
  List<ZigExpr> getExprList();

  @Nullable
  PsiElement getColon();

  @Nullable
  PsiElement getDocComment();

  @Nullable
  PsiElement getEqual();

  @Nullable
  PsiElement getIdentifier();

  @Nullable
  PsiElement getKeywordComptime();

}
