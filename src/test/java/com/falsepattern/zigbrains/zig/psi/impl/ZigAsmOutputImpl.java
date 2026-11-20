// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigAsmInput;
import com.falsepattern.zigbrains.zig.psi.ZigAsmOutput;
import com.falsepattern.zigbrains.zig.psi.ZigAsmOutputList;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.COLON;

public class ZigAsmOutputImpl extends ASTWrapperPsiElement implements ZigAsmOutput {

  public ZigAsmOutputImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitAsmOutput(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigAsmInput getAsmInput() {
    return findChildByClass(ZigAsmInput.class);
  }

  @Override
  @NotNull
  public ZigAsmOutputList getAsmOutputList() {
    return findNotNullChildByClass(ZigAsmOutputList.class);
  }

  @Override
  @NotNull
  public PsiElement getColon() {
    return findNotNullChildByType(COLON);
  }

}
