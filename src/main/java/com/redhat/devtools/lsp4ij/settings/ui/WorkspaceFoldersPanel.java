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
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
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

/**
 * Panel for configuring workspace folders with testing capabilities.
 */
public class WorkspaceFoldersPanel extends JPanel implements Disposable {

    private static final String NO_ROOT_NODE_KEY = "language.server.workspaceFolders.noRoot";

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
                if (node.getUserObject() instanceof FolderNodeData) {
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
                    if (node.getUserObject() instanceof FolderNodeData) {
                        workspaceFoldersTree.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        return;
                    }
                }
                workspaceFoldersTree.setCursor(Cursor.getDefaultCursor());
            }
        });
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

    private void removeSelectedFile() {
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

        // If parent folder is now empty and not in the config folders, remove it too
        if (parentNode != null && parentNode.getChildCount() == 0) {
            Object parentUserObject = parentNode.getUserObject();

            if (parentUserObject instanceof FolderNodeData) {
                FolderNodeData folderData = (FolderNodeData) parentUserObject;
                String folderUri = FileUriSupport.toString(folderData.file, FileUriSupport.DEFAULT);

                // Only remove folder node if it's not in the current config
                boolean isConfigFolder = false;
                List<WorkspaceFolder> configFolders;

                boolean showInitFolders = sendAtInitCheckbox != null && sendAtInitCheckbox.isSelected();
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
                }
            } else if (NO_ROOT_NODE_KEY.equals(parentUserObject)) {
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

    private void testFile(@NotNull VirtualFile file) {
        // Always run slow operation in background thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            WorkspaceFolder folder = strategy.getWorkspaceFolderForFile(file, project, FileUriSupport.DEFAULT);

            // Update UI on EDT
            ApplicationManager.getApplication().invokeLater(() -> {
                addTestedFile(file, folder);
            });
        });
    }

    private void addTestedFile(@NotNull VirtualFile file, @Nullable WorkspaceFolder folder) {
        // Track opened file
        openedFiles.add(file);

        if (folder != null) {
            // Find or create the folder node
            DefaultMutableTreeNode folderNode = folderNodes.get(folder.getUri());
            if (folderNode == null) {
                VirtualFile folderFile = LSPIJUtils.findResourceFor(folder.getUri());
                if (folderFile != null) {
                    folderNode = new DefaultMutableTreeNode(new FolderNodeData(folderFile, true));
                    folderNodes.put(folder.getUri(), folderNode);
                    treeModel.insertNodeInto(folderNode, rootNode, rootNode.getChildCount());
                }
            }

            if (folderNode != null) {
                // Add file under folder
                DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(new FileNodeData(file));
                treeModel.insertNodeInto(fileNode, folderNode, folderNode.getChildCount());
                workspaceFoldersTree.expandPath(new TreePath(folderNode.getPath()));
            }
        } else {
            // Add to "No root" node
            if (noRootNode == null) {
                noRootNode = new DefaultMutableTreeNode(NO_ROOT_NODE_KEY);
                treeModel.insertNodeInto(noRootNode, rootNode, rootNode.getChildCount());
            }
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(new FileNodeData(file));
            treeModel.insertNodeInto(fileNode, noRootNode, noRootNode.getChildCount());
            workspaceFoldersTree.expandPath(new TreePath(noRootNode.getPath()));
        }
    }


    private void updateWorkspaceFoldersDisplay() {
        // Clear tree
        rootNode.removeAllChildren();
        folderNodes.clear();
        noRootNode = null;

        try {
            // Apply JSON configuration if available
            if (showJsonEditor && jsonEditor != null && strategy instanceof ConfigurableWorkspaceFolderStrategy) {
                String jsonContent = jsonEditor.getText().trim();
                ((ConfigurableWorkspaceFolderStrategy) strategy).configure(jsonContent);
            }

            // Update empty text based on strategy type
            updateEmptyText();

            // Get folders based on checkbox state (for preview/testing)
            List<WorkspaceFolder> folders;
            boolean showInitFolders = sendAtInitCheckbox != null && sendAtInitCheckbox.isSelected();

            if (showInitFolders) {
                // Show only what would be sent at initialization (respects lazy config)
                folders = strategy.getInitialWorkspaceFolders(project, FileUriSupport.DEFAULT);
            } else {
                // Show all available workspace folders (discovery/preview mode)
                folders = strategy.getWorkspaceFolders(project, FileUriSupport.DEFAULT);
            }

            // Add folders to tree
            for (WorkspaceFolder folder : folders) {
                VirtualFile file = LSPIJUtils.findResourceFor(folder.getUri());
                if (file != null) {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FolderNodeData(file, strategy.sendAllFoldersOnInitialization()));
                    folderNodes.put(folder.getUri(), node);
                    treeModel.insertNodeInto(node, rootNode, rootNode.getChildCount());
                }
            }

            // Re-test opened files with new configuration
            for (VirtualFile openedFile : openedFiles) {
                WorkspaceFolder folder = strategy.getWorkspaceFolderForFile(openedFile, project, FileUriSupport.DEFAULT);

                if (folder != null) {
                    // Find or create folder node
                    DefaultMutableTreeNode folderNode = folderNodes.get(folder.getUri());
                    if (folderNode == null) {
                        VirtualFile folderFile = LSPIJUtils.findResourceFor(folder.getUri());
                        if (folderFile != null) {
                            folderNode = new DefaultMutableTreeNode(new FolderNodeData(folderFile, strategy.sendAllFoldersOnInitialization()));
                            folderNodes.put(folder.getUri(), folderNode);
                            treeModel.insertNodeInto(folderNode, rootNode, rootNode.getChildCount());
                        }
                    }

                    if (folderNode != null) {
                        // Add file under folder
                        DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(new FileNodeData(openedFile));
                        treeModel.insertNodeInto(fileNode, folderNode, folderNode.getChildCount());
                    }
                } else {
                    // File has no workspace folder, add to "No root"
                    if (noRootNode == null) {
                        noRootNode = new DefaultMutableTreeNode(NO_ROOT_NODE_KEY);
                        treeModel.insertNodeInto(noRootNode, rootNode, rootNode.getChildCount());
                    }
                    DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(new FileNodeData(openedFile));
                    treeModel.insertNodeInto(fileNode, noRootNode, noRootNode.getChildCount());
                }
            }

            treeModel.reload();
            expandAll();
        } catch (Exception e) {
            // On error, clear the tree
            treeModel.reload();
        }
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
        } else if (isLazy) {
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

        FolderNodeData(VirtualFile file, boolean sentAtInit) {
            this.file = file;
            this.sentAtInit = sentAtInit;
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
                String message = data.sentAtInit ?
                        LanguageServerBundle.message("language.server.workspaceFolders.sentAtInit") :
                        LanguageServerBundle.message("language.server.workspaceFolders.lazy");
                appendTextWithHyperlink(" " + message);
            } else if (userObject instanceof FileNodeData) {
                FileNodeData data = (FileNodeData) userObject;
                setIcon(data.file.getFileType().getIcon());
                append(data.file.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                append(" - " + data.file.getPath(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else if (userObject instanceof String && NO_ROOT_NODE_KEY.equals(userObject)) {
                // It's the NO_ROOT node
                setIcon(AllIcons.General.Warning);
                append(LanguageServerBundle.message(NO_ROOT_NODE_KEY), SimpleTextAttributes.ERROR_ATTRIBUTES);
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
