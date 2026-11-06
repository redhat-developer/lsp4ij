// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ZigPrefixTypeOp extends PsiElement {

  @NotNull
  List<ZigAddrSpace> getAddrSpaceList();

  @Nullable
  ZigArrayTypeStart getArrayTypeStart();

  @NotNull
  List<ZigByteAlign> getByteAlignList();

  @NotNull
  List<ZigExpr> getExprList();

  @Nullable
  ZigPtrTypeStart getPtrTypeStart();

  @Nullable
  ZigSliceTypeStart getSliceTypeStart();

  @Nullable
  PsiElement getKeywordAnyframe();

  @Nullable
  PsiElement getMinusrarrow();

  @Nullable
  PsiElement getQuestionmark();

}
