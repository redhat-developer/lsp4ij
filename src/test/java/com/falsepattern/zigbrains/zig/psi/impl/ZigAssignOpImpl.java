// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigAssignOp;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigAssignOpImpl extends ASTWrapperPsiElement implements ZigAssignOp {

  public ZigAssignOpImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitAssignOp(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getAmpersandequal() {
    return findChildByType(AMPERSANDEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getAsteriskequal() {
    return findChildByType(ASTERISKEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getAsteriskpercentequal() {
    return findChildByType(ASTERISKPERCENTEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getAsteriskpipeequal() {
    return findChildByType(ASTERISKPIPEEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getCaretequal() {
    return findChildByType(CARETEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getEqual() {
    return findChildByType(EQUAL);
  }

  @Override
  @Nullable
  public PsiElement getLarrow2Equal() {
    return findChildByType(LARROW2EQUAL);
  }

  @Override
  @Nullable
  public PsiElement getLarrow2Pipeequal() {
    return findChildByType(LARROW2PIPEEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getMinusequal() {
    return findChildByType(MINUSEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getMinuspercentequal() {
    return findChildByType(MINUSPERCENTEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getMinuspipeequal() {
    return findChildByType(MINUSPIPEEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getPercentequal() {
    return findChildByType(PERCENTEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getPipeequal() {
    return findChildByType(PIPEEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getPlusequal() {
    return findChildByType(PLUSEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getPluspercentequal() {
    return findChildByType(PLUSPERCENTEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getPluspipeequal() {
    return findChildByType(PLUSPIPEEQUAL);
  }

  @Override
  @Nullable
  public PsiElement getRarrow2Equal() {
    return findChildByType(RARROW2EQUAL);
  }

  @Override
  @Nullable
  public PsiElement getSlashequal() {
    return findChildByType(SLASHEQUAL);
  }

}
