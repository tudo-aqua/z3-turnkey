/*
 * Copyright 2019-2024 The Z3-TurnKey Authors
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

import com.diffplug.gradle.spotless.FormatExtension
import com.diffplug.gradle.spotless.KotlinExtension
import com.diffplug.gradle.spotless.KotlinGradleExtension
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import de.undercouch.gradle.tasks.download.Download
import org.glavo.mic.tasks.CompileModuleInfo
import org.gradle.api.file.DuplicatesStrategy.EXCLUDE
import org.gradle.api.plugins.BasePlugin.BUILD_GROUP
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.jvm.toolchain.JvmVendorSpec.ADOPTIUM
import org.gradle.jvm.toolchain.JvmVendorSpec.AZUL
import org.gradle.jvm.toolchain.JvmVendorSpec.BELLSOFT
import org.gradle.jvm.toolchain.JvmVendorSpec.GRAAL_VM
import org.gradle.jvm.toolchain.JvmVendorSpec.MICROSOFT
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import tools.aqua.InstallNameToolTask
import tools.aqua.NativeRewriter
import tools.aqua.OfficialZ3Distribution
import tools.aqua.TestToolchain
import tools.aqua.Z3GeneratorTask
import tools.aqua.Z3_PACKAGE
import tools.aqua.Z3_PACKAGE_PATH
import tools.aqua.isStable
import tools.aqua.isUnstable

plugins {
  `java-library`
  `maven-publish`
  signing

  alias(libs.plugins.download)
  alias(libs.plugins.moduleInfo)
  alias(libs.plugins.nexusPublish)
  alias(libs.plugins.spotless)
  alias(libs.plugins.taskTree)
  alias(libs.plugins.versions)
}

group = "tools.aqua"

val z3Version = "4.13.0"
val turnkeyVersion = ""

version = "$z3Version$turnkeyVersion"

val z3Distributions =
    listOf(
        OfficialZ3Distribution("MacOSAArch64", "arm64-osx-11.0", "osx", "aarch64", "dylib"),
        OfficialZ3Distribution("MacOSAmd64", "x64-osx-11.7.10", "osx", "amd64", "dylib"),
        OfficialZ3Distribution("LinuxAmd64", "x64-glibc-2.31", "linux", "amd64", "so"),
        OfficialZ3Distribution("WinAmd64", "x64-win", "windows", "amd64", "dll"),
        OfficialZ3Distribution("WinX86", "x86-win", "windows", "x86", "dll"),
    )

val testToolchains =
    listOf(8, 11, 17, 21).map { TestToolchain("EclipseTemurin$it", it, ADOPTIUM) } +
        listOf(8, 11, 17, 21).map { TestToolchain("AzulZulu$it", it, AZUL) } +
        listOf(8, 11, 17, 21).map { TestToolchain("BellsoftLiberica$it", it, BELLSOFT) } +
        listOf(8, 11, 17, 21).map { TestToolchain("GraalVM$it", it, GRAAL_VM) } +
        listOf(11, 17, 21).map { TestToolchain("MicrosoftOpenJDK$it", it, MICROSOFT) }

val downloadZ3Source by
    tasks.registering(Download::class) {
      description = "Download the Z3 source archive."

      src("https://github.com/Z3Prover/z3/archive/z3-$z3Version/z3-$z3Version.zip")
      dest(layout.buildDirectory.file("source-archive/z3-$z3Version.zip"))
      overwrite(false)
      quiet(true)
    }

val extractZ3Source by
    tasks.registering(Copy::class) {
      description = "Extract the Z3 source archive."

      from(zipTree(downloadZ3Source.map { it.dest }))
      into(layout.buildDirectory.dir("unpacked-source"))
    }

val copyNonGeneratedSources by
    tasks.registering(Copy::class) {
      description = "Copy the non-generated Z3 Java sources to the correct directory structure."

      from(extractZ3Source.map { it.destinationDir.resolve("z3-z3-$z3Version/src/api/java") })
      include("**/*.java")
      eachFile { path = "$Z3_PACKAGE_PATH/$path" }

      into(layout.buildDirectory.dir("non-generated-sources"))
    }

val mkConstsFiles by
    tasks.registering(Z3GeneratorTask::class) {
      description = "Generate the Java source for Z3 enumerations."

      sourceDir = layout.dir(extractZ3Source.map { it.destinationDir.resolve("z3-z3-$z3Version") })
      scriptName = "mk_consts_files"
      realOutputPackage = "$Z3_PACKAGE.enumerations"
      outputDir = layout.buildDirectory.dir("generated-enumerations")
    }

