/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.DummyLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.ReadWriteAccessUsage;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageSearcher;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import com.intellij.util.Processor;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Find Usages of a language plugin's element lists the usages that the language plugin's search finds and those of
 * the language server. A reference that both find is shown once, on one line in the language plugin's group, as the
 * usage view merges the usages of a line only within one group (#729).
 */
public class LSPUsageMergeWithLanguagePluginTest extends LSPCodeInsightFixtureTestCase {

    private static final String EXTENSION = "lsp4ijusages";

    private static final String NAME = "ArgNode";

    // The language plugin has a reference at each "ArgNode" after a "(", and none in the comment
    private static final String TEXT = """
            ArgNode
            first(ArgNode)
            second(ArgNode)
            third(ArgNode)
            // ArgNode
            """;

    private static final Range DECLARATION = range(0, 0);
    private static final Range FIRST = range(1, 6);
    private static final Range SECOND = range(2, 7);
    private static final Range THIRD = range(3, 6);
    private static final Range COMMENT = range(4, 3);

    private static final String METHOD_CALL = "Method call";

    // A usage in the tree of the usage view: its line number, followed by its text
    private static final Pattern USAGE = Pattern.compile("(\\d+)\\s*(\\D.*)");

    public LSPUsageMergeWithLanguagePluginTest() {
        super("*." + EXTENSION);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Unregistering the file type unregisters its language and the language's element types, so that each test
        // has its own
        TestFileType fileType = new TestFileType(new TestLanguage());
        ((FileTypeManagerImpl) FileTypeManager.getInstance()).registerFileType(fileType,
                List.of(new ExtensionFileNameMatcher(EXTENSION)), getTestRootDisposable(), new DefaultPluginDescriptor("plugin"));
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(fileType.getLanguage(), new TestParserDefinition(fileType),
                getTestRootDisposable());
        // A usage type of the language plugin, like the "Method call" of the Scala plugin
        UsageType methodCall = new UsageType(() -> METHOD_CALL);
        UsageTypeProvider.EP_NAME.getPoint().registerExtension(
                element -> element instanceof Body ? methodCall : null, LoadingOrder.FIRST, getTestRootDisposable());
    }

    public void testAReferenceThatTheLanguagePluginAlsoFindsIsShownOnce() throws Exception {
        PsiFile file = open();
        Body body = assertInstanceOf(file.getFirstChild(), Body.class);
        // The language plugin finds the first two references, not the third, which it doesn't resolve to the element
        List<UsageInfo> pluginUsages = List.of(
                new UsageInfo(body.findReferenceAt(offsetOf(FIRST))),
                new UsageInfo(body.findReferenceAt(offsetOf(SECOND))));

        List<String> usages = findUsages(languagePluginHandler(new Declaration(body), pluginUsages));

        assertSameElements(usages,
                definitions() + " 1 ArgNode",
                // found by both
                METHOD_CALL + " 2 first(ArgNode)",
                // found by the language plugin
                METHOD_CALL + " 3 second(ArgNode)",
                // found by the language server, at a reference of the language plugin
                METHOD_CALL + " 4 third(ArgNode)",
                // found by the language server
                references() + " 5 // ArgNode");
    }

    public void testUsagesOfAnElementOfLSP4IJAreListedAsBefore() throws Exception {
        PsiFile file = open();
        // As when Find Usages is invoked on the declaration, if the language plugin's Find Usages isn't used
        TextRange declaration = TextRange.from(offsetOf(DECLARATION), NAME.length());
        LSPUsageTriggeredPsiElement element = new LSPUsageTriggeredPsiElement(file, declaration);

        List<String> usages = findUsages(new LSPFindUsagesHandler(element));

        assertSameElements(usages,
                definitions() + " 1 ArgNode",
                references() + " 2 first(ArgNode)",
                references() + " 4 third(ArgNode)",
                references() + " 5 // ArgNode");
    }

    public void testAReferenceKeepsItsAccess() {
        PsiFile file = myFixture.configureByText("main." + EXTENSION, TEXT);
        Body body = assertInstanceOf(file.getFirstChild(), Body.class);
        LSPUsagePsiElement reference = new LSPUsagePsiElement(file, TextRange.from(offsetOf(FIRST), NAME.length()));
        reference.setKind(LSPUsagePsiElement.UsageKind.references);
        reference.setAccess(ReadWriteAccessDetector.Access.Write);

        Usage usage = LSPUsageSearcher.toUsage(reference, new Declaration(body));

        assertTrue(assertInstanceOf(usage, ReadWriteAccessUsage.class).isAccessedForWriting());
        // at the language plugin's reference
        assertSame(body, assertInstanceOf(usage, UsageInfo2UsageAdapter.class).getElement());
    }

    /**
     * Opens the file, whose language server gives the declaration as definition, and the declaration and the
     * references on the lines of "first", "third" and the comment as references.
     */
    private @NotNull PsiFile open() throws Exception {
        MockLanguageServer.reset();
        PsiFile file = myFixture.configureByText("main." + EXTENSION, TEXT);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        String uri = LSPIJUtils.toUriAsString(file);
        MockLanguageServer.INSTANCE.setDefinition(List.of(new Location(uri, DECLARATION)));
        // With the declaration, which LSP4IJ asks for
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockReferences(new Location(uri, DECLARATION),
                new Location(uri, FIRST), new Location(uri, THIRD), new Location(uri, COMMENT));
        return file;
    }

    /**
     * Returns the Find Usages handler of the language plugin for the given element, whose search finds the given
     * usages.
     */
    private static @NotNull FindUsagesHandler languagePluginHandler(@NotNull Declaration element,
                                                                    @NotNull List<UsageInfo> usages) {
        return new FindUsagesHandler(element) {
            @Override
            public boolean processElementUsages(@NotNull PsiElement element,
                                                @NotNull Processor<? super UsageInfo> processor,
                                                @NotNull FindUsagesOptions options) {
                return usages.stream().allMatch(processor::process);
            }
        };
    }

    /**
     * Returns the usages that the usage view shows when Find Usages searches with the given handler, as the usage
     * type group that each is shown in, followed by its line number and its text.
     */
    private @NotNull List<String> findUsages(@NotNull FindUsagesHandler handler) throws Exception {
        PsiElement element = handler.getPsiElement();
        // As Find Usages: the usages of the handler and of the custom usage searchers, among which LSP4IJ's
        UsageSearcher searcher = FindUsagesManager.createUsageSearcher(handler, new PsiElement[]{element},
                PsiElement.EMPTY_ARRAY, new FindUsagesOptions(myFixture.getProject()));
        List<Usage> usages = new CopyOnWriteArrayList<>();
        // Find Usages searches in a background thread
        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> ProgressManager.getInstance()
                        .runProcess(() -> searcher.generate(usages::add), new EmptyProgressIndicator()))
                .get(30, TimeUnit.SECONDS);
        List<UsageTarget> targets = List.of(new PsiElement2UsageTargetAdapter(element, true));
        String tree = myFixture.getUsageViewTreeTextRepresentation(targets, usages);
        return usagesOf(tree);
    }

    /**
     * Returns the usages of the given tree of the usage view, as the usage type group of each, followed by its line
     * number and its text.
     */
    private static @NotNull List<String> usagesOf(@NotNull String tree) {
        List<String> usages = new ArrayList<>();
        String group = null;
        for (String node : tree.lines().map(String::strip).toList()) {
            Matcher usage = USAGE.matcher(node);
            String usageType = Stream.of(definitions(), METHOD_CALL, references())
                    .filter(name -> node.startsWith(name + " ("))
                    .findFirst()
                    .orElse(null);
            if (usageType != null) {
                group = usageType;
            } else if (usage.matches()) {
                usages.add(group + " " + usage.group(1) + " " + usage.group(2));
            }
        }
        return usages;
    }

    private static @NotNull String definitions() {
        return LanguageServerBundle.message("usage.type.definitions");
    }

    private static @NotNull String references() {
        return LanguageServerBundle.message("usage.type.references");
    }

    private int offsetOf(@NotNull Range range) {
        return LSPIJUtils.toOffset(range.getStart(), myFixture.getEditor().getDocument());
    }

    private static @NotNull Range range(int line, int character) {
        return new Range(new Position(line, character), new Position(line, character + NAME.length()));
    }

    /**
     * The language of a language plugin.
     */
    private static final class TestLanguage extends Language {

        TestLanguage() {
            super("LSP4IJ usages test language");
        }
    }

    private static final class TestFileType extends LanguageFileType {

        TestFileType(@NotNull Language language) {
            super(language);
        }

        @Override
        public @NotNull String getName() {
            return "LSP4IJ usages test file type";
        }

        @Override
        public @NotNull String getDescription() {
            return "LSP4IJ usages test file type";
        }

        @Override
        public @NotNull String getDefaultExtension() {
            return EXTENSION;
        }

        @Override
        public @Nullable Icon getIcon() {
            return null;
        }
    }

    /**
     * Parses a file as a single {@link Body}.
     */
    private static final class TestParserDefinition implements ParserDefinition {

        private final @NotNull LanguageFileType fileType;
        private final @NotNull IFileElementType fileElementType;
        private final @NotNull IElementType textElementType;
        private final @NotNull IElementType bodyElementType;

        TestParserDefinition(@NotNull LanguageFileType fileType) {
            this.fileType = fileType;
            Language language = fileType.getLanguage();
            fileElementType = new IFileElementType(language);
            textElementType = new IElementType("LSP4IJ_USAGES_TEST_TEXT", language);
            bodyElementType = new IElementType("LSP4IJ_USAGES_TEST_BODY", language);
        }

        @Override
        public @NotNull Lexer createLexer(Project project) {
            return new DummyLexer(textElementType);
        }

        @Override
        public @NotNull PsiParser createParser(Project project) {
            return (root, builder) -> {
                PsiBuilder.Marker file = builder.mark();
                PsiBuilder.Marker body = builder.mark();
                while (!builder.eof()) {
                    builder.advanceLexer();
                }
                body.done(bodyElementType);
                file.done(root);
                return builder.getTreeBuilt();
            };
        }

        @Override
        public @NotNull IFileElementType getFileNodeType() {
            return fileElementType;
        }

        @Override
        public @NotNull TokenSet getCommentTokens() {
            return TokenSet.EMPTY;
        }

        @Override
        public @NotNull TokenSet getStringLiteralElements() {
            return TokenSet.EMPTY;
        }

        @Override
        public @NotNull PsiElement createElement(ASTNode node) {
            return new Body(node);
        }

        @Override
        public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
            return new PsiFileBase(viewProvider, fileType.getLanguage()) {
                @Override
                public @NotNull FileType getFileType() {
                    return fileType;
                }
            };
        }
    }

    /**
     * The PSI of a file, with the language plugin's references: one at each "ArgNode" after a "(".
     */
    private static final class Body extends ASTWrapperPsiElement {

        Body(@NotNull ASTNode node) {
            super(node);
        }

        @Override
        public PsiReference @NotNull [] getReferences() {
            List<PsiReference> references = new ArrayList<>();
            String text = getText();
            for (int start = text.indexOf("(" + NAME); start >= 0; start = text.indexOf("(" + NAME, start + 1)) {
                references.add(new PsiReferenceBase.Immediate<>(this, TextRange.from(start + 1, NAME.length()), this));
            }
            return references.toArray(PsiReference.EMPTY_ARRAY);
        }
    }

    /**
     * The language plugin's element whose usages are searched: the "ArgNode" of the first line.
     */
    private static final class Declaration extends FakePsiElement {

        private final @NotNull Body body;

        Declaration(@NotNull Body body) {
            this.body = body;
        }

        @Override
        public PsiElement getParent() {
            return body;
        }

        @Override
        public TextRange getTextRange() {
            return TextRange.from(0, NAME.length());
        }

        @Override
        public int getTextOffset() {
            return 0;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public String getText() {
            return NAME;
        }

        @Override
        public boolean isValid() {
            return body.isValid();
        }
    }
}
