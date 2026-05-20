// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface ZigVarDeclProto extends PsiElement {

  @Nullable
  ZigAddrSpace getAddrSpace();

  @Nullable
  ZigByteAlign getByteAlign();

  @Nullable
  ZigExpr getExpr();

  @Nullable
  ZigLinkSection getLinkSection();

  @Nullable
  PsiElement getColon();

  @Nullable
  PsiElement getIdentifier();

  @Nullable
  PsiElement getKeywordConst();

  @Nullable
  PsiElement getKeywordVar();

}
