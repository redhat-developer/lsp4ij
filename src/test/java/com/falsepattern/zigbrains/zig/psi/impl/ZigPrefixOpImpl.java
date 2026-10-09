// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigPrefixOp;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigPrefixOpImpl extends ASTWrapperPsiElement implements ZigPrefixOp {

  public ZigPrefixOpImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitPrefixOp(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getAmpersand() {
    return findChildByType(AMPERSAND);
  }

  @Override
  @Nullable
  public PsiElement getExclamationmark() {
    return findChildByType(EXCLAMATIONMARK);
  }

  @Override
  @Nullable
  public PsiElement getKeywordAwait() {
    return findChildByType(KEYWORD_AWAIT);
  }

  @Override
  @Nullable
  public PsiElement getKeywordTry() {
    return findChildByType(KEYWORD_TRY);
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
  public PsiElement getTilde() {
    return findChildByType(TILDE);
  }

}
