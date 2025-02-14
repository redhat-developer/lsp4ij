/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;

/**
 * Completion tests by emulating LSP 'textDocument/completion' responses
 * from the liberty-ls language server
 * which returns completion items without text edit.
 */
public class BootstrapPropertiesCompletionTest extends LSPCompletionFixtureTestCase {

    public BootstrapPropertiesCompletionTest() {
        super("bootstrap.properties");
    }

    // ------------ Completion on property key

    public void testCompletionOnPropertyKeyAtEnd() {
        // 1. Test completion items response
        assertCompletion("bootstrap.properties",
                "com.ibm.ws.log<caret>", """                
                        [
                          {
                              "label": "com.ibm.ws.logging.message.file.name",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies the name of the message log file. The message log file has a default name of `messages.log`. This file always exists, and contains INFO and other (AUDIT, WARNING, ERROR, FAILURE) messages in addition to the System.out and System.err streams. This log also contains time stamps and the issuing thread ID. If the log file is rolled over, the names of earlier log files have the format messages_timestamp.log."
                              }
                            },
                            {
                              "label": "com.ibm.ws.logging.max.files",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies how many of each of the logs files are kept. This setting also applies to the number of exception summary logs for FFDC. So if this number is 10, you might have 10 message logs, 10 trace logs, and 10 exception summaries in the ffdc directory. By default, the value is `2`. The console log does not roll so this setting does not apply to the console.log file."
                              }
                            },
                            {
                              "label": "com.ibm.ws.logging.max.file.size",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies the maximum size (in MB) that a log file can reach before it is rolled. Setting the value to `0` disables log rolling. The default value is `20`. The console.log does not roll so this setting does not apply."
                              }
                            }
                          ]"""
                ,
                "com.ibm.ws.logging.message.file.name",
                "com.ibm.ws.logging.max.files",
                "com.ibm.ws.logging.max.file.size");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "com.ibm.ws.logging.max.file.size<caret>");
    }

    public void testCompletionOnPropertyKeyNotAtEnd() {
        // 1. Test completion items response
        assertCompletion("bootstrap.properties",
                "ibm.ws.<caret>=100", """                
                        [
                          {
                              "label": "com.ibm.ws.logging.message.file.name",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies the name of the message log file. The message log file has a default name of `messages.log`. This file always exists, and contains INFO and other (AUDIT, WARNING, ERROR, FAILURE) messages in addition to the System.out and System.err streams. This log also contains time stamps and the issuing thread ID. If the log file is rolled over, the names of earlier log files have the format messages_timestamp.log."
                              }
                            },
                            {
                              "label": "com.ibm.ws.logging.max.files",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies how many of each of the logs files are kept. This setting also applies to the number of exception summary logs for FFDC. So if this number is 10, you might have 10 message logs, 10 trace logs, and 10 exception summaries in the ffdc directory. By default, the value is `2`. The console log does not roll so this setting does not apply to the console.log file."
                              }
                            },
                            {
                              "label": "com.ibm.ws.logging.max.file.size",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies the maximum size (in MB) that a log file can reach before it is rolled. Setting the value to `0` disables log rolling. The default value is `20`. The console.log does not roll so this setting does not apply."
                              }
                            }
                          ]"""
                ,
                "com.ibm.ws.logging.message.file.name",
                "com.ibm.ws.logging.max.files",
                "com.ibm.ws.logging.max.file.size");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "com.ibm.ws.logging.max.file.size<caret>=100");
    }

    public void testCompletionOnPropertyKeyEndsWithDot() {
        // 1. Test completion items response
        assertCompletion("bootstrap.properties",
                "logging.<caret>=100", """                
                        [
                          {
                              "label": "com.ibm.ws.logging.message.file.name",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies the name of the message log file. The message log file has a default name of `messages.log`. This file always exists, and contains INFO and other (AUDIT, WARNING, ERROR, FAILURE) messages in addition to the System.out and System.err streams. This log also contains time stamps and the issuing thread ID. If the log file is rolled over, the names of earlier log files have the format messages_timestamp.log."
                              }
                            },
                            {
                              "label": "com.ibm.ws.logging.max.files",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies how many of each of the logs files are kept. This setting also applies to the number of exception summary logs for FFDC. So if this number is 10, you might have 10 message logs, 10 trace logs, and 10 exception summaries in the ffdc directory. By default, the value is `2`. The console log does not roll so this setting does not apply to the console.log file."
                              }
                            },
                            {
                              "label": "com.ibm.ws.logging.max.file.size",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies the maximum size (in MB) that a log file can reach before it is rolled. Setting the value to `0` disables log rolling. The default value is `20`. The console.log does not roll so this setting does not apply."
                              }
                            }
                          ]"""
                ,
                "com.ibm.ws.logging.message.file.name",
                "com.ibm.ws.logging.max.files",
                "com.ibm.ws.logging.max.file.size");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "com.ibm.ws.logging.max.file.size<caret>=100");
    }

    public void testCompletionOnPropertyKeyEndsWithDotUpperCase() {
        // 1. Test completion items response
        assertCompletion("bootstrap.properties",
                "LOGGING.<caret>=100", """                
                        [
                          {
                              "label": "com.ibm.ws.logging.message.file.name",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies the name of the message log file. The message log file has a default name of `messages.log`. This file always exists, and contains INFO and other (AUDIT, WARNING, ERROR, FAILURE) messages in addition to the System.out and System.err streams. This log also contains time stamps and the issuing thread ID. If the log file is rolled over, the names of earlier log files have the format messages_timestamp.log."
                              }
                            },
                            {
                              "label": "com.ibm.ws.logging.max.files",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies how many of each of the logs files are kept. This setting also applies to the number of exception summary logs for FFDC. So if this number is 10, you might have 10 message logs, 10 trace logs, and 10 exception summaries in the ffdc directory. By default, the value is `2`. The console log does not roll so this setting does not apply to the console.log file."
                              }
                            },
                            {
                              "label": "com.ibm.ws.logging.max.file.size",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies the maximum size (in MB) that a log file can reach before it is rolled. Setting the value to `0` disables log rolling. The default value is `20`. The console.log does not roll so this setting does not apply."
                              }
                            }
                          ]"""
                ,
                "com.ibm.ws.logging.message.file.name",
                "com.ibm.ws.logging.max.files",
                "com.ibm.ws.logging.max.file.size");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "com.ibm.ws.logging.max.file.size<caret>=100");
    }

    public void testCompletionOnPropertyKeyUpperCase() {
        // 1. Test completion items response
        assertCompletion("bootstrap.properties",
                "COM.IBM.<caret>=100", """                
                        [
                          {
                              "label": "com.ibm.ws.logging.message.file.name",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies the name of the message log file. The message log file has a default name of `messages.log`. This file always exists, and contains INFO and other (AUDIT, WARNING, ERROR, FAILURE) messages in addition to the System.out and System.err streams. This log also contains time stamps and the issuing thread ID. If the log file is rolled over, the names of earlier log files have the format messages_timestamp.log."
                              }
                            },
                            {
                              "label": "com.ibm.ws.logging.max.files",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies how many of each of the logs files are kept. This setting also applies to the number of exception summary logs for FFDC. So if this number is 10, you might have 10 message logs, 10 trace logs, and 10 exception summaries in the ffdc directory. By default, the value is `2`. The console log does not roll so this setting does not apply to the console.log file."
                              }
                            },
                            {
                              "label": "com.ibm.ws.logging.max.file.size",
                              "kind": 10,
                              "documentation": {
                                "kind": "markdown",
                                "value": "This setting specifies the maximum size (in MB) that a log file can reach before it is rolled. Setting the value to `0` disables log rolling. The default value is `20`. The console.log does not roll so this setting does not apply."
                              }
                            }
                          ]"""
                ,
                "com.ibm.ws.logging.message.file.name",
                "com.ibm.ws.logging.max.files",
                "com.ibm.ws.logging.max.file.size");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "com.ibm.ws.logging.max.file.size<caret>=100");
    }

    // ------------ Completion on property value

    public void testCompletionOnPropertyValueWithEmptyValueAtEnd() {
        // 1. Test completion items response
        assertCompletion("bootstrap.properties",
                "com.ibm.hpel.trace.bufferingEnabled=<caret>", """                
                        [
                            {
                                 "label": "true",
                                 "kind": 1,
                                 "documentation": {
                                     "kind": "markdown",
                                     "value": "Specifies whether to allow a small delay in saving records to the disk for improved performance. When bufferingEnabled is set to true, records will be briefly held in memory before being written to disk."
                                 },
                                 "preselect": true
                             },
                             {
                                 "label": "false",
                                 "kind": 1,
                                 "documentation": {
                                     "kind": "markdown",
                                     "value": "Specifies whether to allow a small delay in saving records to the disk for improved performance. When bufferingEnabled is set to true, records will be briefly held in memory before being written to disk."
                                 }
                             }
                          ]"""
                ,
                "true",
                "false");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "com.ibm.hpel.trace.bufferingEnabled=false<caret>");
    }

    public void testCompletionOnPropertyValueWithValueAtEnd() {
        // 1. Test completion items response
        assertAutoCompletion("bootstrap.properties",
                "com.ibm.hpel.trace.bufferingEnabled=f<caret>", """                
                        [
                            {
                                 "label": "true",
                                 "kind": 1,
                                 "documentation": {
                                     "kind": "markdown",
                                     "value": "Specifies whether to allow a small delay in saving records to the disk for improved performance. When bufferingEnabled is set to true, records will be briefly held in memory before being written to disk."
                                 },
                                 "preselect": true
                             },
                             {
                                 "label": "false",
                                 "kind": 1,
                                 "documentation": {
                                     "kind": "markdown",
                                     "value": "Specifies whether to allow a small delay in saving records to the disk for improved performance. When bufferingEnabled is set to true, records will be briefly held in memory before being written to disk."
                                 }
                             }
                          ]"""
                , "com.ibm.hpel.trace.bufferingEnabled=false<caret>");
    }

    public void testCompletionOnPropertyValueWithValueAfterEquals() {
        // 1. Test completion items response
        assertCompletion("bootstrap.properties",
                "com.ibm.hpel.trace.bufferingEnabled=<caret>F", """                
                        [
                            {
                                 "label": "true",
                                 "kind": 1,
                                 "documentation": {
                                     "kind": "markdown",
                                     "value": "Specifies whether to allow a small delay in saving records to the disk for improved performance. When bufferingEnabled is set to true, records will be briefly held in memory before being written to disk."
                                 },
                                 "preselect": true
                             },
                             {
                                 "label": "false",
                                 "kind": 1,
                                 "documentation": {
                                     "kind": "markdown",
                                     "value": "Specifies whether to allow a small delay in saving records to the disk for improved performance. When bufferingEnabled is set to true, records will be briefly held in memory before being written to disk."
                                 }
                             }
                          ]"""
                , "true", "false");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "com.ibm.hpel.trace.bufferingEnabled=false<caret>F");
    }

    public void testCompletionOnPropertyValueWithValueInsideValue() {
        // 1. Test completion items response
        assertCompletion("bootstrap.properties",
                "com.ibm.hpel.trace.bufferingEnabled=fa<caret>ls", """                
                        [
                            {
                                 "label": "true",
                                 "kind": 1,
                                 "documentation": {
                                     "kind": "markdown",
                                     "value": "Specifies whether to allow a small delay in saving records to the disk for improved performance. When bufferingEnabled is set to true, records will be briefly held in memory before being written to disk."
                                 },
                                 "preselect": true
                             },
                             {
                                 "label": "false",
                                 "kind": 1,
                                 "documentation": {
                                     "kind": "markdown",
                                     "value": "Specifies whether to allow a small delay in saving records to the disk for improved performance. When bufferingEnabled is set to true, records will be briefly held in memory before being written to disk."
                                 }
                             }
                          ]"""
                , "false");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "com.ibm.hpel.trace.bufferingEnabled=false<caret>");
    }
}
