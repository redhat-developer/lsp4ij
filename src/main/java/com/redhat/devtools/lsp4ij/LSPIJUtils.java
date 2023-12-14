/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.light.LightRecordField;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utilities class for LSP.
 */
public class LSPIJUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(LSPIJUtils.class);

    private static final String JAR_PROTOCOL = "jar";

    private static final String JRT_PROTOCOL = "jrt";

    private static final String JAR_SCHEME = JAR_PROTOCOL + ":";

    private static final String JRT_SCHEME = JRT_PROTOCOL + ":";

    public static void openInEditor(Location location, Project project) {
        if (location == null) {
            return;
        }
        openInEditor(location.getUri(), location.getRange().getStart(), project);
    }

    public static void openInEditor(String fileUri, Position position, Project project) {
        VirtualFile file = findResourceFor(fileUri);
        openInEditor(file, position, project);
    }

    public static void openInEditor(VirtualFile file, Position position, Project project) {
        if (file != null) {
            if (position == null) {
                FileEditorManager.getInstance(project).openFile(file, true);
            } else {
                Document document = FileDocumentManager.getInstance().getDocument(file);
                if (document != null) {
                    OpenFileDescriptor desc = new OpenFileDescriptor(project, file, LSPIJUtils.toOffset(position, document));
                    FileEditorManager.getInstance(project).openTextEditor(desc, true);
                }
            }
        }
    }

    /**
     * Returns the file language of the given file and null otherwise.
     *
     * @param file    the file.
     * @param project the project.
     * @return the file language of the given file and null otherwise.
     */
    @Nullable
    public static Language getFileLanguage(@NotNull VirtualFile file, @NotNull Project project) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return doGetFileLanguage(file, project);
        }
        return ReadAction.compute(() -> doGetFileLanguage(file, project));
    }

    @Nullable
    private static Language doGetFileLanguage(@NotNull VirtualFile file, @NotNull Project project) {
        return LanguageUtil.getLanguageForPsi(project, file);
    }

    private static <T extends TextDocumentPositionParams> T toTextDocumentPositionParamsCommon(T param, int offset, Document document) {
        Position start = toPosition(offset, document);
        param.setPosition(start);
        TextDocumentIdentifier id = new TextDocumentIdentifier();
        URI uri = toUri(document);
        if (uri != null) {
            id.setUri(uri.toASCIIString());
        }
        param.setTextDocument(id);
        return param;
    }

    public static HoverParams toHoverParams(int offset, Document document) {
        return toTextDocumentPositionParamsCommon(new HoverParams(), offset, document);
    }


    /**
     * Returns the Uri of the virtual file corresponding to the specified document.
     *
     * @param document the document for which the virtual file is requested.
     * @return the Uri of the file, or null if the document wasn't created from a virtual file.
     */
    public static @Nullable URI toUri(@NotNull Document document) {
        VirtualFile file = getFile(document);
        return file != null ? toUri(file) : null;
    }

    public static @NotNull URI toUri(@NotNull File file) {
        // URI scheme specified by language server protocol and LSP
        try {
            return new URI("file", "", file.getAbsoluteFile().toURI().getPath(), null); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (URISyntaxException e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
            return file.getAbsoluteFile().toURI();
        }
    }

    public static @Nullable URI toUri(@NotNull PsiFile psiFile) {
        VirtualFile file = getFile(psiFile);
        return file != null ? toUri(file) : null;
    }

    public static @NotNull URI toUri(@NotNull VirtualFile file) {
        return toUri(VfsUtilCore.virtualToIoFile(file));
    }

    public static @Nullable String toUriAsString(@NotNull PsiFile psFile) {
        VirtualFile file = psFile.getVirtualFile();
        return file != null ? toUriAsString(file) : null;
    }

    public static @NotNull String toUriAsString(@NotNull VirtualFile file) {
        String protocol = file.getFileSystem() != null ? file.getFileSystem().getProtocol() : null;
        if (JAR_PROTOCOL.equals(protocol) || JRT_PROTOCOL.equals(protocol)) {
            return VfsUtilCore.convertToURL(file.getUrl()).toExternalForm();
        }
        return toUri(VfsUtilCore.virtualToIoFile(file)).toASCIIString();
    }

    /**
     * Returns the virtual file corresponding to the specified document.
     *
     * @param document the document for which the virtual file is requested.
     * @return the file, or null if the document wasn't created from a virtual file.
     */
    public static @Nullable VirtualFile getFile(@NotNull Document document) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return FileDocumentManager.getInstance().getFile(document);
        }
        return ReadAction.compute(() -> FileDocumentManager.getInstance().getFile(document));
    }

    /**
     * Returns the virtual file corresponding to the PSI file.
     *
     * @return the virtual file, or {@code null} if the file exists only in memory.
     */
    public static @Nullable VirtualFile getFile(@NotNull PsiElement element) {
        PsiFile psFile = element.getContainingFile();
        return psFile != null ? psFile.getVirtualFile() : null;
    }

    public static @Nullable Document getDocument(@NotNull VirtualFile file) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return FileDocumentManager.getInstance().getDocument(file);
        }
        return ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
    }

    /**
     * Returns the @{@link Document} associated to the given @{@link URI}, or <code>null</code> if there's no match.
     *
     * @param documentUri the uri of the Document to return
     * @return the @{@link Document} associated to <code>documentUri</code>, or <code>null</code>
     */
    public static @Nullable Document getDocument(URI documentUri) {
        if (documentUri == null) {
            return null;
        }
        VirtualFile documentFile = findResourceFor(documentUri.toASCIIString());
        return getDocument(documentFile);
    }

    @Nullable
    public static Module getModule(@Nullable VirtualFile file, @NotNull Project project) {
        if (file == null) {
            return null;
        }
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return ProjectFileIndex.getInstance(project).getModuleForFile(file, false);
        }
        return ReadAction.compute(() -> ProjectFileIndex.getInstance(project).getModuleForFile(file, false));
    }

    public static int toOffset(Position start, Document document) throws IndexOutOfBoundsException {
        int lineStartOffset = document.getLineStartOffset(start.getLine());
        return lineStartOffset + start.getCharacter();
    }

    public static Position toPosition(int offset, Document document) {
        int line = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(line);
        String lineTextBeforeOffset = document.getText(new TextRange(lineStart, offset));
        int column = lineTextBeforeOffset.length();
        return new Position(line, column);
    }

    @NotNull
    public static WorkspaceFolder toWorkspaceFolder(@NotNull Project project) {
        WorkspaceFolder folder = new WorkspaceFolder();
        folder.setUri(toUri(project).toASCIIString());
        folder.setName(project.getName());
        return folder;
    }

    public static URI toUri(Module module) {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        if (roots.length > 0) {
            return toUri(roots[0]);
        }
        File file = new File(module.getModuleFilePath()).getParentFile();
        return file.toURI();
    }

    public static URI toUri(Project project) {
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        if (roots.length > 0) {
            return toUri(roots[0]);
        }
        File file = new File(project.getProjectFilePath()).getParentFile();
        return file.toURI();
    }

    public static Range toRange(TextRange range, Document document) {
        return new Range(LSPIJUtils.toPosition(range.getStartOffset(), document), LSPIJUtils.toPosition(range.getEndOffset(), document));
    }

    /**
     * Returns the IJ {@link TextRange} from the given LSP range and null otherwise.
     *
     * @param range    the LSP range to conert.
     * @param document the document.
     * @return the IJ {@link TextRange} from the given LSP range and null otherwise.
     */
    public static @Nullable TextRange toTextRange(Range range, Document document) {
        try {
            final int start = LSPIJUtils.toOffset(range.getStart(), document);
            final int end = LSPIJUtils.toOffset(range.getEnd(), document);
            if (start >= end || end > document.getTextLength()) {
                // Language server reports invalid diagnostic, ignore it.
                return null;
            }
            return new TextRange(start, end);
        } catch (IndexOutOfBoundsException e) {
            // Language server reports invalid diagnostic, ignore it.
            LOGGER.warn("Invalid LSP text range", e);
            return null;
        }
    }

    public static Location toLocation(PsiElement psiMember) {
        PsiElement sourceElement = getNavigationElement(psiMember);

        if (sourceElement != null) {
            PsiFile file = sourceElement.getContainingFile();
            Document document = PsiDocumentManager.getInstance(psiMember.getProject()).getDocument(file);
            if (document != null) {
                TextRange range = sourceElement.getTextRange();
                return toLocation(file, toRange(range, document));
            }
        }
        return null;
    }

    private static @Nullable PsiElement getNavigationElement(PsiElement psiMember) {
        //FIXME LightRecordField depends on the com.intellij.java plugin
        if (psiMember instanceof LightRecordField) {
            psiMember = ((LightRecordField) psiMember).getRecordComponent();
        }
        return psiMember.getNavigationElement();
    }

    public static Location toLocation(PsiFile file, Range range) {
        return toLocation(file.getVirtualFile(), range);
    }

    public static Location toLocation(VirtualFile file, Range range) {
        return new Location(toUriAsString(file), range);
    }

    public static void applyWorkspaceEdit(WorkspaceEdit edit) {
        applyWorkspaceEdit(edit, null);
    }

    public static void applyWorkspaceEdit(WorkspaceEdit edit, String label) {
        if (edit.getDocumentChanges() != null) {
            for (Either<TextDocumentEdit, ResourceOperation> change : edit.getDocumentChanges()) {
                if (change.isLeft()) {
                    VirtualFile file = findResourceFor(change.getLeft().getTextDocument().getUri());
                    if (file != null) {
                        Document document = getDocument(file);
                        if (document != null) {
                            applyWorkspaceEdit(document, change.getLeft().getEdits());
                        }
                    }
                } else if (change.isRight()) {
                    ResourceOperation resourceOperation = change.getRight();
                    if (resourceOperation instanceof CreateFile) {
                        CreateFile createOperation = (CreateFile) resourceOperation;
                        VirtualFile targetFile = findResourceFor(createOperation.getUri());
                        if (targetFile != null && createOperation.getOptions() != null) {
                            if (!createOperation.getOptions().getIgnoreIfExists()) {
                                Document document = getDocument(targetFile);
                                if (document != null) {
                                    TextEdit textEdit = new TextEdit(new Range(toPosition(0, document), toPosition(document.getTextLength(), document)), "");
                                    applyWorkspaceEdit(document, Collections.singletonList(textEdit));
                                }
                            }
                        } else {
                            try {
                                String fileUri = createOperation.getUri();
                                createFile(fileUri);
                            } catch (IOException e) {
                                LOGGER.warn(e.getLocalizedMessage(), e);
                            }
                        }
                    } else if (resourceOperation instanceof DeleteFile) {
                        try {
                            VirtualFile resource = findResourceFor(((DeleteFile) resourceOperation).getUri());
                            if (resource != null) {
                                resource.delete(null);
                            }
                        } catch (IOException e) {
                            LOGGER.warn(e.getLocalizedMessage(), e);
                        }
                    }
                }
            }
        } else if (edit.getChanges() != null) {
            for (Map.Entry<String, List<TextEdit>> change : edit.getChanges().entrySet()) {
                VirtualFile file = findResourceFor(change.getKey());
                if (file != null) {
                    Document document = getDocument(file);
                    if (document != null) {
                        applyWorkspaceEdit(document, change.getValue());
                    }
                }

            }

        }
    }

    /**
     * Create the file with the given file Uri.
     *
     * @param fileUri the file Uri.
     * @return the created virtual file and null otherwise.
     * @throws IOException
     */
    public static @Nullable VirtualFile createFile(String fileUri) throws IOException {
        URI targetURI = URI.create(fileUri);
        return createFile(targetURI);
    }

    /**
     * Create the file with the given file Uri.
     *
     * @param fileUri the file Uri.
     * @return the created virtual file and null otherwise.
     * @throws IOException
     */
    public static @Nullable VirtualFile createFile(URI fileUri) throws IOException {
        File newFile = new File(fileUri);
        FileUtils.createParentDirectories(newFile);
        newFile.createNewFile();
        return VfsUtil.findFileByIoFile(newFile, true);
    }

    private static void applyWorkspaceEdit(Document document, List<TextEdit> edits) {
        for (TextEdit edit : edits) {
            if (edit.getRange() != null) {
                String text = edit.getNewText();
                int start = toOffset(edit.getRange().getStart(), document);
                int end = toOffset(edit.getRange().getEnd(), document);
                if (StringUtils.isEmpty(text)) {
                    document.deleteString(start, end);
                } else {
                    text = text.replaceAll("\r", "");
                    if (end >= 0) {
                        if (end - start <= 0) {
                            document.insertString(start, text);
                        } else {
                            document.replaceString(start, end, text);
                        }
                    } else if (start == 0) {
                        document.setText(text);
                    } else if (start > 0) {
                        document.insertString(start, text);
                    }
                }
            }
        }
    }

    public static @Nullable VirtualFile findResourceFor(URI uri) {
        return LocalFileSystem.getInstance().findFileByIoFile(Paths.get(uri).toFile());
    }

    public static @Nullable VirtualFile findResourceFor(String uri) {
        if (uri.startsWith(JAR_SCHEME) || uri.startsWith(JRT_SCHEME)) {
            // ex : jar:file:///C:/Users/azerr/.m2/repository/io/quarkus/quarkus-core/3.0.1.Final/quarkus-core-3.0.1.Final.jar!/io/quarkus/runtime/ApplicationConfig.class
            try {
                return VfsUtil.findFileByURL(new URL(uri));
            } catch (MalformedURLException e) {
                return null;
            }
        }
        return VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.fixURLforIDEA(uri));
    }

    public static @Nullable Editor editorForElement(@Nullable PsiElement element) {
        if (element != null && element.getContainingFile() != null && element.getContainingFile().getVirtualFile() != null) {
            return editorForFile(element.getContainingFile().getVirtualFile(), element.getProject());
        }
        return null;
    }

    private static @Nullable Editor editorForFile(@Nullable VirtualFile file, @NotNull Project project) {
        Editor[] editors = editorsForDocument(getDocument(file), project);
        return editors.length > 0 ? editors[0] : null;
    }

    private static @NotNull Editor[] editorsForDocument(@Nullable Document document, @Nullable Project project) {
        if (document == null) {
            return new Editor[0];
        }
        return EditorFactory.getInstance().getEditors(document, project);
    }

    public static CompletionParams toCompletionParams(URI fileUri, int offset, Document document) {
        Position start = toPosition(offset, document);
        CompletionParams param = new CompletionParams();
        param.setPosition(start);
        param.setTextDocument(toTextDocumentIdentifier(fileUri));
        return param;
    }

    public static TextDocumentIdentifier toTextDocumentIdentifier(final URI uri) {
        return new TextDocumentIdentifier(uri.toASCIIString());
    }

    public static void applyEdit(Editor editor, TextEdit textEdit, Document document) {
        RangeMarker marker = document.createRangeMarker(LSPIJUtils.toOffset(textEdit.getRange().getStart(), document), LSPIJUtils.toOffset(textEdit.getRange().getEnd(), document));
        marker.setGreedyToRight(true);
        int startOffset = marker.getStartOffset();
        int endOffset = marker.getEndOffset();
        String text = textEdit.getNewText();
        if (text != null) {
            text = text.replaceAll("\r", "");
        }
        if (text == null || text.isEmpty()) {
            document.deleteString(startOffset, endOffset);
        } else if (endOffset - startOffset <= 0) {
            document.insertString(startOffset, text);
        } else {
            document.replaceString(startOffset, endOffset, text);
        }
        if (text != null && !text.isEmpty()) {
            editor.getCaretModel().moveToOffset(marker.getEndOffset());
        }
        marker.dispose();
    }


    public static void applyEdits(Editor editor, Document document, List<TextEdit> edits) {
        ApplicationManager.getApplication().runWriteAction(() -> edits.forEach(edit -> applyEdit(editor, edit, document)));
    }

    public static boolean hasCapability(final Either<Boolean, ? extends Object> eitherCapability) {
        if (eitherCapability == null) {
            return false;
        }
        return eitherCapability.isRight() || (eitherCapability.isLeft() && eitherCapability.getLeft());
    }

    /**
     * Returns the project URI of the given project.
     *
     * @param project the project
     * @return the project URI of the given project.
     */
    public static String getProjectUri(Module project) {
        if (project == null) {
            return null;
        }
        return project.getName();
    }

    /**
     * Returns the project URI of the given project.
     *
     * @param project the project
     * @return the project URI of the given project.
     */
    public static String getProjectUri(Project project) {
        if (project == null) {
            return null;
        }
        return project.getName();
    }

}
