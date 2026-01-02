// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigParamDecl;
import com.falsepattern.zigbrains.zig.psi.ZigParamDeclList;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ZigParamDeclListImpl extends ASTWrapperPsiElement implements ZigParamDeclList {

  public ZigParamDeclListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitParamDeclList(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ZigParamDecl> getParamDeclList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigParamDecl.class);
  }

}
