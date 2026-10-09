// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ZigAsmOutput extends PsiElement {

  @Nullable
  ZigAsmInput getAsmInput();

  @NotNull
  ZigAsmOutputList getAsmOutputList();

  @NotNull
  PsiElement getColon();

}