val updateAPI by
    tasks.registering(Z3GeneratorTask::class) {
      description = "Generate the Java source for the Z3 native binding."

      sourceDir = layout.dir(extractZ3Source.map { it.destinationDir.resolve("z3-z3-$z3Version") })
      scriptName = "update_api"
      requiresJavaInput = true
      realOutputPackage = Z3_PACKAGE
      outputDir = layout.buildDirectory.dir("generated-native")
    }

val rewriteNativeJava by
    tasks.registering(Copy::class) {
      description = "Rewrite the Z3 native binding to use the new unpack-and-link code."

      from(updateAPI.flatMap { it.outputDir })
      include("$Z3_PACKAGE_PATH/Native.java")
      filter(NativeRewriter::class)
      into(layout.buildDirectory.dir("rewritten-native"))
    }

val copyNativeLibs =
    z3Distributions.map { z3 ->
      val download =
          tasks.register("downloadZ3Binary${z3.nameInTasks}", Download::class) {
            description = "Download the Z3 binary distribution for ${z3.nameInTasks}."

            src(z3.downloadURL(z3Version))
            dest(layout.buildDirectory.file("binary-archives/z3${z3.nameInTasks}.zip"))
            overwrite(false)
            quiet(true)
          }

      val extract =
          tasks.register("extractZ3Binary${z3.nameInTasks}", Copy::class) {
            description = "Extract the Z3 binary distribution for ${z3.nameInTasks}."

            from(zipTree(download.map { it.dest }))
            include(
                "${z3.libraryPath(z3Version)}/libz3.${z3.libraryExtension}",
                "${z3.libraryPath(z3Version)}/libz3java.${z3.libraryExtension}")
            eachFile { path = name }

            into(layout.buildDirectory.dir("unpacked-binaries/z3${z3.nameInTasks}"))
          }

      val java =
          if (z3.needsInstallNameTool) {
            tasks
                .register("fixZ3JavaSearchPath${z3.nameInTasks}", InstallNameToolTask::class) {
                  description =
                      "Fix the search path for the Z3Java native library for ${z3.nameInTasks}."

                  sourceFile =
                      layout.file(
                          extract.map {
                            it.destinationDir.resolve("libz3java.${z3.libraryExtension}")
                          })
                  installNameToolName = project.properties["install_name_tool"]?.toString()
                  libraryChanges.put(
                      "libz3.${z3.libraryExtension}", "@loader_path/libz3.${z3.libraryExtension}")
                  outputDirectory = layout.buildDirectory.dir("fixed-binaries/z3${z3.nameInTasks}")
                }
                .flatMap { it.outputDirectory.asFile }
          } else {
            extract.map { it.destinationDir }
          }

      tasks.register("copyNativeLibraries${z3.nameInTasks}", Copy::class) {
        description =
            "Copy the Z3 native libraries for ${z3.nameInTasks} to the correct directory layout."

        from(
            extract.map { it.destinationDir.resolve("libz3.${z3.libraryExtension}") },
            java.map { it.resolve("libz3java.${z3.libraryExtension}") })
        eachFile { path = "native/${z3.operatingSystem}-${z3.cpuArchitecture}/$path" }
        into(layout.buildDirectory.dir("native-libs/z3${z3.nameInTasks}"))
      }
    }

sourceSets {
  main {
    java {
      srcDirs(
          copyNonGeneratedSources.map { it.destinationDir },
          mkConstsFiles.flatMap { it.outputDir },
          rewriteNativeJava.map { it.destinationDir })
    }
    resources {
      copyNativeLibs.forEach { copyNative -> srcDir(copyNative.map { it.destinationDir }) }
    }
  }
}

repositories { mavenCentral() }

val testJar by configurations.registering { extendsFrom(configurations.testRuntimeClasspath.get()) }

dependencies {
  testImplementation(libs.junit.jupiter)
  testJar(libs.junit.console)
}

tasks {
  val sourcesJar by
      registering(Jar::class) {
        group = BUILD_GROUP
        from(sourceSets.main.map { it.allJava }, extractZ3Source.map { it.destinationDir })
        archiveClassifier = DocsType.SOURCES
      }
  assemble { dependsOn(sourcesJar) }
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(8)
    vendor = ADOPTIUM
  }
  withJavadocJar()
  withSourcesJar()
}

