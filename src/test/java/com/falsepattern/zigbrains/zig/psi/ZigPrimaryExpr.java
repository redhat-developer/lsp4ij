// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface ZigPrimaryExpr extends ZigExpr {

  @Nullable
  ZigBlock getBlock();

  @Nullable
  ZigBlockLabel getBlockLabel();

  @Nullable
  ZigBreakLabel getBreakLabel();

  @Nullable
  ZigExpr getExpr();

  @Nullable
  PsiElement getKeywordBreak();

  @Nullable
  PsiElement getKeywordComptime();

  @Nullable
  PsiElement getKeywordContinue();

  @Nullable
  PsiElement getKeywordNosuspend();

  @Nullable
  PsiElement getKeywordResume();

  @Nullable
  PsiElement getKeywordReturn();

}
