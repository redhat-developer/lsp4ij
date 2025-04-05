package com.redhat.devtools.lsp4ij.fixtures;

import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class LSPDiagnosticsFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    public LSPDiagnosticsFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    protected void assertDiagnostics(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull String jsonDiagnostics) {
        if (jsonDiagnostics.trim().startsWith("[")) {
            jsonDiagnostics = "{\"diagnostics\":" + jsonDiagnostics + "}";
        }
        PublishDiagnosticsParams params = JSONUtils.getLsp4jGson().fromJson(jsonDiagnostics, PublishDiagnosticsParams.class);
        assertDiagnostics(fileName, editorContentText, params.getDiagnostics(), "");
    }

    protected void assertDiagnostics(@NotNull String fileName,
                                    @NotNull String editorContentText,
                                    @NotNull List<Diagnostic> diagnostics,
                                    @NotNull String... expectedItems) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);
        MockLanguageServer.INSTANCE.setDiagnostics(diagnostics);
        // Open editor for a given file name and content (which declares <caret> to know where the completion is triggered).
        myFixture.configureByText(fileName, editorContentText);
        // Process completion
        myFixture.doHighlighting();
        myFixture.checkHighlighting();
    }

}
