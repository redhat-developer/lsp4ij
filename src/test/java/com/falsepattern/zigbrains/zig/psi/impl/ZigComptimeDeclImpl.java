// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigBlock;
import com.falsepattern.zigbrains.zig.psi.ZigComptimeDecl;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.KEYWORD_COMPTIME;

public class ZigComptimeDeclImpl extends ASTWrapperPsiElement implements ZigComptimeDecl {

  public ZigComptimeDeclImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitComptimeDecl(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public ZigBlock getBlock() {
    return findNotNullChildByClass(ZigBlock.class);
  }

  @Override
  @NotNull
  public PsiElement getKeywordComptime() {
    return findNotNullChildByType(KEYWORD_COMPTIME);
  }

}
