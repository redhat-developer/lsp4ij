// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.EQUAL;
import static com.falsepattern.zigbrains.zig.psi.ZigTypes.SEMICOLON;

public class ZigVarDeclExprStatementImpl extends ASTWrapperPsiElement implements ZigVarDeclExprStatement {

  public ZigVarDeclExprStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitVarDeclExprStatement(this);
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

  @Override
  @NotNull
  public List<ZigVarDeclProto> getVarDeclProtoList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigVarDeclProto.class);
  }

  @Override
  @Nullable
  public PsiElement getEqual() {
    return findChildByType(EQUAL);
  }

  @Override
  @Nullable
  public PsiElement getSemicolon() {
    return findChildByType(SEMICOLON);
  }

}
