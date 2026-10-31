// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi.impl;

import com.falsepattern.zigbrains.zig.psi.ZigContainerDeclAuto;
import com.falsepattern.zigbrains.zig.psi.ZigContainerDeclType;
import com.falsepattern.zigbrains.zig.psi.ZigContainerMembers;
import com.falsepattern.zigbrains.zig.psi.ZigVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;

public class ZigContainerDeclAutoImpl extends ASTWrapperPsiElement implements ZigContainerDeclAuto {

  public ZigContainerDeclAutoImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitContainerDeclAuto(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public ZigContainerDeclType getContainerDeclType() {
    return findNotNullChildByClass(ZigContainerDeclType.class);
  }

  @Override
  @Nullable
  public ZigContainerMembers getContainerMembers() {
    return findChildByClass(ZigContainerMembers.class);
  }

  @Override
  @Nullable
  public PsiElement getContainerDocComment() {
    return findChildByType(CONTAINER_DOC_COMMENT);
  }

  @Override
  @NotNull
  public PsiElement getLbrace() {
    return findNotNullChildByType(LBRACE);
  }

  @Override
  @Nullable
  public PsiElement getRbrace() {
    return findChildByType(RBRACE);
  }

}
