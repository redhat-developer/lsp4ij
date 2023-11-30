/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.diagnostics;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.HashMap;
import java.util.Map;

public class LSPPSiElement implements PsiElement {
    private final Project project;
    private final PsiFile file;
    private final int start;
    private final int end;
    private String text;
    private Map<Key, Object> userData = new HashMap<>();
    private Map<Key, Object> copyableUserData = new HashMap<>();
    private final PsiReference reference = new LSPPsiReference(this);

    public LSPPSiElement(Project project, PsiFile file, int start, int end, String text) {
        this.project = project;
        this.file = file;
        this.start = start;
        this.end = end;
        this.text = text;
    }

    @NotNull
    @Override
    public Project getProject() throws PsiInvalidElementAccessException {
        return project;
    }

    @NotNull
    @Override
    public Language getLanguage() {
        return PlainTextLanguage.INSTANCE;
    }

    @Override
    public PsiManager getManager() {
        return PsiManager.getInstance(project);
    }

    @NotNull
    @Override
    public PsiElement[] getChildren() {
        return new PsiElement[0];
    }

    @Override
    public PsiElement getParent() {
        return getContainingFile();
    }

    @Override
    public PsiElement getFirstChild() {
        return null;
    }

    @Override
    public PsiElement getLastChild() {
        return null;
    }

    @Override
    public PsiElement getNextSibling() {
        return null;
    }

    @Override
    public PsiElement getPrevSibling() {
        return null;
    }

    @Override
    public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
        return file;
    }

    @Override
    public TextRange getTextRange() {
        return new TextRange(start, end);
    }

    @Override
    public int getStartOffsetInParent() {
        return start;
    }

    @Override
    public int getTextLength() {
        return end - start;
    }

    @Nullable
    @Override
    public PsiElement findElementAt(int offset) {
        return null;
    }

    @Nullable
    @Override
    public PsiReference findReferenceAt(int offset) {
        return null;
    }

    @Override
    public int getTextOffset() {
        return start;
    }

    @Override
    public String getText() {
        return text;
    }

    @NotNull
    @Override
    public char[] textToCharArray() {
        return getText().toCharArray();
    }

    @Override
    public PsiElement getNavigationElement() {
        return this;
    }

    @Override
    public PsiElement getOriginalElement() {
        return this;
    }

    @Override
    public boolean textMatches(@NotNull CharSequence text) {
        return text.equals(this.text);
    }

    @Override
    public boolean textMatches(@NotNull PsiElement element) {
        return getText().equals(element.getText());
    }

    @Override
    public boolean textContains(char c) {
        return getText().indexOf(c) != -1;
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
        visitor.visitElement(this);

    }

    @Override
    public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    }

    @Override
    public PsiElement copy() {
        return null;
    }

    @Override
    public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public PsiElement addBefore(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public PsiElement addAfter(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public void delete() throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public void checkDelete() throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Nullable
    @Override
    public PsiReference getReference() {
        return reference;
    }

    @NotNull
    @Override
    public PsiReference[] getReferences() {
        return new PsiReference[] { reference };
    }

    @Nullable
    @Override
    public <T> T getCopyableUserData(Key<T> key) {
        return (T) copyableUserData.get(key);
    }

    @Override
    public <T> void putCopyableUserData(Key<T> key, @Nullable T value) {
        copyableUserData.put(key, value);
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, @Nullable PsiElement lastParent, @NotNull PsiElement place) {
        return false;
    }

    @Nullable
    @Override
    public PsiElement getContext() {
        return null;
    }

    @Override
    public boolean isPhysical() {
        return true;
    }

    @NotNull
    @Override
    public GlobalSearchScope getResolveScope() {
        return getContainingFile().getResolveScope();
    }

    @NotNull
    @Override
    public SearchScope getUseScope() {
        return getContainingFile().getUseScope();
    }

    @Override
    public ASTNode getNode() {
        return null;
    }

    @Override
    public boolean isEquivalentTo(PsiElement another) {
        return this == another;
    }

    @Override
    public Icon getIcon(int flags) {
        return null;
    }

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
        return (T) userData.get(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
        userData.put(key, value);
    }
}
