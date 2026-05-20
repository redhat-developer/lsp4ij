// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigFnProtoImpl extends ASTWrapperPsiElement implements ZigFnProto {

  public ZigFnProtoImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitFnProto(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigAddrSpace getAddrSpace() {
    return findChildByClass(ZigAddrSpace.class);
  }

  @Override
  @Nullable
  public ZigByteAlign getByteAlign() {
    return findChildByClass(ZigByteAlign.class);
  }

  @Override
  @Nullable
  public ZigCallConv getCallConv() {
    return findChildByClass(ZigCallConv.class);
  }

  @Override
  @Nullable
  public ZigExpr getExpr() {
    return findChildByClass(ZigExpr.class);
  }

  @Override
  @Nullable
  public ZigLinkSection getLinkSection() {
    return findChildByClass(ZigLinkSection.class);
  }

  @Override
  @Nullable
  public ZigParamDeclList getParamDeclList() {
    return findChildByClass(ZigParamDeclList.class);
  }

  @Override
  @Nullable
  public PsiElement getExclamationmark() {
    return findChildByType(EXCLAMATIONMARK);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

  @Override
  @NotNull
  public PsiElement getKeywordFn() {
    return findNotNullChildByType(KEYWORD_FN);
  }

  @Override
  @Nullable
  public PsiElement getLparen() {
    return findChildByType(LPAREN);
  }

  @Override
  @Nullable
  public PsiElement getRparen() {
    return findChildByType(RPAREN);
  }

}
