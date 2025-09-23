/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files;

import com.intellij.openapi.util.SystemInfo;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.features.files.watcher.FileSystemWatcherManager;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.WatchKind;
import org.junit.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.net.URI;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test glob pattern with {@link FileSystemWatcherManager}.
 * <p>
 * The glob pattern to watch relative to the base path. Glob patterns can have
 * the following syntax:
 * - `*` to match one or more characters in a path segment
 * - `?` to match on one character in a path segment
 * - `**` to match any number of path segments, including none
 * - `{}` to group conditions (e.g. `**​/*.{ts,js}` matches all TypeScript
 * and JavaScript files)
 * - `[]` to declare a range of characters to match in a path segment
 * (e.g., `example.[0-9]` to match on `example.0`, `example.1`, …)
 * - `[!...]` to negate a range of characters to match in a path segment
 * (e.g., `example.[!0-9]` to match on `example.a`, `example.b`,
 * but not `example.0`)
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#pattern">LSP Pattern</a>
 */
public class FileSystemWatcherManagerTest {

    public static final String DEFAULT_WATCHER = "default";
    final private FileSystemWatcherManager manager = new FileSystemWatcherManager(Paths.get(getProjectDir()));

    @BeforeEach
    void setUp() {
        unregisterWatchers(DEFAULT_WATCHER);
    }

    @AfterEach
    void tearDown() {
        unregisterWatchers(DEFAULT_WATCHER);
    }

    @Test
    public void sap_cds_ls() {
        // On Windows OS, we generate a base dir with lower case because JDT LS generate this base dir.
        String baseDir = SystemInfo.isWindows ? getProjectDir().toLowerCase() : getProjectDir();
        registerWatchers( // language=json
                """
                          {"watchers": [
                              {
                                 "kind": 7,
                                 "globPattern": "package.json"
                               },
                               {
                                 "kind": 7,
                                 "globPattern": "{.git,.cds}ignore"
                               },
                               {
                                 "kind": 7,
                                 "globPattern": ".cdsrc.json"
                               },
                               {
                                 "kind": 7,
                                 "globPattern": "**/{_i18n,i18n}/i18n{*.properties,*.json,*.csv}"
                               }
                          ]
                        }
                        """.formatted(baseDir));

        // Match package.json
        assertMatchFile(getBaseUri() + "package.json"); // file:///C:/package.json
        assertMatchFile(getBaseUri() + "package.json", WatchKind.Create); // file:///C:/package.json
        assertMatchFile(getBaseUri() + "package.json", WatchKind.Change); // file:///C:/package.json
        assertMatchFile(getBaseUri() + "package.json", WatchKind.Delete); // file:///C:/package.json

        assertNoMatchFile(getBaseUri() + "package.jso"); // file:///C:/package.jso
        assertNoMatchFile(getBaseUri() + "foo/package.json"); // file:///C:/foo/package.json
        assertNoMatchFile(getExternalDir() + "package.json"); // file:///D:/package.json
        assertNoMatchFile(getExternalDir() + "package.json", WatchKind.Create); // file:///D:/package.json
        assertNoMatchFile(getExternalDir() + "package.json", WatchKind.Change); // file:///D:/package.json
        assertNoMatchFile(getExternalDir() + "package.json", WatchKind.Delete); // file:///D:/package.json

        // Match {.git,.cds}ignore
        assertNoMatchFile(getBaseUri() + "gitignore"); // file:///C:/gitignore
        assertMatchFile(getBaseUri() + ".gitignore"); // file:///C:/.gitignore
        assertMatchFile(getBaseUri() + ".gitignore", WatchKind.Create); // file:///C:/.gitignore
        assertMatchFile(getBaseUri() + ".gitignore", WatchKind.Change); // file:///C:/.gitignore
        assertMatchFile(getBaseUri() + ".gitignore", WatchKind.Delete); // file:///C:/.gitignore

        assertNoMatchFile(getBaseUri() + "cdsignore"); // file:///C:/cdsignore
        assertMatchFile(getBaseUri() + ".cdsignore"); // file:///C:/.cdsignore
        assertMatchFile(getBaseUri() + ".cdsignore", WatchKind.Create); // file:///C:/.cdsignore
        assertMatchFile(getBaseUri() + ".cdsignore", WatchKind.Change); // file:///C:/.cdsignore
        assertMatchFile(getBaseUri() + ".cdsignore", WatchKind.Delete); // file:///C:/.cdsignore

        // Match .cdsrc.json
        assertNoMatchFile(getBaseUri() + "cdsrc.json"); // file:///C:/cdsrc.json
        assertMatchFile(getBaseUri() + ".cdsrc.json"); // file:///C:/package.json
        assertMatchFile(getBaseUri() + ".cdsrc.json", WatchKind.Create); // file:///C:/.cdsrc.json
        assertMatchFile(getBaseUri() + ".cdsrc.json", WatchKind.Change); // file:///C:/.cdsrc.json
        assertMatchFile(getBaseUri() + ".cdsrc.json", WatchKind.Delete); // file:///C:/.cdsrc.json

        // Match **/{_i18n,i18n}/i18n{*.properties,*.json,*.csv}
        assertNoMatchFile(getBaseUri() + "/_i18n/i19n.properties"); // file:///C:/_i18n/i19n.properties
        assertMatchFile(getBaseUri() + "/_i18n/i18n.properties"); // file:///C:/_i18n/i18n.properties
        assertMatchFile(getBaseUri() + "/_i18n/i18nFoo.properties"); // file:///C:/_i18n/i18nFoo.properties

        assertNoMatchFile(getBaseUri() + "/_i18n/i19n.json"); // file:///C:/_i18n/i19n.json
        assertMatchFile(getBaseUri() + "/_i18n/i18n.json"); // file:///C:/_i18n/i18n.json
        assertMatchFile(getBaseUri() + "/_i18n/i18nFoo.json"); // file:///C:/_i18n/i18nFoo.json

        assertNoMatchFile(getBaseUri() + "/_i18n/i19n.csv"); // file:///C:/_i18n/i19n.csv
        assertMatchFile(getBaseUri() + "/_i18n/i18n.csv"); // file:///C:/_i18n/i18n.csv
        assertMatchFile(getBaseUri() + "/_i18n/i18nFoo.csv"); // file:///C:/_i18n/i18nFoo.csv

        assertNoMatchFile(getBaseUri() + "/_i18n/i18n.xml"); // file:///C:/_i18n/i18n.xml
    }

