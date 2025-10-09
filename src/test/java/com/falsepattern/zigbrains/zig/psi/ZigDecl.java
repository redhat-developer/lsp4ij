// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface ZigDecl extends PsiElement {

  @Nullable
  ZigBlock getBlock();

  @Nullable
  ZigExpr getExpr();

  @Nullable
  ZigFnProto getFnProto();

  @Nullable
  ZigGlobalVarDecl getGlobalVarDecl();

  @Nullable
  PsiElement getKeywordExport();

  @Nullable
  PsiElement getKeywordExtern();

  @Nullable
  PsiElement getKeywordInline();

  @Nullable
  PsiElement getKeywordNoinline();

  @Nullable
  PsiElement getKeywordThreadlocal();

  @Nullable
  PsiElement getKeywordUsingnamespace();

  @Nullable
  PsiElement getSemicolon();

  @Nullable
  PsiElement getStringLiteralSingle();

}
