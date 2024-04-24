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
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
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

    /**
     * Open the LSP location in an editor.
     *
     * @param location the LSP location.
     * @param project  the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@Nullable Location location,
                                       @NotNull Project project) {
        if (location == null) {
            return false;
        }
        return openInEditor(location.getUri(), location.getRange() != null ? location.getRange().getStart() : null, project);
    }

    /**
     * Open the given fileUri with the given position in an editor.
     *
     * @param fileUri  the file Uri.
     * @param position the position.
     * @param project  the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@NotNull String fileUri,
                                       @Nullable Position position,
                                       @NotNull Project project) {
        return openInEditor(fileUri, position, true, project);
    }

    /**
     * Open the given fileUri with the given position in an editor.
     *
     * @param fileUri     the file Uri.
     * @param position    the position.
     * @param focusEditor true if editor will take the focus and false otherwise.
     * @param project     the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@NotNull String fileUri,
                                       @Nullable Position position,
                                       boolean focusEditor,
                                       @NotNull Project project) {
        VirtualFile file = findResourceFor(fileUri);
        return openInEditor(file, position, focusEditor, project);
    }

    /**
     * Open the given file with the given position in an editor.
     *
     * @param file     the file.
     * @param position the position.
     * @param project  the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@Nullable VirtualFile file,
                                       @Nullable Position position,
                                       @NotNull Project project) {
        return openInEditor(file, position, true, project);
    }

    /**
     * Open the given file with the given position in an editor.
     *
     * @param file        the file.
     * @param position    the position.
     * @param focusEditor true if editor will take the focus and false otherwise.
     * @param project     the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@Nullable VirtualFile file,
                                       @Nullable Position position,
                                       boolean focusEditor,
                                       @NotNull Project project) {
        if (file != null) {
            if (position == null) {
                return FileEditorManager.getInstance(project).openFile(file, true).length > 0;
            } else {
                Document document = FileDocumentManager.getInstance().getDocument(file);
                if (document != null) {
                    OpenFileDescriptor desc = new OpenFileDescriptor(project, file, LSPIJUtils.toOffset(position, document));
                    return FileEditorManager.getInstance(project).openTextEditor(desc, focusEditor) != null;
                }
            }
        }
        return false;
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
     * Returns the Psi file corresponding to the virtual file in the given project.
     *
     * @param file    the virtual file.
     * @param project the project.
     * @return the Psi file corresponding to the virtual file in the given project.
     */
    public static @Nullable PsiFile getPsiFile(@NotNull VirtualFile file, @NotNull Project project) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return PsiManager.getInstance(project).findFile(file);
        }
        return ReadAction.compute(() -> PsiManager.getInstance(project).findFile(file));
    }


    /**
     * Returns the virtual file corresponding to the Psi file.
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

    /**
     * Returns a valid offset from the given position in the given document even if position is invalid.
     *
     * <ul>
     *     <li>If a line number is negative, it defaults to 0.</li>
     *     <li>If a line number is greater than the number of lines in a document, it defaults back to the number of lines in the document.</li>
     *     <li>If the character value is greater than the line length it defaults back to the line length</li>
     * </ul>
     *
     * @param position the LSP position.
     * @param document the IJ document.
     * @return a valid offset from the given position in the given document.
     */
    public static int toOffset(@NotNull Position position, @NotNull Document document) {
        // See https://github.com/microsoft/vscode-languageserver-node/blob/8e625564b531da607859b8cb982abb7cdb2fbe2e/textDocument/src/main.ts#L304

        // Adjust position line/character according to this comment https://github.com/microsoft/vscode-languageserver-node/blob/ed3cd0f78c1495913bda7318ace2be7f968008af/textDocument/src/main.ts#L26
        int line = position.getLine();
        if (line >= document.getLineCount()) {
            // The line number is greater than the number of lines in a document, it defaults back to the number of lines in the document.
            return document.getTextLength();
        } else if (line < 0) {
            // The line number is negative, it defaults to 0.
            return 0;
        }
        int lineOffset = document.getLineStartOffset(line);
        int nextLineOffset = document.getLineEndOffset(line);
        // If the character value is greater than the line length it defaults back to the line length
        return Math.max(Math.min(lineOffset + position.getCharacter(), nextLineOffset), lineOffset);
    }

    /**
     * Returns the LSP position from the given offset in the given document.
     *
     * @param offset   the offset.
     * @param document the document.
     * @return the LSP position from the given offset in the given document.
     */
    @NotNull
    public static Position toPosition(int offset, @NotNull Document document) {
        // Adjust offset
        offset = Math.max(Math.min(offset, document.getTextLength()), 0);
        int line = document.getLineNumber(offset);
        int character = offset - document.getLineStartOffset(line);
        return new Position(line, character);
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

    public static @Nullable TextRange toTextRange(Range range, Document document) {
        return toTextRange(range, document, false);
    }

    /**
     * Returns the IJ {@link TextRange} from the given LSP range and null otherwise.
     *
     * @param range    the LSP range to convert.
     * @param document the document.
     * @return the IJ {@link TextRange} from the given LSP range and null otherwise.
     */
    public static @Nullable TextRange toTextRange(Range range, Document document, boolean adjust) {
        try {
            int start = LSPIJUtils.toOffset(range.getStart(), document);
            int end = LSPIJUtils.toOffset(range.getEnd(), document);
            int docLength = document.getTextLength();
            if (start > end || end > docLength) {
                // Language server reports invalid diagnostic, ignore it.
                return null;
            }
            if (start != end) {
                return new TextRange(start, end);
            }
            if (!adjust) {
                // No adjustment, the TextRange with start/end offset is invalid
                return null;
            }
            // Select token at current offset, if possible
            TextRange tokenRange = getTokenRange(document, start);
            if (tokenRange != null) {
                return tokenRange;
            }
            // Adjust the end offset if the offset is not at the end of the line.
            if (!isEndOfLine(document, range.getEnd().getLine(), start)) {
                end++;
            }
            return new TextRange(start, end);
        } catch (IndexOutOfBoundsException e) {
            // Language server reports invalid diagnostic, ignore it.
            LOGGER.warn("Invalid LSP text range", e);
            return null;
        }
    }

    private static boolean isEndOfLine(@NotNull Document document, int line, int offset) {
        return offset == document.getLineEndOffset(line);
    }

    /**
     * Returns the token range from the document at given offset and null otherwise.
     *
     * <code><pre>
     *  - fo|o bar -> [foo]
     *  - fo|o.bar() -> [foo]
     *  - foo.b|ar() -> [bar]
     *  - foo.bar(|) -> null
     *  - foo  |  bar -> null
     * </pre></code>
     *
     * @param document
     * @param offset
     * @return the token range from the document at given offset and null otherwise.
     */
    @Nullable
    public static TextRange getTokenRange(Document document, int offset) {
        if (offset > 0 && offset >= document.getTextLength()) {
            offset = document.getTextLength() - 1;
        }
        int start = getLeftOffsetOfPart(document, offset);
        int end = getRightOffsetOfPart(document, offset);
        return (start < end) ? new TextRange(start, end) : null;
    }

    private static int getLeftOffsetOfPart(Document document, int offset) {
        for (int i = offset - 1; i >= 0; i--) {
            char c = document.getCharsSequence().charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static int getRightOffsetOfPart(Document document, int offset) {
        for (int i = offset; i < document.getTextLength(); i++) {
            char c = document.getCharsSequence().charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                return i;
            }
        }
        return document.getTextLength();
    }

    public static void applyWorkspaceEdit(@NotNull WorkspaceEdit edit) {
        applyWorkspaceEdit(edit, null);
    }

    public static void applyWorkspaceEdit(@NotNull WorkspaceEdit edit,
                                          @Nullable String label) {
        if (edit.getDocumentChanges() != null) {
            for (Either<TextDocumentEdit, ResourceOperation> change : edit.getDocumentChanges()) {
                if (change.isLeft()) {
                    var textDocumentEdit = change.getLeft();
                    VirtualFile file = findResourceFor(textDocumentEdit.getTextDocument().getUri());
                    if (file != null) {
                        Document document = getDocument(file);
                        if (document != null) {
                            applyTextEdits(document, textDocumentEdit.getEdits());
                        }
                    }
                } else if (change.isRight()) {
                    ResourceOperation resourceOperation = change.getRight();
                    if (resourceOperation instanceof CreateFile createFile) {
                        applyCreateFile(createFile);
                    } else if (resourceOperation instanceof DeleteFile deleteFile) {
                        applyDeleteFile(deleteFile);
                    } else if (resourceOperation instanceof RenameFile renameFile) {
                        applyRenameFile(renameFile);
                    }
                }
            }
        } else if (edit.getChanges() != null) {
            for (Map.Entry<String, List<TextEdit>> change : edit.getChanges().entrySet()) {
                String fileUri = change.getKey();
                VirtualFile file = findResourceFor(fileUri);
                if (file == null) {
                    try {
                        file = createFile(fileUri);
                    } catch (Exception e) {
                        LOGGER.error("Cannot create file '" + fileUri + "'", e);
                    }
                }
                if (file != null) {
                    Document document = getDocument(file);
                    if (document != null) {
                        applyTextEdits(document, change.getValue());
                    }
                }
            }
        }
    }

    private static void applyTextEdits(Document document, List<TextEdit> edits) {
        edits.sort((b, a) -> {
            int diff = a.getRange().getStart().getLine() - b.getRange().getStart().getLine();
            if (diff == 0) {
                return a.getRange().getStart().getCharacter() - b.getRange().getStart().getCharacter();
            }
            return diff;
        });
        for (TextEdit edit : edits) {
            applyTextEdit(document, edit);
        }
    }

    private static void applyTextEdit(Document document, TextEdit textEdit) {
        Range range = textEdit.getRange();
        if (range == null) {
            return;
        }
        String text = textEdit.getNewText();
        int start = toOffset(range.getStart(), document);
        int end = toOffset(range.getEnd(), document);

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

    private static void applyCreateFile(CreateFile createFile) {
        VirtualFile targetFile = findResourceFor(createFile.getUri());
        if (targetFile != null && createFile.getOptions() != null) {
            if (!createFile.getOptions().getIgnoreIfExists()) {
                Document document = getDocument(targetFile);
                if (document != null) {
                    TextEdit textEdit = new TextEdit(new Range(toPosition(0, document), toPosition(document.getTextLength(), document)), "");
                    applyTextEdits(document, Collections.singletonList(textEdit));
                }
            }
        } else {
            try {
                String fileUri = createFile.getUri();
                createFile(fileUri);
            } catch (IOException e) {
                LOGGER.warn(e.getLocalizedMessage(), e);
            }
        }
    }

    private static void applyDeleteFile(DeleteFile deleteFile) {
        try {
            VirtualFile resource = findResourceFor(deleteFile.getUri());
            if (resource != null) {
                // TODO: use deleteFile.getOptions()
                resource.delete(null);
            }
        } catch (IOException e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
        }
    }

    private static void applyRenameFile(RenameFile renameFile) {
        // "documentChanges": [
        //  {
        //    "oldUri": "file://.../foo.clj",
        //    "newUri": "file://.../bar.clj",
        //    "kind": "rename"
        //  }
        //]
        try {
            VirtualFile resource = findResourceFor(renameFile.getOldUri());
            if (resource != null) {
                // TODO: use renameFile.getOptions()

                // The IJ rename works only with a file name which is hosted in the same directory
                // as the old file
                var path = Paths.get(URI.create(renameFile.getNewUri()));
                String newFileName = path.toFile().getName();
                resource.rename(null, newFileName);
            }
        } catch (IOException e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
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

    public static @Nullable VirtualFile findResourceFor(URI uri) {
        return LocalFileSystem.getInstance().findFileByIoFile(Paths.get(uri).toFile());
    }

    public static @Nullable VirtualFile findResourceFor(@NotNull String uri) {
        if (uri.startsWith(JAR_SCHEME) || uri.startsWith(JRT_SCHEME)) {
            // ex : jar:file:///C:/Users/azerr/.m2/repository/io/quarkus/quarkus-core/3.0.1.Final/quarkus-core-3.0.1.Final.jar!/io/quarkus/runtime/ApplicationConfig.class
            try {
                return VfsUtil.findFileByURL(new URL(uri));
            } catch (MalformedURLException e) {
                return null;
            }
        }
        if (uri.contains("%")) {
            try {
                // ex : file:///c%3A/Users/azerr/IdeaProjects/untitled7/test.js
                // the uri must be decoded (ex : file:///c:/Users) otherwise IntelliJ cannot retrieve the virtual file.
                uri = URLDecoder.decode(uri, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                // Do nothing
            }
        }
        return VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.fixURLforIDEA(uri));
    }

    /**
     * Returns the editor which is editing the given Psi element and null otherwise.
     *
     * @param element the Psi element.
     * @return the editor which is editing the given Psi element and null otherwise.
     */
    public static @Nullable Editor editorForElement(@Nullable PsiElement element) {
        VirtualFile file = element != null && element.getContainingFile() != null ? element.getContainingFile().getVirtualFile() : null;
        if (file != null) {
            return editorForFile(file, element.getProject());
        }
        return null;
    }

    /**
     * Returns the editors which are editing the given virtual file and an empty array otherwise.
     *
     * @param file    the virtual file.
     * @param project the project.
     * @return the editors which are editing the given virtual file and an empty array otherwise
     */
    public static @NotNull Editor[] editorsForFile(@Nullable VirtualFile file, @NotNull Project project) {
        return editorsForDocument(getDocument(file), project);
    }

    /**
     * Returns the first editor which is editing the given virtual file and an empty array otherwise.
     *
     * @param file    the virtual file.
     * @param project the project.
     * @return the first editor which is editing the given virtual file and an empty array otherwise
     */
    private static @Nullable Editor editorForFile(@Nullable VirtualFile file, @NotNull Project project) {
        Editor[] editors = editorsForFile(file, project);
        return editors.length > 0 ? editors[0] : null;
    }

    /**
     * Returns the editors which are editing the given document and an empty array otherwise.
     *
     * @param document the document.
     * @param project  the project.
     * @return the editors which are editing the given document and an empty array otherwise
     */
    private static @NotNull Editor[] editorsForDocument(@Nullable Document document, @Nullable Project project) {
        if (document == null) {
            return new Editor[0];
        }
        return EditorFactory.getInstance().getEditors(document, project);
    }

    public static TextDocumentIdentifier toTextDocumentIdentifier(VirtualFile file) {
        return toTextDocumentIdentifier(toUri(file));
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
        if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
            edits.forEach(edit -> applyEdit(editor, edit, document));
        } else {
            WriteAction.run(() -> edits.forEach(edit -> applyEdit(editor, edit, document)));
        }
    }

    /**
     * Get the tab size for the given editor.
     */
    public static int getTabSize(@NotNull Editor editor) {
        Project project = editor.getProject();
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return editor.getSettings().getTabSize(project);
        }
        return ReadAction.compute(() -> editor.getSettings().getTabSize(project));
    }

    public static boolean isInsertSpaces(@NotNull Editor editor) {
        Project project = editor.getProject();
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return !editor.getSettings().isUseTabCharacter(project);
        }
        return ReadAction.compute(() -> !editor.getSettings().isUseTabCharacter(project));
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
