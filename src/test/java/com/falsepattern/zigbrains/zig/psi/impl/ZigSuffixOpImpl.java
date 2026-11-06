// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigExpr;
import com.falsepattern.zigbrains.zig.psi.ZigSuffixOp;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigSuffixOpImpl extends ASTWrapperPsiElement implements ZigSuffixOp {

  public ZigSuffixOpImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitSuffixOp(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ZigExpr> getExprList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigExpr.class);
  }

  @Override
  @Nullable
  public PsiElement getColon() {
    return findChildByType(COLON);
  }

  @Override
  @Nullable
  public PsiElement getDot() {
    return findChildByType(DOT);
  }

  @Override
  @Nullable
  public PsiElement getDot2() {
    return findChildByType(DOT2);
  }

  @Override
  @Nullable
  public PsiElement getDotasterisk() {
    return findChildByType(DOTASTERISK);
  }

  @Override
  @Nullable
  public PsiElement getDotquestionmark() {
    return findChildByType(DOTQUESTIONMARK);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getLbracket() {
    return findChildByType(LBRACKET);
  }

  @Override
  @Nullable
  public PsiElement getRbracket() {
    return findChildByType(RBRACKET);
  }

}
