// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ZigSwitchProng extends PsiElement {

  @Nullable
  ZigExpr getExpr();

  @Nullable
  ZigPtrIndexPayload getPtrIndexPayload();

  @NotNull
  ZigSwitchCase getSwitchCase();

  @NotNull
  PsiElement getEqualrarrow();

  @Nullable
  PsiElement getKeywordInline();

}
