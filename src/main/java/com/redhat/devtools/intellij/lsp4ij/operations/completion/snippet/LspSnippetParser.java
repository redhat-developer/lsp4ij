/*******************************************************************************
 * Copyright (c) 2013, 2016 EclipseSource.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * LSP snippet parser.
 * <p>
 * This code is a copy/paste from
 * https://github.com/ralfstx/minimal-json/blob/master/com.eclipsesource.json/src/main/java/com/eclipsesource/json/JsonParser.java
 * adapted for LSP Snippet.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax">https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax</a>
 */
public class LspSnippetParser {
    private static final int MIN_BUFFER_SIZE = 10;
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private final LspSnippetHandler handler;
    private Reader reader;
    private char[] buffer;
    private int bufferOffset;
    private int index;
    private int fill;
    private int line;
    private int lineOffset;
    private int current;
    private StringBuilder captureBuffer;
    private int captureStart;
    private int nestingLevel;

    /*
     * | bufferOffset v [a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t] < input
     * [l|m|n|o|p|q|r|s|t|?|?] < buffer ^ ^ | index fill
     */

    public LspSnippetParser(LspSnippetHandler handler) {
        this.handler = handler;
    }

    /**
     * Parses the given input string. The input must contain a valid JSON value,
     * optionally padded with whitespace.
     *
     * @param string the input string, must be valid JSON
     * @throws ParseException if the input is not valid JSON
     */
    public void parse(String string) {
        if (string == null) {
            throw new NullPointerException("string is null");
        }
        int bufferSize = Math.max(MIN_BUFFER_SIZE, Math.min(DEFAULT_BUFFER_SIZE, string.length()));
        try {
            parse(new StringReader(string), bufferSize);
        } catch (IOException exception) {
            // StringReader does not throw IOException
            throw new RuntimeException(exception);
        }
    }

