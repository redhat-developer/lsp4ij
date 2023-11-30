# Check version to clone from https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#intellij-platform-based-products-of-recent-ide-versions
IDEA_VERSION=$(grep '^pluginSinceBuild' gradle.properties | cut -d '=' -f 2 | tr -d ' ') # 222 = 2022.2
rm -rf intellij-community-tmp 2>/dev/null
git clone --single-branch --depth 1 --branch $IDEA_VERSION https://github.com/JetBrains/intellij-community intellij-community-tmp
rm -rf intellij-community 2>/dev/null
mkdir -p intellij-community/java/testFramework/src/com/intellij/compiler/artifacts
cp -p intellij-community-tmp/java/testFramework/src/com/intellij/compiler/artifacts/ArtifactsTestUtil.java intellij-community/java/testFramework/src/com/intellij/compiler/artifacts
mkdir -p intellij-community/platform/testFramework/extensions/src/com/intellij/testFramework/assertions
cp -p intellij-community-tmp/platform/testFramework/extensions/src/com/intellij/testFramework/assertions/Assertions.java intellij-community/platform/testFramework/extensions/src/com/intellij/testFramework/assertions
cp -p intellij-community-tmp/platform/testFramework/extensions/src/com/intellij/testFramework/assertions/JdomAssert.kt intellij-community/platform/testFramework/extensions/src/com/intellij/testFramework/assertions
cp -p intellij-community-tmp/platform/testFramework/extensions/src/com/intellij/testFramework/assertions/StringAssertEx.kt intellij-community/platform/testFramework/extensions/src/com/intellij/testFramework/assertions
cp -p intellij-community-tmp/platform/testFramework/extensions/src/com/intellij/testFramework/assertions/snapshot.kt intellij-community/platform/testFramework/extensions/src/com/intellij/testFramework/assertions
cp -p intellij-community-tmp/platform/testFramework/extensions/src/com/intellij/testFramework/assertions/PathAssertEx.kt intellij-community/platform/testFramework/extensions/src/com/intellij/testFramework/assertions
mkdir -p intellij-community/platform/lang-impl/testSources/com/intellij/openapi/roots/ui/configuration
cp intellij-community-tmp/platform/lang-impl/testSources/com/intellij/openapi/roots/ui/configuration/SdkTestCase.kt intellij-community/platform/lang-impl/testSources/com/intellij/openapi/roots/ui/configuration
mkdir -p intellij-community/platform/external-system-api/testFramework/src/com/intellij/platform/externalSystem/testFramework
cp -p intellij-community-tmp/platform/external-system-api/testFramework/src/com/intellij/platform/externalSystem/testFramework/ExternalSystemImportingTestCase.java intellij-community/platform/external-system-api/testFramework/src/com/intellij/platform/externalSystem/testFramework
cp -p intellij-community-tmp/platform/external-system-api/testFramework/src/com/intellij/platform/externalSystem/testFramework/ExternalSystemTestCase.java intellij-community/platform/external-system-api/testFramework/src/com/intellij/platform/externalSystem/testFramework
mkdir -p intellij-community/platform/external-system-impl/testSrc/com/intellij/openapi/externalSystem/service/execution
cp intellij-community-tmp/platform/external-system-impl/testSrc/com/intellij/openapi/externalSystem/service/execution/TestUnknownSdkResolver.kt intellij-community/platform/external-system-impl/testSrc/com/intellij/openapi/externalSystem/service/execution
mkdir -p intellij-community/platform/external-system-impl/testSrc/com/intellij/openapi/externalSystem/test
cp intellij-community-tmp/platform/external-system-impl/testSrc/com/intellij/openapi/externalSystem/test/JavaExternalSystemImportingTestCase.java intellij-community/platform/external-system-impl/testSrc/com/intellij/openapi/externalSystem/test
mkdir -p intellij-community/plugins/maven/testFramework/src/com/intellij/maven/testFramework
cp intellij-community-tmp/plugins/maven/testFramework/src/com/intellij/maven/testFramework/MavenImportingTestCase.java intellij-community/plugins/maven/testFramework/src/com/intellij/maven/testFramework
cp intellij-community-tmp/plugins/maven/testFramework/src/com/intellij/maven/testFramework/MavenTestCase.java intellij-community/plugins/maven/testFramework/src/com/intellij/maven/testFramework
cp intellij-community-tmp/plugins/maven/testFramework/src/com/intellij/maven/testFramework/NullMavenConsole.java intellij-community/plugins/maven/testFramework/src/com/intellij/maven/testFramework
mkdir -p intellij-community/plugins/gradle/testSources/org/jetbrains/plugins/gradle/importing
cp -p intellij-community-tmp/plugins/gradle/testSources/org/jetbrains/plugins/gradle/importing/TestGradleBuildScriptBuilder.kt intellij-community/plugins/gradle/testSources/org/jetbrains/plugins/gradle/importing
cp -p intellij-community-tmp/plugins/gradle/testSources/org/jetbrains/plugins/gradle/importing/GradleImportingTestCase.java intellij-community/plugins/gradle/testSources/org/jetbrains/plugins/gradle/importing
mkdir -p intellij-community/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling/builder
cp -p intellij-community-tmp/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling/builder/AbstractModelBuilderTest.java intellij-community/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling/builder
cp -p intellij-community-tmp/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling/VersionMatcherRule.java intellij-community/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling
mkdir -p intellij-community/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling/annotation
cp -p intellij-community-tmp/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling/annotation/TargetVersions.java intellij-community/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling/annotation
mkdir -p intellij-community/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling/util/
cp -p intellij-community-tmp/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling/util/VersionMatcher.java intellij-community/plugins/gradle/tooling-extension-impl/testSources/org/jetbrains/plugins/gradle/tooling/util
mkdir -p intellij-community/plugins/gradle/src/org/jetbrains/plugins/gradle/util
cp -p intellij-community-tmp/plugins/gradle/src/org/jetbrains/plugins/gradle/util/GradleJvmSupportMatricies.kt intellij-community/plugins/gradle/src/org/jetbrains/plugins/gradle/util
echo "Done!"