// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigAsmInputItem;
import com.falsepattern.zigbrains.zig.psi.ZigExpr;
import com.falsepattern.zigbrains.zig.psi.ZigStringLiteral;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigAsmInputItemImpl extends ASTWrapperPsiElement implements ZigAsmInputItem {

  public ZigAsmInputItemImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitAsmInputItem(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public ZigExpr getExpr() {
    return findNotNullChildByClass(ZigExpr.class);
  }

  @Override
  @NotNull
  public ZigStringLiteral getStringLiteral() {
    return findNotNullChildByClass(ZigStringLiteral.class);
  }

  @Override
  @NotNull
  public PsiElement getIdentifier() {
    return findNotNullChildByType(IDENTIFIER);
  }

  @Override
  @NotNull
  public PsiElement getLbracket() {
    return findNotNullChildByType(LBRACKET);
  }

  @Override
  @NotNull
  public PsiElement getLparen() {
    return findNotNullChildByType(LPAREN);
  }

  @Override
  @NotNull
  public PsiElement getRbracket() {
    return findNotNullChildByType(RBRACKET);
  }

  @Override
  @NotNull
  public PsiElement getRparen() {
    return findNotNullChildByType(RPAREN);
  }

}
