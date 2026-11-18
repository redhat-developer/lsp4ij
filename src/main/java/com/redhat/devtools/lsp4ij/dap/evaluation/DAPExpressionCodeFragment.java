/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.evaluation;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug Adapter Protocol (DAP) expression code fragment
 * used in the "Evaluate expression" editor.
 * <p>
 * {@link #create} makes the fragment a file of its language as that language's parser definition creates it (a
 * {@code JSFile} for JavaScript, for example), since a language's PSI expects its own file class above it. This class
 * is the fragment only for a language without a parser definition.
 */
public class DAPExpressionCodeFragment extends PsiFileBase {

    private static final Key<DAPDebugProcess> DEBUG_PROCESS = Key.create("lsp4ij.dap.expressionCodeFragment.debugProcess");

    /**
     * Creates the code fragment of an expression in the given file type and language.
     *
     * @param physical true if the fragment is physical, as IntelliJ asks for the document of an expression editor.
     * @return a file of the language as its parser definition creates it, or else a {@link DAPExpressionCodeFragment}.
     */
    public static @NotNull PsiFile create(@NotNull CharSequence text,
                                          @NotNull FileType fileType,
                                          @NotNull Language language,
                                          @Nullable DAPDebugProcess debugProcess,
                                          @NotNull Project project,
                                          boolean physical) {
        if (LanguageParserDefinitions.INSTANCE.forLanguage(language) != null) {
            var file = new LightVirtualFile(fileName(fileType), fileType, text);
            file.setLanguage(language);
            // As PsiFileFactory.createFileFromText, with the language's own view provider (a template language has one).
            // As for IntelliJ's own code fragments, only a physical one sends PSI events.
            PsiFile psiFile = ((PsiFileFactoryImpl) PsiFileFactory.getInstance(project))
                    .trySetupPsiForFile(file, language, physical, false);
            if (psiFile != null) {
                psiFile.putUserData(DEBUG_PROCESS, debugProcess);
                return psiFile;
            }
        }
        return new DAPExpressionCodeFragment(text, fileType, language, debugProcess, project, physical);
    }

    /**
     * Returns the current DAP stack frame of the debug process an expression code fragment was created for.
     *
     * @return the current stack frame, or null if the file is no DAP expression code fragment or nothing is suspended.
     */
    public static @Nullable DAPStackFrame getCurrentDapStackFrame(@NotNull PsiFile file) {
        DAPDebugProcess debugProcess = file.getUserData(DEBUG_PROCESS);
        return debugProcess != null ? debugProcess.getCurrentDapStackFrame() : null;
    }

    private static @NotNull String fileName(@NotNull FileType fileType) {
        return "DAPExpressionCodeFragment." + fileType.getDefaultExtension();
    }

    private final @NotNull FileType fileType;
    private final @Nullable DAPDebugProcess debugProcess;
    private final boolean physical;

    public DAPExpressionCodeFragment(@NotNull CharSequence text,
                                     @NotNull FileType fileType,
                                     @NotNull Language language,
                                     @Nullable DAPDebugProcess debugProcess,
                                     @NotNull Project project) {
        this(text, fileType, language, debugProcess, project, false);
    }

    /**
     * Creates an expression code fragment, physical when an editor shows it: IntelliJ then highlights and inspects
     * it as a file, which it does only for a physical one.
     *
     * @param physical true if the fragment is physical, as IntelliJ asks for the document of an expression editor.
     */
    public DAPExpressionCodeFragment(@NotNull CharSequence text,
                                     @NotNull FileType fileType,
                                     @NotNull Language language,
                                     @Nullable DAPDebugProcess debugProcess,
                                     @NotNull Project project,
                                     boolean physical) {
        // As for IntelliJ's own code fragments, only a physical one sends PSI events
        super(new SingleRootFileViewProvider(PsiManager.getInstance(project),
                new LightVirtualFile(fileName(fileType), fileType, text),
                physical),
                language);
        this.fileType = fileType;
        this.debugProcess = debugProcess;
        this.physical = physical;
        putUserData(DEBUG_PROCESS, debugProcess);
        ((SingleRootFileViewProvider) getViewProvider()).forceCachedPsi(this);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return fileType;
    }

    @Override
    public boolean isPhysical() {
        return physical;
    }

    public @Nullable DAPStackFrame getCurrentDapStackFrame() {
        return debugProcess != null ? debugProcess.getCurrentDapStackFrame() : null;
    }
}