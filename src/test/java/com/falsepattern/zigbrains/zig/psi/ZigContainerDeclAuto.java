// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ZigContainerDeclAuto extends PsiElement {

  @NotNull
  ZigContainerDeclType getContainerDeclType();

  @Nullable
  ZigContainerMembers getContainerMembers();

  @Nullable
  PsiElement getContainerDocComment();

  @NotNull
  PsiElement getLbrace();

  @Nullable
  PsiElement getRbrace();

}
