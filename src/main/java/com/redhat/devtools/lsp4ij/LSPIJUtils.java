/*******************************************************************************
 * Copyright (c) 2019-2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * FalsePattern - Order-independent doApplyEdits
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
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.internal.SimpleLanguageUtils;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utilities class for LSP.
 */
public class LSPIJUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPIJUtils.class);

    private static final Key<URI> DEFAULT_LSP_FILE_URI_KEY = Key.create("lsp.file.uri");

    private static final String JAR_PROTOCOL = "jar";

    private static final String JRT_PROTOCOL = "jrt";

    private static final String JAR_SCHEME = JAR_PROTOCOL + ":";

    private static final String JRT_SCHEME = JRT_PROTOCOL + ":";

    public static final String HASH_SEPARATOR = "#";

    private static final String ENCODED_HASH_SEPARATOR = "%23";

    private static final Comparator<TextEdit> TEXT_EDITS_ASCENDING_COMPARATOR = (a, b) -> {
        int diff = a.getRange().getStart().getLine() - b.getRange().getStart().getLine();
        if (diff == 0) {
            return a.getRange().getStart().getCharacter() - b.getRange().getStart().getCharacter();
        }
        return diff;
    };

    private static final Comparator<TextEdit> TEXT_EDITS_DESCENDING_COMPARATOR = (a, b) -> {
        int diff = b.getRange().getStart().getLine() - a.getRange().getStart().getLine();
        if (diff == 0) {
            return b.getRange().getStart().getCharacter() - a.getRange().getStart().getCharacter();
        }
        return diff;
    };

    /**
     * Open the LSP location in an editor.
     * <p>
     * Not used but declared to support backward compatibility.
     * </p>
     *
     * @param location the LSP location.
     * @param project  the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@Nullable Location location,
                                       @NotNull Project project) {
        return openInEditor(location, null, project);
    }

    /**
     * Open the LSP location in an editor.
     *
     * @param location       the LSP location.
     * @param fileUriSupport the file Uri support.
     * @param project        the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@Nullable Location location,
                                       @Nullable FileUriSupport fileUriSupport,
                                       @NotNull Project project) {
        if (location == null) {
            return false;
        }
        return openInEditor(location.getUri(), location.getRange() != null ? location.getRange().getStart() : null, fileUriSupport, project);
    }

    /**
     * Open the given fileUri with the given position in an editor.
     * <p>
     * Not used but declared to support backward compatibility.
     * </p>
     *
     * @param fileUri  the file Uri.
     * @param position the position.
     * @param project  the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@NotNull String fileUri,
                                       @Nullable Position position,
                                       @NotNull Project project) {
        return openInEditor(fileUri, position, null, project);
    }

    /**
     * Open the given fileUri with the given position in an editor.
     *
     * @param fileUri        the file Uri.
     * @param position       the position.
     * @param fileUriSupport the file Uri support.
     * @param project        the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@NotNull String fileUri,
                                       @Nullable Position position,
                                       @Nullable FileUriSupport fileUriSupport,
                                       @NotNull Project project) {
        return openInEditor(fileUri, position, true, fileUriSupport, project);
    }

    /**
     * Open the given fileUri with the given position in an editor.
     * <p>
     * Not used but declared to support backward compatibility.
     * </p>
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
        return openInEditor(fileUri, position, focusEditor, null, project);
    }

    /**
     * Open the given fileUri with the given position in an editor.
     *
     * @param fileUri        the file Uri.
     * @param position       the position.
     * @param focusEditor    true if editor will take the focus and false otherwise.
     * @param fileUriSupport the file Uri support.
     * @param project        the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@NotNull String fileUri,
                                       @Nullable Position position,
                                       boolean focusEditor,
                                       @Nullable FileUriSupport fileUriSupport,
                                       @NotNull Project project) {
        return openInEditor(fileUri, position, focusEditor, false, fileUriSupport, project);
    }

    /**
     * Open the given fileUrl in an editor.
     * <p>
     * Not used but declared to support backward compatibility.
     * </p>
     *
     * <p>
     * the following syntax is supported for fileUrl:
     *     <ul>
     *         <li>file:///C:/Users/username/foo.txt</li>
     *         <li>C:/Users/username/foo.txt</li>
     *         <li>file:///C:/Users/username/foo.txt#L1:5</li>
     *     </ul>
     * </p>
     *
     * @param fileUri            the file Uri to open.
     * @param position           the position.
     * @param focusEditor        true if editor will take the focus and false otherwise.
     * @param createFileIfNeeded true if file must be created if doesn't exist and false otherwise.
     * @param project            the project.
     * @return true if file Url can be opened and false otherwise.
     */
    public static boolean openInEditor(@NotNull String fileUri,
                                       @Nullable Position position,
                                       boolean focusEditor,
                                       boolean createFileIfNeeded,
                                       @NotNull Project project) {
        return openInEditor(fileUri, position, focusEditor, createFileIfNeeded, null, project);
    }

    /**
     * Open the given fileUrl in an editor.
     *
     * <p>
     * the following syntax is supported for fileUrl:
     *     <ul>
     *         <li>file:///C:/Users/username/foo.txt</li>
     *         <li>C:/Users/username/foo.txt</li>
     *         <li>file:///C:/Users/username/foo.txt#L1:5</li>
     *     </ul>
     * </p>
     *
     * @param fileUri            the file Uri to open.
     * @param startPosition      the start position.
     * @param endPosition        the end position to make it selected.
     * @param focusEditor        true if editor will take the focus and false otherwise.
     * @param createFileIfNeeded true if file must be created if doesn't exist and false otherwise.
     * @param fileUriSupport     the file Uri support.
     * @param project            the project.
     * @return true if file Url can be opened and false otherwise.
     */
    public static boolean openInEditor(@NotNull String fileUri,
                                       @Nullable Position startPosition,
                                       @Nullable Position endPosition,
                                       boolean focusEditor,
                                       boolean createFileIfNeeded,
                                       @Nullable FileUriSupport fileUriSupport,
                                       @NotNull Project project) {
        if (startPosition == null) {
            // Try to get position information from the fileUri
            // ex :
            // - file:///c:/Users/azerr/Downloads/simpleTest/simpleTest/yes.lua#L2
            // - file:///c:/Users/azerr/Downloads/simpleTest/simpleTest/yes.lua#L2:5
            // - file:///c%3A/Users/azerr/Downloads/simpleTest/simpleTest/yes.lua%23L2
            String findHash = HASH_SEPARATOR;
            int hashIndex = fileUri.lastIndexOf(findHash);
            if (hashIndex == -1) {
                findHash = ENCODED_HASH_SEPARATOR;
                hashIndex = fileUri.lastIndexOf(findHash);
            }
            boolean hasPosition = hashIndex > 0 && hashIndex != fileUri.length() - 1;
            if (hasPosition) {
                startPosition = toPosition(fileUri.substring(hashIndex + findHash.length()));
                fileUri = fileUri.substring(0, hashIndex);
            }
        }
        VirtualFile file = FileUriSupport.findFileByUri(fileUri, fileUriSupport);
        if (file == null && createFileIfNeeded) {
            // The file doesn't exist,
            // open a dialog to confirm the creation of the file.
            final String uri = fileUri;
            if (ApplicationManager.getApplication().isDispatchThread()) {
                return createFileAndOpenInEditor(uri, project);
            } else {
                AtomicBoolean result = new AtomicBoolean(false);
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    result.set(createFileAndOpenInEditor(uri, project));
                });
                return result.get();
            }
        }
        return openInEditor(file, startPosition, endPosition, focusEditor, project);
    }

    /**
     * Open the given fileUrl in an editor and make the range selected.
     *
     * @param fileUri            the file Uri to open.
     * @param position      the start position.
     * @param focusEditor        true if editor will take the focus and false otherwise.
     * @param createFileIfNeeded true if file must be created if doesn't exist and false otherwise.
     * @param fileUriSupport     the file Uri support.
     * @param project            the project.
     * @return true if file Url can be opened and false otherwise.
     */
    public static boolean openInEditor(@NotNull String fileUri,
                                       @Nullable Position position,
                                       boolean focusEditor,
                                       boolean createFileIfNeeded,
                                       @Nullable FileUriSupport fileUriSupport,
                                       @NotNull Project project) {
       return openInEditor(fileUri, position, null, focusEditor, createFileIfNeeded, fileUriSupport, project);
    }

    private static boolean createFileAndOpenInEditor(@NotNull String fileUri, @NotNull Project project) {
        int result = Messages.showYesNoDialog(LanguageServerBundle.message("lsp.create.file.confirm.dialog.message", fileUri),
                LanguageServerBundle.message("lsp.create.file.confirm.dialog.title"), Messages.getQuestionIcon());
        if (result == Messages.YES) {
            try {
                // Create file
                VirtualFile newFile = LSPIJUtils.createFile(fileUri);
                if (newFile != null) {
                    // Open it in an editor
                    return LSPIJUtils.openInEditor(newFile, null, project);
                }
            } catch (Exception e) {
                Messages.showErrorDialog(LanguageServerBundle.message("lsp.create.file.error.dialog.message", fileUri, e.getMessage()),
                        LanguageServerBundle.message("lsp.create.file.error.dialog.title"));
            }
        }
        return false;
    }

    /**
     * Convert position String 'L1:2' to an LSP {@link Position} and null otherwise.
     *
     * @param positionString the position string (ex: 'L1:2')
     * @return position String 'L1:2' to an LSP {@link Position} and null otherwise.
     */
    private static Position toPosition(String positionString) {
        if (positionString == null || positionString.isEmpty()) {
            return null;
        }
        if (positionString.charAt(0) != 'L') {
            return null;
        }
        positionString = positionString.substring(1, positionString.length());
        String[] positions = positionString.split(":");
        if (positions.length == 0) {
            return null;
        }
        int line = toInt(0, positions) - 1; // Line numbers should be 1-based
        int character = toInt(1, positions);
        return new Position(line, character);
    }

    private static int toInt(int index, String[] positions) {
        if (index < positions.length) {
            try {
                return Integer.valueOf(positions[index]);
            } catch (Exception e) {
            }
        }
        return 0;
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
     * @param file          the file.
     * @param startPosition the start position.
     * @param endPosition   the end position to make it selected.
     * @param focusEditor   true if editor will take the focus and false otherwise.
     * @param project       the project.
     * @return true if the file was opened and false otherwise.
     */
    public static boolean openInEditor(@Nullable VirtualFile file,
                                       @Nullable Position startPosition,
                                       @Nullable Position endPosition,
                                       boolean focusEditor,
                                       @NotNull Project project) {
        if (file != null) {
            final Document document = startPosition != null ? LSPIJUtils.getDocument(file) : null;
            if (ApplicationManager.getApplication().isDispatchThread()) {
                return doOpenInEditor(file, startPosition, endPosition, document, focusEditor, project);
            } else {
                AtomicBoolean result = new AtomicBoolean(false);
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    result.set(doOpenInEditor(file, startPosition, endPosition, document, focusEditor, project));
                });
                return result.get();
            }
        }
        return false;
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
        return openInEditor(file, position, null, focusEditor, project);
    }

    private static boolean doOpenInEditor(@NotNull VirtualFile file,
                                          @Nullable Position startPosition,
                                          @Nullable Position endPosition,
                                          @Nullable Document document,
                                          boolean focusEditor,
                                          @NotNull Project project) {
        if (startPosition == null) {
            return FileEditorManager.getInstance(project).openFile(file, true).length > 0;
        } else {
            if (document != null) {
                OpenFileDescriptor desc = new OpenFileDescriptor(project, file, LSPIJUtils.toOffset(startPosition, document));
                var startOffset = LSPIJUtils.toOffset(startPosition, document);
                var editor = FileEditorManager.getInstance(project).openTextEditor(desc, focusEditor);
                if (editor != null && endPosition != null && !startPosition.equals(endPosition)) {
                    var endOffset = LSPIJUtils.toOffset(endPosition, document);
                    editor.getSelectionModel().setSelection(startOffset, endOffset);
                    editor.getCaretModel().moveToOffset(endOffset);
                }
                return editor != null;
            }
            return false;
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
        URI fileUri = file.getUserData(DEFAULT_LSP_FILE_URI_KEY);
        if (fileUri == null) {
            // Cache the file Uri to avoid recomputing again
            fileUri = toUri(VfsUtilCore.virtualToIoFile(file));
            file.putUserData(DEFAULT_LSP_FILE_URI_KEY, fileUri);
        }
        return fileUri;
    }

    public static @Nullable String toUriAsString(@NotNull PsiFile psFile) {
        VirtualFile file = psFile.getVirtualFile();
        return file != null ? toUriAsString(file) : null;
    }

    public static @NotNull String toUriAsString(@NotNull VirtualFile file) {
        String protocol = file.getFileSystem().getProtocol();
        if (JAR_PROTOCOL.equals(protocol) || JRT_PROTOCOL.equals(protocol)) {
            return Objects.requireNonNull(VfsUtilCore.convertToURL(file.getUrl())).toExternalForm();
        }
        String uri = toUri(VfsUtilCore.virtualToIoFile(file)).toASCIIString();
        if (file.isDirectory()) {
            // For directory case, remove last '/'
            char last = uri.charAt(uri.length() - 1);
            if (last == '/' || last == '\\') {
                return uri.substring(0, uri.length() - 1);
            }
        }
        return uri;
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
            return doGetPsiFile(file, project);
        }
        return ReadAction.compute(() -> doGetPsiFile(file, project));
    }

    /**
     * Returns the Psi file corresponding to the virtual file in the given project. Must be called in a Read Action.
     *
     * @param file    the virtual file.
     * @param project the project.
     * @return the Psi file corresponding to the virtual file in the given project.
     */
    private static @Nullable PsiFile doGetPsiFile(@NotNull VirtualFile file, @NotNull Project project) {
        // Prevent PsiManager.findFile from logging a nasty error if file is not valid.
        return (file.isValid()) ? PsiManager.getInstance(project).findFile(file) : null;
    }


    /**
     * Returns the virtual file corresponding to the PSI element.
     *
     * @param element the PSI element
     * @return the virtual file, or {@code null} if the file exists only in memory.
     */
    public static @Nullable VirtualFile getFile(@NotNull PsiElement element) {
        PsiFile psFile = element.getContainingFile();
        return psFile != null ? psFile.getVirtualFile() : null;
    }

    /**
     * Returns the document corresponding to the virtual file.
     *
     * @param file the virtual file
     * @return the document corresponding to the virtual file, or {@code null} if no document could be found
     */
    public static @Nullable Document getDocument(@NotNull VirtualFile file) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return FileDocumentManager.getInstance().getDocument(file);
        }
        return ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
    }

    /**
     * Returns the document corresponding to the PSI element
     *
     * @param element the PSI element
     * @return the document corresponding to the PSI element, or {@code null} if no document could be found
     */
    public static @Nullable Document getDocument(@NotNull PsiElement element) {
        VirtualFile virtualFile = getFile(element);
        return virtualFile != null ? getDocument(virtualFile) : null;
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
        return toOffset(position.getLine(), position.getCharacter(), document);
    }

    public static int toOffset(int line, int character, @NotNull Document document) {
        // See https://github.com/microsoft/vscode-languageserver-node/blob/8e625564b531da607859b8cb982abb7cdb2fbe2e/textDocument/src/main.ts#L304

        // Adjust position line/character according to this comment https://github.com/microsoft/vscode-languageserver-node/blob/ed3cd0f78c1495913bda7318ace2be7f968008af/textDocument/src/main.ts#L26
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
        return Math.max(Math.min(lineOffset + character, nextLineOffset), lineOffset);
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
    public static List<WorkspaceFolder> toWorkspaceFolders(@NotNull Project project) {
        Set<VirtualFile> roots = getRoots(project);
        List<WorkspaceFolder> workspaceFolders = new ArrayList<>(roots.size());
        for (var root : roots) {
            WorkspaceFolder folder = new WorkspaceFolder();
            folder.setUri(Objects.requireNonNull(toUriAsString(root)));
            folder.setName(root.getName());
            workspaceFolders.add(folder);
        }
        return workspaceFolders;
    }

    public static URI toUri(Module module) {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        if (roots.length > 0) {
            return toUri(roots[0]);
        }
        VirtualFile moduleDir = ProjectUtil.guessModuleDir(module);
        if (moduleDir != null) {
            return toUri(moduleDir);
        }
        return toUri(module.getProject());
    }

    /**
     * Return top-level directories which contain files related to the project.
     *
     * @param project the project.
     * @return top-level directories which contain files related to the project.
     */
    @NotNull
    public static Set<VirtualFile> getRoots(Project project) {
        return BaseProjectDirectories.Companion.getBaseDirectories(project);
    }

    public static URI toUri(Project project) {
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        if (roots.length > 0) {
            return toUri(roots[0]);
        }
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir == null) {//Most likely when running tests
            String baseDir = project.getBasePath();
            if (baseDir == null) {
                return null;//It's the default project, we're probably screwed
            }
            return toUri(new File(baseDir));
        }
        return toUri(projectDir);
    }

    public static Range toRange(TextRange range, Document document) {
        return new Range(LSPIJUtils.toPosition(range.getStartOffset(), document), LSPIJUtils.toPosition(range.getEndOffset(), document));
    }

    public static @Nullable TextRange toTextRange(@NotNull Range range, @NotNull Document document) {
        return toTextRange(range, document, null, false);
    }

    /**
     * Returns the IJ {@link TextRange} from the given LSP range and null otherwise.
     *
     * @param range    the LSP range to convert.
     * @param document the document.
     * @return the IJ {@link TextRange} from the given LSP range and null otherwise.
     */
    @Deprecated
    public static @Nullable TextRange toTextRange(@NotNull Range range,
                                                  @NotNull Document document,
                                                  boolean adjust) {
        return toTextRange(range, document, null, adjust);
    }

    /**
     * Returns the IJ {@link TextRange} from the given LSP range and null otherwise.
     *
     * @param range    the LSP range to convert.
     * @param document the document.
     * @param file     the PsiFile or null otherwise.
     * @return the IJ {@link TextRange} from the given LSP range and null otherwise.
     */
    public static @Nullable TextRange toTextRange(@NotNull Range range,
                                                  @NotNull Document document,
                                                  @Nullable PsiFile file,
                                                  boolean adjust) {
        try {
            int start = LSPIJUtils.toOffset(range.getStart(), document);
            int end = LSPIJUtils.toOffset(range.getEnd(), document);
            int docLength = document.getTextLength();
            if (start > end || end > docLength) {
                // Language server reports invalid range, ignore it.
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
            TextRange tokenRange = getWordRangeAt(document, file, start);
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
     * Returns the word range from the document at given offset and null otherwise.
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
     * @return the word range from the document at given offset and null otherwise.
     */
    @Deprecated
    @Nullable
    public static TextRange getWordRangeAt(@NotNull Document document,
                                           int offset) {
        return getWordRangeAt(document, null, offset);
    }

    /**
     * Returns the word range from the document at given offset and null otherwise.
     *
     * <code><pre>
     *  - fo|o bar -> [foo]
     *  - fo|o.bar() -> [foo]
     *  - foo.b|ar() -> [bar]
     *  - foo.bar(|) -> null
     *  - foo  |  bar -> null
     * </pre></code>
     *
     * @param document the document.
     * @param file     the PsiFile or null otherwise.
     * @param offset   the offset.
     * @return the word range from the document at given offset and null otherwise.
     */
    @Nullable
    public static TextRange getWordRangeAt(@NotNull Document document,
                                           @Nullable PsiFile file,
                                           int offset) {
        if (offset > document.getTextLength()) {
            offset = document.getTextLength() - 1;
        }
        if (file != null && !SimpleLanguageUtils.isSupported(file.getLanguage())) {
            // It is not TextMate, TEXT file (since those language doesn't tokenize the file)
            // Try to use the PsiElement text range found at the given offset
            TextRange textRange = findBestTextRangeAt(file, offset);
            if (textRange != null) {
                return textRange;
            }
        }

        int start = getLeftOffsetOfPart(document, offset);
        int end = getRightOffsetOfPart(document, offset);
        return (start < end) ? new TextRange(start, end) : null;
    }

    private static TextRange findBestTextRangeAt(@Nullable PsiFile file, int offset) {
        TextRange fileTextRange = file.getTextRange();
        PsiElement element = file.findElementAt(Math.max(offset - 1, 0));
        if (element != null) {
            TextRange textRange = element.getTextRange();
            if (offset == textRange.getEndOffset()) {
                // my.property|
                // my.property=|
                // my.property|=
                // my.property=v|
                // my.property=value|
                if (textRange.getLength() == 1) {
                    // my.property=| --> =
                    // my.property=v| --> v
                    // In this case (for properties file):
                    // - '=' (equals) must be forbidden
                    // - 'v' (property value) mus be allowed
                    // To fix that with a generic mean, we check if the character is a letter or a digit
                    // FIXME : provide an LSP API getWordRangeAt for a given language server
                    char c = element.getText().charAt(0);
                    if (!Character.isLetterOrDigit(c)) {
                        return null;
                    }
                }
            }
            return textRange;
        }
        return null;
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
                            applyEdits(null, document, textDocumentEdit.getEdits());
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
                        LOGGER.error("Cannot create file '{}'", fileUri, e);
                    }
                }
                if (file != null) {
                    Document document = getDocument(file);
                    if (document != null) {
                        applyEdits(null, document, change.getValue());
                    }
                }
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
                    applyEdits(null, document, Collections.singletonList(textEdit));
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

    public static @Nullable VirtualFile findResourceFor(@NotNull URI uri) {
        return LocalFileSystem.getInstance().findFileByIoFile(Paths.get(uri).toFile());
    }

    /**
     * Returns the virtual file from the given uri and null otherwise.
     *
     * @param uri the Uri.
     * @return the virtual file from the given uri and null otherwise.
     */
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
            // ex : file:///c%3A/Users/azerr/IdeaProjects/untitled7/test.js
            // the uri must be decoded (ex : file:///c:/Users) otherwise IntelliJ cannot retrieve the virtual file.
            // Keep the original '+' after decoding the Uri
            uri = uri.replace("+", "%2B");
            // Decode the uri
            uri = URLDecoder.decode(uri, StandardCharsets.UTF_8);
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
        if (file == null) {
            return new Editor[0];
        }
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
        return new TextDocumentIdentifier(toUriAsString(file));
    }

    /**
     * Apply text edits to the given document and move the caret offset of the given editor if needed.
     *
     * @param editor   the editor used to update the caret offset after the apply edits and null otherwise.
     * @param document the document to update.
     * @param edits    the text edit list to apply to the given document.
     */
    public static void applyEdits(@Nullable Editor editor,
                                  @NotNull Document document,
                                  @NotNull List<TextEdit> edits) {
        if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
            doApplyEdits(editor, document, edits);
        } else {
            WriteAction.run(() -> doApplyEdits(editor, document, edits));
        }
    }

    /**
     * Apply text edits to the given document and move the caret offset of the given editor if needed.
     *
     * <p>
     * This method is called in Write Action.
     * </p>
     *
     * @param editor   the editor used to update the caret offset after the apply edits and null otherwise.
     * @param document the document to update.
     * @param edits    the text edit list to apply to the given document.
     */
    private static void doApplyEdits(@Nullable Editor editor,
                                     @NotNull Document document,
                                     @NotNull List<TextEdit> edits) {
        // Create an owned copy to insulate against modification of the provided list while processing it
        List<TextEdit> ownedEdits = new ArrayList<>(edits);

        if (ownedEdits.isEmpty()) {
            return;
        }
        // Convert TextEdit positions into RangeMarkers
        final var pairs = new ArrayList<Pair<TextEdit, RangeMarker>>();
        for (var textEdit : ownedEdits) {
            var range = textEdit.getRange();
            if (range != null) {
                int start = toOffset(range.getStart(), document);
                int end = toOffset(range.getEnd(), document);
                // Range is valid, add it to the converted list
                if (end >= start) {
                    var marker = document.createRangeMarker(start, end);
                    pairs.add(Pair.create(textEdit, marker));
                }
            }
        }
        if (pairs.isEmpty()) {
            return;
        }
        final int oldCaretOffset = editor != null ? editor.getCaretModel().getOffset() : -1;
        int newCaretOffset = oldCaretOffset;
        // Apply each text edit to update the given document
        for (var pair: pairs) {
            var edit = pair.first;
            var marker = pair.second;
            int increment = applyEdit(marker.getStartOffset(), marker.getEndOffset(), edit.getNewText(), document, oldCaretOffset);
            if (newCaretOffset != -1) {
                newCaretOffset += increment;
            }
            marker.dispose();
        }
        if (newCaretOffset > -1 && oldCaretOffset != newCaretOffset) {
            editor.getCaretModel().moveToOffset(newCaretOffset);
        }
    }

    /**
     * Apply text edit by updating the given document.
     *
     * @param start       the start offset of the text edit.
     * @param end         the end offset of the text edit.
     * @param newText     the text to insert/replace and empty or null if delete must be done.
     * @param document    the document to update.
     * @param caretOffset the current caret offset and -1 if caret must be not moved.
     * @return the increment (positive or negative) used to update caret offset.
     */
    public static int applyEdit(int start,
                                int end,
                                @Nullable String newText,
                                @NotNull Document document,
                                int caretOffset) {
        if (StringUtils.isEmpty(newText)) {
            // Delete operation

            // {
            //  "range": {
            //    "start": {
            //      "line": 8,
            //      "character": 1
            //    },
            //    "end": {
            //      "line": 8,
            //      "character": 3
            //    }
            //  },
            //  "newText": ""
            //}
            document.deleteString(start, end);
            return -getIncrement(start, end, caretOffset);
        }

        newText = newText.replaceAll("\r", "");

        if (start == end) {
            // Insert operation

            // {
            //  "range": {
            //    "start": {
            //      "line": 8,
            //      "character": 3
            //    },
            //    "end": {
            //      "line": 8,
            //      "character": 3
            //    }
            //  },
            //  "newText": "fmt.Printf(\"s: %v\\n\", s)"
            //}
            document.insertString(start, newText);
            if (start > caretOffset) {
                // <caret>...<start><end>
                // The text edit doesn't impact the caret offset
                return 0;
            }
            // <start><end>...<caret>...
            return newText.length();
        }

        // Replace operation

        // {
        //  "range": {
        //    "start": {
        //      "line": 2,
        //      "character": 7
        //    },
        //    "end": {
        //      "line": 4,
        //      "character": 1
        //    }
        //  },
        //  "newText": "\"fmt\""
        //}
        document.replaceString(start, end, newText);
        return newText.length() - getIncrement(start, end, caretOffset);
    }

    private static int getIncrement(int start, int end, int caret) {
        if (caret == -1) {
            return 0;
        }
        if (start > caret) {
            // <caret>...<start>foo<end>
            // The text edit doesn't impact the caret offset
            return 0;
        }
        if (caret > end) {
            // ...<start>foo<end>...<caret>
            return end - start;
        }
        // ...<start>fo<caret>o<end>...
        return caret - start;
    }

    /**
     * Apply text edits by using the given document without updating and returns the response of the apply text edits.
     *
     * @param document the document used to apply text edits without updating it.
     * @param edits    the text edit list to apply to the given document.
     * @return the response of the apply text edits.
     */
    public static String applyEdits(@NotNull Document document,
                                    @NotNull List<? extends TextEdit> edits) {
        // Create an owned mutable copy since we're going to modify the list
        List<TextEdit> mutableEdits = new ArrayList<>(edits);

        // Sort text edits
        if (mutableEdits.size() > 1) {
            mutableEdits.sort(TEXT_EDITS_ASCENDING_COMPARATOR);
        }
        String text = document.getText();
        int lastModifiedOffset = 0;
        List<String> spans = new ArrayList<>(mutableEdits.size() + 1);
        for (TextEdit textEdit : mutableEdits) {
            int startOffset = LSPIJUtils.toOffset(textEdit.getRange().getStart(), document);
            if (startOffset < lastModifiedOffset) {
                throw new Error("Overlapping edit");
            } else if (startOffset > lastModifiedOffset) {
                spans.add(text.substring(lastModifiedOffset, startOffset));
            }
            if (textEdit.getNewText() != null) {
                spans.add(textEdit.getNewText());
            }
            lastModifiedOffset = LSPIJUtils.toOffset(textEdit.getRange().getEnd(), document);
        }
        spans.add(text.substring(lastModifiedOffset));
        //
        return String.join("", spans);
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

    /**
     * Extracts the most specific location information from the provided list of locations or location links.
     *
     * @param locations the locations/links
     * @return the most specific location information that was found based on the provided locations/links
     */
    public static @NotNull List<LocationData> getLocations(@Nullable Either<List<? extends Location>, List<? extends LocationLink>> locations,
                                                           @Nullable LanguageServerItem languageServer) {
        if (locations == null) {
            // textDocument/definition may return null
            return Collections.emptyList();
        }
        if (locations.isLeft()) {
            return locations.getLeft()
                    .stream()
                    .map(l -> new LocationData(new Location(l.getUri(), l.getRange()), languageServer))
                    .toList();

        }
        return locations.getRight()
                .stream()
                .map(l -> new LocationData(new Location(l.getTargetUri(), l.getTargetSelectionRange() != null ? l.getTargetSelectionRange() : l.getTargetRange()), languageServer))
                .toList();
    }
}
