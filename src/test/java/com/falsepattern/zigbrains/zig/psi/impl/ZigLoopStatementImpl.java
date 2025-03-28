// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigForStatement;
import com.falsepattern.zigbrains.zig.psi.ZigLoopStatement;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.falsepattern.zigbrains.zig.psi.ZigWhileStatement;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.KEYWORD_INLINE;

public class ZigLoopStatementImpl extends ASTWrapperPsiElement implements ZigLoopStatement {

  public ZigLoopStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitLoopStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigForStatement getForStatement() {
    return findChildByClass(ZigForStatement.class);
  }

  @Override
  @Nullable
  public ZigWhileStatement getWhileStatement() {
    return findChildByClass(ZigWhileStatement.class);
  }

  @Override
  @Nullable
  public PsiElement getKeywordInline() {
    return findChildByType(KEYWORD_INLINE);
  }

}
