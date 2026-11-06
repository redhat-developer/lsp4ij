// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigAssignOp;
import com.falsepattern.zigbrains.zig.psi.ZigExpr;
import com.falsepattern.zigbrains.zig.psi.ZigSingleAssignExpr;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ZigSingleAssignExprImpl extends ZigExprImpl implements ZigSingleAssignExpr {

  public ZigSingleAssignExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitSingleAssignExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigAssignOp getAssignOp() {
    return findChildByClass(ZigAssignOp.class);
  }

  @Override
  @NotNull
  public List<ZigExpr> getExprList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigExpr.class);
  }

}