    @Test
    public void relativePattern() {
        // On Windows OS, we generate a base dir with lower case because JDT LS generate this base dir.
        String externalBaseDir = SystemInfo.isWindows ? getExternalDir().toLowerCase() : getExternalDir();
        registerWatchers( // language=json
                """
                          {"watchers": [
                              {
                                 "kind": 7,
                                 "globPattern": {
                                   "baseUri": "%s",
                                   "pattern": "package.json"
                                 }
                               },
                               {
                                 "kind": 7,
                                 "globPattern": "*/package.json"
                               }
                          ]
                        }
                        """.formatted(externalBaseDir));

        // Match **/package.json
        assertNoMatchFile(getBaseUri() + "package.json"); // file:///C:/package.json
        assertMatchFile(getBaseUri() + "/foo/package.json"); // file:///C:/package.json

        // Match package.json
        assertMatchFile(getExternalDir() + "package.json"); // file:///C:/package.json
        assertNoMatchFile(getExternalDir() + "/foo/package.json"); // file:///C:/package.json
    }

    @Test
    public void jdt_ls() {
        // On Windows OS, we generate a base dir with lower case because JDT LS generate this base dir.
        String baseDir = SystemInfo.isWindows ? getProjectDir().toLowerCase() : getProjectDir();
        registerWatchers(// language=json
                """
                        {"watchers": [
                                  {
                                    "globPattern": "**/*.java"
                                  },
                                  {
                                    "globPattern": "**/.project"
                                  },
                                  {
                                    "globPattern": "**/.classpath"
                                  },
                                  {
                                    "globPattern": "**/.settings/*.prefs"
                                  },
                                  {
                                    "globPattern": "**/src/**"
                                  },
                                  {
                                    "globPattern": "**/*.gradle"
                                  },
                                  {
                                    "globPattern": "**/*.gradle.kts"
                                  },
                                  {
                                    "globPattern": "**/gradle.properties"
                                  },
                                  {
                                    "globPattern": "**/pom.xml"
                                  },
                                  {
                                    "globPattern": "Users/X/foo/lib/**"
                                  },
                                  {
                                    "globPattern": "**/.settings"
                                  }
                                ]
                              }
                        """);

        // Match "**/*.java"
        assertMatchFile(getBaseUri() + "foo.java"); // file:///C:/foo.java
        assertMatchFile(getBaseUri().toLowerCase() + "foo.java"); // file:///c:/foo.java

        // Match "**/src/**"
        assertNoMatchFile(getBaseUri() + "foo.ts"); // file:///C:/foo.ts
        assertMatchFile(getBaseUri() + "src/foo.ts"); // "file:///C:/src/foo.ts"

        // Match c:/Users/X/foo/lib/**
        assertNoMatchFile(getBaseUri() + "Users/X/lib/bar.jar"); // file:///C:/Users/X/lib/bar.jar
        assertMatchFile(getBaseUri() + "Users/X/foo/lib/bar.jar"); // file:///C:/Users/X/foo/lib/bar.jar

        // Match "**/.settings
        assertNoMatchFile(getBaseUri() + "foo.ts"); // file:///C:/foo.ts
        assertMatchFile(getBaseUri() + ".settings"); // file:///C:/.settings

        // Match **/.settings/*.prefs
        assertNoMatchFile(getBaseUri() + ".settings/foo.pref"); // file:///C:/.settings/foo.pref
        assertNoMatchFile(getBaseUri() + ".settings/bar/foo.prefs"); // file:///C:/.settings/bar/foo.prefs
        assertMatchFile(getBaseUri() + ".settings/foo.prefs"); // file:///C:/.settings/foo.prefs
    }

