// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigCompareOp;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigCompareOpImpl extends ASTWrapperPsiElement implements ZigCompareOp {

  public ZigCompareOpImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitCompareOp(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getEqualequal() {
    return findChildByType(EQUALEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getExclamationmarkequal() {
    return findChildByType(EXCLAMATIONMARKEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getLarrow() {
    return findChildByType(LARROW);
  }

  @Override
  @Nullable
  public PsiElement getLarrowequal() {
    return findChildByType(LARROWEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getRarrow() {
    return findChildByType(RARROW);
  }

  @Override
  @Nullable
  public PsiElement getRarrowequal() {
    return findChildByType(RARROWEQUAL);
  }

}
