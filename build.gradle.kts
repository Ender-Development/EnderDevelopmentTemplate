// v2.4.0

// =====================================================================================
//  BUILDSCRIPT-MANAGED FILE: DO NOT EDIT
// =====================================================================================
//  This file is part of the buildscript and is overwritten whenever the buildscript is
//  updated (./gradlew updateBuildScript). Any local changes will be lost.
//  Only edit it if you are sure of what you are doing.
//
//  The first line ("//v<version>") is the version header. It is generated from
//  'buildscript_version' in gradle.properties by ./gradlew syncBuildScriptVersion and
//  is used by the update check. Do not change it by hand and keep it on the first line.
//
//  To configure your project, edit the files meant for that instead of this script:
//    - dependencies.properties   add dependencies
//    - tags.properties           constants for the generated reference/tags class
//    - secrets.properties        credentials (never committed)
//    - project.properties        project data, tweak internal properties
//
//  Check the gradle/template/PROPERTIES.md for every property you can override.
//
//  Update tasks:
//    ./gradlew checkBuildScriptUpdate   check if a newer buildscript version exists
//    ./gradlew updateBuildScript        update the buildscript files
// =====================================================================================

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessTask
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.lang.GroovyShell
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.modmuss50.mpp.ReleaseType
import me.modmuss50.mpp.platforms.modrinth.ModrinthEnvironment
import org.gradle.internal.extensions.core.extra
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.changelog.Changelog
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.compiler
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

plugins {
    id("java")
    id("java-library")
    id("maven-publish")
    alias(libs.plugins.jetbrains.idea.ext)
    alias(libs.plugins.retrofuturagradle)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // formatting
    alias(libs.plugins.spotless)
    alias(libs.plugins.clearskies)
    // publishing
    alias(libs.plugins.jetbrains.changelog)
    alias(libs.plugins.mod.publish)
}
// region Setup
val props = PropertyManager()

class PropertyManager {
    /**
     * Loads properties from the specified property file and returns them as a Properties object.
     * @param filePath The path of the properties file to load. e.g. `project.properties`
     * @return A [Properties] object containing the loaded properties with evaluated values
     * @throws GradleException when the given file doesn't exist.
     */
    fun load(filePath: String): Map<String, String> {
        val file = file(filePath)
        require(file.exists()) { "File $filePath doesn't exist!" }
        val props = Properties().apply { file.reader().use { load(it) } }
        return props.stringPropertyNames().associateWith { eval(props.getProperty(it)).trim().ifEmpty { throw GradleException("Empty tag value found for key: $it") } }
    }

    /**
     * Tries to get the value of the given property key.
     * @param key name of the property you want to get
     * @param default what else should be used when the key can't be found
     * @return An evaluated [String] of the value
     * @throws GradleException when the key can't be found and no default is defined
     */
    fun get(
        key: String,
        default: Any? = null,
    ): String =
        eval(
            extra.takeIf { it.has(key) }?.get(key)?.toString()
                ?: findProperty(key)?.toString()
                ?: default?.toString()
                ?: throw GradleException("Property $key needs to be specified!"),
        )

    /**
     * Gets the value of a property key as list otherwise returns an empty one.
     * @param key name of the property to get
     * @param delimiter what separates entries in the value
     * @return An evaluated [MutableList] of the value
     */
    fun list(
        key: String,
        delimiter: String = " ",
    ): MutableList<String> =
        try {
            get(key)
                .split(delimiter)
                .filter { it.isNotEmpty() }
                .map(String::trim)
                .toMutableList()
        } catch (_: GradleException) {
            mutableListOf()
        }

    /**
     * Gets the value of a property from an environment variable.
     * @param key name of the property to get
     * @return Either the value of the environment variable or an evaluated property with the same name
     */
    fun env(key: String): String = System.getenv(key) ?: get(key, "")

    /**
     * Evaluates a boolean from the given key
     * @param key name of the property to get
     * @param default what else to evaluate if no value is found
     * @return The evaluated value as boolean
     */
    fun flag(
        key: String,
        default: Any,
    ): Boolean = get(key, default).toBoolean()

    private fun eval(input: String): String {
        if (input.startsWith($$"${{") && input.endsWith("}}")) {
            val expression = substitute((input.substring(3, input.length - 2).trim()))
            try {
                return GroovyShell().evaluate(expression).toString()
            } catch (e: Exception) {
                throw GradleException("Failed to evaluate expression '$expression' in property evaluation.", e)
            }
        }
        return substitute(input)
    }

    private fun substitute(input: String): String {
        var result = input
        val template = "\\$\\{([^}]+)}".toRegex()
        template.findAll(input).forEach {
            val placeholder = it.value
            val key = it.groupValues[1]
            val replacement = if (key == "mod_version") project.version.toString() else get(key)
            result = result.replace(placeholder, replacement)
        }
        return result
    }
}

val runner = CommandRunner()

class CommandRunner {
    fun git(vararg args: String): String = command("git", *args)

    fun java(vararg args: String): List<String> = command("java", *args).split("\n")

    private fun command(vararg args: String): String {
        val p = ProcessBuilder(*args).directory(rootDir).redirectErrorStream(true).start()
        val out =
            p.inputStream
                .bufferedReader()
                .readText()
                .trim()
        check(p.waitFor() == 0) { "${args.first()} ${args.joinToString(" ")} failed:\n$out" }
        return out
    }
}

val featAccessTransformer = AccessTransformer()

class AccessTransformer {
    val location = props.get("access_transformer_location", $$"${mod_id}_at.cfg")
    val enabled = props.flag("use_access_transformer", false) || file("$projectDir/src/main/resources/$location").exists()
}

val featTags = Tags()

class Tags {
    val enabled = props.flag("use_tags", true) && file("tags.properties").exists()
    val className = props.get("tag_class_name", $$"${root_package}.${mod_id}.Tags")
}

val featMixins = Mixin()

class Mixin {
    val enabled = props.flag("use_mixins", false)
    val refMap = props.get("mixin_refmap", $$"mixins.${mod_id}.refmap.json")
    val connectorClass = props.get("mixin_connector_class", $$"${root_package}.${mod_id}.MixinConnector")
}

val featKotlin = Kotlin()

class Kotlin {
    val enabled = props.flag("use_kotlin", false)
    val catalyx = props.flag("use_catalyx", $$"${{ \"${mod_id}\" != \"catalyx\" }}")
    val forgelinContinuous = props.flag("use_forgelincontinuous", false) || enabled || catalyx
}

val featPublishing = Publishing()

class Publishing {
    private val releaseType = props.get("release_type", "release")
    private val types =
        mapOf(
            "alpha" to ReleaseType.ALPHA,
            "beta" to ReleaseType.BETA,
            "release" to ReleaseType.STABLE,
        )

    val publishToMaven = props.flag("publish_to_maven", true)
    val mavenUrl = props.get("maven_url", "https://maven.ender-development.org")

    val server = props.flag("is_server_side", true)
    val client = props.flag("is_client_side", true)

    init {
        require(releaseType in types.keys) { "'release_type' is none of ['release', 'beta', 'alpha']" }
        require(client || server) { "At least one of 'client_side' and 'server_side' must be true!" }
    }

    val branch = types[releaseType] ?: ReleaseType.STABLE

    val environment: ModrinthEnvironment =
        when {
            client && !server -> ModrinthEnvironment.CLIENT_ONLY
            !client && server -> ModrinthEnvironment.SERVER_ONLY
            else -> ModrinthEnvironment.CLIENT_AND_SERVER
        }
}

val mod = Mod()

class Mod {
    val id =
        props.get("mod_id").apply {
            lowercase()
            trim()
            replace(" ", "_")
        }
    val name = props.get("mod_name", $$"${mod_id}")
    val version = project.version.toString()
    val description = props.get("mod_description", "")
    val authors = props.list("mod_authors", ",")
    val credits = props.get("mod_credits", "Ender-Development.org")
    val url = props.get("mod_url")
    val logoPath = props.get("mod_logo_path", $$"assets/${mod_id}/textures/logo.png")

    init {
        require(id.matches(Regex("^[a-z0-9_]+$"))) { "'mod_name' can only contain letters, digits or underscores!" }
    }
}

val readme = Readme()

class Readme {
    val license = props.get("license", "All Rights Reserved")
    val content = props.get("readme", $$"${mod_description}")
    val partnership = props.get("partnership", "")
}

val utils = Utilities()

