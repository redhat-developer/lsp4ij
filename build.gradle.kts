fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)
fun prop(name: String): String {
    return properties(name).get()
}

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = prop("pluginGroup")
version = prop("pluginVersion")

val lsp4jVersion = prop("lsp4jVersion")
val flexmarkVersion = prop("flexmarkVersion")

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://repository.jboss.org/nexus/content/repositories/snapshots") }
    maven { url = uri("https://repository.jboss.org/nexus/content/groups/public") }
    maven { url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
}

dependencies {
    implementation("org.zeroturnaround:zt-zip:1.14")
    implementation("org.jsoup:jsoup:1.17.1")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:$lsp4jVersion")
    // Required by lsp4j as the version from IJ is incompatible
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.vladsch.flexmark:flexmark:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:$flexmarkVersion")
    testImplementation("com.redhat.devtools.intellij:intellij-common-ui-test-library:0.2.0")
    testImplementation("org.assertj:assertj-core:3.19.0")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2023.3")
    type.set("IC") // Target IDE Platform
    plugins.set(listOf("com.redhat.devtools.intellij.telemetry:1.2.1.62", "textmate", "properties"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("243.*")
    }

    // TODO: Once LSP4IJ no longer supports 2023.*, the hotswap JVM args can be added unconditionally
    fun supportsEnhancedClassRedefinition(): Boolean {
        val platformVersion = properties("platformVersion").getOrNull()
        return when (platformVersion) {
            // Versions before 2024.1 don't support enhanced class redefinition
            "2023.3" -> false
            else -> true
        }
    }

    runIde {
        // Improved hotswap for the IDE's JVM
        if (supportsEnhancedClassRedefinition()) {
            jvmArgumentProviders += CommandLineArgumentProvider {
                listOf("-XX:+AllowEnhancedClassRedefinition", "-XX:HotswapAgent=fatjar")
            }
        }
        //Use "debug" to send telemetry to dev source at segment.com
        systemProperties["com.redhat.devtools.intellij.telemetry.mode"] = "debug" // "disabled"
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
