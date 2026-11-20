// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ZigVarDeclExprStatement extends PsiElement {

  @Nullable
  ZigAssignOp getAssignOp();

  @NotNull
  List<ZigExpr> getExprList();

  @NotNull
  List<ZigVarDeclProto> getVarDeclProtoList();

  @Nullable
  PsiElement getEqual();

  @Nullable
  PsiElement getSemicolon();

}
