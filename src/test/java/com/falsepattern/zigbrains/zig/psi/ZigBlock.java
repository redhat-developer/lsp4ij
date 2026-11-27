// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ZigBlock extends PsiElement {

  @NotNull
  List<ZigStatement> getStatementList();

  @NotNull
  PsiElement getLbrace();

  @Nullable
  PsiElement getRbrace();

}
