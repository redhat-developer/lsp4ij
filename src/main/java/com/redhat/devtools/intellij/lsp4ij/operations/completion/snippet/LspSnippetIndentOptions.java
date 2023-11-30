/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet;

/**
 * LSP snippet indent options used to replace LSP snippet content '\n' and '\t:
 *
 * <ul>
 *     <li>'\n' with the line separator settings of the LSP client.</li>
 *     <li>'\t' with the tab size settings of the LSP client if the LSP client settings has insert spaces.</li>
 * </ul>
 */
public class LspSnippetIndentOptions {

    private static final String CRLF = "\r\n";

	private final int tabSize;

    private final boolean insertSpaces;

    private final String lineSeparator;

    private String replacementTab;

    public LspSnippetIndentOptions(int tabSize, boolean insertSpaces, String lineSeparator) {
        this.tabSize = tabSize;
        this.insertSpaces = insertSpaces;
        this.lineSeparator = lineSeparator;
    }

    public int getTabSize() {
        return tabSize;
    }

    public boolean isInsertSpaces() {
        return insertSpaces;
    }

    public String getLineSeparator() {
        return lineSeparator;
    }

    /**
     * Replace '\n' and '\t' declared in the snippets according to the LSP client settings.
     *
     * @param text the text to format according to the LSP client settings.
     *
     * @return the result of '\n' and '\t' replacement declared in the snippets according to the LSP client settings.
     */
    protected String formatText(String text) {
        if (!shouldBeFormatted(text)) {
            return text;
        }
        StringBuilder formattedText = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\t':
                    if (!insertSpaces) {
                        formattedText.append(c);
                    } else {
                        formattedText.append(getSpacesReplacement());
                    }
                    break;
                case '\n':
                	if (shouldIgnoreLineSeparatorSettings(text, i)) {
                		formattedText.append(c);
                	} else {
                		formattedText.append(lineSeparator);
                	}                    
                    break;
                default:
                    formattedText.append(c);
            }
        }
        return formattedText.toString();
    }
    
    private boolean shouldIgnoreLineSeparatorSettings(String text, int i) {
    	 if (lineSeparator == null || lineSeparator.isEmpty()) {
    		 // Line separator is not customized
    		 return true;
    	 }
    	 if (lineSeparator.equals(CRLF)) {
    		 // Line separator settings is '\r\n', check that the previous character of the text is not '\r'
    		 return i >= 1 && text.charAt(i-1) == '\r'; 
    	 }
    	 return false;
    }

    public String getSpacesReplacement() {
        if(replacementTab == null) {
            StringBuilder spaces = new StringBuilder();
            for (int i = 0; i < tabSize; i++) {
                spaces.append(' ');
            }
            replacementTab = spaces.toString();
        }
        return replacementTab;
    }

    /**
     * Returns true if the given text contains '\n' or '\t' and false otherwise.
     * @param text the text to format according the LSP client settings.
     * @return true if the given text contains '\n' or '\t' and false otherwise.
     */
    public static boolean shouldBeFormatted(String text) {
        return text.indexOf('\t') != -1 || text.indexOf('\n') != -1;
    }

}