    @Test
    public void register_unregister_watchers() {
        // Register Java watcher
        String id_java = "watcher-java";
        registerWatchers(id_java, // language=json
                """
                        {"watchers": [
                                  {
                                    "globPattern": "**/*.java"
                                  }
                                ]
                              }
                        """);
        assertMatchFile(getBaseUri() + "foo.java"); // file:///C:/foo.java// Match "**/*.java"
        assertNoMatchFile(getBaseUri() + "foo.ts"); // file:///C:/foo.ts

        // Register TypeScript watcher
        String id_ts = "watcher-ts";
        registerWatchers(id_ts,// language=json
                """
                        {"watchers": [
                                  {
                                    "globPattern": "**/*.ts"
                                  }
                                ]
                              }
                        """);
        assertMatchFile(getBaseUri() + "foo.java"); // file:///C:/foo.java// Match "**/*.java"
        assertMatchFile(getBaseUri() + "foo.ts"); // file:///C:/foo.java// Match "**/*.ts"

        // Unregister Java watcher
        unregisterWatchers(id_java);
        assertNoMatchFile(getBaseUri() + "foo.java"); // file:///C:/foo.java
        assertMatchFile(getBaseUri() + "foo.ts"); // file:///C:/foo.java// Match "**/*.ts"

        // Unregister TypeScript watcher
        unregisterWatchers(id_ts);
        assertNoMatchFile(getBaseUri() + "foo.java"); // file:///C:/foo.java
        assertNoMatchFile(getBaseUri() + "foo.ts"); // file:///C:/foo.ts
    }

    @Test
    public void watcherKind() {
        // Register Java watcher
        registerWatchers("watcher-kind", // language=json
                """
                        {"watchers": [
                                  {
                                    "globPattern": "**/*.kind_null"
                                  },
                                  {
                                    "globPattern": "**/*.kind_7",
                                    "kind": 7
                                  },
                                  {
                                    "globPattern": "**/*.kind_Create",
                                    "kind": 1
                                  },
                                  {
                                    "globPattern": "**/*.kind_Change",
                                    "kind": 2
                                  },
                                  {
                                    "globPattern": "**/*.kind_Delete",
                                    "kind": 4
                                  }
                                ]
                              }
                        """);

        assertMatchFile(getBaseUri() + "foo.kind_null", WatchKind.Create); // file:///C:/foo.king_null// Match "**/*.kind_null"
        assertMatchFile(getBaseUri() + "foo.kind_null", WatchKind.Change); // file:///C:/foo.king_null// Match "**/*.kind_null"
        assertMatchFile(getBaseUri() + "foo.kind_null", WatchKind.Delete); // file:///C:/foo.king_null// Match "**/*.kind_null"

        assertMatchFile(getBaseUri() + "foo.kind_7", WatchKind.Create); // file:///C:/foo.kind_7// Match "**/*.kind_7"
        assertMatchFile(getBaseUri() + "foo.kind_7", WatchKind.Change); // file:///C:/foo.kind_7// Match "**/*.kind_7"
        assertMatchFile(getBaseUri() + "foo.kind_7", WatchKind.Delete); // file:///C:/foo.kind_7// Match "**/*.kind_7"

        assertMatchFile(getBaseUri() + "foo.kind_Create", WatchKind.Create); // file:///C:/foo.king_null// Match "**/*.kind_Create"
        assertNoMatchFile(getBaseUri() + "foo.kind_Create", WatchKind.Change); // file:///C:/foo.kind_Create No match
        assertNoMatchFile(getBaseUri() + "foo.kind_Create", WatchKind.Delete); // file:///C:/foo.kind_Create No match

        assertNoMatchFile(getBaseUri() + "foo.kind_Change", WatchKind.Create); // file:///C:/foo.kind_Change No match
        assertMatchFile(getBaseUri() + "foo.kind_Change", WatchKind.Change); // file:///C:/foo.king_Change// Match "**/*.kind_Change"
        assertNoMatchFile(getBaseUri() + "foo.kind_Change", WatchKind.Delete); // file:///C:/foo.kind_Create No match

        assertNoMatchFile(getBaseUri() + "foo.kind_Delete", WatchKind.Create); // file:///C:/foo.kind_Delete No match
        assertNoMatchFile(getBaseUri() + "foo.kind_Delete", WatchKind.Change); // file:///C:/foo.kind_Delete No match
        assertMatchFile(getBaseUri() + "foo.kind_Delete", WatchKind.Delete); // file:///C:/foo.king_Delete// Match "**/*.kind_Delete"
    }

    private void unregisterWatchers(String id) {
        manager.unregisterFileSystemWatchers(id);
    }

