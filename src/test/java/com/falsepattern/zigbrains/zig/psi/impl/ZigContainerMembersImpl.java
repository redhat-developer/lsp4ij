// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigContainerDeclaration;
import com.falsepattern.zigbrains.zig.psi.ZigContainerField;
import com.falsepattern.zigbrains.zig.psi.ZigContainerMembers;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ZigContainerMembersImpl extends ASTWrapperPsiElement implements ZigContainerMembers {

  public ZigContainerMembersImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitContainerMembers(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ZigContainerDeclaration> getContainerDeclarationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigContainerDeclaration.class);
  }

  @Override
  @NotNull
  public List<ZigContainerField> getContainerFieldList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ZigContainerField.class);
  }

}
