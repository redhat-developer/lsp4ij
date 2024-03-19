package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.model.Pointer;
import com.intellij.navigation.TargetPresentation;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LSPDocumentationTarget implements DocumentationTarget {

    private final Hover hover;

    public LSPDocumentationTarget(Hover hover) {
        this.hover = hover;
    }

    @NotNull
    @Override
    public TargetPresentation computePresentation() {
        return TargetPresentation
                .builder("TODO")
                .presentation();
    }

    @Nullable
    @Override
    public DocumentationResult computeDocumentation() {
        return DocumentationResult.documentation(toHTML(hover));
    }

    @NotNull
    @Override
    public Pointer<? extends DocumentationTarget> createPointer() {
        return  Pointer.hardPointer(this);
    }

    private String toHTML(Hover hover) {
        MarkupContent content = getHoverString(hover);
        return content != null ? content.getValue() : "";
    }

    private static @Nullable MarkupContent getHoverString(Hover hover) {
        if (hover == null) {
            // textDocument/hover may return null
            return null;
        }
        Either<List<Either<String, MarkedString>>, MarkupContent> hoverContent = hover.getContents();
        if (hoverContent == null) {
            return null;
        }
        if (hoverContent.isLeft()) {
            List<Either<String, MarkedString>> contents = hoverContent.getLeft();
            if (contents == null || contents.isEmpty()) {
                return null;
            }
            String s = contents.stream()
                    .map(content -> {
                        if (content.isLeft()) {
                            return content.getLeft();
                        } else if (content.isRight()) {
                            MarkedString markedString = content.getRight();
                            // TODO this won't work fully until markup parser will support syntax
                            // highlighting but will help display
                            // strings with language tags, e.g. without it things after <?php tag aren't
                            // displayed
                            if (markedString.getLanguage() != null && !markedString.getLanguage().isEmpty()) {
                                return String.format("```%s%n%s%n```", markedString.getLanguage(), markedString.getValue()); //$NON-NLS-1$
                            } else {
                                return markedString.getValue();
                            }
                        } else {
                            return ""; //$NON-NLS-1$
                        }
                    })
                    .filter(((Predicate<String>) String::isEmpty).negate())
                    .collect(Collectors.joining("\n\n"));
            return new MarkupContent(MarkupKind.PLAINTEXT, s);
        } else {
            return hoverContent.getRight();
        }
    }
}
