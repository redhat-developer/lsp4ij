// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ZigSuffixExpr extends ZigExpr {

  @NotNull
  ZigExpr getExpr();

  @NotNull
  List<ZigFnCallArguments> getFnCallArgumentsList();

  @NotNull
  List<ZigSuffixOp> getSuffixOpList();

  @Nullable
  PsiElement getKeywordAsync();

}
