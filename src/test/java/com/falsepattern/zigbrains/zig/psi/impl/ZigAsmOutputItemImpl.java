// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigAsmOutputItem;
import com.falsepattern.zigbrains.zig.psi.ZigExpr;
import com.falsepattern.zigbrains.zig.psi.ZigStringLiteral;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigAsmOutputItemImpl extends ASTWrapperPsiElement implements ZigAsmOutputItem {

  public ZigAsmOutputItemImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitAsmOutputItem(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigExpr getExpr() {
    return findChildByClass(ZigExpr.class);
  }

  @Override
  @NotNull
  public ZigStringLiteral getStringLiteral() {
    return findNotNullChildByClass(ZigStringLiteral.class);
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
  @Nullable
  public PsiElement getMinusrarrow() {
    return findChildByType(MINUSRARROW);
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