    @Test
    public void vscode_simple() {
        // See https://github.com/microsoft/vscode/blob/e49b522e12d1a55dfe2950af355767c9d5b824aa/src/vs/base/test/common/glob.test.ts#L77
        String p = "node_modules";

        assertGlobMatch(p, "node_modules");
        assertNoGlobMatch(p, "node_module");
        // assertNoGlobMatch(p, baseUri + "/node_modules");
        assertNoGlobMatch(p, "test/node_modules");

        p = "test.txt";
        assertGlobMatch(p, "test.txt");
        assertNoGlobMatch(p, "test?txt");
        assertNoGlobMatch(p, "/text.txt");
        assertNoGlobMatch(p, "test/test.txt");

        p = "test(.txt";
        assertGlobMatch(p, "test(.txt");
        assertNoGlobMatch(p, "test?txt");

        p = "qunit";

        assertGlobMatch(p, "qunit");
        assertNoGlobMatch(p, "qunit.css");
        assertNoGlobMatch(p, "test/qunit");

        // Absolute
        p = "DNXConsoleApp/**/*.cs";
        assertGlobMatch(p, "/DNXConsoleApp/Program.cs");
        assertGlobMatch(p, "/DNXConsoleApp/foo/Program.cs");

        p = "*";
        // FIXME: the glob  from Java IO doesn't support optional *
        // assertGlobMatch(p, "");

        assertGlobMatch(p, "a");
        assertGlobMatch(p, "ab");
    }

    @Test
    public void vscode_dot_hidden() {
        // See https://github.com/microsoft/vscode/blob/e49b522e12d1a55dfe2950af355767c9d5b824aa/src/vs/base/test/common/glob.test.ts#L116C3-L157C45
        String p = ".*";

        assertGlobMatch(p, ".git");
        assertGlobMatch(p, ".hidden.txt");
        assertNoGlobMatch(p, "git");
        assertNoGlobMatch(p, "hidden.txt");
        assertNoGlobMatch(p, "path/.git");
        assertNoGlobMatch(p, "path/.hidden.txt");

        p = "**/.*";
        assertGlobMatch(p, ".git");
        assertGlobMatch(p, "/.git");
        assertGlobMatch(p, ".hidden.txt");
        assertNoGlobMatch(p, "git");
        assertNoGlobMatch(p, "hidden.txt");

        assertGlobMatch(p, "path/.git");
        assertGlobMatch(p, "path/.hidden.txt");
        assertGlobMatch(p, "/path/.git");
        assertGlobMatch(p, "/path/.hidden.txt");
        assertNoGlobMatch(p, "path/git");
        assertNoGlobMatch(p, "pat.h/hidden.txt");

        p = "._*";

        assertGlobMatch(p, "._git");
        assertGlobMatch(p, "._hidden.txt");
        assertNoGlobMatch(p, "git");
        assertNoGlobMatch(p, "hidden.txt");
        assertNoGlobMatch(p, "path/._git");
        assertNoGlobMatch(p, "path/._hidden.txt");

        p = "**/._*";
        assertGlobMatch(p, "._git");
        assertGlobMatch(p, "._hidden.txt");
        assertNoGlobMatch(p, "git");
        assertNoGlobMatch(p, "hidden._txt");

        assertGlobMatch(p, "path/._git");
        assertGlobMatch(p, "path/._hidden.txt");
        assertGlobMatch(p, "/path/._git");
        assertGlobMatch(p, "/path/._hidden.txt");
        assertNoGlobMatch(p, "path/git");
        assertNoGlobMatch(p, "pat.h/hidden._txt");
    }

    @Test
    public void vscode_file_pattern() {
        // See https://github.com/microsoft/vscode/blob/e49b522e12d1a55dfe2950af355767c9d5b824aa/src/vs/base/test/common/glob.test.ts#L161
        String p = "*.js";

        assertGlobMatch(p, "foo.js");
        assertNoGlobMatch(p, "folder/foo.js");
        assertNoGlobMatch(p, "/node_modules/foo.js");
        assertNoGlobMatch(p, "foo.jss");
        assertNoGlobMatch(p, "some.js/test");

        p = "html.*";
        assertGlobMatch(p, "html.js");
        assertGlobMatch(p, "html.txt");
        assertNoGlobMatch(p, "htm.txt");

        p = "*.*";
        assertGlobMatch(p, "html.js");
        assertGlobMatch(p, "html.txt");
        assertGlobMatch(p, "htm.txt");
        assertNoGlobMatch(p, "folder/foo.js");
        assertNoGlobMatch(p, "/node_modules/foo.js");

        p = "node_modules/test/*.js";
        assertGlobMatch(p, "node_modules/test/foo.js");
        assertNoGlobMatch(p, "folder/foo.js");
        assertNoGlobMatch(p, "/node_module/test/foo.js");
        assertNoGlobMatch(p, "foo.jss");
        assertNoGlobMatch(p, "some.js/test");
    }

