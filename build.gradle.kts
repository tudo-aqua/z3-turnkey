/*
 * Copyright 2019 Simon Dierl <simon.dierl@cs.tu-dortmund.de>
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

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.InitializerDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.JavaVersion.VERSION_1_8
import java.nio.file.Files.createDirectories
import java.nio.file.Files.list
import java.nio.file.Files.writeString
import java.nio.file.Path
import kotlin.streams.toList


plugins {
    id("de.undercouch.download").version("4.0.2")
    `java-library`
    `maven-publish`
}


group = "io.github.tudo-aqua"
version = "4.8.7"


java {
    sourceCompatibility = VERSION_1_8
    targetCompatibility = VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}


buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        "classpath"(group = "com.github.javaparser", name = "javaparser-core", version = "3.15.5")
    }
}


/**
 * Operating System + CPU architecture metadata.
 * @param os the operating system name used in the final distribution.
 * @param architecture the CPU architecture name used in the final distribution.
 * @param extension the library file name extension used by the OS.
 * @param hasLibPrefix `true` iff the OS prefixes libraries with `lib`.
 * */
data class OSData(val os: String, val architecture: String, val extension: String, val hasLibPrefix: Boolean = true) {
    /**
     * The library prefix as a string. `"lib"` iff [hasLibPrefix], else `""`.
     */
    val libPrefix = if (hasLibPrefix) "lib" else ""
}

/** The OS-CPU combinations Z3 distributions are available for. */
val z3Architectures = mapOf(
    "x64-osx-10.14.6" to OSData("osx", "amd64", "dylib"),
    "x64-ubuntu-16.04" to OSData("linux", "amd64", "so"),
    "x64-win" to OSData("windows", "amd64", "dll", false),
    "x86-win" to OSData("windows", "x86", "dll", false)
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


/**
 * Download all architecture-specific distrubutions of Z3 + the source ZIP from GitHub.
 */
val downloadZ3 by tasks.registering(Download::class) {
    src(z3Architectures.keys.map { "https://github.com/Z3Prover/z3/releases/download/z3-$version/z3-$version-$it.zip" })
    src("https://github.com/Z3Prover/z3/archive/z3-$version/z3-$version.zip")
    dest(buildDir.toPath().resolve("archives").toFile())
    overwrite(false)
}


/**
 * Extract the Z3 source ZIP.
 */
val extractZ3SourceZIP by tasks.registering(Copy::class) {
    dependsOn(downloadZ3)
    from(zipTree(downloadZ3.get().dest.toPath().resolve("z3-$version.zip")))
    into(buildDir.toPath().resolve("unpacked-source-archive"))
}


/**
 * Extract the Z3 binary distributions.
 */
val extractZ3BinaryZIPs by tasks.registering(Copy::class) {
    dependsOn(downloadZ3)
    from(z3Architectures.keys.map { zipTree(downloadZ3.get().dest.toPath().resolve("z3-$version-$it.zip")) })
    into(buildDir.toPath().resolve("unpacked-binary-archives"))
}


/**
 * Copy the non-generated Z3 Java sources (i.e., the object-oriented API by Christoph M. Wintersteiger) to an
 * appropriate directory.
 */
val copyNonGeneratedSources by tasks.registering {
    dependsOn(extractZ3SourceZIP)

    val sourceDir = extractZ3SourceZIP.get().destinationDir.toPath().resolve("z3-z3-$version")
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
    dependsOn(extractZ3SourceZIP)

    val sourceDir = extractZ3SourceZIP.get().destinationDir.toPath().resolve("z3-z3-$version")
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
        exec {
            commandLine = listOf("python3", "-B", scriptDir.resolve("$scriptName.py").toString()) + generatorOptions
        }
    }
}


/** Invoke the Z3 enumeration generator script. */
val mkConstsFiles by tasks.registering {
    z3GeneratorScript("mk_consts_files", "generated-enumerations", "$z3Package.enumerations")
}


/** Invoke the Z3 native binding generator script. */
val updateAPI by tasks.registering {
    z3GeneratorScript("update_api", "generated-native", z3Package)
}


/**
 * Rewrite the Z3 native binding to use our unpack-and-link code. This replaces the static initializer by a call to
 * the `Z3Loader` class provided in this project.
 */
val rewriteNative by tasks.registering {
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


/** Copy the Z3 native libraries to the correct directories. */
val repackNativeLibraries by tasks.registering {
    dependsOn(extractZ3BinaryZIPs)

    val input = extractZ3BinaryZIPs.get().outputs.files.singleFile.toPath()
    inputs.dir(input)

    val output = buildDir.toPath().resolve("native-libraries")
    outputs.dir(output)
    doLast {
        listOf("z3", "z3java").forEach { library ->
            z3Architectures.forEach { (arch, osData) ->
                copy {
                    from(
                        input.resolve("z3-$version-$arch").resolve("bin")
                            .resolve("lib$library.${osData.extension}")
                    )
                    rename { "${osData.libPrefix}$library.${osData.extension}" }
                    into(output.resolve("native").resolve("${osData.os}-${osData.architecture}"))
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
                *listOf(copyNonGeneratedSources, mkConstsFiles, rewriteNative)
                    .map { it.get().outputs.files }.toTypedArray()
            )
        }
        resources {
            srcDirs(repackNativeLibraries.get().outputs.files)
        }
    }
    create("it") {
        compileClasspath += tasks.jar.get().outputs.files
        runtimeClasspath += tasks.jar.get().outputs.files
    }
}


/** Integration test implementation configuration. */
val itImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

/** Integration test runtime-only configuration. */
val itRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.runtimeOnly.get())
}

repositories {
    mavenCentral()
}

dependencies {
    itImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    itRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
}

/** Run the integration tests. These should test the final JAR instead of unpacked files. */
val integrationTest by tasks.registering(Test::class) {
    dependsOn(tasks.jar)

    useJUnitPlatform()
    setForkEvery(1)

    testClassesDirs = sourceSets["it"].output.classesDirs
    classpath = sourceSets["it"].runtimeClasspath
}


// disable doclint -- the Z3 JavaDoc contains invalid HTML5.
(tasks.javadoc.get().options as? StandardJavadocDocletOptions)?.addBooleanOption("Xdoclint:none", true)


// ensure correct build order
tasks.compileJava.get().dependsOn(copyNonGeneratedSources, mkConstsFiles, rewriteNative)
tasks.processResources.get().dependsOn(repackNativeLibraries)


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}