import com.adarshr.gradle.testlogger.theme.ThemeType
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.*
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.VerificationReportsFormats.*
import org.jetbrains.intellij.platform.gradle.TestFrameworkType


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

// Configure project's dependencies
repositories {
    mavenLocal()
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

val lsp: Configuration by configurations.creating

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(properties("platformType"), properties("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        // starting from 2024.3, all json related code is know on its own plugin
        val platformBundledPlugins =  ArrayList<String>()
        platformBundledPlugins.addAll(properties("platformBundledPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }.get())
        /*
         * platformVersion check for JSON breaking changes since 2024.3
         */
        if (prop("platformVersion").startsWith("25")) {
            platformBundledPlugins.add("com.intellij.modules.json")
        }
        println("use bundled Plugins: $platformBundledPlugins")
        bundledPlugins(platformBundledPlugins)

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(properties("platformPlugins").map { it.split(',') })

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:$lsp4jVersion")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:$lsp4jVersion")

    implementation("com.vladsch.flexmark:flexmark:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:$flexmarkVersion")

    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter.api)
}

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
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
        failureLevel = listOf(INVALID_PLUGIN, COMPATIBILITY_PROBLEMS, MISSING_DEPENDENCIES)
        verificationReportsFormats = listOf(MARKDOWN, PLAIN)
        ides {
            recommended()
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

tasks.withType<Test> {
    environment("GRADLE_RELEASE_REPOSITORY","https://services.gradle.org/distributions")
    systemProperty("idea.log.leaked.projects.in.tests", "false")
    systemProperty( "idea.maven.test.mirror", "https://repo1.maven.org/maven2")
    systemProperty( "com.redhat.devtools.intellij.telemetry.mode", "disabled")
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
    }

    // TODO: Once LSP4IJ no longer supports 2023.*, the hotswap JVM args can be added unconditionally
    fun supportsEnhancedClassRedefinition(): Boolean {
        val platformVersion = properties("platformVersion").get()
        // Versions before 2024.1 don't support enhanced class redefinition
        return !platformVersion.startsWith("2023.")
    }

    runIde {
        // Improved hotswap for the IDE's JVM
        if (supportsEnhancedClassRedefinition()) {
            println("Enabling enhanced class redefinition.")
            jvmArgs("-XX:+AllowEnhancedClassRedefinition", "-XX:HotswapAgent=fatjar")
        }

        //Use "debug" to send telemetry to dev source at segment.com
        systemProperties["com.redhat.devtools.intellij.telemetry.mode"] = "debug" // "disabled"
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
