// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ZigAsmExpr extends ZigExpr {

  @Nullable
  ZigAsmOutput getAsmOutput();

  @NotNull
  ZigExpr getExpr();

  @NotNull
  PsiElement getKeywordAsm();

  @Nullable
  PsiElement getKeywordVolatile();

  @NotNull
  PsiElement getLparen();

  @NotNull
  PsiElement getRparen();

}
