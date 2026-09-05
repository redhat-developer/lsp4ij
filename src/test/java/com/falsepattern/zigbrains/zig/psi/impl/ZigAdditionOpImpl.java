// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigAdditionOp;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigAdditionOpImpl extends ASTWrapperPsiElement implements ZigAdditionOp {

  public ZigAdditionOpImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitAdditionOp(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getMinus() {
    return findChildByType(MINUS);
  }

  @Override
  @Nullable
  public PsiElement getMinuspercent() {
    return findChildByType(MINUSPERCENT);
  }

  @Override
  @Nullable
  public PsiElement getMinuspipe() {
    return findChildByType(MINUSPIPE);
  }

  @Override
  @Nullable
  public PsiElement getPlus() {
    return findChildByType(PLUS);
  }

  @Override
  @Nullable
  public PsiElement getPlus2() {
    return findChildByType(PLUS2);
  }

  @Override
  @Nullable
  public PsiElement getPluspercent() {
    return findChildByType(PLUSPERCENT);
  }

  @Override
  @Nullable
  public PsiElement getPluspipe() {
    return findChildByType(PLUSPIPE);
  }

}