    @Test
    public void vscode_star() {
        // See https://github.com/microsoft/vscode/blob/e49b522e12d1a55dfe2950af355767c9d5b824aa/src/vs/base/test/common/glob.test.ts#L190C3-L203C48
        String p = "node*modules";

        assertGlobMatch(p, "node_modules");
        assertGlobMatch(p, "node_super_modules");
        assertNoGlobMatch(p, "node_module");
        //assertNoGlobMatch(p, "/node_modules");
        assertNoGlobMatch(p, "test/node_modules");

        p = "*";
        assertGlobMatch(p, "html.js");
        assertGlobMatch(p, "html.txt");
        assertGlobMatch(p, "htm.txt");
        assertNoGlobMatch(p, "folder/foo.js");
        assertNoGlobMatch(p, "/node_modules/foo.js");
    }

    @Test
    public void vscode_file_folder_match() {
        // See https://github.com/microsoft/vscode/blob/e49b522e12d1a55dfe2950af355767c9d5b824aa/src/vs/base/test/common/glob.test.ts#L207C3-L221C51
        String p = "**/node_modules/**";

        assertGlobMatch(p, "node_modules");
        assertGlobMatch(p, "node_modules/");
        assertGlobMatch(p, "a/node_modules");
        assertGlobMatch(p, "a/node_modules/");
        assertGlobMatch(p, "node_modules/foo");
        assertGlobMatch(p, "foo/node_modules/foo/bar");

        assertGlobMatch(p, "/node_modules");
        assertGlobMatch(p, "/node_modules/");
        assertGlobMatch(p, "/a/node_modules");
        assertGlobMatch(p, "/a/node_modules/");
        assertGlobMatch(p, "/node_modules/foo");
        assertGlobMatch(p, "/foo/node_modules/foo/bar");
    }

    @Test
    public void vscode_questionmark() {
        // See https://github.com/microsoft/vscode/blob/e49b522e12d1a55dfe2950af355767c9d5b824aa/src/vs/base/test/common/glob.test.ts#L225C3-L238C48
        String p = "node?modules";

        assertGlobMatch(p, "node_modules");
        assertNoGlobMatch(p, "node_super_modules");
        assertNoGlobMatch(p, "node_module");
        //assertNoGlobMatch(p, "/node_modules");
        //assertNoGlobMatch(p, "test/node_modules");

        p = "?";
        assertGlobMatch(p, "h");
        assertNoGlobMatch(p, "html.txt");
        assertNoGlobMatch(p, "htm.txt");
        assertNoGlobMatch(p, "folder/foo.js");
        assertNoGlobMatch(p, "/node_modules/foo.js");
    }