class Utilities {
    private fun cSpan(): String {
        val yFrom = runner.git("log", "--max-parents=0", "--format=%as").split("-").first()
        val yLast = runner.git("log", "-1", "--format=%as").split("-").first()
        return if (yFrom == yLast) yFrom else "$yFrom-$yLast"
    }

    val latestRelease = if (mod.version.split("-").size == 1) mod.version else runner.git("tag").split("\n").last()
    val header = "/*\n * Copyright (c) ${cSpan()} ${mod.authors.first()}\n * SPDX-License-Identifier: ${readme.license}\n */\n\n"
    val javaVersion: String by lazy {
        runner
            .java("--version")
            .firstOrNull()
            ?.split(" ")
            ?.getOrNull(1) ?: JavaVersion.current().toString()
    }
    val isTemplateRepo: Boolean by lazy {
        runner.git("remote").split("\n").any { runner.git("remote", "get-url", it).contains(repoName) }
    }

    val versionGradle: String by lazy {
        runner
            .java("-jar", "gradle/wrapper/gradle-wrapper.jar", "-v")
            .getOrNull(2)
            ?.split(" ")
            ?.lastOrNull() ?: GradleVersion.current().toString()
    }

    val markdownChangelog: Provider<String> =
        provider {
            if (!file("CHANGELOG.md").exists()) throw GradleException("CHANGELOG.md does not exist!")
            val log =
                changelog.renderItem(
                    changelog.get(mod.version).withHeader(false).withEmptySections(false),
                    Changelog.OutputType.MARKDOWN,
                )
            if (log.isEmpty()) throw GradleException("changelog for version ${mod.version} is empty!")
            log
        }
}

data class ModDependency(
    val slug: String,
    val flag: Boolean,
    val dep: String,
    val availableOnCurseForge: Boolean = true,
    val availableOnModrinth: Boolean = true,
    val optional: Boolean = false,
)

data class Platform(
    val id: String,
    val url: String,
    val downloadsCount: Long,
)

data class ModdexProject(
    val id: Int,
    val slug: String,
    val rating: Double?,
    val curseforge: Platform?,
    val modrinth: Platform?,
)

fun moddexLookup(
    moddexId: String? = null,
    curseforgeId: String? = null,
    modrinthId: String? = null,
): ModdexProject? {
    val params =
        listOf(
            "moddex_id" to moddexId,
            "curseforge_id" to curseforgeId,
            "modrinth_id" to modrinthId,
        ).mapNotNull { (key, value) ->
            value?.trim()?.takeIf { it.isNotBlank() }?.let { key to URLEncoder.encode(it, "UTF-8") }
        }.ifEmpty { return null }

    val query = params.joinToString("&") { (key, value) -> "$key=$value" }
    return parseModdex(fetchText("https://moddex.gg/api/v2/lookup?$query"))
}

fun parseModdex(text: String): ModdexProject {
    val data =
        Json
            .parseToJsonElement(text)
            .jsonObject
            .getValue("data")
            .jsonObject

    fun platform(key: String): Platform? {
        val o = data[key] as? JsonObject ?: return null
        return Platform(
            id = o.getValue("id").jsonPrimitive.content,
            url = o.getValue("url").jsonPrimitive.content,
            downloadsCount = o["downloads_count"]?.jsonPrimitive?.longOrNull ?: 0,
        )
    }

    return ModdexProject(
        id = data.getValue("id").jsonPrimitive.int,
        slug = data.getValue("slug").jsonPrimitive.content,
        rating = (data["rating"] as? JsonObject)?.get("average")?.jsonPrimitive?.doubleOrNull,
        curseforge = platform("curseforge"),
        modrinth = platform("modrinth"),
    )
}

fun MinimalExternalModuleDependency.toCord(): String = "${this.group}:${this.name}:${this.version}"

val repoName = "Ender-Development/EnderDevelopmentTemplate"
val editorconfig = props.get("editorconfig", "spotless")
val minecraftVersion = props.get("minecraft_version", "1.12.2")
val rootPackage = props.get("root_package", "org.ender_development")

val modDependencies =
    listOf(
        ModDependency("mixin-booter", featMixins.enabled, ""),
        ModDependency("assetmover", props.flag("use_assetmover", false), libs.assetmover.get().toCord()),
        ModDependency("configanytime", props.flag("use_configanytime", false) || featKotlin.catalyx, libs.configanytime.get().toCord()),
        ModDependency("better-config", props.flag("use_betterconfig", false), libs.betterconfig.get().toCord()),
        ModDependency("crafttweaker", props.flag("use_crafttweaker", false), "", optional = true),
        ModDependency("groovyscript", props.flag("use_groovyscript", false), libs.groovyscript.get().toCord(), optional = true),
        ModDependency("forgelin-continuous", featKotlin.forgelinContinuous, "T#io.github.chaosunity.forgelin:Forgelin-Continuous:${libs.versions.kotlin.get()}.0"),
        ModDependency("catalyx", featKotlin.catalyx, "C#${libs.catalyx.get().toCord()}"),
        ModDependency("modularui-uf", props.flag("use_modularui", false), "D#curse.maven:modularui-uf-1663175:${libs.versions.modularui}"),
        ModDependency("reach-fix", props.flag("use_reachfix", false), "D#curse.maven:reach-fix-556777:${libs.versions.reachfix}"),
        ModDependency("expanded-events", props.flag("use_expandedevents", false), "D#curse.maven:expanded-events-1396395:${libs.versions.expandedevents}", availableOnModrinth = false),
    )

val moddexProject: ModdexProject? by lazy {
    moddexLookup(props.get("moddex_id", ""), props.get("curseforge_id", ""), props.get("modrinth_id", ""))
}
// endregion

group = rootPackage

base {
    archivesName = mod.id
}

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.kotlinx.serialization.json)
    }
}

clearSkies {
    include("${rootPackage.replace(".", "/")}/**")
}

configurations {
    create("mixinAgent") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    all { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }
}

dependencies {
    runtimeOnly(libs.strip.latest.forge.requirements)
    runtimeOnly(libs.osxnarratorblocker)

    if (featKotlin.enabled) runtimeOnly(libs.rozutils) { isTransitive = false }

    runtimeOnly("curse.maven:scalingguis-319656:${libs.versions.scalingguis.get()}")

    compileOnly(libs.jetbrains.annotations)
    annotationProcessor(libs.jetbrains.annotations)

    val mixin: String = modUtils.enableMixins(libs.mixinbooter.get().toCord(), featMixins.refMap) as String
    implementation(mixin) { isTransitive = false }
    annotationProcessor(mixin)
    add("mixinAgent", mixin) { isTransitive = false }

    implementation(libs.hei)
    implementation(rfg.deobf("curse.maven:theonesmeagle-977883:${libs.versions.top.get()}"))

    if (modDependencies.first { it.slug == "crafttweaker" }.flag) {
        implementation(libs.crafttweaker.api)
        implementation(libs.crafttweaker.zenscript)
        implementation("CraftTweaker2:CraftTweaker2-MC1120-Main:1.12-${libs.versions.crafttweaker.get()}")
    }

    val modDeps = modDependencies.map { if (it.flag) it.dep else "" } + props.load("dependencies.properties").values
    modDeps.filter { it.isNotEmpty() }.forEach { raw ->
        if (raw.count { it == '#' } > 1) throw GradleException("Dependency $raw contains more than one delimiter '#'!")
        val flags = raw.substringBefore('#', "").uppercase()
        val notation = raw.substringAfter('#')

        val transitive = 'T' in flags
        val changing = 'C' in flags

        require(!('R' in flags && 'D' in flags)) { "R and D cannot be combined in '$raw'" }
        require(!('D' in flags && ('T' in flags || 'C' in flags))) { "D cannot be combined with T or C in '$raw'" }

        when {
            'R' in flags -> {
                runtimeOnly(notation) {
                    isTransitive = transitive
                    isChanging = changing
                }
            }

            'D' in flags -> {
                implementation(rfg.deobf(notation))
            }

            else -> {
                implementation(notation) {
                    isTransitive = transitive
                    isChanging = changing
                }
            }
        }
    }
}

