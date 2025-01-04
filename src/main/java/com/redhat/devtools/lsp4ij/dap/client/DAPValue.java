package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DAPValue extends XNamedValue {

    @NotNull
    private final DAPClient client;
    @NotNull
    private final Variable variable;
    @Nullable
    private final Icon icon;

    public DAPValue(@NotNull DAPClient client,
                    @NotNull Variable variable,
                    @Nullable Icon icon) {
        super(variable.getName());
        this.client = client;
        this.variable = variable;
        this.icon = icon;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        XValuePresentation presentation = getPresentation();
        boolean hasChildren = false;
        if (variable.getVariablesReference() > 0) {
            hasChildren = true;
        }
        node.setPresentation(icon, presentation, hasChildren);
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {

            var server = client.getDebugProtocolServer();
            if (server != null) {
                VariablesArguments varArgs = new VariablesArguments();
                varArgs.setVariablesReference(variable.getVariablesReference());

                server.variables(varArgs)
                        .thenAccept(variablesResponse -> {
                            XValueChildrenList list = new XValueChildrenList();
                            for (Variable variable : variablesResponse.getVariables()) {
                                list.add(variable.getName(), new DAPValue(client, variable, getIconFor(variable)));
                            }
                            node.addChildren(list, true);
                        });
            }
    }

    @Nullable
    @Override
    public XValueModifier getModifier() {
        return null;
    }

    @NotNull
    private XValuePresentation getPresentation() {
        return client.getClientFeatures().getValuePresentation(variable);
    }

    @Nullable
    private static PsiElement findTargetElement(@NotNull Project project, @NotNull XSourcePosition position,
                                                @NotNull Editor editor, @NotNull String name) {
        // Todo
        return null;
    }

    @Override
    public void computeSourcePosition(@NotNull XNavigatable navigatable) {
        readActionInPooledThread(new Runnable() {

            @Override
            public void run() {
                navigatable.setSourcePosition(findPosition());
            }

            @Nullable
            private XSourcePosition findPosition() {
                XDebugSession debugSession = client.getSession();
                if (debugSession == null) {
                    return null;
                }
                XStackFrame stackFrame = debugSession.getCurrentStackFrame();
                if (stackFrame == null) {
                    return null;
                }
                Project project = debugSession.getProject();
                XSourcePosition position = debugSession.getCurrentPosition();
                Editor editor = ((FileEditorManagerImpl) FileEditorManager.getInstance(project))
                        .getSelectedTextEditor(true);
                if (editor == null || position == null) {
                    return null;
                }
                String name = myName.startsWith("&") ? myName.replaceFirst("&", "") : myName;
                PsiElement resolved = findTargetElement(project, position, editor, name);
                if (resolved == null) {
                    return null;
                }
                VirtualFile virtualFile = resolved.getContainingFile().getVirtualFile();
                return XDebuggerUtil.getInstance().createPositionByOffset(virtualFile, resolved.getTextOffset());
            }
        });
    }

    private static void readActionInPooledThread(@NotNull Runnable runnable) {
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                ApplicationManager.getApplication().runReadAction(runnable));
    }

    @NotNull
    @Override
    public ThreeState computeInlineDebuggerData(@NotNull XInlineDebuggerDataCallback callback) {
        computeSourcePosition(callback::computed);
        return ThreeState.YES;
    }

    @Override
    public boolean canNavigateToSource() {
        return true;
    }

    @Override
    public boolean canNavigateToTypeSource() {
        // Todo
        return false;
    }

    @Override
    public void computeTypeSourcePosition(@NotNull XNavigatable navigatable) {
        // Todo
    }

    public static Icon getIconFor(@NotNull Variable variable) {
        String variableType = variable.getType();
        /*if (BallerinaValueType.ARRAY.getValue().equals(variableType)
                || BallerinaValueType.TUPLE.getValue().equals(variableType)) {
            return AllIcons.Debugger.Db_array;
        } else if (BallerinaValueType.OBJECT.getValue().equals(variableType)
                || BallerinaValueType.RECORD.getValue().equals(variableType)
                || BallerinaValueType.MAP.getValue().equals(variableType)
                || BallerinaValueType.JSON.getValue().equals(variableType)) {
            return AllIcons.Debugger.Db_db_object;
        } else if (variableType.equals(BallerinaValueType.XML.getValue())) {
            return AllIcons.FileTypes.Xml;
        } else if (variableType.equals(BallerinaValueType.ERROR.getValue())) {
            return AllIcons.Nodes.ExceptionClass;
        } else {*/
            return AllIcons.Nodes.Variable;
        //}
    }
}