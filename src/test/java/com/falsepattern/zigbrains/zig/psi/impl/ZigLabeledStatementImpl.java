// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZigLabeledStatementImpl extends ASTWrapperPsiElement implements ZigLabeledStatement {

  public ZigLabeledStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitLabeledStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigBlock getBlock() {
    return findChildByClass(ZigBlock.class);
  }

  @Override
  @Nullable
  public ZigBlockLabel getBlockLabel() {
    return findChildByClass(ZigBlockLabel.class);
  }

  @Override
  @Nullable
  public ZigLoopStatement getLoopStatement() {
    return findChildByClass(ZigLoopStatement.class);
  }

  @Override
  @Nullable
  public ZigSwitchExpr getSwitchExpr() {
    return findChildByClass(ZigSwitchExpr.class);
  }

}
