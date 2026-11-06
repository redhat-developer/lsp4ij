// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigExpr;
import com.falsepattern.zigbrains.zig.psi.ZigLoopExpr;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.KEYWORD_INLINE;

public class ZigLoopExprImpl extends ZigExprImpl implements ZigLoopExpr {

  public ZigLoopExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitLoopExpr(this);
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
  @Nullable
  public PsiElement getKeywordInline() {
    return findChildByType(KEYWORD_INLINE);
  }

}
