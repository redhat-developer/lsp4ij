package com.redhat.devtools.lsp4ij.features.codeAction;

/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeActionFixtureTestCase;

/**
 * Test case for PostConstructAnnotation quick fix
 */
public class PostConstructAnnotationQuickFixTest extends LSPCodeActionFixtureTestCase {

    public PostConstructAnnotationQuickFixTest() {
        super("*.java");
    }

    public void testPostConstructAnnotationQuickFix() {
        assertCodeActions("PostConstructAnnotation.java",
                "package io.openliberty.sample.jakarta.annotations;\n" +
                        "\n" +
                        "import jakarta.annotation.PostConstruct;\n" +
                        "import jakarta.annotation.Resource;\n" +
                        "\n" +
                        "@Resource(type = Object.class, name = \"aa\")\n" +
                        "public class PostConstructAnnotation {\n" +
                        "\n" +
                        "    private Integer studentId;\n" +
                        "\n" +
                        "    private boolean isHappy;\n" +
                        "\n" +
                        "    private boolean isSad;\n" +
                        "\n" +
                        "    @PostConstruct()\n" +
                        "    public Integer getS<caret>tudentId() {\n" +
                        "        return this.studentId;\n" +
                        "    }\n" +
                        "\n" +
                        "    @PostConstruct\n" +
                        "    public void getHappiness(String type) {\n" +
                        "\n" +
                        "    }\n" +
                        "\n" +
                        "    @PostConstruct\n" +
                        "    public void throwTantrum() throws Exception {\n" +
                        "        System.out.println(\"I'm sad\");\n" +
                        "    }\n" +
                        "\n" +
                        "    private String emailAddress;\n" +
                        "\n" +
                        "}",
                """                
                            [{
                                 "title": "Change return type to void",
                                     "kind": "quickfix",
                                     "diagnostics": [
                                 {
                                     "range": {
                                     "start": {
                                         "line": 15,
                                                 "character": 19
                                     },
                                     "end": {
                                         "line": 15,
                                                 "character": 31
                                     }
                                 },
                                     "severity": 1,
                                         "code": "PostConstructReturnType",
                                         "source": "jakarta-annotations",
                                         "message": "A method with the @PostConstruct annotation must be void."
                                 }
                             ],
                                 "data": {
                                 "participantId": "io.openliberty.tools.intellij.lsp4jakarta.lsp4ij.annotations.PostConstructReturnTypeQuickFix",
                                         "documentUri": "src/test/resources/templates/test_template_code_action/PostConstructAnnotation.java",
                                         "range": {
                                     "start": {
                                         "line": 15,
                                                 "character": 19
                                     },
                                     "end": {
                                         "line": 15,
                                                 "character": 31
                                     }
                                 },
                                 "extendedData": {},
                                 "resourceOperationSupported": true,
                                         "commandConfigurationUpdateSupported": false
                             }
                            }]"""
                ,
                "Change return type to void");

        assertApplyCodeAction("Change return type to void", "package io.openliberty.sample.jakarta.annotations;\n" +
                "\n" +
                "import jakarta.annotation.PostConstruct;\n" +
                "import jakarta.annotation.Resource;\n" +
                "\n" +
                "@Resource(type = Object.class, name = \"aa\")\n" +
                "public class PostConstructAnnotation {\n" +
                "\n" +
                "    private Integer studentId;\n" +
                "\n" +
                "    private boolean isHappy;\n" +
                "\n" +
                "    private boolean isSad;\n" +
                "\n" +
                "    @PostConstruct()\n" +
                "    public void<caret> getStudentId() {\n" +
                "        return this.studentId;\n" +
                "    }\n" +
                "\n" +
                "    @PostConstruct\n" +
                "    public void getHappiness(String type) {\n" +
                "\n" +
                "    }\n" +
                "\n" +
                "    @PostConstruct\n" +
                "    public void throwTantrum() throws Exception {\n" +
                "        System.out.println(\"I'm sad\");\n" +
                "    }\n" +
                "\n" +
                "    private String emailAddress;\n" +
                "\n" +
                "}");
    }
}

