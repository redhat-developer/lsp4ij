// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.DOC_COMMENT;
import static com.falsepattern.zigbrains.zig.psi.ZigTypes.KEYWORD_PUB;

public class ZigContainerDeclarationImpl extends ASTWrapperPsiElement implements ZigContainerDeclaration {

  public ZigContainerDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitContainerDeclaration(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigComptimeDecl getComptimeDecl() {
    return findChildByClass(ZigComptimeDecl.class);
  }

  @Override
  @Nullable
  public ZigDecl getDecl() {
    return findChildByClass(ZigDecl.class);
  }

  @Override
  @Nullable
  public ZigTestDecl getTestDecl() {
    return findChildByClass(ZigTestDecl.class);
  }

  @Override
  @Nullable
  public PsiElement getDocComment() {
    return findChildByType(DOC_COMMENT);
  }

  @Override
  @Nullable
  public PsiElement getKeywordPub() {
    return findChildByType(KEYWORD_PUB);
  }

}
