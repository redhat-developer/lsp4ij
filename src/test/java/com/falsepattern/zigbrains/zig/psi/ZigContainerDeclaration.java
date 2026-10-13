// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface ZigContainerDeclaration extends PsiElement {

  @Nullable
  ZigComptimeDecl getComptimeDecl();

  @Nullable
  ZigDecl getDecl();

  @Nullable
  ZigTestDecl getTestDecl();

  @Nullable
  PsiElement getDocComment();

  @Nullable
  PsiElement getKeywordPub();

}
