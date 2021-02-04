/*
 * Copyright 2019-2021 The Z3-TurnKey Authors
 * SPDX-License-Identifier: ISC
 *
 * Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby
 * granted, provided that the above copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN
 * AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.InitializerDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.process.internal.ExecException
import ru.vyarus.gradle.plugin.python.cmd.Python
import java.io.ByteArrayOutputStream
import java.nio.file.Files.createDirectories
import java.nio.file.Files.list
import java.nio.file.Files.writeString
import java.nio.file.Path
import java.time.LocalDate.now
import kotlin.streams.toList


plugins {
    id("com.github.ben-manes.versions") version "0.36.0"
    id("de.undercouch.download") version "4.1.1"
    `java-library`
    `maven-publish`
    id("org.cadixdev.licenser") version "0.5.0"
    id("ru.vyarus.use-python") version "2.2.0"
    signing
}


group = "io.github.tudo-aqua"

val z3Version = "4.8.7"
val turnkeyVersion = ".1"
version = "$z3Version$turnkeyVersion"


java {
    sourceCompatibility = VERSION_1_8
    targetCompatibility = VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}


fun isStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    return stableKeyword || regex.matches(version)
}


tasks.named("dependencyUpdates", DependencyUpdatesTask::class.java).configure {
    rejectVersionIf {
        !isStable(candidate.version) && isStable(currentVersion)
    }
}


class ExecResult(
    val executed: Boolean, exitValue: Int?,
    val standardOutput: ByteArray, val standardError: ByteArray
) {
    val successful = executed && exitValue == 0
}


fun execCommand(vararg commands: String): ExecResult {
    val stdOut = ByteArrayOutputStream()
    val stdErr = ByteArrayOutputStream()
    val (executed, exitValue) = try {
        true to exec {
            commandLine = listOf(*commands)
            isIgnoreExitValue = true
            standardOutput = stdOut
            errorOutput = stdErr
        }.exitValue
    } catch (_: ExecException) {
        false to null
    }
    return ExecResult(executed, exitValue, stdOut.toByteArray(), stdErr.toByteArray())
}


val installNameTool: String by lazy {
    (properties["install_name_tool"] as? String)?.let { return@lazy it }

    listOf("install_name_tool", "x86_64-apple-darwin-install_name_tool").forEach {
        if (execCommand(it).executed) return@lazy it
    }

    throw GradleException("No install_name_tool defined or found on the search path")
}


buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(group = "com.github.javaparser", name = "javaparser-core", version = "3.18.0")
    }
}


/**
 * Operating System + CPU architecture metadata.
 * @param os the operating system name used in the final distribution.
 * @param architecture the CPU architecture name used in the final distribution.
 * @param extension the library file name extension used by the OS.
 */
data class OSData(val os: String, val architecture: String, val extension: String)

/** The OS-CPU combinations Z3 distributions are available for. */
val z3Architectures = mapOf(
    "x64-osx-10.14.6" to OSData("osx", "amd64", "dylib"),
    "x64-ubuntu-16.04" to OSData("linux", "amd64", "so"),
    "x64-win" to OSData("windows", "amd64", "dll"),
    "x86-win" to OSData("windows", "x86", "dll")
)


/** Convert a Java package name to the expected class file path.
 * @param packageName the package name.
 * @return the respective relative path.
 * */
fun packageToPath(packageName: String): Path = Path.of(
    packageName.substringBefore("."),
    *packageName.split(".").drop(1).toTypedArray()
)


/** The name of the Z3 Java package. */
val z3Package = "com.microsoft.z3"
/** The relative path to the Z3 package. */
val z3PackagePath = packageToPath(z3Package)


val downloadZ3Source by tasks.registering(Download::class) {
    description = "Download the Z3 source archive."
    src("https://github.com/Z3Prover/z3/archive/z3-$z3Version/z3-$z3Version.zip")
    dest(buildDir.toPath().resolve("source-archive").resolve("z3-$z3Version.zip").toFile())
    quiet(true)
    overwrite(false)
}


val extractZ3Source by tasks.registering(Copy::class) {
    description = "Extract the Z3 source archive."
    dependsOn(downloadZ3Source)
    from(zipTree(downloadZ3Source.get().dest))
    into(buildDir.toPath().resolve("unpacked-source"))
}


val copyNonGeneratedSources by tasks.registering {
    description = "Copy the non-generated Z3 Java sources to the correct directory structure."
    dependsOn(extractZ3Source)

    val sourceDir = extractZ3Source.get().destinationDir.toPath().resolve("z3-z3-$z3Version")
    val output = buildDir.toPath().resolve("non-generated-sources")

    inputs.dir(sourceDir)
    outputs.dir(output)

    doLast {
        copy {
            from(sourceDir.resolve("src").resolve("api").resolve("java"))
            include("**/*.java")
            into(output.resolve(z3PackagePath))
        }
    }
}


