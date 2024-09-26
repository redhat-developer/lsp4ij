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
 * from the MicroProfile language server
 * which uses itemDefaults to defines commons text edit.
 */
public class MicroProfileConfigPropertiesCompletionTest extends LSPCompletionFixtureTestCase {

    public MicroProfileConfigPropertiesCompletionTest() {
        super("microprofile-config.properties");
    }

    // ------------ Completion on property key

    public void testCompletionOnPropertyKeyWithEmptyContent() {
        // 1. Test completion items response
        assertCompletion("microprofile-config.properties",
                "<caret>", """                
                        {
                              "isIncomplete": false,
                              "items": [
                                {
                                  "label": "quarkus.banner.enabled",
                                  "kind": 10,
                                  "insertTextFormat": 2,
                                  "textEditText": "quarkus.banner.enabled\\u003d${1|false,true|}"
                                },
                                {
                                  "label": "config_ordinal",
                                  "kind": 10,
                                  "insertTextFormat": 2,
                                  "textEditText": "config_ordinal\\u003d$0"
                                }
                              ],
                              "itemDefaults": {
                                "editRange": {
                                  "start": {
                                    "line": 0,
                                    "character": 0
                                  },
                                  "end": {
                                    "line": 0,
                                    "character": 0
                                  }
                                }
                              }
                            }"""
                ,
                "config_ordinal",
                "quarkus.banner.enabled");
        // 2. Test new editor content after applying the second completion item
        assertApplyCompletionItem(1, "quarkus.banner.enabled=false<caret>");
    }

    public void testCompletionOnPropertyKeyWithNotEmptyContent() {
        // 1. Test completion items response
        assertCompletion("microprofile-config.properties",
                "qu<caret>k", """                
                        {
                              "isIncomplete": false,
                              "items": [
                                {
                                  "label": "quarkus.banner.enabled",
                                  "kind": 10,
                                  "insertTextFormat": 2,
                                  "textEditText": "quarkus.banner.enabled\\u003d${1|false,true|}"
                                },
                                {
                                  "label": "config_ordinal",
                                  "kind": 10,
                                  "insertTextFormat": 2,
                                  "textEditText": "config_ordinal\\u003d$0"
                                }
                              ],
                              "itemDefaults": {
                                "editRange": {
                                  "start": {
                                    "line": 0,
                                    "character": 0
                                  },
                                  "end": {
                                    "line": 0,
                                    "character": 3
                                  }
                                }
                              }
                            }"""
                ,
                "quarkus.banner.enabled");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "quarkus.banner.enabled=false<caret>");
    }

    // ------------ Completion on property value

    public void testCompletionOnPropertyValueWithEmptyContent() {
        // 1. Test completion items response
        assertCompletion("microprofile-config.properties",
                "quarkus.banner.enabled=<caret>", """                
                        {
                               "isIncomplete": false,
                               "items": [
                                 {
                                   "label": "false",
                                   "kind": 12,
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": "**false**\r\n"
                                   },
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 0,
                                         "character": 23
                                       },
                                       "end": {
                                         "line": 0,
                                         "character": 23
                                       }
                                     },
                                     "newText": "false"
                                   }
                                 },
                                 {
                                   "label": "true",
                                   "kind": 12,
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": "**true**\r\n"
                                   },
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 0,
                                         "character": 23
                                       },
                                       "end": {
                                         "line": 0,
                                         "character": 23
                                       }
                                     },
                                     "newText": "true"
                                   }
                                 }
                               ],
                               "itemDefaults": {
                                 "editRange": {
                                   "start": {
                                     "line": 0,
                                     "character": 0
                                   },
                                   "end": {
                                     "line": 0,
                                     "character": 23
                                   }
                                 }
                               }
                             }"""
                ,
                "false",
                "true");
        // 2. Test new editor content after applying the second completion item
        assertApplyCompletionItem(1, "quarkus.banner.enabled=true<caret>");
    }

    public void testCompletionOnPropertyValueWithNotEmptyContentAt_1_caret() {
        // 1. Test completion items response
        assertCompletion("microprofile-config.properties",
                "quarkus.banner.enabled=t<caret>re", """                
                        {
                               "isIncomplete": false,
                               "items": [
                                 {
                                   "label": "false",
                                   "kind": 12,
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": "**false**\r\n"
                                   },
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 0,
                                         "character": 23
                                       },
                                       "end": {
                                         "line": 0,
                                         "character": 26
                                       }
                                     },
                                     "newText": "false"
                                   }
                                 },
                                 {
                                   "label": "true",
                                   "kind": 12,
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": "**true**\r\n"
                                   },
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 0,
                                         "character": 23
                                       },
                                       "end": {
                                         "line": 0,
                                         "character": 26
                                       }
                                     },
                                     "newText": "true"
                                   }
                                 }
                               ],
                               "itemDefaults": {
                                 "editRange": {
                                   "start": {
                                     "line": 0,
                                     "character": 0
                                   },
                                   "end": {
                                     "line": 0,
                                     "character": 26
                                   }
                                 }
                               }
                             }"""
                ,
                "true");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "quarkus.banner.enabled=true<caret>");
    }

    public void testCompletionOnPropertyValueWithNotEmptyContentAt_3_caret() {
        // 1. Test completion items response
        assertCompletion("microprofile-config.properties",
                "quarkus.banner.enabled=tre<caret>", """                
                        {
                               "isIncomplete": false,
                               "items": [
                                 {
                                   "label": "false",
                                   "kind": 12,
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": "**false**\r\n"
                                   },
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 0,
                                         "character": 23
                                       },
                                       "end": {
                                         "line": 0,
                                         "character": 26
                                       }
                                     },
                                     "newText": "false"
                                   }
                                 },
                                 {
                                   "label": "true",
                                   "kind": 12,
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": "**true**\r\n"
                                   },
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 0,
                                         "character": 23
                                       },
                                       "end": {
                                         "line": 0,
                                         "character": 26
                                       }
                                     },
                                     "newText": "true"
                                   }
                                 }
                               ],
                               "itemDefaults": {
                                 "editRange": {
                                   "start": {
                                     "line": 0,
                                     "character": 0
                                   },
                                   "end": {
                                     "line": 0,
                                     "character": 26
                                   }
                                 }
                               }
                             }"""
                ,
                "true");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "quarkus.banner.enabled=true<caret>");
    }

    public void testCompletionOnPropertyValueWithNotEmptyContent_false_value() {
        // 1. Test completion items response
        assertCompletion("microprofile-config.properties",
                "quarkus.banner.enabled=<caret>false", """                
                        {
                               "isIncomplete": false,
                               "items": [
                                 {
                                   "label": "false",
                                   "kind": 12,
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": "**false**\r\n"
                                   },
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 0,
                                         "character": 23
                                       },
                                       "end": {
                                         "line": 0,
                                         "character": 28
                                       }
                                     },
                                     "newText": "false"
                                   }
                                 },
                                 {
                                   "label": "true",
                                   "kind": 12,
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": "**true**\r\n"
                                   },
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 0,
                                         "character": 23
                                       },
                                       "end": {
                                         "line": 0,
                                         "character": 28
                                       }
                                     },
                                     "newText": "true"
                                   }
                                 }
                               ],
                               "itemDefaults": {
                                 "editRange": {
                                   "start": {
                                     "line": 0,
                                     "character": 0
                                   },
                                   "end": {
                                     "line": 0,
                                     "character": 28
                                   }
                                 }
                               }
                             }"""
                ,
                "false", "true");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(1, "quarkus.banner.enabled=true<caret>");
    }
}
