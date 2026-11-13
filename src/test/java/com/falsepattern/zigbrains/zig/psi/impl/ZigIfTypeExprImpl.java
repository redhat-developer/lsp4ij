// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.KEYWORD_ELSE;

public class ZigIfTypeExprImpl extends ZigExprImpl implements ZigIfTypeExpr {

  public ZigIfTypeExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitIfTypeExpr(this);
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
  @NotNull
  public ZigIfPrefix getIfPrefix() {
    return findNotNullChildByClass(ZigIfPrefix.class);
  }

  @Override
  @Nullable
  public ZigPayload getPayload() {
    return findChildByClass(ZigPayload.class);
  }

  @Override
  @Nullable
  public PsiElement getKeywordElse() {
    return findChildByType(KEYWORD_ELSE);
  }

}
