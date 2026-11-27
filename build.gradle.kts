import com.adarshr.gradle.testlogger.theme.ThemeType
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.*
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.VerificationReportsFormats.*
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease


fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)
fun prop(name: String): String {
    return properties(name).get()
}

plugins {
    java // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.testLogger) // Nice test logs
    alias(libs.plugins.kover) // Gradle Kover Plugin
    jacoco // Code coverage
}

group = prop("pluginGroup")
version = prop("pluginVersion")

val lsp4jVersion = prop("lsp4jVersion")
val flexmarkVersion = prop("flexmarkVersion")

val platformVersion = prop("platformVersion")
val ideaVersionInt = when {
    // e.g. '20XY.Z', or a release with its patch number, such as '2026.2.5.1': XYZ
    platformVersion.matches(Regex("""20\d\d\.\d+(\.\d+)*""")) ->
        platformVersion.split(".").let { (it[0].substring(2) + it[1]).toInt() }
    // e.g. '2XY.ABCDE.12'
    else -> platformVersion.substringBefore(".").toInt()
}

// Configure project's dependencies
repositories {
    mavenLocal()
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

val lsp = configurations.dependencyScope("lsp")

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Disabling the installer dependency since IntelliJ 253 (2025.3.x) and potentially other
        // early access/snapshot versions may lack direct installer packages in the artifact repositories.
        create(properties("platformType"), properties("platformVersion")) {
            useInstaller = false
        }

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        // starting from 2024.3, all json related code is know on its own plugin
        val platformBundledPlugins =  ArrayList<String>()
        platformBundledPlugins.addAll(properties("platformBundledPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }.get())
        /*
         * platformVersion check for JSON breaking changes since 2024.3
         */
        if (ideaVersionInt >= 243) {
            platformBundledPlugins.add("com.intellij.modules.json")
        }
        println("use bundled Plugins: $platformBundledPlugins")
        bundledPlugins(platformBundledPlugins)

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(properties("platformPlugins").map { it.split(',') })

        if (ideaVersionInt >= 252) {
            // Since 2025.2, JetBrains moved spellchecker in a new "intellij.spellchecker" module
            bundledModule("intellij.spellchecker")
        }
        if (ideaVersionInt >= 262) {
            // Since 2026.2, the debugger API is in modules of its own, and the structure view in a plugin of its own,
            // whose ID is intellij.structureView.plugin in 2026.2, and com.intellij.structureView, with the former as an
            // alias, since 2026.3
            bundledModule("intellij.platform.debugger")
            bundledModule("intellij.platform.debugger.impl")
            bundledModule("intellij.platform.debugger.impl.shared")
            bundledModule("intellij.platform.debugger.impl.ui")
            bundledPlugin(if (ideaVersionInt >= 263) "com.intellij.structureView" else "intellij.structureView.plugin")
        }

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:$lsp4jVersion") {
        exclude(group = "org.eclipse.lsp4j", module="org.eclipse.lsp4j.jsonrpc")
    }
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:$lsp4jVersion") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:$lsp4jVersion") {
        exclude(group = "org.eclipse.lsp4j", module = "org.eclipse.lsp4j.jsonrpc.debug")
    }
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc.debug:$lsp4jVersion") {
        exclude(group = "com.google.code.gson", module = "gson")
    }

    implementation("com.vladsch.flexmark:flexmark:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:$flexmarkVersion")

    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly(libs.junit.vintage.engine)
    // Required for IntelliJ Platform 2026.1 to discover and run tests via JUnit Platform, and by HangingTestWatchdog,
    // a listener of the launcher
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.0")
}

// Set the JDK version used by the toolchain to build the project (Java 21 is required for platform 2025.3, and Java 25
// for platform 2026.2 and later, whose classes are Java 25 classes). Output compatibility and API usage are restricted
// to Java 17 in the tasks below.
kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(if (ideaVersionInt >= 262) 25 else 21)
    }
}

// Maintain compatibility with Java 17 runtimes, ensuring the built plugin 
// can run on older IDEs even though the toolchain uses Java 21 for the 253 platform.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

