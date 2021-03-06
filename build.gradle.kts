import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.changelog.closure
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml

buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.redundent:kotlin-xml-builder:1.6.0")
    }
}

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
    // gradle-intellij-plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij") version "0.4.21"
    // gradle-changelog-plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
    id("org.jetbrains.changelog") version "0.4.0"
    // detekt linter - read more: https://detekt.github.io/detekt/kotlindsl.html
    id("io.gitlab.arturbosch.detekt") version "1.10.0"
    // ktlint linter - read more: https://github.com/JLLeitschuh/ktlint-gradle
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
}

// Import variables from gradle.properties file
val pluginGroup: String by project
val pluginName: String by project
val pluginVersion: String by project
val pluginSinceBuild: String by project
val pluginUntilBuild: String by project
val buildPluginPath = "ide-plugin-$pluginVersion.zip"

val platformType: String by project
val platformVersion: String by project
val platformDownloadSources: String by project

group = pluginGroup
version = pluginVersion

// Configure project's dependencies
repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://jetbrains.bintray.com/intellij-third-party-dependencies")
}
dependencies {
    implementation(kotlin("stdlib-jdk8"))
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.10.0")
    implementation("org.junit.jupiter:junit-jupiter-params:5.5.1")
    implementation("org.junit.jupiter:junit-jupiter-api:5.5.1")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.5.1")
    implementation("org.jetbrains.intellij.deps.completion:ngram-slp:0.0.2")
    implementation("org.tensorflow", "tensorflow", "1.13.1")
    implementation("com.github.javaparser:javaparser-core:3.0.0-alpha.4")
    implementation("net.razorvine", "pyrolite", "4.19")
    implementation("org.eclipse.mylyn.github", "org.eclipse.egit.github.core", "2.1.5")
}


// Configure gradle-intellij-plugin plugin.
// Read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName = pluginName
    version = platformVersion
    type = platformType
    downloadSources = platformDownloadSources.toBoolean()
    updateSinceUntilBuild = true

//  Plugin Dependencies:
//  https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_dependencies.html
//
    setPlugins("java")
}

// Configure detekt plugin.
// Read more: https://detekt.github.io/detekt/kotlindsl.html
detekt {
    config = files("./detekt-config.yml")
    buildUponDefaultConfig = true

    reports {
        html.enabled = false
        xml.enabled = false
        txt.enabled = false
    }
}

tasks {
    // Set the compatibility versions to 1.8
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    listOf("compileKotlin", "compileTestKotlin").forEach {
        getByName<KotlinCompile>(it) {
            kotlinOptions.jvmTarget = "1.8"
        }
    }

    withType<Detekt> {
        jvmTarget = "1.8"
    }

    patchPluginXml {
        version(pluginVersion)
        sinceBuild(pluginSinceBuild)
        untilBuild(pluginUntilBuild)


        // Get the latest available change notes from the changelog file
        changeNotes(closure {
            changelog.getLatest().toHTML()
        })
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token(System.getenv("PUBLISH_TOKEN"))
        channels(pluginVersion.split('-').getOrElse(1) { "default" }.split('.').first())
    }

    runIde {
        jvmArgs = listOf("-Xmx8G")
    }

//    varMiner part
//    runIde {
//        val dataset: String? by project
//        val saveDir: String? by project
//        args = listOfNotNull("varMiner", dataset, saveDir)
//        jvmArgs = listOf("-Djava.awt.headless=true")
//        maxHeapSize = "8g"
//    }

//    graphVarMiner part
//    runIde {
//        val dataset: String? by project
//        val outputPrefix: String? by project
//        args = listOfNotNull("graphVarMiner", dataset, outputPrefix)
//        jvmArgs = listOf("-Djava.awt.headless=true")
//        maxHeapSize = "8g"
//    }

//    (graph)ModelsEvaluator part
//    runIde {
//        val evaluatorToUse: String? by project
//        val dataset: String? by project
//        val saveDir: String? by project
//        val ngramContributorType: String? by project
//        args = listOfNotNull(evaluatorToUse, dataset, saveDir, ngramContributorType)
//        jvmArgs = listOf("-Djava.awt.headless=true")
//        maxHeapSize = "8g"
//    }


    register("generateUpdatePluginsXML") {
        doLast {
            file("updatePlugins.xml").writer().use { writer ->
                val x = xml("plugins") {
                    globalProcessingInstruction("xml", "version" to "1.0", "encoding" to "UTF-8")
                    comment(
                        "AUTO-GENERATED FILE. DO NOT MODIFY. " +
                                "$buildPluginPath is generated by the generateUpdatePluginsXML gradle task"
                    )
                    "plugin"{
                        "name"{ -"Id Names Suggesting" }
                        "id"{ -"com.github.davidenkoim.idnamessuggestingplugin" }
                        "version"{ -pluginVersion }
                        "idea-version"{ attribute("since-build", pluginSinceBuild) }
                        "vendor"{
                            attribute("url", "https://www.jetbrains.com")
                            -"JetBrains"
                        }
                        "download-url"{ -buildPluginPath }
                        "description"{
                            -"""<![CDATA[
                        <p>Provides assistance in naming variables.</p>

                        <p>To use the <b>Id Names Suggesting</b> tool, press <b>Alt+Enter</b> on a variable and select <b>Suggest variable name</b>.</p>
                        ]]>"""
                        }
                    }
                }
                writer.write(x.toString(PrintOptions(singleLineTextElements = true)))
            }
        }
    }
}