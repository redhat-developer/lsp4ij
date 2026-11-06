// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigPrimaryTypeExprImpl extends ZigExprImpl implements ZigPrimaryTypeExpr {

  public ZigPrimaryTypeExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitPrimaryTypeExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigContainerDecl getContainerDecl() {
    return findChildByClass(ZigContainerDecl.class);
  }

  @Override
  @Nullable
  public ZigErrorSetDecl getErrorSetDecl() {
    return findChildByClass(ZigErrorSetDecl.class);
  }

  @Override
  @Nullable
  public ZigExpr getExpr() {
    return findChildByClass(ZigExpr.class);
  }

  @Override
  @Nullable
  public ZigFnCallArguments getFnCallArguments() {
    return findChildByClass(ZigFnCallArguments.class);
  }

  @Override
  @Nullable
  public ZigFnProto getFnProto() {
    return findChildByClass(ZigFnProto.class);
  }

  @Override
  @Nullable
  public ZigInitList getInitList() {
    return findChildByClass(ZigInitList.class);
  }

  @Override
  @Nullable
  public ZigStringLiteral getStringLiteral() {
    return findChildByClass(ZigStringLiteral.class);
  }

  @Override
  @Nullable
  public PsiElement getBuiltinidentifier() {
    return findChildByType(BUILTINIDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getCharLiteral() {
    return findChildByType(CHAR_LITERAL);
  }

  @Override
  @Nullable
  public PsiElement getDot() {
    return findChildByType(DOT);
  }

  @Override
  @Nullable
  public PsiElement getFloat() {
    return findChildByType(FLOAT);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getInteger() {
    return findChildByType(INTEGER);
  }

  @Override
  @Nullable
  public PsiElement getKeywordAnyframe() {
    return findChildByType(KEYWORD_ANYFRAME);
  }

  @Override
  @Nullable
  public PsiElement getKeywordComptime() {
    return findChildByType(KEYWORD_COMPTIME);
  }

  @Override
  @Nullable
  public PsiElement getKeywordError() {
    return findChildByType(KEYWORD_ERROR);
  }

  @Override
  @Nullable
  public PsiElement getKeywordUnreachable() {
    return findChildByType(KEYWORD_UNREACHABLE);
  }

}
