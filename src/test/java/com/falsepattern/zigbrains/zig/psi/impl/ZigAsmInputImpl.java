// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigAsmClobbers;
import com.falsepattern.zigbrains.zig.psi.ZigAsmInput;
import com.falsepattern.zigbrains.zig.psi.ZigAsmInputList;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.COLON;

public class ZigAsmInputImpl extends ASTWrapperPsiElement implements ZigAsmInput {

  public ZigAsmInputImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitAsmInput(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigAsmClobbers getAsmClobbers() {
    return findChildByClass(ZigAsmClobbers.class);
  }

  @Override
  @NotNull
  public ZigAsmInputList getAsmInputList() {
    return findNotNullChildByClass(ZigAsmInputList.class);
  }

  @Override
  @NotNull
  public PsiElement getColon() {
    return findNotNullChildByType(COLON);
  }

}