tasks {
  val versionFile by registering {
    doLast { layout.buildDirectory.file("z3.version").get().asFile.writeText(z3Version) }
  }

  named<DependencyUpdatesTask>("dependencyUpdates") {
    gradleReleaseChannel = "current"
    rejectVersionIf { candidate.version.isUnstable && currentVersion.isStable }
  }

  javadoc { exclude("module-info.java") }

  named<CompileModuleInfo>("compileModuleInfo") {
    targetFile = layout.buildDirectory.file("classes/META-INF/versions/9")
  }

  val platformTests =
      testToolchains.map { (name, jvmVersion, jvmVendor) ->
        register<Test>("testOn$name") {
          group = VERIFICATION_GROUP
          javaLauncher =
              project.javaToolchains.launcherFor {
                languageVersion = jvmVersion
                vendor = jvmVendor
              }
          useJUnitPlatform()
          systemProperty("expectedZ3Version", z3Version)
          if (jvmVersion.asInt() > 8) {
            jvmArgs("--add-opens", "java.base/java.io=ALL-UNNAMED")
          }
          forkEvery = 1 // for hook tests
          testLogging { events(FAILED, STANDARD_ERROR, SKIPPED, PASSED) }
        }
      }

  test {
    dependsOn(*platformTests.toTypedArray())
    exclude("*")
  }

  val testRunner by
      registering(Jar::class) {
        group = BUILD_GROUP

        destinationDirectory = layout.buildDirectory.dir("libs")
        archiveClassifier = "test-runner"

        from(
            compileJava.map { it.destinationDirectory },
            sourceSets.main.map { it.resources.sourceDirectories },
            compileTestJava.map { it.destinationDirectory },
            sourceSets.test.map { it.resources.sourceDirectories },
            testJar.map { cp -> cp.files.map { if (it.isDirectory()) it else zipTree(it) } },
        )
        duplicatesStrategy = EXCLUDE
        manifest { attributes["Main-Class"] = "org.junit.platform.console.ConsoleLauncher" }
      }

  javadoc {
    (options as? StandardJavadocDocletOptions)?.apply {
      // disable doclint -- the Z3 JavaDoc contains invalid HTML5.
      addBooleanOption("Xdoclint:none", true)
      links("https://docs.oracle.com/javase/8/docs/api/")
    }
  }
}

spotless {
  java {
    target("src/*/java/**/*.java") // do not reformat Z3!
    licenseHeaderFile(file("contrib/license-header.java")).also { it.updateYearWithLatest(true) }
    googleJavaFormat()
  }
  kotlinGradle {
    licenseHeaderFile(
            file("contrib/license-header.kt"),
            "(import |@file|plugins |dependencyResolutionManagement|rootProject.name)")
        .also { it.updateYearWithLatest(true) }
    ktfmt()
  }
  format("kotlinBuildSrc", KotlinExtension::class.java) {
    target("buildSrc/src/*/kotlin/**/*.kt")
    licenseHeaderFile(
            file("contrib/license-header.kt"),
        )
        .also { it.updateYearWithLatest(true) }
    ktfmt()
  }
  format("kotlinGradleBuildSrc", KotlinGradleExtension::class.java) {
    target("buildSrc/*.gradle.kts", "buildSrc/src/*/kotlin/**/*.gradle.kts")
    licenseHeaderFile(
            file("contrib/license-header.kt"),
            "(import |@file|plugins |dependencyResolutionManagement|rootProject.name)")
        .also { it.updateYearWithLatest(true) }
    ktfmt()
  }
  format("github", FormatExtension::class.java) {
    target(".github/**/*.yml")
    licenseHeaderFile(file("contrib/license-header.yml"), "[a-z]+:").also {
      it.updateYearWithLatest(true)
    }
  }
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
      pom {
        name = "Z3-TurnKey"
        description =
            "A self-unpacking, standalone Z3 distribution that ships all required native support " +
                "code and automatically unpacks it at runtime."
        url = "https://github.com/tudo-aqua/z3-turnkey"
        licenses {
          license {
            name = "The MIT License"
            url = "https://opensource.org/licenses/MIT"
          }
          license {
            name = "ISC License"
            url = "https://opensource.org/licenses/ISC"
          }
        }
        developers {
          developer {
            name = "Simon Dierl"
            email = "simon.dierl@cs.tu-dortmund.de"
            organization = "TU Dortmund University"
          }
        }
        scm {
          connection = "scm:git:git://github.com:tudo-aqua/z3-turnkey.git"
          developerConnection = "scm:git:ssh://git@github.com:tudo-aqua/z3-turnkey.git"
          url = "https://github.com/tudo-aqua/z3-turnkey/tree/master"
        }
      }
    }
  }
}

signing {
  setRequired { gradle.taskGraph.allTasks.any { it.group == PUBLISH_TASK_GROUP } }
  useGpgCmd()
  sign(publishing.publications["maven"])
}

nexusPublishing { this.repositories { sonatype() } }
