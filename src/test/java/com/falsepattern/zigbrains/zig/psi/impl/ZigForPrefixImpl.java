// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigForInput;
import com.falsepattern.zigbrains.zig.psi.ZigForPayload;
import com.falsepattern.zigbrains.zig.psi.ZigForPrefix;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigForPrefixImpl extends ASTWrapperPsiElement implements ZigForPrefix {

  public ZigForPrefixImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitForPrefix(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ZigForInput> getForInputList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigForInput.class);
  }

  @Override
  @Nullable
  public ZigForPayload getForPayload() {
    return findChildByClass(ZigForPayload.class);
  }

  @Override
  @NotNull
  public PsiElement getKeywordFor() {
    return findNotNullChildByType(KEYWORD_FOR);
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
