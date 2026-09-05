// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface ZigStatement extends PsiElement {

  @Nullable
  ZigBlockExprStatement getBlockExprStatement();

  @Nullable
  ZigComptimeStatement getComptimeStatement();

  @Nullable
  ZigIfStatement getIfStatement();

  @Nullable
  ZigLabeledStatement getLabeledStatement();

  @Nullable
  ZigPayload getPayload();

  @Nullable
  ZigVarDeclExprStatement getVarDeclExprStatement();

  @Nullable
  PsiElement getKeywordComptime();

  @Nullable
  PsiElement getKeywordDefer();

  @Nullable
  PsiElement getKeywordErrdefer();

  @Nullable
  PsiElement getKeywordNosuspend();

  @Nullable
  PsiElement getKeywordSuspend();

}