/**
 * Shared logic for invoking the Z3 code generator scripts.
 * @param scriptName the name of the generator script to invoke, without directory or file name extension.
 * @param outputName the name of the output directory.
 * @param realOutputPackage the actual package the sources are output to by the script. The appropriate directory is
 * generated ahead of time.
 */
fun Task.z3GeneratorScript(scriptName: String, outputName: String, realOutputPackage: String) {
    dependsOn(extractZ3Source)

    val sourceDir = extractZ3Source.get().destinationDir.toPath().resolve("z3-z3-$z3Version")
    val output = buildDir.toPath().resolve(outputName)

    inputs.dir(sourceDir)
    outputs.dir(output)

    doLast {
        val scriptDir = sourceDir.resolve("scripts")
        val outputDir = output.resolve(z3PackagePath)

        val headers = list(sourceDir.resolve("src").resolve("api"))
            .filter {
                val fileName = it.fileName.toString()
                fileName.startsWith("z3") && fileName.endsWith(".h") && !fileName.contains("v1")
            }.map(Path::toString).toList()
        val generatorOptions =
            listOf("--java-package-name", z3Package, "--java-output-dir", outputDir.toString()) + headers

        createDirectories(output.resolve(packageToPath(realOutputPackage)))

        Python(project).exec(
            (listOf(
                "-B", scriptDir.resolve("$scriptName.py").toString()
            ) + generatorOptions).toTypedArray()
        )
    }
}


val mkConstsFiles by tasks.registering {
    description = "Generate the Java source for Z3 enumerations."
    z3GeneratorScript("mk_consts_files", "generated-enumerations", "$z3Package.enumerations")
}


val updateAPI by tasks.registering {
    description = "Generate the Java source for the Z3 native binding."
    z3GeneratorScript("update_api", "generated-native", z3Package)
}


val rewriteNativeJava by tasks.registering {
    description = "Rewrite the Z3 native binding to use the new unpack-and-link code."
    dependsOn(updateAPI)

    val input = updateAPI.get().outputs.files.singleFile.toPath()
    inputs.dir(input)
    val output = buildDir.toPath().resolve("rewritten-native")
    outputs.dir(output)

    doLast {
        val nativeJava = input.resolve(z3PackagePath).resolve("Native.java")
        val parse = JavaParser().parse(nativeJava)

        val compilationUnit = parse.result.orElseThrow()
        val nativeClass = compilationUnit.primaryType.orElseThrow()
        val staticInitializer = nativeClass.members
            .filterIsInstance(InitializerDeclaration::class.java).first(InitializerDeclaration::isStatic)
        staticInitializer.body =
            BlockStmt(NodeList(ExpressionStmt(MethodCallExpr("Z3Loader.loadZ3"))))

        val rewrittenNativeJava = output.resolve(z3PackagePath).resolve("Native.java")
        createDirectories(rewrittenNativeJava.parent)
        writeString(rewrittenNativeJava, compilationUnit.toString())
    }
}


z3Architectures.forEach { (arch, osData) ->
    tasks.register("downloadZ3Binary-$arch", Download::class) {
        description = "Download the Z3 binary distribution for $arch."
        src("https://github.com/Z3Prover/z3/releases/download/z3-$z3Version/z3-$z3Version-$arch.zip")
        dest(buildDir.toPath().resolve("binary-archives").resolve("z3-$z3Version-$arch.zip").toFile())
        quiet(true)
        overwrite(false)
    }

    tasks.register("extractZ3Binary-$arch", Copy::class) {
        description = "Extract the Z3 binary distribution for $arch."
        dependsOn("downloadZ3Binary-$arch")
        from(zipTree(tasks.named<Download>("downloadZ3Binary-$arch").get().dest))
        into(buildDir.toPath().resolve("unpacked-binaries-$arch"))
    }

    tasks.register("copyNativeLibraries-$arch") {
        description = "Copy the Z3 native libraries for $arch to the correct directory layout" +
                "${if (osData.os == "osx") " and fix the library search path" else ""}."
        dependsOn("extractZ3Binary-$arch")

        val input = tasks.named("extractZ3Binary-$arch").get().outputs.files.singleFile.toPath()
        inputs.dir(input)

        val output = buildDir.toPath().resolve("native-libraries-$arch")
        outputs.dir(output)
        doLast {
            listOf("z3", "z3java").forEach { library ->
                copy {
                    from(input.resolve("z3-$z3Version-$arch").resolve("bin").resolve("lib$library.${osData.extension}"))
                    into(output.resolve("native").resolve("${osData.os}-${osData.architecture}"))
                }
            }
            if (osData.os == "osx") {
                exec {
                    commandLine = listOf(
                        installNameTool,
                        "-change", "libz3.${osData.extension}", "@loader_path/libz3.${osData.extension}",
                        output.resolve("native")
                            .resolve("${osData.os}-${osData.architecture}")
                            .resolve("libz3java.${osData.extension}").toAbsolutePath().toString()
                    )
                }
            }
        }
    }
}


