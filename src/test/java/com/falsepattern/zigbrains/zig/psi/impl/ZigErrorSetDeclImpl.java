// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigErrorSetDecl;
import com.falsepattern.zigbrains.zig.psi.ZigIdentifierList;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigErrorSetDeclImpl extends ASTWrapperPsiElement implements ZigErrorSetDecl {

  public ZigErrorSetDeclImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitErrorSetDecl(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public ZigIdentifierList getIdentifierList() {
    return findNotNullChildByClass(ZigIdentifierList.class);
  }

  @Override
  @NotNull
  public PsiElement getKeywordError() {
    return findNotNullChildByType(KEYWORD_ERROR);
  }

  @Override
  @NotNull
  public PsiElement getLbrace() {
    return findNotNullChildByType(LBRACE);
  }

  @Override
  @NotNull
  public PsiElement getRbrace() {
    return findNotNullChildByType(RBRACE);
  }

}