    @Test
    public void vscode_globstar() {
        // See https://github.com/microsoft/vscode/blob/e49b522e12d1a55dfe2950af355767c9d5b824aa/src/vs/base/test/common/glob.test.ts#L242C3-L352C42
        String p = "**/*.js";

        assertGlobMatch(p, "foo.js");
        assertGlobMatch(p, "/foo.js");
        assertGlobMatch(p, "folder/foo.js");
        assertGlobMatch(p, "/node_modules/foo.js");
        assertNoGlobMatch(p, "foo.jss");
        assertNoGlobMatch(p, "some.js/test");
        assertNoGlobMatch(p, "/some.js/test");
        //assertNoGlobMatch(p, "\\some.js\\test"); // invalid file uri

        p = "**/project.json";

        assertGlobMatch(p, "project.json");
        assertGlobMatch(p, "/project.json");
        assertGlobMatch(p, "some/folder/project.json");
        assertGlobMatch(p, "/some/folder/project.json");
        assertNoGlobMatch(p, "some/folder/file_project.json");
        assertNoGlobMatch(p, "some/folder/fileproject.json");
        assertNoGlobMatch(p, "some/rrproject.json");
        //assertNoGlobMatch(p, "some\\rrproject.json"); // invaid file uri

        p = "test/**";
        assertGlobMatch(p, "test");
        assertGlobMatch(p, "test/foo");
        assertGlobMatch(p, "test/foo/");
        assertGlobMatch(p, "test/foo.js");
        assertGlobMatch(p, "test/other/foo.js");
        assertNoGlobMatch(p, "est/other/foo.js");

        p = "**";
        assertGlobMatch(p, "/");
        assertGlobMatch(p, "foo.js");
        assertGlobMatch(p, "folder/foo.js");
        assertGlobMatch(p, "folder/foo/");
        assertGlobMatch(p, "/node_modules/foo.js");
        assertGlobMatch(p, "foo.jss");
        assertGlobMatch(p, "some.js/test");

        p = "test/**/*.js";
        assertGlobMatch(p, "test/foo.js");
        assertGlobMatch(p, "test/other/foo.js");
        assertGlobMatch(p, "test/other/more/foo.js");
        assertNoGlobMatch(p, "test/foo.ts");
        assertNoGlobMatch(p, "test/other/foo.ts");
        assertNoGlobMatch(p, "test/other/more/foo.ts");

        p = "**/**/*.js";

        assertGlobMatch(p, "foo.js");
        assertGlobMatch(p, "folder/foo.js");
        assertGlobMatch(p, "/node_modules/foo.js");
        assertNoGlobMatch(p, "foo.jss");
        assertNoGlobMatch(p, "some.js/test");

        p = "**/node_modules/**/*.js";

        assertNoGlobMatch(p, "foo.js");
        assertNoGlobMatch(p, "folder/foo.js");
        assertGlobMatch(p, "node_modules/foo.js");
        assertGlobMatch(p, "/node_modules/foo.js");
        assertGlobMatch(p, "node_modules/some/folder/foo.js");
        assertGlobMatch(p, "/node_modules/some/folder/foo.js");
        assertNoGlobMatch(p, "node_modules/some/folder/foo.ts");
        assertNoGlobMatch(p, "foo.jss");
        assertNoGlobMatch(p, "some.js/test");

        p = "{**/node_modules/**,**/.git/**,**/bower_components/**}";

        assertGlobMatch(p, "node_modules");
        assertGlobMatch(p, "/node_modules");
        assertGlobMatch(p, "/node_modules/more");
        assertGlobMatch(p, "some/test/node_modules");
        //assertGlobMatch(p, "some\\test\\node_modules"); // invalid file uri
        assertGlobMatch(p, "/some/test/node_modules");
        //assertGlobMatch(p, "\\some\\test\\node_modules"); // invalid file uri
        //assertGlobMatch(p, "C:\\\\some\\test\\node_modules"); // invalid file uri
        //assertGlobMatch(p, "C:\\\\some\\test\\node_modules\\more"); // invalid file uri

        assertGlobMatch(p, "bower_components");
        assertGlobMatch(p, "bower_components/more");
        assertGlobMatch(p, "/bower_components");
        assertGlobMatch(p, "some/test/bower_components");
        //assertGlobMatch(p, "some\\test\\bower_components"); // invalid file uri
        assertGlobMatch(p, "/some/test/bower_components");
        //assertGlobMatch(p, "\\some\\test\\bower_components"); // invalid file uri
        //assertGlobMatch(p, "C:\\\\some\\test\\bower_components"); // invalid file uri
        //assertGlobMatch(p, "C:\\\\some\\test\\bower_components\\more"); // invalid file uri

        assertGlobMatch(p, ".git");
        assertGlobMatch(p, "/.git");
        assertGlobMatch(p, "some/test/.git");
        //assertGlobMatch(p, "some\\test\\.git"); // invalid file uri
        assertGlobMatch(p, "/some/test/.git");
        //assertGlobMatch(p, "\\some\\test\\.git"); // invalid file uri
        //assertGlobMatch(p, "C:\\\\some\\test\\.git"); // invalid file uri

        assertNoGlobMatch(p, "tempting");
        assertNoGlobMatch(p, "/tempting");
        assertNoGlobMatch(p, "some/test/tempting");
        //assertNoGlobMatch(p, "some\\test\\tempting"); // invalid file uri
        assertNoGlobMatch(p, "/some/test/tempting");
        //assertNoGlobMatch(p, "\\some\\test\\tempting"); // invalid file uri
        //assertNoGlobMatch(p, "C:\\\\some\\test\\tempting"); // invalid file uri

        p = "{**/package.json,**/project.json}";
        assertGlobMatch(p, "package.json");
        assertGlobMatch(p, "/package.json");
        assertGlobMatch(p, "src/package.json");
        assertNoGlobMatch(p, "xpackage.json");
        assertNoGlobMatch(p, "/xpackage.json");
    }

    @Test
    public void vscode_issue_41724() {
        // See https://github.com/microsoft/vscode/blob/e49b522e12d1a55dfe2950af355767c9d5b824aa/src/vs/base/test/common/glob.test.ts#L356
        String p = "some/**/*.js";

        assertGlobMatch(p, "some/foo.js");
        assertGlobMatch(p, "some/folder/foo.js");
        assertNoGlobMatch(p, "something/foo.js");
        assertNoGlobMatch(p, "something/folder/foo.js");

        p = "some/**/*";

        assertGlobMatch(p, "some/foo.js");
        assertGlobMatch(p, "some/folder/foo.js");
        assertNoGlobMatch(p, "something/foo.js");
        assertNoGlobMatch(p, "something/folder/foo.js");
    }

