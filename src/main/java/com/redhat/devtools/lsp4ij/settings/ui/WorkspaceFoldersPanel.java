/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ConfigurableWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ProjectWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.RootType;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.settings.jsonSchema.LSPWorkspaceFoldersJsonSchemaFileProvider;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Panel for configuring workspace folders with testing capabilities.
 */
public class WorkspaceFoldersPanel extends JPanel implements Disposable {

    private static final String NO_ROOT_NODE_KEY = "language.server.workspaceFolders.noRoot";
    private static final String NO_INSTANCE_NODE_KEY = "language.server.workspaceFolders.noInstance";

    private final Project project;
    private final WorkspaceFolderStrategy strategy;
    private final boolean showJsonEditor;
    private JsonTextField jsonEditor;
    private final Tree workspaceFoldersTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final Map<String, DefaultMutableTreeNode> folderNodes = new HashMap<>();
    private DefaultMutableTreeNode noRootNode;
    private final Set<VirtualFile> openedFiles = new HashSet<>();

    private JBCheckBox sendAtInitCheckbox;

    private Alarm updateAlarm;

    public WorkspaceFoldersPanel(@NotNull Project project, @Nullable WorkspaceFolderStrategy strategy) {
        super(new BorderLayout());
        this.project = project;
        this.strategy = strategy != null ? strategy : new ProjectWorkspaceFolderStrategy();
        this.showJsonEditor = this.strategy instanceof ConfigurableWorkspaceFolderStrategy;

        // Create tree model
        rootNode = new DefaultMutableTreeNode("Workspace Folders");
        treeModel = new DefaultTreeModel(rootNode);
        workspaceFoldersTree = new Tree(treeModel);
        workspaceFoldersTree.setRootVisible(false);
        workspaceFoldersTree.setCellRenderer(new WorkspaceFolderTreeCellRenderer());

        // Setup empty text with hyperlink for scanning
        StatusText emptyText = workspaceFoldersTree.getEmptyText();
        emptyText.clear();

        // Setup drag and drop on tree
        setupDragAndDrop(workspaceFoldersTree);

        // Setup context menu and delete key for tested files
        setupTreeActions();

        // Setup mouse listener for clickable links in tree
        setupTreeLinkListener();

        JPanel jsonEditorPanel = null;
        if (showJsonEditor) {
            this.updateAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

            // Create JSON editor
            jsonEditor = new JsonTextField(project);
            jsonEditor.setJsonFilename(LSPWorkspaceFoldersJsonSchemaFileProvider.WORKSPACE_FOLDERS_JSON_FILE_NAME);

            // Initialize with current configuration
            if (this.strategy instanceof ConfigurableWorkspaceFolderStrategy configurableWorkspaceFolderStrategy) {
                String currentConfig = configurableWorkspaceFolderStrategy.getJsonConfiguration();
                jsonEditor.setText(currentConfig);
            }

            // Add document listener to refresh workspace folders list with debounce
            jsonEditor.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void documentChanged(@NotNull DocumentEvent event) {
                    updateAlarm.cancelAllRequests();
                    updateAlarm.addRequest(() -> {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                            updateWorkspaceFoldersDisplay();
                        });
                    }, 500);
                }
            });

            jsonEditorPanel = new JPanel(new BorderLayout());
            jsonEditorPanel.add(jsonEditor, BorderLayout.CENTER);
            jsonEditorPanel.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()));
        }

        // Main layout
        if (showJsonEditor) {
            OnePixelSplitter splitter = new OnePixelSplitter(false, 0.5f);
            splitter.setFirstComponent(createLeftPanel(jsonEditorPanel));
            splitter.setSecondComponent(createRightPanel());
            add(splitter, BorderLayout.CENTER);
        } else {
            add(createRightPanel(), BorderLayout.CENTER);
        }

        // Initialize
        updateWorkspaceFoldersDisplay();
    }

    private JPanel createLeftPanel(JPanel jsonEditorPanel) {
        JPanel panel = new JPanel(new BorderLayout());

        // Create header panel with label and doc link
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JLabel label = new JLabel(LanguageServerBundle.message("language.server.workspaceFolders.configuration"));
        headerPanel.add(label);

        HyperlinkLabel docLink = new HyperlinkLabel("Learn more");
        docLink.addHyperlinkListener(e -> BrowserUtil.browse("https://github.com/redhat-developer/lsp4ij/blob/main/docs/UserDefinedLanguageServer.md#workspace-folders-tab"));
        headerPanel.add(docLink);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(jsonEditorPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Header with label and description
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(JBUI.Borders.empty(5));

        JPanel labelPanel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(LanguageServerBundle.message("language.server.workspaceFolders.detected"));
        labelPanel.add(label, BorderLayout.NORTH);

        // Add description to explain this is a preview/test panel
        JBLabel descriptionLabel = new JBLabel(LanguageServerBundle.message("language.server.workspaceFolders.detected.description"));
        descriptionLabel.setForeground(UIUtil.getLabelDisabledForeground());
        descriptionLabel.setBorder(JBUI.Borders.emptyTop(2));
        descriptionLabel.setFont(JBUI.Fonts.smallFont());
        labelPanel.add(descriptionLabel, BorderLayout.CENTER);

        headerPanel.add(labelPanel, BorderLayout.WEST);

        // Test zone (above tree)
        JPanel testPanel = createTestPanel();

        // Combine header and test panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(testPanel, BorderLayout.SOUTH);

        // Tree
        JPanel foldersPanel = new JPanel(new BorderLayout());
        foldersPanel.add(topPanel, BorderLayout.NORTH);

        JScrollPane treeScrollPane = new JBScrollPane(workspaceFoldersTree);
        treeScrollPane.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()));
        foldersPanel.add(treeScrollPane, BorderLayout.CENTER);

        panel.add(foldersPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createTestPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(5));

        // Compact drop zone
        JPanel dropZone = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        dropZone.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()));
        dropZone.setBackground(UIUtil.getPanelBackground());

        JBLabel testLabel = new JBLabel(LanguageServerBundle.message("language.server.workspaceFolders.test"));
        testLabel.setForeground(UIUtil.getLabelDisabledForeground());
        testLabel.setToolTipText(LanguageServerBundle.message("language.server.workspaceFolders.test.description"));
        dropZone.add(testLabel);

        HyperlinkLabel dropZoneLabel = new HyperlinkLabel();
        dropZoneLabel.setTextWithHyperlink(LanguageServerBundle.message("language.server.workspaceFolders.dropZone"));
        dropZoneLabel.addHyperlinkListener(e -> browseFile());
        dropZone.add(dropZoneLabel);

        // Send at init checkbox (only for configurable strategy)
        if (showJsonEditor) {
            dropZone.add(new JLabel(" - "));

            // Create checkbox without text
            sendAtInitCheckbox = new JBCheckBox("", false);
            sendAtInitCheckbox.setOpaque(false);
            sendAtInitCheckbox.setToolTipText(LanguageServerBundle.message("language.server.workspaceFolders.sendAtInit.tooltip"));
            sendAtInitCheckbox.addActionListener(e -> {
                ApplicationManager.getApplication().invokeLater(this::updateWorkspaceFoldersDisplay);
            });
            dropZone.add(sendAtInitCheckbox);

            // Create label with clickable link
            HyperlinkLabel sendAtInitLabel = new HyperlinkLabel();
            sendAtInitLabel.setTextWithHyperlink(LanguageServerBundle.message("language.server.workspaceFolders.sendAtInit"));
            sendAtInitLabel.addHyperlinkListener(e -> BrowserUtil.browse("https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#initialize"));
            dropZone.add(sendAtInitLabel);
        }

        // Setup drag and drop
        setupDragAndDrop(dropZone);

        panel.add(dropZone, BorderLayout.CENTER);

        return panel;
    }

    private void setupDragAndDrop(Component component) {
        if (GraphicsEnvironment.isHeadless()) {
            // A drop target needs a display, which a headless IDE, such as the tests on a CI server, doesn't have
            return;
        }
        new DropTarget(component, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>)
                            dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        VirtualFile vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                                .findFileByIoFile(files.get(0));
                        if (vFile != null) {
                            testFile(vFile);
                        }
                    }
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    dtde.dropComplete(false);
                }
            }
        });
    }

    private void setupTreeActions() {
        // Add Delete key support
        workspaceFoldersTree.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    removeSelectedFile();
                }
            }
        });

        // Add context menu
        workspaceFoldersTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
        });
    }

    private void setupTreeLinkListener() {
        workspaceFoldersTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TreePath path = workspaceFoldersTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (hasLink(node)) {
                    FolderNodeData data = (FolderNodeData) node.getUserObject();

                    // Open LSP specification link
                    String url;
                    if (data.sentAtInit) {
                        url = "https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#initialize";
                    } else {
                        url = "https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didChangeWorkspaceFolders";
                    }

                    BrowserUtil.browse(url);
                }
            }
        });

        // Change cursor to hand when hovering over folder nodes
        workspaceFoldersTree.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                TreePath path = workspaceFoldersTree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (hasLink(node)) {
                        workspaceFoldersTree.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        return;
                    }
                }
                workspaceFoldersTree.setCursor(Cursor.getDefaultCursor());
            }
        });
    }

    private static boolean hasLink(@NotNull DefaultMutableTreeNode node) {
        // The row of a language server instance names no LSP message to link to
        return node.getUserObject() instanceof FolderNodeData data && !data.serverInstance;
    }

    private void showContextMenu(MouseEvent e) {
        TreePath path = workspaceFoldersTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof FileNodeData) {
            workspaceFoldersTree.setSelectionPath(path);

            JPopupMenu popup = new JPopupMenu();
            JMenuItem removeItem = new JMenuItem(LanguageServerBundle.message("language.server.workspaceFolders.remove"));
            removeItem.addActionListener(evt -> removeSelectedFile());
            popup.add(removeItem);
            popup.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    @VisibleForTesting
    void removeSelectedFile() {
        TreePath selectedPath = workspaceFoldersTree.getSelectionPath();
        if (selectedPath == null) {
            return;
        }

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        if (!(selectedNode.getUserObject() instanceof FileNodeData)) {
            return;
        }

        FileNodeData fileData = (FileNodeData) selectedNode.getUserObject();

        // Remove from opened files tracking
        openedFiles.remove(fileData.file);

        // Remove the file node
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
        treeModel.removeNodeFromParent(selectedNode);

        removeEmptyParent(parentNode);
    }

    private void removeEmptyParent(@Nullable DefaultMutableTreeNode parentNode) {
        // If parent folder is now empty and not in the config folders, remove it too
        if (parentNode != null && parentNode.getChildCount() == 0) {
            Object parentUserObject = parentNode.getUserObject();

            if (parentUserObject instanceof FolderNodeData) {
                FolderNodeData folderData = (FolderNodeData) parentUserObject;
                String folderUri = FileUriSupport.toString(folderData.file, FileUriSupport.DEFAULT);
                var grandParentNode = (DefaultMutableTreeNode) parentNode.getParent();

                // Only remove folder node if it's not in the current config
                boolean isConfigFolder = false;
                List<WorkspaceFolder> configFolders;

                boolean showInitFolders = isShowingInitFolders();
                if (showInitFolders) {
                    configFolders = strategy.getInitialWorkspaceFolders(project, FileUriSupport.DEFAULT);
                } else {
                    configFolders = strategy.getWorkspaceFolders(project, FileUriSupport.DEFAULT);
                }

                for (WorkspaceFolder folder : configFolders) {
                    if (folder.getUri().equals(folderUri)) {
                        isConfigFolder = true;
                        break;
                    }
                }

                if (!isConfigFolder) {
                    treeModel.removeNodeFromParent(parentNode);
                    if (folderUri != null) {
                        folderNodes.remove(folderUri);
                    }
                    // The instance of a workspace folder can be empty now
                    removeEmptyParent(grandParentNode);
                }
            } else if (NO_ROOT_NODE_KEY.equals(parentUserObject) || NO_INSTANCE_NODE_KEY.equals(parentUserObject)) {
                // Remove "No root" node if empty
                treeModel.removeNodeFromParent(parentNode);
                noRootNode = null;
            }
        }
    }

    private void browseFile() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, false);
        VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
        if (file != null) {
            testFile(file);
        }
    }

    @TestOnly
    JTree getWorkspaceFoldersTree() {
        return workspaceFoldersTree;
    }

    /**
     * Shows the given file in the preview, under the folder and instance it would have.
     *
     * @param file the file.
     * @return a future that completes once the file is shown.
     */
    CompletableFuture<Void> testFile(@NotNull VirtualFile file) {
        CompletableFuture<Void> shown = new CompletableFuture<>();
        // Always run slow operation in background thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            FileLocation location;
            try {
                location = locate(file);
            } catch (Exception e) {
                shown.completeExceptionally(e);
                return;
            }

            // Update UI on EDT
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    addTestedFile(file, location);
                } finally {
                    shown.complete(null);
                }
            }, ModalityState.any());
        });
        return shown;
    }

    private void addTestedFile(@NotNull VirtualFile file, @NotNull FileLocation location) {
        // Track opened file
        openedFiles.add(file);

        // As the refresh says it, so a row reads the same before and after it
        var parentNode = addFileNode(file, location, strategy.sendAllFoldersOnInitialization());
        workspaceFoldersTree.expandPath(new TreePath(parentNode.getPath()));
    }

    /**
     * Where a file goes in the tree: the root of its language server instance, if there are instances, and its
     * workspace folder inside that instance. The folder is null if the file has neither.
     */
    private record FileLocation(@Nullable WorkspaceFolder instanceRoot, @Nullable WorkspaceFolder folder) {
    }

    private @NotNull FileLocation locate(@NotNull VirtualFile file) {
        var folder = strategy.getWorkspaceFolderForFile(file, project, FileUriSupport.DEFAULT);
        if (!strategy.hasServerInstances()) {
            return new FileLocation(null, folder);
        }
        var instanceRoot = getServerInstanceRoot(file);
        boolean inInstance = instanceRoot != null && folder != null
                && strategy.belongsToServerInstance(folder, instanceRoot, project, FileUriSupport.DEFAULT);
        return new FileLocation(instanceRoot, inInstance ? folder : instanceRoot);
    }

    /**
     * Returns the root of the instance of the given file or directory as the language server gets it: one directory is
     * one instance, however the strategy writes its URI.
     */
    private @Nullable WorkspaceFolder getServerInstanceRoot(@NotNull VirtualFile file) {
        var root = strategy.getServerInstanceRoot(file, project, FileUriSupport.DEFAULT);
        if (root == null) {
            return null;
        }
        String uri = FileUriSupport.normalize(root.getUri(), FileUriSupport.DEFAULT);
        return uri.equals(root.getUri()) ? root : new WorkspaceFolder(uri, root.getName());
    }

    private @NotNull DefaultMutableTreeNode addFileNode(@NotNull VirtualFile file,
                                                        @NotNull FileLocation location,
                                                        boolean sentAtInit) {
        var parentNode = location.folder() != null ? getOrCreateFolderNode(location, sentAtInit) : null;
        if (parentNode == null) {
            parentNode = getOrCreateNoRootNode();
        }
        treeModel.insertNodeInto(new DefaultMutableTreeNode(new FileNodeData(file)), parentNode, parentNode.getChildCount());
        return parentNode;
    }

    private @NotNull DefaultMutableTreeNode getOrCreateNoRootNode() {
        if (noRootNode == null) {
            noRootNode = new DefaultMutableTreeNode(getNoRootNodeKey());
            treeModel.insertNodeInto(noRootNode, rootNode, rootNode.getChildCount());
        }
        return noRootNode;
    }

    /**
     * Returns the node of a workspace folder, under the node of its instance if it has one. A folder that is the root
     * of its instance shares that node.
     */
    private @Nullable DefaultMutableTreeNode getOrCreateFolderNode(@NotNull FileLocation location, boolean sentAtInit) {
        var parentNode = rootNode;
        if (location.instanceRoot() != null) {
            parentNode = getOrCreateFolderNode(location.instanceRoot(), rootNode, sentAtInit, true);
            if (parentNode == null || location.instanceRoot().getUri().equals(location.folder().getUri())) {
                return parentNode;
            }
        }
        return getOrCreateFolderNode(location.folder(), parentNode, sentAtInit, false);
    }

    private @Nullable DefaultMutableTreeNode getOrCreateFolderNode(@NotNull WorkspaceFolder folder,
                                                                   @NotNull DefaultMutableTreeNode parentNode,
                                                                   boolean sentAtInit,
                                                                   boolean serverInstance) {
        var node = folderNodes.get(folder.getUri());
        if (node == null) {
            VirtualFile file = LSPIJUtils.findResourceFor(folder.getUri());
            if (file != null) {
                node = new DefaultMutableTreeNode(new FolderNodeData(file, sentAtInit, serverInstance));
                folderNodes.put(folder.getUri(), node);
                treeModel.insertNodeInto(node, parentNode, parentNode.getChildCount());
            }
        }
        return node;
    }


    private void updateWorkspaceFoldersDisplay() {
        try {
            // Apply JSON configuration if available
            if (showJsonEditor && jsonEditor != null && strategy instanceof ConfigurableWorkspaceFolderStrategy) {
                String jsonContent = jsonEditor.getText().trim();
                ((ConfigurableWorkspaceFolderStrategy) strategy).configure(jsonContent);
            }

            // Update empty text based on strategy type
            updateEmptyText();
            if (sendAtInitCheckbox != null) {
                boolean instances = strategy.hasServerInstances();
                sendAtInitCheckbox.setEnabled(!instances);
                sendAtInitCheckbox.setToolTipText(LanguageServerBundle.message(instances
                        ? "language.server.workspaceFolders.sendAtInit.instances.tooltip"
                        : "language.server.workspaceFolders.sendAtInit.tooltip"));
            }
        } catch (Exception e) {
            // On error, clear the tree
            showPreview(null);
            return;
        }

        // Get folders based on checkbox state (for preview/testing)
        boolean showInitFolders = isShowingInitFolders();
        var files = List.copyOf(openedFiles);
        // The strategy can do I/O for each folder and file, as for markers, so not on the UI thread
        ReadAction.nonBlocking(() -> computePreview(showInitFolders, files))
                .coalesceBy(this)
                .expireWith(this)
                .finishOnUiThread(ModalityState.any(), this::showPreview)
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    /**
     * What the preview shows: the folders of the configuration and the tested files, each with its place in the tree.
     */
    private record Preview(@NotNull List<FileLocation> folders,
                           @NotNull List<Pair<VirtualFile, FileLocation>> files,
                           boolean instances,
                           boolean sentAtInit) {
    }

    private @Nullable Preview computePreview(boolean showInitFolders, @NotNull List<VirtualFile> files) {
        try {
            List<WorkspaceFolder> folders;
            if (showInitFolders) {
                // Show only what would be sent at initialization (respects lazy config)
                folders = strategy.getInitialWorkspaceFolders(project, FileUriSupport.DEFAULT);
            } else {
                // Show all available workspace folders (discovery/preview mode)
                folders = strategy.getWorkspaceFolders(project, FileUriSupport.DEFAULT);
            }
            boolean instances = strategy.hasServerInstances();
            List<FileLocation> folderLocations = new ArrayList<>(folders.size());
            for (WorkspaceFolder folder : folders) {
                VirtualFile directory = instances ? LSPIJUtils.findResourceFor(folder.getUri()) : null;
                folderLocations.add(new FileLocation(directory != null ? getServerInstanceRoot(directory) : null, folder));
            }
            // Re-test opened files with new configuration
            List<Pair<VirtualFile, FileLocation>> fileLocations = new ArrayList<>(files.size());
            for (VirtualFile file : files) {
                fileLocations.add(Pair.create(file, locate(file)));
            }
            return new Preview(folderLocations, fileLocations, instances, strategy.sendAllFoldersOnInitialization());
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            // On error, clear the tree
            return null;
        }
    }

    private void showPreview(@Nullable Preview preview) {
        // Clear tree
        rootNode.removeAllChildren();
        folderNodes.clear();
        noRootNode = null;

        if (preview != null) {
            // Add folders to tree
            for (FileLocation location : preview.folders()) {
                if (!preview.instances()) {
                    getOrCreateFolderNode(location.folder(), rootNode, preview.sentAtInit(), false);
                } else if (location.instanceRoot() != null) {
                    getOrCreateFolderNode(location, preview.sentAtInit());
                } else {
                    // A folder without the root of an instance gets none, as a file there
                    getOrCreateFolderNode(location.folder(), getOrCreateNoRootNode(), preview.sentAtInit(), false);
                }
            }
            for (var file : preview.files()) {
                addFileNode(file.getFirst(), file.getSecond(), preview.sentAtInit());
            }
        }

        treeModel.reload();
        expandAll();

        if (preview != null && !openedFiles.equals(ContainerUtil.map2Set(preview.files(), file -> file.getFirst()))) {
            // A file was tested or removed while this preview was computed: the next one shows it as it is
            updateWorkspaceFoldersDisplay();
        }
    }

    private @NotNull String getNoRootNodeKey() {
        return strategy.hasServerInstances() ? NO_INSTANCE_NODE_KEY : NO_ROOT_NODE_KEY;
    }

    private boolean isShowingInitFolders() {
        // Which folders an instance gets at initialization is only known once there is a file of that instance
        return !strategy.hasServerInstances() && sendAtInitCheckbox != null && sendAtInitCheckbox.isSelected();
    }

    private void updateEmptyText() {
        StatusText emptyText = workspaceFoldersTree.getEmptyText();
        emptyText.clear();

        if (!(strategy instanceof ConfigurableWorkspaceFolderStrategy)) {
            emptyText.setText("No workspace folders");
            return;
        }

        ConfigurableWorkspaceFolderStrategy configurableStrategy = (ConfigurableWorkspaceFolderStrategy) strategy;
        RootType rootType = configurableStrategy.getRootType();
        boolean isLazy = !strategy.sendAllFoldersOnInitialization();

        if (rootType == RootType.MARKERS) {
            // Multi-line message for markers mode
            boolean hasOpenedFiles = !openedFiles.isEmpty();

            if (hasOpenedFiles) {
                // Files have been tested
                emptyText.setText("Workspace folders are discovered when you open a file,");
                emptyText.appendLine("by walking up to find marker files.");
            } else {
                // No files tested yet, explicit instruction to open a file
                emptyText.setText("Workspace folders are discovered when you open a file,");
                emptyText.appendLine("by walking up to find marker files.");
                emptyText.appendLine("");
                emptyText.appendText("Please ");
                emptyText.appendText("Open a file", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> browseFile());
                emptyText.appendText(" to test discovery.");
            }
        } else if (rootType != RootType.NONE && (isLazy || strategy.hasServerInstances())) {
            boolean hasOpenedFiles = !openedFiles.isEmpty();
            if (hasOpenedFiles) {
                emptyText.setText("Workspace folders will be discovered dynamically as files are opened");
            } else {
                emptyText.setText("Workspace folders are discovered when you open a file.");
                emptyText.appendLine("");
                emptyText.appendText("Drag & drop a file here or ");
                emptyText.appendText("Open a file", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> browseFile());
                emptyText.appendText(" to test discovery.");
            }
        } else {
            emptyText.setText("No workspace folders");
        }
    }

    private void expandAll() {
        for (int i = 0; i < workspaceFoldersTree.getRowCount(); i++) {
            workspaceFoldersTree.expandRow(i);
        }
    }

    @Nullable
    public String getJsonConfiguration() {
        if (!showJsonEditor || jsonEditor == null) {
            return null;
        }
        String jsonContent = jsonEditor.getText().trim();
        return (jsonContent.isEmpty() || jsonContent.equals("{}")) ? null : jsonContent;
    }

    public void setJsonConfiguration(@Nullable String jsonConfiguration) {
        if (!showJsonEditor || jsonEditor == null) {
            return;
        }
        if (jsonConfiguration == null || jsonConfiguration.trim().isEmpty()) {
            jsonEditor.setText("{}");
        } else {
            jsonEditor.setText(jsonConfiguration);
        }
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
            updateWorkspaceFoldersDisplay();
        });
    }

    @Override
    public void dispose() {
        if (updateAlarm != null && !updateAlarm.isDisposed()) {
            updateAlarm.cancelAllRequests();
        }
    }

    // Data classes for tree nodes
    private static class FolderNodeData {
        final VirtualFile file;
        final boolean sentAtInit;
        final boolean serverInstance;

        FolderNodeData(VirtualFile file, boolean sentAtInit, boolean serverInstance) {
            this.file = file;
            this.sentAtInit = sentAtInit;
            this.serverInstance = serverInstance;
        }
    }

    private static class FileNodeData {
        final VirtualFile file;

        FileNodeData(VirtualFile file) {
            this.file = file;
        }
    }

    // Tree cell renderer
    private static class WorkspaceFolderTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof FolderNodeData) {
                FolderNodeData data = (FolderNodeData) userObject;
                setIcon(AllIcons.Nodes.Folder);
                append(data.file.getPath(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                // Parse and render message with hyperlink
                String message = data.serverInstance ?
                        LanguageServerBundle.message("language.server.workspaceFolders.instance") :
                        data.sentAtInit ?
                        LanguageServerBundle.message("language.server.workspaceFolders.sentAtInit") :
                        LanguageServerBundle.message("language.server.workspaceFolders.lazy");
                appendTextWithHyperlink(" " + message);
            } else if (userObject instanceof FileNodeData) {
                FileNodeData data = (FileNodeData) userObject;
                setIcon(data.file.getFileType().getIcon());
                append(data.file.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                append(" - " + data.file.getPath(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else if (userObject instanceof String key && (NO_ROOT_NODE_KEY.equals(key) || NO_INSTANCE_NODE_KEY.equals(key))) {
                // It's the NO_ROOT node
                setIcon(AllIcons.General.Warning);
                append(LanguageServerBundle.message(key), SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
        }

        private void appendTextWithHyperlink(String text) {
            // Parse <hyperlink>text</hyperlink> tags
            int start = text.indexOf("<hyperlink>");
            if (start == -1) {
                append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                return;
            }

            int end = text.indexOf("</hyperlink>", start);
            if (end == -1) {
                append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                return;
            }

            // Append text before hyperlink
            if (start > 0) {
                append(text.substring(0, start), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }

            // Append hyperlink text
            String linkText = text.substring(start + "<hyperlink>".length(), end);
            append(linkText, SimpleTextAttributes.LINK_ATTRIBUTES);

            // Append text after hyperlink
            if (end + "</hyperlink>".length() < text.length()) {
                append(text.substring(end + "</hyperlink>".length()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }
    }
}
