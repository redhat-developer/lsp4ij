package com.redhat.devtools.lsp4ij.dap;

import com.intellij.json.JsonFileType;
import com.intellij.json.JsonLanguage;
import com.intellij.json.json5.Json5Language;
import com.intellij.openapi.fileTypes.MockLanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;

public class DAPDebuggerEditorsProviderTest extends LightPlatformTestCase {

    public void testWithSimpleLanguage() {
        PsiFile textFile = createFile("test.txt", "Hello, world.");
        assertEquals(PlainTextFileType.INSTANCE, textFile.getFileType());
        assertEquals(PlainTextLanguage.INSTANCE, textFile.getLanguage());

        DAPDebuggerEditorsProvider debuggerEditorsProvider = new DAPDebuggerEditorsProvider(PlainTextFileType.INSTANCE, null);

        PsiFile codeFragment = debuggerEditorsProvider.createExpressionCodeFragment(getProject(), "", textFile, true);
        assertEquals(PlainTextFileType.INSTANCE, codeFragment.getFileType());
        assertEquals(PlainTextLanguage.INSTANCE, codeFragment.getLanguage());
    }

    public void testWithDialectLanguage() {
        // Create a file with the dialect language
        PsiFile dialectFile = PsiFileFactory.getInstance(getProject()).createFileFromText("test.json", Json5Language.INSTANCE, "{}");
        VirtualFile dialectVirtualFile = dialectFile.getVirtualFile();
        // Force the file type to be the base instead of the one inferred from the dialect language used above
        ((LightVirtualFile) dialectVirtualFile).setFileType(JsonFileType.INSTANCE);
        assertEquals(JsonFileType.INSTANCE, dialectFile.getFileType());
        assertEquals(Json5Language.INSTANCE, dialectFile.getLanguage());

        // This should also use the base file type
        DAPDebuggerEditorsProvider debuggerEditorsProvider = new DAPDebuggerEditorsProvider(MockLanguageFileType.INSTANCE, null);

        // If the original issue exists, this will fail with "JSON5 doesn't participate in view provider..."
        PsiFile codeFragment = debuggerEditorsProvider.createExpressionCodeFragment(getProject(), "", dialectFile, true);
        assertEquals(JsonFileType.INSTANCE, codeFragment.getFileType());
        // It should have degraded to the base language
        assertEquals(JsonLanguage.INSTANCE, codeFragment.getLanguage());
    }
}