// Set Kotlin JVM language level to match Java compatibility.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    // LSP4IJ has no GUI Designer forms, so instrumentation only added null checks for @NotNull to the classes. It goes
    // through Gradle's Ant integration (Task.getAnt()), which Gradle 9.9 deprecates and Gradle 10 removes
    // (JetBrains/intellij-platform-gradle-plugin#2294)
    instrumentCode = false

    // check builds the plugin, and so its searchable options, which start an IDE when the build cache hasn't got them.
    // The Test jobs of a pull request leave them out with -PbuildSearchableOptions=false, as no test reads them; every
    // other build has them, as on main, in the Build Plugin zip workflow and in releases.
    buildSearchableOptions = properties("buildSearchableOptions").map(String::toBooleanStrict).orElse(true)

    pluginConfiguration {
        name = properties("pluginName")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
                    .joinToString("\n")
                    .replace("](./docs/images/", "](https://raw.githubusercontent.com/redhat-developer/lsp4ij/main/docs/images/")
                    .replace("](./", "](https://github.com/redhat-developer/lsp4ij/blob/main/")
                    .let(::markdownToHTML)
            }
        }

        // Change notes are maintained in plugin.xml, not rendered from CHANGELOG.md by the Changelog plugin
        changeNotes = provider { null }

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = environment("PUBLISH_TOKEN")
        channels = properties("channel").map { listOf(it) }
    }

    pluginVerification {
        // MISSING_DEPENDENCIES also fails for unavailable optional="true" dependencies; see https://youtrack.jetbrains.com/issue/MP-2974.
        failureLevel = listOf(INVALID_PLUGIN, COMPATIBILITY_PROBLEMS)
        verificationReportsFormats = listOf(MARKDOWN, PLAIN)
        ides {
            // A pull request is verified against the IDEs that IJ.yml names, the oldest and the newest release, where
            // an API goes missing or is removed; main, against every release since the oldest, and the current EAP
            val verifiedIdes = providers.gradleProperty("verifiedIdes").orNull
            if (verifiedIdes != null) {
                verifiedIdes.split(',').map(String::trim).filter(String::isNotEmpty).forEach { version ->
                    create(IntelliJPlatformType.IntellijIdea, version)
                }
            } else {
                select {
                    types = listOf(IntelliJPlatformType.IntellijIdea)
                    // The latest release of each version, and the current EAP, so that an API that the coming
                    // version removes fails the verification before that version is released.
                    channels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.EAP)
                    sinceBuild = "242" // From your minimum supported version
                }
            }
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

configurations {
    runtimeClasspath {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
}

testlogger {
    theme = ThemeType.STANDARD
    showExceptions = true
    showStackTraces = true
    showFullStackTraces = false
    showCauses = true
    slowThreshold = 2000
    showSummary = true
    showSimpleNames = false
    showPassed = true
    showSkipped = true
    showFailed = true
    showOnlySlow = false
    showStandardStreams = false
    showPassedStandardStreams = true
    showSkippedStandardStreams = true
    showFailedStandardStreams = true
    logLevel = LogLevel.LIFECYCLE
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = properties("pluginRepositoryUrl")
}

// Tests that need IntelliJ's JavaScript plugin. The sandbox of the other tests disables it (see prepareTestSandbox), so
// they run in testJavaScript, whose sandbox keeps it. The IntelliJ Platform Gradle Plugin makes check run testJavaScript.
// Of the IDEs the tests run on, only IntelliJ IDEA Ultimate (-PplatformType=IU, as on CI's 2026.3 leg) bundles the plugin;
// elsewhere the task is skipped.
val javaScriptPluginTests = "**/*JavaScriptPluginTest.class"
val bundlesJavaScriptPlugin = properties("platformType").get() == "IU"

intellijPlatformTesting {
    testIde {
        register("testJavaScript") {
            testFramework(TestFrameworkType.Platform)
            task {
                description = "Runs the tests that need the JavaScript plugin (*JavaScriptPluginTest), with -PplatformType=IU."
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                // Gradle gives the classes and the classpath of the test source set to the test task only. Of those, the
                // IntelliJ Platform Gradle Plugin gives this task only the classes of instrumentTestCode, which
                // instrumentCode = false leaves empty. As for test, the main classes and their dependencies come from the
                // plugin in the sandbox, so they are left out of the test runtime classpath that is added here.
                val testSourceSet = sourceSets.test.get()
                testClassesDirs = testSourceSet.output.classesDirs
                classpath += testSourceSet.runtimeClasspath - sourceSets.main.get().runtimeClasspath
                include(javaScriptPluginTests)
                // Local copies: the configuration cache can't store a reference to the build script
                val bundlesPlugin = bundlesJavaScriptPlugin
                val testPattern = javaScriptPluginTests
                onlyIf("the IDE bundles the JavaScript plugin (-PplatformType=IU)") { bundlesPlugin }
                // Gradle skips a test task that finds no test class as NO-SOURCE, and the build passes. Checked before
                // that, and only where the tests are to run, this fails the build instead.
                onlyIf("testJavaScript finds a $testPattern") { task ->
                    val testTask = task as Test
                    if (testTask.candidateClassFiles.isEmpty) {
                        throw GradleException("${task.path} finds no $testPattern in ${testTask.testClassesDirs.files}")
                    }
                    true
                }
            }
        }
    }
}

/**
 * How many test JVMs a test task runs at once when -PtestForks doesn't say: half the CPUs, rounded up, since a test JVM
 * keeps more than one CPU busy, and no more than the memory holds, at 3 GB a JVM once 4 GB are left to Gradle and the OS.
 * A test JVM gets the -Xmx2048m and -XX:ReservedCodeCacheSize=512m of the IDE's vmoptions, and takes up to about 3 GB
 * with its metaspace and native memory. That makes 5 on a Mac with 10 CPUs and 64 GB, 2 on GitHub's Linux and Windows
 * runners (4 CPUs, 16 GB), and 1 on its macOS runner (3 CPUs, 7 GB). As a ValueSource, it's computed again when the build
 * reuses its configuration cache, as on another machine.
 */
abstract class MachineTestForks : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int {
        val byCpus = (Runtime.getRuntime().availableProcessors() + 1) / 2
        val memoryMb = (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)?.totalMemorySize?.div(1024 * 1024)
        val byMemory = memoryMb?.let { ((it - 4096) / 3072).toInt() } ?: byCpus
        return byCpus.coerceAtMost(byMemory).coerceAtLeast(1)
    }
}

// With more than one fork, TestForkSandbox gives each test JVM a sandbox of its own
val testForks = properties("testForks")
    .map { it.toIntOrNull()?.takeIf { forks -> forks >= 1 } ?: throw GradleException("-PtestForks=$it is no number of test JVMs, 1 or more") }
    .orElse(providers.of(MachineTestForks::class) {})
    .get()

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    environment("GRADLE_RELEASE_REPOSITORY","https://services.gradle.org/distributions")
    systemProperty("idea.log.leaked.projects.in.tests", "false")
    systemProperty( "idea.maven.test.mirror", "https://repo1.maven.org/maven2")
    // Since 2026.3, IntelliJ writes <pid>-ide-instance.json, which tells external tools about a running IDE, to the
    // user's own JetBrains data directory (PathManager.getCommonDataPath()/discovery) each time a project opens or
    // closes. It also deletes the stale files of other IDEs there. The write on a close is launched and not awaited,
    // so the thread leak check of a test that closes its project can find it still running. The tests need none of
    // it. The property, misspelled as in IntelliJ, has no effect on older versions.
    systemProperty("jetbrains.ide.builtin.descovery.enabled", "false")
    maxParallelForks = testForks
    if (testForks > 1) {
        systemProperty("lsp4ij.test.forks", testForks)
        systemProperty("junit.platform.launcher.interceptors.enabled", "true")
    }
    // Local copy: the configuration cache can't store a reference to the build script
    val jvms = if (testForks == 1) "1 test JVM" else "up to $testForks test JVMs at once"
    doFirst {
        logger.lifecycle("$path runs its test classes in $jvms (-PtestForks=<n> sets how many)")
    }
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion").get()
        // The default of 10 seconds is at times too short for a CI runner to download the distribution
        networkTimeout = 60_000
        // A download of the distribution that fails, as on a CI runner, is tried again after 1, 2 and 4 seconds
        retries = 3
        retryBackOffMs = 1_000
    }

    prepareTestSandbox {
        // The tests load every plugin bundled with the IDE they run on. IntelliJ IDEA Ultimate and the unified IntelliJ
        // IDEA, such as 2026.3, bundle plugins for JavaScript and CSS, which take over the .js, .ts and .css files of
        // the tests that IntelliJ IDEA Community leaves to LSP4IJ. Community has neither plugin, so there, this changes
        // nothing.
        disabledPlugins.addAll("JavaScript", "com.intellij.css")
    }

    test {
        // They run in testJavaScript, with the JavaScript plugin
        exclude(javaScriptPluginTests)
        // -PtestShard=<k>/<n> runs only the k-th of n parts of the test classes, so that CI can run them in n jobs on a
        // runner that holds only one test JVM. A class belongs to the part that the hash of its name gives, with its
        // nested classes, so the parts divide the classes without a list to keep up to date, and about evenly.
        properties("testShard").orNull?.let { shard ->
            val (part, parts) = shard.split("/").mapNotNull(String::toIntOrNull).takeIf { it.size == 2 }
                ?.takeIf { (k, n) -> k in 1..n }
                ?: throw GradleException("-PtestShard=$shard is no <k>/<n> with k from 1 to n")
            exclude { element ->
                !element.isDirectory && element.path.endsWith(".class")
                        && Math.floorMod(element.path.substringBefore('$').removeSuffix(".class").hashCode(), parts) != part - 1
            }
            doFirst {
                logger.lifecycle("$path runs part $part of $parts of the test classes (-PtestShard=$shard)")
            }
        }
    }

    // TODO: Once LSP4IJ no longer supports 2023.*, the hotswap JVM args can be added unconditionally
    fun supportsEnhancedClassRedefinition(): Boolean {
        // Versions before 2024.1 don't support enhanced class redefinition
        return ideaVersionInt >= 241
    }

    runIde {
        // Improved hotswap for the IDE's JVM
        if (supportsEnhancedClassRedefinition()) {
            println("Enabling enhanced class redefinition.")
            jvmArgs("-XX:+AllowEnhancedClassRedefinition", "-XX:HotswapAgent=fatjar")
        }

    }

    publishPlugin {
        // The IntelliJ Platform Gradle Plugin makes publishing run patchChangelog, which creates an empty CHANGELOG.md;
        // change notes are maintained in plugin.xml
        setDependsOn(dependsOn - "patchChangelog")
    }

    jacocoTestReport {
        reports {
            xml.required = true
            html.required = true
        }
    }

    check {
        dependsOn(jacocoTestReport)
    }
}