    @Test
    public void vscode_brace_expansion() {
        // See https://github.com/microsoft/vscode/blob/e49b522e12d1a55dfe2950af355767c9d5b824aa/src/vs/base/test/common/glob.test.ts#L372C3-L469C39
        String p = "*.{html,js}";

        assertGlobMatch(p, "foo.js");
        assertGlobMatch(p, "foo.html");
        assertNoGlobMatch(p, "folder/foo.js");
        assertNoGlobMatch(p, "/node_modules/foo.js");
        assertNoGlobMatch(p, "foo.jss");
        assertNoGlobMatch(p, "some.js/test");

        p = "*.{html}";

        assertGlobMatch(p, "foo.html");
        assertNoGlobMatch(p, "foo.js");
        assertNoGlobMatch(p, "folder/foo.js");
        assertNoGlobMatch(p, "/node_modules/foo.js");
        assertNoGlobMatch(p, "foo.jss");
        assertNoGlobMatch(p, "some.js/test");

        p = "{node_modules,testing}";
        assertGlobMatch(p, "node_modules");
        assertGlobMatch(p, "testing");
        assertNoGlobMatch(p, "node_module");
        assertNoGlobMatch(p, "dtesting");

        p = "**/{foo,bar}";
        assertGlobMatch(p, "foo");
        assertGlobMatch(p, "bar");
        assertGlobMatch(p, "test/foo");
        assertGlobMatch(p, "test/bar");
        assertGlobMatch(p, "other/more/foo");
        assertGlobMatch(p, "other/more/bar");
        assertGlobMatch(p, "/foo");
        assertGlobMatch(p, "/bar");
        assertGlobMatch(p, "/test/foo");
        assertGlobMatch(p, "/test/bar");
        assertGlobMatch(p, "/other/more/foo");
        assertGlobMatch(p, "/other/more/bar");

        p = "{foo,bar}/**";
        assertGlobMatch(p, "foo");
        assertGlobMatch(p, "bar");
        assertGlobMatch(p, "bar/");
        assertGlobMatch(p, "foo/test");
        assertGlobMatch(p, "bar/test");
        assertGlobMatch(p, "bar/test/");
        assertGlobMatch(p, "foo/other/more");
        assertGlobMatch(p, "bar/other/more");
        assertGlobMatch(p, "bar/other/more/");

        p = "{**/*.d.ts,**/*.js}";

        assertGlobMatch(p, "foo.js");
        assertGlobMatch(p, "testing/foo.js");
        //assertGlobMatch(p, "testing\\foo.js"); // invalid file uri
        assertGlobMatch(p, "/testing/foo.js");
        //assertGlobMatch(p, "\\testing\\foo.js"); // invalid file uri
        //assertGlobMatch(p, "C:\\testing\\foo.js"); // invalid file uri

        assertGlobMatch(p, "foo.d.ts");
        assertGlobMatch(p, "testing/foo.d.ts");
        //assertGlobMatch(p, "testing\\foo.d.ts"); // invalid file uri
        assertGlobMatch(p, "/testing/foo.d.ts");
        //assertGlobMatch(p, "\\testing\\foo.d.ts"); // invalid file uri
        //assertGlobMatch(p, "C:\\testing\\foo.d.ts"); // invalid file uri

        assertNoGlobMatch(p, "foo.d");
        assertNoGlobMatch(p, "testing/foo.d");
        //assertNoGlobMatch(p, "testing\\foo.d"); // invalid file uri
        assertNoGlobMatch(p, "/testing/foo.d");
        //assertNoGlobMatch(p, "\\testing\\foo.d"); // invalid file uri
        //assertNoGlobMatch(p, "C:\\testing\\foo.d"); // invalid file uri

        p = "{**/*.d.ts,**/*.js,path/simple.jgs}";

        //assertGlobMatch(p, "foo.js"); // invalid file uri
        assertGlobMatch(p, "testing/foo.js");
        //assertGlobMatch(p, "testing\\foo.js"); // invalid file uri
        assertGlobMatch(p, "/testing/foo.js");
        assertGlobMatch(p, "path/simple.jgs");
        //assertNoGlobMatch(p, "/path/simple.jgs"); // invalid file uri
        //assertGlobMatch(p, "\\testing\\foo.js"); // invalid file uri
        //assertGlobMatch(p, "C:\\testing\\foo.js"); // invalid file uri

        p = "{**/*.d.ts,**/*.js,foo.[0-9]}";

        assertGlobMatch(p, "foo.5");
        assertGlobMatch(p, "foo.8");
        assertNoGlobMatch(p, "bar.5");
        assertNoGlobMatch(p, "foo.f");
        assertGlobMatch(p, "foo.js");

        p = "prefix/{**/*.d.ts,**/*.js,foo.[0-9]}";

        assertGlobMatch(p, "prefix/foo.5");
        assertGlobMatch(p, "prefix/foo.8");
        assertNoGlobMatch(p, "prefix/bar.5");
        assertNoGlobMatch(p, "prefix/foo.f");
        assertGlobMatch(p, "prefix/foo.js");
    }

