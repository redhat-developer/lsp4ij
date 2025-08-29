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

public class ZigVarDeclProtoImpl extends ASTWrapperPsiElement implements ZigVarDeclProto {

  public ZigVarDeclProtoImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitVarDeclProto(this);
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
  public PsiElement getColon() {
    return findChildByType(COLON);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getKeywordConst() {
    return findChildByType(KEYWORD_CONST);
  }

  @Override
  @Nullable
  public PsiElement getKeywordVar() {
    return findChildByType(KEYWORD_VAR);
  }

}
