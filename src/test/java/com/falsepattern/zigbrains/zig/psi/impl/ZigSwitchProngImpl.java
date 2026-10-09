// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.EQUALRARROW;
import static com.falsepattern.zigbrains.zig.psi.ZigTypes.KEYWORD_INLINE;

public class ZigSwitchProngImpl extends ASTWrapperPsiElement implements ZigSwitchProng {

  public ZigSwitchProngImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitSwitchProng(this);
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
  @Nullable
  public ZigPtrIndexPayload getPtrIndexPayload() {
    return findChildByClass(ZigPtrIndexPayload.class);
  }

  @Override
  @NotNull
  public ZigSwitchCase getSwitchCase() {
    return findNotNullChildByClass(ZigSwitchCase.class);
  }

  @Override
  @NotNull
  public PsiElement getEqualrarrow() {
    return findNotNullChildByType(EQUALRARROW);
  }

  @Override
  @Nullable
  public PsiElement getKeywordInline() {
    return findChildByType(KEYWORD_INLINE);
  }

}