    @Test
    public void vscode_bracket() {
        // See https://github.com/microsoft/vscode/blob/e49b522e12d1a55dfe2950af355767c9d5b824aa/src/vs/base/test/common/glob.test.ts#L528C3-L580C31
        String p = "foo.[0-9]";

        assertGlobMatch(p, "foo.5");
        assertGlobMatch(p, "foo.8");
        assertNoGlobMatch(p, "bar.5");
        assertNoGlobMatch(p, "foo.f");

        // Seems ^ not supported by Java NIO
        p = "foo.[^0-9]";

        //assertNoGlobMatch(p, "foo.5");
        //assertNoGlobMatch(p, "foo.8");
        //assertNoGlobMatch(p, "bar.5");
        //assertGlobMatch(p, "foo.f");

        p = "foo.[!0-9]";

        assertNoGlobMatch(p, "foo.5");
        assertNoGlobMatch(p, "foo.8");
        assertNoGlobMatch(p, "bar.5");
        assertGlobMatch(p, "foo.f");

        p = "foo.[0!^*?]";

        assertNoGlobMatch(p, "foo.5");
        assertNoGlobMatch(p, "foo.8");
        assertGlobMatch(p, "foo.0");
        assertGlobMatch(p, "foo.!");
        //assertGlobMatch(p, "foo.^"); // not a valid file uri
        //assertGlobMatch(p, "foo.*"); // not a valid file uri
        //assertGlobMatch(p, "foo.?"); // not a valid file uri

        // Seems [/] not supported by Java NIO (fails with java.util.regex.PatternSyntaxException: Explicit 'name separator' in class near index 19
        //C:/Users/azerr/foo[/]bar)
        p = "foo[/]bar";

        //assertNoGlobMatch(p, "foo/bar");

        p = "foo.[[]";

        //assertGlobMatch(p, "foo.["); // not a valid file uri

        p = "foo.[]]";

        // assertGlobMatch(p, "foo.]"); // not a valid file uri

        p = "foo.[][!]";

        //assertGlobMatch(p, "foo.]"); // not a valid file uri
        //assertGlobMatch(p, "foo.["); // not a valid file uri

        // java.util.regex.PatternSyntaxException: Unclosed character class near index 47
//^C:\\Users\\azerr\\foo\.[[^\\]&&[]][[^\\]&&[^]]$
        // assertGlobMatch(p, "foo.!");

        p = "foo.[]-]";

        //assertGlobMatch(p, "foo.]"); // not a valid file uri
        //assertGlobMatch(p, "foo.-");
    }

    private void assertGlobMatch(String pattern, String uri) {
        assertGlobMatch(pattern, uri, true);
    }

    private void assertNoGlobMatch(String pattern, String uri) {
        assertGlobMatch(pattern, uri, false);
    }

    private void assertGlobMatch(String pattern, String uri, boolean expected) {

        // Tests must be adapted to use file uri

        String baseUri = getBaseUri();
        if (uri.startsWith("/")) {
            uri = baseUri + uri.substring(1);
        } else {
            uri = baseUri + uri;
        }

        registerWatchers("""
                {"watchers": [
                          {
                            "globPattern": "%s"
                          }
                          ]}
                """.formatted(pattern));
        if (expected) {
            assertMatchFile(uri);
        } else {
            assertNoMatchFile(uri);
        }
    }

    private void assertMatchFile(String fileUri) {
        assertMatchFile(fileUri, WatchKind.Create);
    }

    private void assertMatchFile(String fileUri, int kind) {
        URI uri = URI.create(fileUri);
        boolean matched = manager.isMatchFilePattern(uri, kind);
        String watchers = JSONUtils.getLsp4jGson().toJson(manager.getFileSystemWatchers());
        assertTrue(watchers + " should match " + fileUri, matched);
    }

    private void assertNoMatchFile(String fileUri) {
        assertNoMatchFile(fileUri, WatchKind.Create);
    }

    private void assertNoMatchFile(String fileUri, int kind) {
        URI uri = URI.create(fileUri);
        boolean matched = manager.isMatchFilePattern(uri, kind);
        String watchers = JSONUtils.getLsp4jGson().toJson(manager.getFileSystemWatchers());
        assertFalse(watchers + " should not match " + fileUri, matched);
    }

    private void registerWatchers(String watchers) {
        registerWatchers(DEFAULT_WATCHER, watchers);
    }

    private void registerWatchers(String id, String watchers) {
        var options = JSONUtils.getLsp4jGson().fromJson(watchers, DidChangeWatchedFilesRegistrationOptions.class);
        manager.registerFileSystemWatchers(id, options.getWatchers());
    }

    private static String getBaseUri() {
        return (SystemInfo.isWindows ? "file:///" : "file://") + getProjectDir();
    }

    private static String getExternalDir() {
        if (SystemInfo.isWindows) {
            return "file:///C://Users/user/a-project/";
        }
        return "file:///home/user/a-project/";

    }

    private static String getProjectDir() {
        if (SystemInfo.isWindows) {
            return "C:/Users/user/current-project/";
        }
        return "/home/user/current-project/";
    }
}
