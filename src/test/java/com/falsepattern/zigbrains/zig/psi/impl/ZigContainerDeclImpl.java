// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigContainerDecl;
import com.falsepattern.zigbrains.zig.psi.ZigContainerDeclAuto;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.KEYWORD_EXTERN;
import static com.falsepattern.zigbrains.zig.psi.ZigTypes.KEYWORD_PACKED;

public class ZigContainerDeclImpl extends ASTWrapperPsiElement implements ZigContainerDecl {

  public ZigContainerDeclImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitContainerDecl(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public ZigContainerDeclAuto getContainerDeclAuto() {
    return findNotNullChildByClass(ZigContainerDeclAuto.class);
  }

  @Override
  @Nullable
  public PsiElement getKeywordExtern() {
    return findChildByType(KEYWORD_EXTERN);
  }

  @Override
  @Nullable
  public PsiElement getKeywordPacked() {
    return findChildByType(KEYWORD_PACKED);
  }

}