// add all generated files to source sets and create integration test source set
sourceSets {
    main {
        java {
            srcDirs(
                *listOf(copyNonGeneratedSources, mkConstsFiles, rewriteNativeJava)
                    .map { it.get().outputs.files }.toTypedArray()
            )
        }
        resources {
            srcDirs(
                *z3Architectures.keys
                    .map { tasks.named("copyNativeLibraries-$it").get().outputs.files }.toTypedArray()
            )
        }
    }
    create("integrationTest") {
        compileClasspath += tasks.jar.get().outputs.files
        runtimeClasspath += tasks.jar.get().outputs.files
    }
}


/** Integration test implementation configuration. */
val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

/** Integration test runtime-only configuration. */
val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.runtimeOnly.get())
}

repositories {
    mavenCentral()
}

dependencies {
    integrationTestImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    integrationTestRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.1")
}

val integrationTest by tasks.registering(Test::class) {
    description = "Run the integration tests against the final JAR."
    dependsOn(tasks.jar)

    useJUnitPlatform()
    setForkEvery(1)

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    testLogging {
        events(FAILED, STANDARD_ERROR, SKIPPED, PASSED)
    }
}


val integrationTestJar by tasks.registering(Jar::class) {
    description = "Package the integration tests into a JAR for standalone execution."
    dependsOn(tasks["integrationTestClasses"])

    archiveClassifier.set("integration-tests")
    from(sourceSets["integrationTest"].output.classesDirs)
}


// disable doclint -- the Z3 JavaDoc contains invalid HTML5.
(tasks.javadoc.get().options as? StandardJavadocDocletOptions)?.addBooleanOption("Xdoclint:none", true)


// ensure correct build order
tasks.compileJava.get().dependsOn(copyNonGeneratedSources, mkConstsFiles, rewriteNativeJava)
tasks.processResources.get().dependsOn(
    *z3Architectures.keys
        .map { tasks.named("copyNativeLibraries-$it") }.toTypedArray()
)


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Z3-TurnKey")
                description.set(
                    "A self-unpacking, standalone Z3 distribution that ships all required native support " +
                            "code and automatically unpacks it at runtime."
                )
                url.set("https://github.com/tudo-aqua/z3-turnkey")
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                    license {
                        name.set("ISC License")
                        url.set("https://opensource.org/licenses/ISC")
                    }
                }
                developers {
                    developer {
                        name.set("Simon Dierl")
                        email.set("simon.dierl@cs.tu-dortmund.de")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com:tudo-aqua/z3-turnkey.git")
                    developerConnection.set("scm:git:ssh://git@github.com:tudo-aqua/z3-turnkey.git")
                    url.set("https://github.com/tudo-aqua/z3-turnkey/tree/master")
                }
            }
        }
    }
    repositories {
        maven {
            name = "nexusOSS"
            val releasesUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
            credentials {
                username = properties["nexusUsername"] as? String
                password = properties["nexusPassword"] as? String
            }
        }
    }
}


signing {
    isRequired = !hasProperty("skip-signing")
    useGpgCmd()
    sign(publishing.publications["maven"])
}


license {
    /*
     * This plugin needs to go after the declaration of the integrationTest subset. Since it can not exclude source
     * files based on the file tree's location, i.e., the downloaded Z3 files, the main source set is removed and
     * added (as mainInternal) without files in the build directory.
     */

    header = project.file("contrib/license-header.txt")
    ext["year"] = now().year

    sourceSets = sourceSets.filter { it != project.sourceSets.main.get() }

    tasks {
        create("buildFiles") {
            files = project.files("build.gradle.kts", "settings.gradle.kts")
        }
        create("contrib") {
            files = fileTree("${project.rootDir}/contrib").matching { exclude("license-header.txt") }
        }
        create("pipeline") {
            files = project.files("azure-pipelines.yml")
        }
        create("mainInternal") {
            files = project.sourceSets.main.get().allSource.filter { !it.startsWith(project.buildDir) }
        }
    }
}