// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface ZigPrimaryTypeExpr extends ZigExpr {

  @Nullable
  ZigContainerDecl getContainerDecl();

  @Nullable
  ZigErrorSetDecl getErrorSetDecl();

  @Nullable
  ZigExpr getExpr();

  @Nullable
  ZigFnCallArguments getFnCallArguments();

  @Nullable
  ZigFnProto getFnProto();

  @Nullable
  ZigInitList getInitList();

  @Nullable
  ZigStringLiteral getStringLiteral();

  @Nullable
  PsiElement getBuiltinidentifier();

  @Nullable
  PsiElement getCharLiteral();

  @Nullable
  PsiElement getDot();

  @Nullable
  PsiElement getFloat();

  @Nullable
  PsiElement getIdentifier();

  @Nullable
  PsiElement getInteger();

  @Nullable
  PsiElement getKeywordAnyframe();

  @Nullable
  PsiElement getKeywordComptime();

  @Nullable
  PsiElement getKeywordError();

  @Nullable
  PsiElement getKeywordUnreachable();

}
