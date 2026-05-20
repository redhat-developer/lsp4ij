// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ZigFnProto extends PsiElement {

  @Nullable
  ZigAddrSpace getAddrSpace();

  @Nullable
  ZigByteAlign getByteAlign();

  @Nullable
  ZigCallConv getCallConv();

  @Nullable
  ZigExpr getExpr();

  @Nullable
  ZigLinkSection getLinkSection();

  @Nullable
  ZigParamDeclList getParamDeclList();

  @Nullable
  PsiElement getExclamationmark();

  @Nullable
  PsiElement getIdentifier();

  @NotNull
  PsiElement getKeywordFn();

  @Nullable
  PsiElement getLparen();

  @Nullable
  PsiElement getRparen();

}
