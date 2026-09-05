// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigBlockExpr;
import com.falsepattern.zigbrains.zig.psi.ZigComptimeStatement;
import com.falsepattern.zigbrains.zig.psi.ZigVarDeclExprStatement;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZigComptimeStatementImpl extends ASTWrapperPsiElement implements ZigComptimeStatement {

  public ZigComptimeStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitComptimeStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigBlockExpr getBlockExpr() {
    return findChildByClass(ZigBlockExpr.class);
  }

  @Override
  @Nullable
  public ZigVarDeclExprStatement getVarDeclExprStatement() {
    return findChildByClass(ZigVarDeclExprStatement.class);
  }

}
