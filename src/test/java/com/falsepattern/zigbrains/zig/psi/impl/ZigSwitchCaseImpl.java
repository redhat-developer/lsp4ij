// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigSwitchCase;
import com.falsepattern.zigbrains.zig.psi.ZigSwitchItem;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.KEYWORD_ELSE;

public class ZigSwitchCaseImpl extends ASTWrapperPsiElement implements ZigSwitchCase {

  public ZigSwitchCaseImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitSwitchCase(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ZigSwitchItem> getSwitchItemList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigSwitchItem.class);
  }

  @Override
  @Nullable
  public PsiElement getKeywordElse() {
    return findChildByType(KEYWORD_ELSE);
  }

}