idea {
    module {
        inheritOutputDirs = true
        isDownloadJavadoc = true
        isDownloadSources = true
    }
    project {
        jdkName = "azul-25"
        languageLevel = IdeaLanguageLevel(8)
        settings {
            taskTriggers { afterSync("afterSync") }
            runConfigurations {
                fun newTask(
                    name: String,
                    task: String,
                ) = add(Gradle("${runConfigurations.size}. $name").apply { setProperty("taskNames", listOf(task)) })

                fun release(
                    name: String,
                    params: String,
                ) = add(
                    Gradle(name).apply {
                        setProperty("taskNames", listOf((if (utils.isTemplateRepo) "pushRelease" else "releaseAndPublish")))
                        setProperty("scriptParameters", params)
                    },
                )
                clear()
                newTask("Run Client", "runClient")
                newTask("Run Server", "runServer")
                newTask("Run Obfuscated Client", "runObfClient")
                newTask("Run Obfuscated Server", "runObfServer")
                newTask("Build Jars", "build")
                newTask("publish to Maven", "publish")
                newTask("Update Buildscripts", "updateBuildScript")
                newTask("Apply Spotless", "spotlessApply")

                if (utils.isTemplateRepo) {
                    newTask("Generate Template Docs", "generatePropertyDocs")
                    newTask("Update Gradle Version", "updateGradle")
                }

                release("Bump (patch)", "-Preckon.stage=final")
                release("Bump (minor)", "-Preckon.stage=final -Preckon.scope=minor")
                release("Bump (major)", "-Preckon.stage=final -Preckon.scope=major")
            }
            compiler.javac {
                afterEvaluate {
                    javacAdditionalOptions = "-encoding utf8"
                    moduleJavacAdditionalOptions =
                        mapOf(
                            "${project.name}.main" to
                                tasks.compileJava
                                    .get()
                                    .options.compilerArgs
                                    .joinToString(" ") { "\"$it\"" },
                        )
                }
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
        vendor = JvmVendorSpec.AZUL
    }
    if (props.flag("generate_source_jar", true)) withSourcesJar()
    if (props.flag("generate_javadocs_jar", true)) withJavadocJar()
}

kotlin {
    jvmToolchain(8)
    compilerOptions {
        extraWarnings = true
    }
}

minecraft {
    mcVersion = minecraftVersion
    mcpMappingChannel = props.get("mapping_channel", "stable")
    mcpMappingVersion = props.get("mapping_version", 39)
    username = props.env("DEV_USERNAME").ifBlank { props.get("minecraft_username", "Developer") }
    useDependencyAccessTransformers = props.flag("use_dependency_at_files", true)

    val args = mutableListOf("-ea:$group.${mod.id}")
    args.add("-Dmixin.hotSwap=true")
    args.add("-Dmixin.checks.interfaces=true")
    args.add("-Dmixin.debug.export=true")
    if (props.flag("is_coremod", false)) {
        args.add("-Dlegacy.debugClassLoading=true")
        args.add("-Dlegacy.debugClassLoadingFiner=true")
        args.add("-Dlegacy.debugClassLoadingSave=true")
    }
    extraRunJvmArguments.addAll(args)
    extraRunJvmArguments.addAll(props.list("extra_jvm_args", ";"))

    if (featTags.enabled && !featKotlin.enabled) {
        injectedTags.set(props.load("tags.properties").ifEmpty { return@minecraft })
    }
}

publishing {
    repositories {
        maven {
            name = props.get("maven_name", $$"${mod_id}")
            url = uri(featPublishing.mavenUrl)
            credentials {
                username = props.env("MAVEN_USERNAME")
                password = props.env("MAVEN_PASSWORD")
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = rootPackage
            artifactId = mod.id
        }
    }
}
tasks.publish {
    onlyIf { !utils.isTemplateRepo && featPublishing.publishToMaven }
}

fun modArchive(): Provider<RegularFile> {
    val jar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    if (!tasks.names.contains("reobfJar")) return jar
    return tasks.named("reobfJar").flatMap { reobf ->
        val outputs = reobf.outputs.files
        if (outputs.files.size != 1) {
            jar
        } else {
            layout.file(provider { outputs.singleFile })
        }
    }
}

publishMods {
    if (moddexProject == null) return@publishMods
    modLoaders.add("forge")
    maxRetries = 5
    changelog = utils.markdownChangelog
    type = featPublishing.branch
    curseforge {
        if (!props.flag("publish_to_curseforge", false) || moddexProject!!.curseforge == null) return@curseforge
        dryRun = props.flag("curseforge_debug", false)
        accessToken = props.env("CURSEFORGE_TOKEN").ifEmpty { return@curseforge }
        projectId = moddexProject?.curseforge?.id ?: return@curseforge
        minecraftVersions.add(minecraftVersion)
        client = featPublishing.client
        server = featPublishing.server

        optional("had-enough-items")
        optional("theonesmeagle")
        modDependencies.filter { it.flag && it.availableOnCurseForge }.forEach {
            if (it.optional) {
                optional(it.slug)
            } else {
                requires(it.slug)
            }
        }
    }
    modrinth {
        if (!props.flag("publish_to_modrinth", false) || moddexProject!!.modrinth == null) return@modrinth
        dryRun = props.flag("modrinth_debug", false)
        accessToken = props.env("MODRINTH_TOKEN").ifEmpty { return@modrinth }
        projectId = moddexProject?.modrinth?.id ?: return@modrinth
        minecraftVersions.add(minecraftVersion)
        if (props.flag("modrinth_sync_readme", true)) {
            projectDescription = providers.fileContents(layout.projectDirectory.file("README.me")).asText
        }
        environment = featPublishing.environment

        optional("had-enough-items")
        optional("theonesmeagle")
        // removeSuffix is needed as modularui-uf isn't published on modrinth
        modDependencies.filter { it.flag && it.availableOnModrinth }.forEach {
            val slug = it.slug.removeSuffix("-uf")
            if (it.optional) {
                optional(slug)
            } else {
                requires(slug)
            }
        }
        props.list("additional_dependencies").forEach { requires(it) }
    }
}

repositories {
    maven {
        name = "CleanroomMC Maven"
        url = uri("https://maven.cleanroommc.com")
    }
    // Our own maven, meow :3
    maven {
        name = "Ender-Development Maven"
        url = uri("https://maven.ender-development.org/")
        content {
            includeGroupByRegex("org\\.ender_development(\\..*)?")
            includeGroupByRegex("futbol(\\..*)?")
            includeGroupByRegex("teamroots(\\..*)?")
        }
    }
    // Better implementation of the curseforge maven, which allows adding mods that disable third party downloads
    exclusiveContent {
        forRepository {
            maven {
                name = "CurseMaven"
                url = uri("https://curse.cleanroommc.com")
            }
        }
        filter {
            includeGroup("curse.maven")
        }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    maven {
        name = "Red Studio"
        url = uri("https://repo.redstudio.dev/dev")
        content {
            includeGroupByRegex("dev\\.redstudio(\\..*)?")
        }
    }
    maven {
        name = "BlameJared's Maven"
        url = uri("https://maven.blamejared.com/")
        content {
            includeGroup("CraftTweaker2")
        }
    }
    mavenCentral()
    mavenLocal() // Must be last for caching to work
}

configure<SpotlessExtension> {
    encoding("UTF-8")
    format("misc") {
        target(".gitignore", ".gitattributes")
        replaceRegex("collapse blank lines", "\\n{3,}", "\n\n")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("properties") {
        target("*.properties", "gradle/**/*.properties")
        // key=value -> key = value
        replaceRegex(
            "normalize assignment spacing",
            "(?m)^([A-Za-z0-9_.\\-]+)[ \\t]*=[ \\t]*(.*)$",
            $$"$1 = $2",
        )
        // at most one blank line in a row
        replaceRegex("collapse blank lines", "\\n{3,}", "\n\n")
        trimTrailingWhitespace()
        endWithNewline()
    }
    freshmark {
        target(".github/**/*.md", "docs/**/*.md", "gradle/**/*.md", "src/*/resources/**/*.md", "*.md")
        propertiesFile("gradle.properties")
        properties {
            putAll(extra.properties.mapValues { (k, v) -> props.get(k) })
            put("javaVersion", utils.javaVersion)
            put("kotlinVersion", libs.versions.kotlin.get())
            put("gradleVersion", project.gradle.gradleVersion)
            put("repoName", repoName)
            put("mavenUrl", featPublishing.mavenUrl.takeIf { featPublishing.publishToMaven }.orEmpty())
            put("LICENSE", readme.license)
            put("README", readme.content)
            put("SPONSOR", readme.partnership)
            put("VERSION", utils.latestRelease)
            put("MODRINTH", moddexProject?.modrinth?.id ?: "")
            put("MODRINTH_LINK", moddexProject?.modrinth?.url ?: "")
            put("CURSEFORGE", moddexProject?.curseforge?.id ?: "")
            put("CURSEFORGE_LINK", moddexProject?.curseforge?.url ?: "")
            put("MODDEX", moddexProject?.slug ?: "")
            put("REVIEW", if (moddexProject?.rating != null) "${moddexProject!!.rating}%2F5.0" else "Rate_on_ModDex_%E2%99%A5")
        }
    }
    flexmark {
        target(".github/**/*.md", "docs/**/*.md", "gradle/**/*.md", "src/*/resources/**/*.md", "CHANGELOG.md")
        flexmark(libs.versions.flexmark.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    json {
        target("src/*/resources/**/*.json", "src/*/resources/*.info", "src/*/resources/**/*.mcmeta", "examples/**/*.json", "run/groovy/**/*.json")
        gson().indentWithSpaces(2).sortByKeys().version(libs.versions.gson.get())
    }
    shell {
        target("gradlew")
    }
    groovy {
        target("examples/**/*.groovy", "run/groovy/**/*.groovy")
        importOrder()
        removeSemicolons()
        greclipse(libs.versions.greclipse.get()).configProperties(
            "org.eclipse.jdt.core.formatter.tabulation.char=space",
            "org.eclipse.jdt.core.formatter.tabulation.size=2",
            "org.eclipse.jdt.core.formatter.indent_empty_lines=false",
        )
    }
    yaml {
        target(".github/**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
    java {
        target("src/main/java/**/*.java")
        licenseHeader(utils.header)
        removeUnusedImports()
        if (editorconfig != "spotless") return@java
        importOrder()
        forbidWildcardImports()
        shortenFullyQualifiedTypes()
        princeOfSpace(libs.versions.princeOfSpace.get())
            .indentStyle("SPACES")
            .indentSize(4)
            .lineLength(200)
            .closingParenOnNewLine(true)
            .trailingCommas(true)
            .javaLanguageLevel(8)
        formatAnnotations()
    }
    kotlin {
        target("src/main/kotlin/**/*.kt")
        licenseHeader(utils.header)
        if (editorconfig != "spotless") return@kotlin
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("ktlint_standard_package-name" to "disabled"))
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

// Manage Access Transformers
if (featAccessTransformer.enabled) {
    val atFile = file("$projectDir/src/main/resources/${featAccessTransformer.location}")
    if (atFile.exists()) {
        tasks.deobfuscateMergedJarToSrg
            .get()
            .accessTransformerFiles
            .from(atFile)
        tasks.srgifyBinpatchedJar
            .get()
            .accessTransformerFiles
            .from(atFile)
    } else {
        throw GradleException("Access Transformer file '${featAccessTransformer.location}' does not exist!")
    }
    // Apply ATs to both Minecraft and Forge sources/runtime patched jar
    tasks.applyJST.configure { accessTransformerFiles.from(atFile) }
}

val afterSync =
    tasks.register("afterSync") {
        group = "ender-development"
        description = "Task that runs after the template has been synced. Can be used for custom actions."
        dependsOn(
            tasks.injectTags,
            switchEditorConfig,
            createReference,
            createIJScope,
            checkBuildScriptUpdate,
            generateMixinJson,
            checkPropertyDocs,
        )
        doLast { logger.lifecycle("Current Project Version: ${mod.version}") }
        finalizedBy(tasks.spotlessApply)
    }

fun guessIndent(): String {
    val default = "    "
    val editorconfig = file(".editorconfig").takeIf { it.exists() } ?: return default
    // Crude: ignores sections/globs, but good enough (spec: https://spec.editorconfig.org/)
    val settings =
        editorconfig.useLines { lines ->
            lines
                .map { it.trim() }
                .filter { '=' in it }
                .associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=').trim() }
        }
    val size = settings["indent_size"]?.toIntOrNull() ?: 4
    return when (settings["indent_style"]?.lowercase()) {
        "tab" -> "\t"
        "space" -> " ".repeat(size)
        else -> default
    }
}

listOf("runClient", "runServer").forEach { task ->
    tasks.named<JavaExec>(task).configure {
        if (props.flag("is_coremod", false)) {
            jvmArgs("-Dfml.coreMods.load=${props.get("coremod_plugin_class_name")}")
        }
        if (task.contains("Client") && modDependencies.first { it.slug == "groovyscript" }.flag) {
            mapOf(
                "-Dgroovyscript.use_examples_folder=true" to props.flag("grs_use_examples_folder", true),
                "-Dgroovyscript.run_ls=true" to props.flag("grs_run_ls", false),
                "-Dgroovyscript.generate_examples=true" to props.flag("grs_generate_examples", false),
                "-Dgroovyscript.generate_wiki=true" to props.flag("grs_generate_wiki", true),
                "-Dgroovyscript.generate_and_crash=true" to props.flag("grs_generate_and_crash", false),
                "-Dgroovyscript.log_missing_lang_keys=true" to props.flag("grs_log_missing_lang_keys", true),
                "-Dgroovyscript.disable_cache=true" to props.flag("grs_disable_cache", false),
            ).filter { it.value }.forEach { jvmArgs(it.key) }
        }
        if (jvmArgs.isNotEmpty()) logger.lifecycle(jvmArgs.toString())
    }
}

tasks.compileInjectedTagsKotlin.configure { dependsOn(tasks.injectTags) }
tasks.compilePatchedMcKotlin.configure { dependsOn(tasks.decompressDecompiledSources) }
tasks.compileMcLauncherKotlin.configure { dependsOn(tasks.createMcLauncherFiles) }
tasks.injectTags.configure {
    onlyIf { featTags.enabled && tags.get().isNotEmpty() && !featKotlin.enabled }
    outputClassName = featTags.className
}
tasks.prepareObfModsFolder.configure { finalizedBy("prioritizeCoremods") }
tasks.spotlessApply.configure {
    onlyIf { props.flag("disable_spotless", false) }
    dependsOn(tasks.clearSkiesApply)
}
tasks.decompressDecompiledSources {
    enabled =
        !layout.buildDirectory
            .dir("rfg/minecraft-src")
            .get()
            .asFile
            .exists()
}
val prioritizeCoremods =
    tasks.register("prioritizeCoremods") {
        description = "Load MixinBooter and ConfigAnytime before everything else"
        dependsOn(tasks.prepareObfModsFolder)
        doLast {
            val identifier = Regex("(mixinbooter|configanytime)-[0-9]+\\.[0-9]+\\.jar")
            fileTree("run/obfuscated").filter { it.isFile && it.name.matches(identifier) }.forEach { it.renameTo(File(it.parentFile, "!${it.name}")) }
        }
    }

val createReference =
    tasks.register("createReference") {
        group = "ender-development"
        description = "Generates the Reference.kt file from tags.properties."
        onlyIf { featKotlin.enabled && featTags.enabled }
        doLast {
            val tagsFile = "tags.properties"
            val tagsPath = featTags.className.removeSuffix(".Tags")
            val objectName = mod.name.filter(Char::isLetterOrDigit)
            val tags = props.load(tagsFile)
            val indent = guessIndent()
            val reference =
                buildString {
                    appendLine("package $tagsPath")
                    appendLine("")
                    appendLine("internal typealias Reference = ${objectName}Reference")
                    appendLine("")
                    appendLine("/**")
                    appendLine(" * Auto-generated reference object containing constants from `$tagsFile`.")
                    appendLine(" * Don't change this file manually as it will be overwritten.")
                    appendLine(" */")
                    appendLine("@Suppress(\"UNUSED\")")
                    appendLine("object ${objectName}Reference {")
                    appendLine("$indent// buildscript defaults")
                    mapOf(
                        "KOTLIN_VERSION" to libs.versions.kotlin.get(),
                        "FORGELIN_VERSION" to "${libs.versions.kotlin.get()}.0",
                        "KOTLIN_LANGUAGE_ADAPTER" to "io.github.chaosunity.forgelin.KotlinAdapter",
                        "GROOVYSCRIPT_VERSION" to libs.versions.groovyscript.get(),
                        "CRAFTTWEAKER_VERSION" to libs.versions.crafttweaker.get(),
                        "HADENOUGHITEMS_VERSION" to libs.versions.hei.get(),
                        "THEONEPROBE_VERSION" to libs.versions.top.get(),
                        "CATALYX_VERSION" to libs.versions.catalyx.get(),
                        "CONFIGANYTIME_VERSION" to libs.versions.configanytime.get(),
                        "ASSETMOVER_VERSION" to libs.versions.assetmover.get(),
                        "MIXINBOOTER_VERSION" to libs.versions.mixinBooter.get(),
                    ).forEach { (key, value) -> appendLine("${indent}const val $key = \"$value\"") }
                    appendLine("$indent// project-specific tags")
                    tags.forEach { (key, value) ->
                        val entry = if (value.toDoubleOrNull() != null) value else "\"${value.replace("\"", "\\\"")}\""
                        appendLine("${indent}const val $key = $entry")
                    }
                    appendLine("}")
                }
            val outputPath = "src/main/kotlin/${tagsPath.replace(".", "/")}/${objectName}Reference.kt"
            val outputFile = layout.projectDirectory.file(outputPath).asFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(reference.replace("\n", System.lineSeparator()))
        }
    }

val switchEditorConfig =
    tasks.register("switchEditorConfig") {
        group = "ender-development"
        description = "Switches the .editorconfig file based on the 'editorconfig' property."
        val spotlessEditorConfig = file("$projectDir/gradle/template/.editorconfig.spotless")
        val rozEditorConfig = file("$projectDir/gradle/template/.editorconfig.roz")

        val destination = file("$projectDir/.editorconfig")

        onlyIf { !props.flag("disable_editorconfig", false) }

        doFirst {
            val sourceFile =
                when (editorconfig) {
                    "spotless" -> spotlessEditorConfig
                    "roz" -> rozEditorConfig
                    else -> throw IllegalArgumentException("Unknown editorConfigType: $editorconfig")
                }
            if (!sourceFile.exists()) {
                throw GradleException("Source .editorconfig file '${sourceFile.path}' does not exist!")
            }
            Files.copy(sourceFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

val createIJScope =
    tasks.register("createIJScope") {
        group = "ender-development"
        description = "Creates the IntelliJ scope file for the mod sources"
        onlyIf { !utils.isTemplateRepo }
        inputs.property("modName", mod.name)
        val outputFile = layout.projectDirectory.file(".idea/scopes/Mod_Source.xml")
        outputs.file(outputFile)
        doLast {
            val filtered = mod.name.filter(Char::isLetterOrDigit)

            val ijScope =
                buildString {
                    appendLine("<component name=\"DependencyValidationManager\">")
                    appendLine("  <scope name=\"${mod.name} Source\" pattern=\"file[$filtered.main]:*/&amp;&amp;!file[$filtered.main]:resources//*\" />")
                    appendLine("</component>")
                }

            val file = outputFile.asFile
            file.parentFile.mkdirs()
            file.writeText(ijScope)
            logger.lifecycle("IntelliJ scope file created at ${file.toPath().normalize()}")
        }
    }

val generateMixinJson =
    tasks.register("generateMixinJson") {
        group = "ender-development"
        description = "Generate missing mixin files"

        fun mixinCfg(name: String) = "src/main/resources/mixins/mixins.${mod.id}.$name.json"
        val missingConfigs = props.list("mixin_configs").filter { !file(mixinCfg(it)).exists() }
        onlyIf { featMixins.enabled && props.flag("generate_mixin_json", true) && missingConfigs.isNotEmpty() }
        doLast {
            missingConfigs.forEach {
                val mixinCfg = file(mixinCfg(it))

                val format =
                    Json {
                        prettyPrint = true
                        prettyPrintIndent = guessIndent()
                    }
                val cfg =
                    buildJsonObject {
                        put("package", "$rootPackage.${mod.id}.")
                        put("refmap", $$"${mixin_refmap}")
                        put("target", "@env(DEFAULT)")
                        put("minVersion", "0.8.5")
                        put("compatibilityLevel", "JAVA_8")
                        putJsonArray("mixins") { }
                        putJsonArray("server") { }
                        putJsonArray("client") { }
                    }
                mixinCfg.writeText(format.encodeToString(JsonObject.serializer(), cfg))
            }
        }
    }

tasks.withType<ProcessResources> {
    // This will ensure that this task is redone when the versions change
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("mod_id", mod.id)
    inputs.property("mod_name", mod.name)
    inputs.property("mod_version", mod.version)
    inputs.property("mod_description", mod.description)
    inputs.property("mod_authors", mod.authors.joinToString(", "))
    inputs.property("mod_credits", mod.credits)
    inputs.property("mod_url", mod.url)
    inputs.property("mod_logo_path", mod.logoPath)
    inputs.property("mixin_refmap", featMixins.refMap)

    // Replace various properties
    fun getFiles(
        pattern: String,
        baseDir: File = file("src/main/resources"),
    ) = fileTree(baseDir) { include(pattern) }.files.map { it.relativeTo(baseDir).invariantSeparatorsPath }

    val mixins = getFiles("mixins/mixins.${mod.id}.*.json")
    val lang = getFiles("assets/${mod.id}/lang/*.lang")
    filesMatching(arrayListOf("mcmod.info", "pack.mcmeta") + mixins + lang) {
        expand(
            "minecraft_version" to minecraftVersion,
            "mod_id" to mod.id,
            "mod_name" to mod.name,
            "mod_version" to mod.version,
            "mod_description" to mod.description,
            "mod_authors" to mod.authors.joinToString(", "),
            "mod_credits" to mod.credits,
            "mod_url" to mod.url,
            "mod_logo_path" to mod.logoPath,
            "mixin_refmap" to featMixins.refMap,
        )
    }

    if (featAccessTransformer.enabled) {
        // Make sure Access Transformer files are in META-INF folder
        rename("(.+_at.cfg)", "META-INF/$1")
    }
}

tasks.withType<Jar> {
    manifest {
        val attributeMap = mutableMapOf<String, String>()
        if (props.flag("is_coremod", false)) {
            attributeMap["FMLCorePlugin"] = props.get("coremod_plugin_class_name")
            if (props.flag("coremod_includes_mod", true)) {
                attributeMap["FMLCorePluginContainsFMLMod"] = "true"
                val currentTask = gradle.startParameter.taskNames
                val validTasks = arrayOf("build", "prepareObfModsFolder", "runObfClient")
                if (currentTask.isNotEmpty() && currentTask[0] in validTasks) attributeMap["ForceLoadAsMod"] = "true"
            }
        }
        if (featAccessTransformer.enabled) {
            attributeMap["FMLAT"] = featAccessTransformer.location
        }
        if (featMixins.enabled && props.list("mixin_configs").isNotEmpty()) {
            attributeMap["MixinConfigs"] = props.list("mixin_configs").joinToString(",")
        }
        if (featMixins.enabled && featMixins.connectorClass.isNotBlank()) {
            attributeMap["MixinConnector"] = featMixins.connectorClass
        }
        attributes(attributeMap)
    }
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

/** Updated only when the remote first-line version is newer than the local one. */
data class VersionedFile(
    val path: String,
    val headerPrefix: String,
    val headerSuffix: String = "",
    val userSectionMarker: String? = null,
)

val versionedFiles =
    listOf(
        VersionedFile("build.gradle.kts", "// v"),
        VersionedFile("settings.gradle.kts", "// v"),
        VersionedFile("gradle.properties", "# v"),
        VersionedFile("secrets.example.properties", "# v"),
        VersionedFile("README.md", "<!-- v", " -->"),
        VersionedFile(".gitignore", "# v", userSectionMarker = "# ---- USER SECTION"),
        VersionedFile(".gitattributes", "# v", userSectionMarker = "# ---- USER SECTION"),
        VersionedFile(".github/workflows/dependency-submission.yml", "# v"),
        VersionedFile(".github/workflows/deploy.yml", "# v"),
        VersionedFile("gradle/libs.versions.toml", "# v"),
    )

class PendingUpdate(
    val path: String,
    val content: String,
    val reason: String,
)

fun connect(url: String): HttpURLConnection {
    val conn =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Ender-Development.org BuildScripts")
        }

    // Only send the token to GitHub hosts, never to arbitrary URLs
    if (props.env("GITHUB_TOKEN").isNotBlank() && conn.url.host in setOf("api.github.com", "raw.githubusercontent.com")) {
        conn.setRequestProperty("Authorization", "token ${props.env("GITHUB_TOKEN")}")
    }
    return conn
}

fun fetchText(url: String): String = connect(url).inputStream.bufferedReader().use { it.readText() }

fun fetchRaw(path: String): String = fetchText("https://raw.githubusercontent.com/$repoName/${resolveLatestTag()}/$path")

fun resolveLatestTag(): String {
    val tags =
        JsonSlurper().parseText(
            fetchText("https://api.github.com/repos/$repoName/tags?per_page=100"),
        ) as List<*>
    return tags
        .map { (it as Map<*, *>)["name"] as String }
        .mapNotNull { name ->
            val parts = name.split('.').map { it.toIntOrNull() ?: return@mapNotNull null }
            name to parts
        }.maxWithOrNull { a, b -> compareVersions(a.second, b.second) }
        ?.first
        ?: throw GradleException("No release tag found in $repoName")
}

fun listRemoteTemplateFiles(): List<String> {
    val json =
        JsonSlurper().parseText(
            fetchText("https://api.github.com/repos/$repoName/git/trees/${resolveLatestTag()}?recursive=1"),
        ) as Map<*, *>
    return (json["tree"] as List<*>)
        .map { it as Map<*, *> }
        .filter { it["type"] == "blob" && (it["path"] as String).startsWith("gradle/template/") }
        .map { it["path"] as String }
}

fun readVersion(
    text: String,
    prefix: String,
    suffix: String,
): List<Int>? {
    val first = text.lineSequence().firstOrNull() ?: return null
    if (!first.startsWith(prefix)) return null
    return first
        .removePrefix(prefix)
        .removeSuffix(suffix)
        .trim()
        .split('.')
        .map { it.toIntOrNull() ?: return null }
}

fun compareVersions(
    a: List<Int>,
    b: List<Int>,
): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val c = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

fun normalize(s: String) = s.replace("\r\n", "\n")

fun collectUpdates(): List<PendingUpdate> {
    val updates = mutableListOf<PendingUpdate>()

    versionedFiles.forEach { versionedFile ->
        val remoteText = fetchRaw(versionedFile.path)
        val remoteVersion = readVersion(remoteText, versionedFile.headerPrefix, versionedFile.headerSuffix) ?: return@forEach
        val local = file(versionedFile.path)
        val localVersion = if (local.exists()) readVersion(local.readText(), versionedFile.headerPrefix, versionedFile.headerSuffix) else null
        if (localVersion == null || compareVersions(remoteVersion, localVersion) > 0) {
            val content =
                versionedFile.userSectionMarker
                    ?.let { mergeUserSection(remoteText, if (local.exists()) local.readText() else null, it) }
                    ?: remoteText
            val from = localVersion?.joinToString(".") ?: "none"
            updates += PendingUpdate(versionedFile.path, content, "$from -> ${remoteVersion.joinToString(".")}")
        }
    }

    // Template files are always updated; for the check, "changed" means content differs or file is missing
    for (path in listRemoteTemplateFiles()) {
        val remoteText = fetchRaw(path)
        val local = file(path)
        when {
            !local.exists() -> updates += PendingUpdate(path, remoteText, "new file")
            normalize(local.readText()) != normalize(remoteText) -> updates += PendingUpdate(path, remoteText, "changed")
        }
    }
    return updates
}

fun mergeUserSection(
    remote: String,
    local: String?,
    markerPrefix: String,
): String {
    val remoteLines = remote.lines()
    val remoteMarker = remoteLines.indexOfFirst { it.startsWith(markerPrefix) }
    if (remoteMarker < 0) return remote // upstream has no marker, nothing to preserve

    val managed = remoteLines.subList(0, remoteMarker + 1)
    val localLines = local?.lines().orEmpty()
    val localMarker = localLines.indexOfFirst { it.startsWith(markerPrefix) }

    val userPart =
        if (localMarker >= 0) {
            localLines.subList(localMarker + 1, localLines.size)
        } else {
            val known = remoteLines.map { it.trim() }.toSet()
            listOf("") + localLines.filter { it.isNotBlank() && it.trim() !in known && !it.startsWith("#") }
        }
    return (managed + userPart).joinToString("\n").trimEnd() + "\n"
}

val checkBuildScriptLatest =
    tasks.register("checkBuildScriptLatest") {
        group = "ender-development"
        description = "Get the version number of the latest release"
        notCompatibleWithConfigurationCache("Performs a network lookup")
        onlyIf { !utils.isTemplateRepo }
        dependsOn(checkGradleUpdate)
        doLast { logger.lifecycle("Latest Buildscript Release: ${resolveLatestTag()}") }
    }

fun fetchLatestGradleVersion(): String {
    val json = JsonSlurper().parseText(fetchText("https://services.gradle.org/versions/current")) as Map<*, *>
    return json.getOrDefault("version", utils.versionGradle).toString()
}

fun fetchSha256(url: String): String {
    val hash = fetchText(url)
    require(Regex("[0-9a-f]{64}").matches(hash)) { "Unexpected checksum response from $url" }
    return hash
}

val checkGradleUpdate =
    tasks.register("checkGradleUpdate") {
        group = "ender-development"
        description = "Checks whether a newer Gradle release is available."
        notCompatibleWithConfigurationCache("Performs a network lookup")
        doLast {
            val latest = fetchLatestGradleVersion()
            when {
                latest != gradle.gradleVersion -> logger.lifecycle("Gradle $latest is available (you're on ${gradle.gradleVersion}). Run `./gradlew updateGradle` to upgrade.")
                else -> logger.lifecycle("Gradle is up to date (${gradle.gradleVersion}).")
            }
        }
    }

val updateGradle =
    tasks.register("updateGradle") {
        group = "ender-development"
        description = "Points the Gradle wrapper at the latest release and ensures a checksum is set."
        notCompatibleWithConfigurationCache("Performs a network lookup")

        val propsFile = rootProject.file("gradle/wrapper/gradle-wrapper.properties")

        doLast {
            val latest = fetchLatestGradleVersion()
            val props = props.load(propsFile.path).toMutableMap()
            val oldUrl = props["distributionUrl"] ?: throw GradleException("No 'distributionUrl' in $propsFile")

            val type = if (oldUrl.endsWith("-all.zip")) "all" else "bin"
            val newUrl = oldUrl.substringBeforeLast("/") + "/gradle-$latest-$type.zip"

            val urlChanged = newUrl != oldUrl
            val shaMissing = props["distributionSha256Sum"].isNullOrBlank()

            if (!urlChanged && !shaMissing) {
                logger.lifecycle("Gradle wrapper is already on $latest with a checksum set.")
                return@doLast
            }

            // Fetched before anything is written, so a failure leaves the file untouched
            val sha =
                try {
                    fetchSha256("https://services.gradle.org/distributions/gradle-$latest-$type.zip.sha256")
                } catch (e: Exception) {
                    throw GradleException("Could not fetch the checksum for Gradle $latest: ${e.message}", e)
                }

            props["distributionUrl"] = newUrl
            props["distributionSha256Sum"] = sha

            val out = props.map { (k, v) -> "$k = $v" }.sorted().joinToString("\n", prefix = "# Managed by Ender-Development.org buildscripts", postfix = "\n")

            propsFile.writeText(out)

            logger.lifecycle(
                when {
                    urlChanged -> "Wrapper updated to $latest. Reload the Gradle project to apply it."
                    else -> "Already on $latest; added the missing checksum."
                },
            )
        }
    }

val checkBuildScriptUpdate =
    tasks.register("checkBuildScriptUpdate") {
        group = "ender-development"
        description = "Checks whether a newer build script version is available (does not modify anything)"
        notCompatibleWithConfigurationCache("Performs a network lookup")
        onlyIf { !utils.isTemplateRepo }
        dependsOn(checkBuildScriptLatest, checkGradleUpdate)
        doLast {
            val updates = collectUpdates()
            if (updates.isEmpty()) {
                logger.lifecycle("Build script is up to date.")
            } else {
                logger.lifecycle("Build script update available! Run './gradlew updateBuildScript'")
                for (u in updates) logger.lifecycle("  ${u.path} (${u.reason})")
            }
        }
    }

val updateBuildScript =
    tasks.register("updateBuildScript") {
        group = "ender-development"
        description = "Updates build.gradle.kts, gradle.properties and gradle/template/** to the latest version"
        notCompatibleWithConfigurationCache("Performs a network lookup")
        dependsOn(updateGradle)
        doLast {
            val updates = collectUpdates()
            if (updates.isEmpty()) {
                logger.lifecycle("Build script already up to date!")
                return@doLast
            }
            for (u in updates) {
                val target = file(u.path)
                target.parentFile.mkdirs()
                target.writeText(u.content)
                logger.lifecycle("Updated ${u.path} (${u.reason})")
            }
            logger.lifecycle("Build script updated. Please REIMPORT the project or RESTART your IDE!")
        }
    }

fun withVersionHeader(
    text: String,
    prefix: String,
    suffix: String,
    version: String,
): String {
    val nl = if ("\r\n" in text) "\r\n" else "\n"
    val lines = text.split(nl)
    val body = if (lines.first().startsWith(prefix)) lines.drop(1) else lines
    return (listOf("$prefix$version$suffix") + body).joinToString(nl)
}

val syncBuildScriptVersion =
    tasks.register("syncBuildScriptVersion") {
        group = "ender-development"
        description = "Writes the buildscript_version property into the version header of the versioned files"

        onlyIf { utils.isTemplateRepo }
        mustRunAfter(tasks.patchChangelog)
        doLast {
            require(Regex("""\d+(\.\d+)*""").matches(mod.version)) {
                "buildscript_version '${mod.version}' is not a valid version (expected e.g. 1.2.3)"
            }

            versionedFiles.forEach {
                val target = file(it.path)
                val old = target.readText()
                val new = withVersionHeader(old, it.headerPrefix, it.headerSuffix, mod.version)
                if (new != old) {
                    target.writeText(new)
                    logger.lifecycle("Set ${it.path} header to ${it.headerPrefix}${mod.version}${it.headerSuffix}")
                } else {
                    logger.lifecycle("${it.path} already at ${mod.version}")
                }
            }
        }
    }

// Documentation Generation
enum class PropKind { PROP, LIST, ENV, FLAG }

data class PropUsage(
    val name: String,
    val kind: PropKind,
    val arg: String?,
    val file: String,
    val line: Int,
)

data class PropMeta(
    val category: String,
    val description: String,
) {
    val isComplete get() = category.isNotBlank() && description.isNotBlank()
}

val propCallRegex = Regex("""\b(props\.(?:get|list|env|flag))\(\s*"([^"$\\]+)"\s*(?:,\s*(\$*"(?:[^"\\]|\\.)*"|true|false|-?\d+)\s*)?\)""")
val propMetaFile = file("gradle/template/property-docs.json")
val propDocFile = file("gradle/template/PROPERTIES.md")

fun String.unquoted(): String {
    val s = trimStart('$')
    return if (s.length > 2 && s.first() == '"' && s.last() == '"') s.substring(1, s.length - 1) else this
}

fun PropUsage.isRequired() = kind == PropKind.PROP && arg == null

fun scanProps(source: File): List<PropUsage> {
    val text = source.readLines().joinToString("\n") { if (it.trimStart().startsWith("//")) "" else it }
    return propCallRegex
        .findAll(text)
        .map { m ->
            val kind =
                when {
                    m.groupValues[1].contains("list") -> PropKind.LIST
                    m.groupValues[1].contains("env") -> PropKind.ENV
                    m.groupValues[1].contains("flag") -> PropKind.FLAG
                    else -> PropKind.PROP
                }
            PropUsage(
                name = m.groupValues[2],
                kind = kind,
                arg = m.groups[3]?.value,
                file = source.name,
                line = text.substring(0, m.range.first).count { it == '\n' } + 1,
            )
        }.toList()
}

fun loadPropMeta(): Map<String, PropMeta> {
    if (!propMetaFile.exists()) return emptyMap()
    val root = JsonSlurper().parse(propMetaFile) as Map<*, *>
    return root.entries.associate { (key, value) ->
        val m = value as Map<*, *>
        key as String to PropMeta(m["category"] as? String ?: "", m["description"] as? String ?: "")
    }
}

fun savePropMeta(meta: Map<String, PropMeta>) {
    val out =
        meta.toSortedMap().mapValues { (_, m) ->
            linkedMapOf("category" to m.category, "description" to m.description)
        }
    propMetaFile.parentFile.mkdirs()
    propMetaFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(out)) + "\n")
}

fun scanAllProps(): Map<String, List<PropUsage>> =
    listOf(file("build.gradle.kts"))
        .filter { it.exists() }
        .flatMap { scanProps(it) }
        .groupBy { it.name }
        .toSortedMap()

fun renderProp(
    name: String,
    usages: List<PropUsage>,
    meta: PropMeta,
): String {
    val isList = usages.any { it.kind == PropKind.LIST }
    val isFlag = usages.any { it.kind == PropKind.FLAG }
    val hasEnv = usages.any { it.kind == PropKind.ENV }
    val required = usages.any { it.isRequired() }
    val defaults = usages.filter { it.kind == PropKind.PROP || it.kind == PropKind.FLAG }.mapNotNull { it.arg }.distinct()

    val type =
        when {
            isList -> {
                usages
                    .filter { it.kind == PropKind.LIST }
                    .map { it.arg ?: "\" \"" }
                    .distinct()
                    .joinToString(", ", prefix = "List (delimiter ", postfix = ")") { "`$it`" }
            }

            isFlag -> {
                "Boolean"
            }

            defaults.isNotEmpty() && defaults.all { it.unquoted().toIntOrNull() != null } -> {
                "Integer"
            }

            else -> {
                "String"
            }
        }

    val default =
        when {
            isList -> "empty list"
            defaults.isEmpty() -> "none"
            else -> defaults.joinToString(", ") { "`${it.unquoted()}`" }
        }

    return buildString {
        appendLine("### `$name`")
        appendLine()
        appendLine(meta.description.ifBlank { "_No description yet._" })
        appendLine()
        appendLine("- **Type:** $type")
        appendLine("- **Required:** ${if (required) "yes" else "no"}")
        appendLine("- **Default:** $default")
        if (hasEnv) appendLine("- **Environment variable:** `$name` takes precedence over the property")
    }
}

fun renderPropDocs(
    usages: Map<String, List<PropUsage>>,
    meta: Map<String, PropMeta>,
): String {
    val uncategorized = "⚠️ Uncategorized"
    val byCategory = usages.keys.groupBy { meta[it]?.category?.ifBlank { null } ?: uncategorized }
    val categories = byCategory.keys.sortedWith(compareBy({ it == uncategorized }, { it.split(" ", limit = 2).last().lowercase() }))

    return buildString {
        appendLine("<!-- Generated by ./gradlew generatePropertyDocs from gradle/property-docs.json. Do not edit by hand. -->")
        appendLine()
        appendLine("# Properties")
        for (category in categories) {
            appendLine()
            appendLine("## $category")
            for (name in byCategory.getValue(category).sortedWith(compareBy({ !it.startsWith("use_") }, { !it.endsWith("_version") }, { it.lowercase() }))) {
                appendLine()
                append(renderProp(name, usages.getValue(name), meta[name] ?: PropMeta("", "")))
            }
        }
    }
}

val checkPropertyDocs =
    tasks.register("checkPropertyDocs") {
        group = "ender-development"
        description = "Logs properties used in the build script that are missing or incomplete in property-docs.json"
        onlyIf { utils.isTemplateRepo }
        doLast {
            val usages = scanAllProps()
            val meta = loadPropMeta()

            val missing = usages.keys - meta.keys
            val incomplete = usages.keys.filter { it in meta && !meta.getValue(it).isComplete }
            val stale = meta.keys - usages.keys

            if (missing.isEmpty() && incomplete.isEmpty() && stale.isEmpty()) {
                logger.lifecycle("All ${usages.size} properties are documented.")
                return@doLast
            }
            if (missing.isNotEmpty()) {
                logger.lifecycle("${missing.size} properties not in ${propMetaFile.name}:")
                missing.forEach { logger.lifecycle("  $it (${usages.getValue(it).first().let { u -> "${u.file}:${u.line}" }})") }
            }
            if (incomplete.isNotEmpty()) {
                logger.lifecycle("${incomplete.size} properties with empty category or description:")
                incomplete.forEach { logger.lifecycle("  $it") }
            }
            if (stale.isNotEmpty()) {
                logger.lifecycle("${stale.size} entries no longer used in the build script:")
                stale.forEach { logger.lifecycle("  $it") }
            }
            logger.lifecycle("Run './gradlew generatePropertyDocs' to add stubs and regenerate PROPERTIES.md.")
        }
    }

val generatePropertyDocs =
    tasks.register("generatePropertyDocs") {
        group = "ender-development"
        description = "Adds new properties to property-docs.json and regenerates PROPERTIES.md"
        onlyIf { utils.isTemplateRepo }
        doLast {
            val usages = scanAllProps()
            val meta = loadPropMeta().toMutableMap()

            val added = usages.keys.filter { it !in meta }
            added.forEach { meta[it] = PropMeta("", "") }
            savePropMeta(meta)

            propDocFile.writeText(renderPropDocs(usages, meta))

            if (added.isNotEmpty()) {
                logger.lifecycle("Added ${added.size} new entries to ${propMetaFile.name}: ${added.joinToString()}")
                logger.lifecycle("Fill in their category and description, then run this task again.")
            }
            logger.lifecycle("Wrote ${propDocFile.name} (${usages.size} properties).")
        }
    }

val changelogGroups = listOf("Additions", "Changes", "Bugfixes", "Miscellaneous")
val prefixToGroup = mapOf("add" to "Additions", "fix" to "Bugfixes", "misc" to "Miscellaneous")
val changelogFile = file(if (utils.isTemplateRepo) "gradle/template/CHANGELOG.md" else "CHANGELOG.md")

changelog {
    version.set(mod.version)
    versionPrefix.set("")
    groups.set(changelogGroups)
    repositoryUrl.set(mod.url)
    path = changelogFile.canonicalPath
}

val verifyRelease =
    tasks.register("verifyRelease") {
        group = "release"
        description = "Fails unless tree is clean, HEAD is untagged and the version is final"
        notCompatibleWithConfigurationCache("runs git")
        doLast {
            check(runner.git("status", "--porcelain").isEmpty()) { "Working tree is not clean" }
            check(runner.git("tag", "--points-at", "HEAD").isEmpty()) { "HEAD is already tagged, nothing to release" }
            check('-' !in mod.version) { "Not a release version (${mod.version}). Run with -Preckon.stage=final" }
            delete(fileTree(layout.buildDirectory.dir("libs")) { include("*.jar") })
        }
    }
tasks.patchChangelog { mustRunAfter("collectCommits") }
tasks.withType<SpotlessTask>().configureEach {
    mustRunAfter(collectCommits, tasks.patchChangelog, syncBuildScriptVersion)
}
tasks.build { mustRunAfter(tasks.spotlessApply) }
val prepareRelease =
    tasks.register("prepareRelease") {
        group = "release"
        description = "Prepares the final changes, updates version references etc."
        dependsOn(verifyRelease, collectCommits, tasks.patchChangelog, syncBuildScriptVersion, tasks.spotlessApply, tasks.build)
    }
val tagRelease =
    tasks.register("tagRelease") {
        group = "release"
        description = "Builds, patches the changelog, commits and tags locally (nothing is pushed)"
        notCompatibleWithConfigurationCache("runs git")
        dependsOn(prepareRelease)
        doLast {
            val diffLink = changelog.instance.get().links[mod.version]
            val commitMsg =
                buildString {
                    appendLine("Release ${mod.version}")
                    appendLine("Diff: $diffLink")
                    appendLine(runner.git("log", "-1", "--format='Signed-off-by: %an <%ae>'").removeSurrounding("'"))
                }
            if (utils.isTemplateRepo) {
                versionedFiles.forEach { runner.git("add", file(it.path).canonicalPath) }
            }
            runner.git("add", changelogFile.canonicalPath)
            runner.git("commit", "-m", commitMsg)
            runner.git("tag", "-a", mod.version, "-m", changelog.renderItem(changelog.get(mod.version), Changelog.OutputType.PLAIN_TEXT))
        }
    }
val collectCommits =
    tasks.register("collectCommits") {
        group = "release"
        description = "Adds commits since the last tag to [Unreleased], grouped by prefix"
        notCompatibleWithConfigurationCache("runs git")
        mustRunAfter(verifyRelease)
        doLast {
            // commits since last version tag
            val lastTag = runCatching { runner.git("describe", "--tags", "--abbrev=0", "--match", "[0-9]*") }.getOrNull()
            val range = if (lastTag != null) "$lastTag..HEAD" else "HEAD"
            val subjects = runner.git("log", range, "--no-merges", "--pretty=format:%s").lines().filter { it.isNotBlank() && !it.startsWith("Release ") }

            // classify: "fix: x" -> Bugfixes, "misc: x" -> Miscellaneous, else Changes
            val prefix = Regex("^(add|fix|misc)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
            val newEntries: List<Pair<String, String>> =
                subjects.map { s ->
                    val m = prefix.find(s)
                    if (m != null) {
                        prefixToGroup.getValue(m.groupValues[1].lowercase()) to m.groupValues[2].trim()
                    } else {
                        "Changes" to s.trim()
                    }
                }

            // locate [Unreleased] section
            val lines = changelogFile.readLines()
            val start = lines.indexOfFirst { it.trim().startsWith("## [Unreleased]") }
            require(start >= 0) { "No '## [Unreleased]' section in CHANGELOG.md" }
            val linkRef = Regex("^\\[[^\\]]+\\]:\\s")
            val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") || linkRef.containsMatchIn(lines[it]) } ?: lines.size

            // parse existing entries
            val grouped = linkedMapOf<String, MutableList<String>>()
            changelogGroups.forEach { grouped[it] = mutableListOf() }
            var current = "Changes"
            for (line in lines.subList(start + 1, end)) {
                when {
                    line.startsWith("### ") -> {
                        current = line.removePrefix("### ").trim()
                        grouped.getOrPut(current) { mutableListOf() }
                    }

                    line.isBlank() -> {
                        continue
                    }

                    line.startsWith("- ") || line.startsWith("* ") -> {
                        grouped.getOrPut(current) { mutableListOf() }.add(line.drop(2).trim())
                    }

                    else -> {
                        val items = grouped[current]
                        if (!items.isNullOrEmpty()) {
                            items[items.lastIndex] = items.last() + "\n" + line
                        }
                    }
                }
            }

            // merge, skipping anything already present
            var added = 0
            for ((group, text) in newEntries) {
                if (grouped.values.none { text in it }) {
                    grouped.getOrPut(group) { mutableListOf() }.add(text)
                    added++
                }
            }
            if (added == 0) {
                logger.lifecycle("No new changelog entries")
                return@doLast
            }

            // render in fixed group order, empty groups omitted
            val body =
                buildList {
                    add("")
                    grouped.filterValues { it.isNotEmpty() }.forEach { (g, items) ->
                        add("### $g")
                        items.forEach { add("- $it") }
                        add("")
                    }
                }
            val result = lines.subList(0, start + 1) + body + lines.subList(end, lines.size)
            changelogFile.writeText(result.joinToString("\n") + "\n")
            logger.lifecycle("Added $added changelog entries")
        }
    }
tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn("verifyPushed")
    onlyIf { !utils.isTemplateRepo }
}
val verifyPushed =
    tasks.register("verifyPushed") {
        group = "release"
        description = "Fails unless the release tag exists on remote and points at HEAD"
        notCompatibleWithConfigurationCache("runs git")
        mustRunAfter(pushRelease)
        doLast {
            if (props.flag("modrinth_debug", false) && props.flag("curseforge_debug", false)) return@doLast
            check('-' !in mod.version) { "Not a release version (${mod.version}), refusing to publish" }
            val head = runner.git("rev-parse", "HEAD")
            runner.git("remote").split("\n").forEach { remote ->
                val remoteShas =
                    runner
                        .git("ls-remote", "--tags", remote)
                        .lines()
                        .map { it.split('\t') }
                        .filter { it.size == 2 && (it[1] == "refs/tags/${mod.version}^{}" || it[1] == "refs/tags/${mod.version}") }
                        .map { it[0] }
                check(head in remoteShas) { "Tag ${mod.version} is not on $remote at HEAD ($head). Push it first; nothing was published." }
            }
        }
    }
val pushRelease =
    tasks.register("pushRelease") {
        group = "release"
        description = "Atomically pushes the release commit and tag"
        notCompatibleWithConfigurationCache("runs git")
        dependsOn(tagRelease)
        doLast {
            runner.git("remote").split("\n").forEach { runner.git("push", "--atomic", it, "HEAD", "refs/tags/${mod.version}") }
        }
    }
val releaseAndPublish =
    tasks.register("releaseAndPublish") {
        group = "release"
        description = "release, then publish. Run with -Preckon.stage=final"
        dependsOn(pushRelease, tasks.publish, tasks.publishMods)
    }
