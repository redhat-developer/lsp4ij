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

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigPrefixTypeOpImpl extends ASTWrapperPsiElement implements ZigPrefixTypeOp {

  public ZigPrefixTypeOpImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitPrefixTypeOp(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ZigAddrSpace> getAddrSpaceList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigAddrSpace.class);
  }

  @Override
  @Nullable
  public ZigArrayTypeStart getArrayTypeStart() {
    return findChildByClass(ZigArrayTypeStart.class);
  }

  @Override
  @NotNull
  public List<ZigByteAlign> getByteAlignList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigByteAlign.class);
  }

  @Override
  @NotNull
  public List<ZigExpr> getExprList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigExpr.class);
  }

  @Override
  @Nullable
  public ZigPtrTypeStart getPtrTypeStart() {
    return findChildByClass(ZigPtrTypeStart.class);
  }

  @Override
  @Nullable
  public ZigSliceTypeStart getSliceTypeStart() {
    return findChildByClass(ZigSliceTypeStart.class);
  }

  @Override
  @Nullable
  public PsiElement getKeywordAnyframe() {
    return findChildByType(KEYWORD_ANYFRAME);
  }

  @Override
  @Nullable
  public PsiElement getMinusrarrow() {
    return findChildByType(MINUSRARROW);
  }

  @Override
  @Nullable
  public PsiElement getQuestionmark() {
    return findChildByType(QUESTIONMARK);
  }

}