    /**
     * Reads the entire input from the given reader and parses it as JSON. The input
     * must contain a valid JSON value, optionally padded with whitespace.
     * <p>
     * Characters are read in chunks into a default-sized input buffer. Hence,
     * wrapping a reader in an additional <code>BufferedReader</code> likely won't
     * improve reading performance.
     * </p>
     *
     * @param reader the reader to read the input from
     * @throws IOException    if an I/O error occurs in the reader
     * @throws ParseException if the input is not valid JSON
     */
    public void parse(Reader reader) throws IOException {
        parse(reader, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Reads the entire input from the given reader and parses it as JSON. The input
     * must contain a valid JSON value, optionally padded with whitespace.
     * <p>
     * Characters are read in chunks into an input buffer of the given size. Hence,
     * wrapping a reader in an additional <code>BufferedReader</code> likely won't
     * improve reading performance.
     * </p>
     *
     * @param reader     the reader to read the input from
     * @param buffersize the size of the input buffer in chars
     * @throws IOException    if an I/O error occurs in the reader
     * @throws ParseException if the input is not valid JSON
     */
    public void parse(Reader reader, int buffersize) throws IOException {
        if (reader == null) {
            throw new NullPointerException("reader is null");
        }
        if (buffersize <= 0) {
            throw new IllegalArgumentException("buffersize is zero or negative");
        }
        this.reader = reader;
        buffer = new char[buffersize];
        bufferOffset = 0;
        index = 0;
        fill = 0;
        line = 1;
        lineOffset = 0;
        current = 0;
        captureStart = -1;
        handler.startSnippet();
        read();
        readAny();
        handler.endSnippet();
        if (!isEndOfText()) {
            throw error("Unexpected character");
        }
    }

    // Snippet syntax:

	/*
	  	any         ::= tabstop | placeholder | choice | variable | text
		tabstop     ::= '$' int | '${' int '}'
		placeholder ::= '${' int ':' any '}'
		choice      ::= '${' int '|' text (',' text)* '|}'
		variable    ::= '$' var | '${' var }'
		                | '${' var ':' any '}'
		                | '${' var '/' regex '/' (format | text)+ '/' options '}'
		format      ::= '$' int | '${' int '}'
		                | '${' int ':' '/upcase' | '/downcase' | '/capitalize' '}'
		                | '${' int ':+' if '}'
		                | '${' int ':?' if ':' else '}'
		                | '${' int ':-' else '}' | '${' int ':' else '}'
		regex       ::= Regular Expression value (ctor-string)
		options     ::= Regular Expression option (ctor-options)
		var         ::= [_a-zA-Z] [_a-zA-Z0-9]*
		int         ::= [0-9]+
		text        ::= .*
		if			::= text
		else		::= text
	 */

    /**
     * @throws IOException
     */
    private void readAny() throws IOException {
        if (isEndOfText()) {
            return;
        }
        switch (current) {
            case '$':
                // read next character
                read();
                if (isDigit()) {
                    // ex : $0, $10
                    int index = readInt();
                    handleTabstop(index);
                } else if (readChar('{')) {
                    if (isDigit()) {
                        // - ${1:name} <-- placeholder
                        // - ${1|one,two,three|} <-- choice
                        // - ${1} <-- tabstop
                        int index = readInt();
                        if (readChar(':')) {
                            // - ${1:name} <-- placeholder
                            String name = readString('}', '$');
                            nestingLevel++;
                            handler.startPlaceholder(index, name, nestingLevel);
                            // placeholder ::= '${' int ':' any '}'
                            if (current == '}') {
                                // read next character
                                read();
                            } else {
                                readAny();
                            }
                            handler.endPlaceholder(nestingLevel);
                            nestingLevel--;
                        } else if (readChar('|')) {
                            // - ${1|one,two,three|} <-- choice
                            handleChoice(null, index);
                        } else {
                            // - ${1} <-- tabstop
                            handleTabstop(index);
                            readRequiredChar('}');
                        }
                    } else {
                        // - ${name} <-- variable
                        String name = readString('}');
                        handleVariable(name);
                        readRequiredChar('}');
                    }

                } else {
                    // - $name <-- variable
                    String name = readString('$', ' ');
                    handleVariable(name);
                }
                break;
            default:
                handleText();
                break;
        }
        readAny();
    }

    private void handleChoice(String name, Integer index) throws IOException {
        List<String> choices = new ArrayList<>();
        String choice = readString(',', '|');
        while (!choice.isEmpty()) {
            choices.add(choice);
            if (readChar(',')) {
                choice = readString(',', '|');
            } else {
                break;
            }
        }
        if (name == null) {
            handler.choice(index, choices);
        } else {
            handler.choice(name, choices);
        }
        readRequiredChar('|');
        readRequiredChar('}');
    }

    private void handleTabstop(int index) {
        handler.tabstop(index);
    }

    private void handleVariable(String name) {
        handler.variable(name);
    }

    private String readString(int... stopOn) throws IOException {
        startCapture();
        do {
            read();
            for (int i = 0; i < stopOn.length; i++) {
                if (current == stopOn[i]) {
                    return endCapture();
                }
            }

        } while (!isEndOfText());
        return endCapture();
    }

    private void handleText() throws IOException {
        String text = readString('$');
        handler.text(text);
    }

    private int readInt() throws IOException {
        startCapture();
        int firstDigit = current;
        if (!readDigit()) {
            throw expected("digit");
        }
        if (firstDigit != '0') {
            while (readDigit()) {
            }
        }
        return Integer.parseInt(endCapture());
    }

    private void readRequiredChar(char ch) throws IOException {
        if (!readChar(ch)) {
            throw expected("'" + ch + "'");
        }
    }

    private boolean readChar(char ch) throws IOException {
        if (current != ch) {
            return false;
        }
        read();
        return true;
    }

    private boolean readDigit() throws IOException {
        if (!isDigit()) {
            return false;
        }
        read();
        return true;
    }

    private void read() throws IOException {
        if (index == fill) {
            if (captureStart != -1) {
                captureBuffer.append(buffer, captureStart, fill - captureStart);
                captureStart = 0;
            }
            bufferOffset += fill;
            fill = reader.read(buffer, 0, buffer.length);
            index = 0;
            if (fill == -1) {
                current = -1;
                index++;
                return;
            }
        }
        if (current == '\n') {
            line++;
            lineOffset = bufferOffset + index;
        }
        current = buffer[index++];
    }

    private void startCapture() {
        if (captureBuffer == null) {
            captureBuffer = new StringBuilder();
        }
        captureStart = index - 1;
    }

    private String endCapture() {
        int start = captureStart;
        int end = index - 1;
        captureStart = -1;
        if (captureBuffer.length() > 0) {
            captureBuffer.append(buffer, start, end - start);
            String captured = captureBuffer.toString();
            captureBuffer.setLength(0);
            return captured;
        }
        return new String(buffer, start, end - start);
    }

    Location getLocation() {
        int offset = bufferOffset + index - 1;
        int column = offset - lineOffset + 1;
        return new Location(offset, line, column);
    }

    private ParseException expected(String expected) {
        if (isEndOfText()) {
            return error("Unexpected end of input");
        }
        return error("Expected " + expected);
    }

    private ParseException error(String message) {
        return new ParseException(message, getLocation());
    }

    private boolean isDigit() {
        return current >= '0' && current <= '9';
    }

    private boolean isEndOfText() {
        return current == -1;
    }

}
