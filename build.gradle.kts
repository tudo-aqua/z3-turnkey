/*
 * Copyright 2019-2022 The Z3-TurnKey Authors
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
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import tools.aqua.InstallNameToolTask
import tools.aqua.NativeRewriter
import tools.aqua.OfficialZ3Distribution
import tools.aqua.SelfBuiltZ3Distribution
import tools.aqua.Z3GeneratorTask
import tools.aqua.Z3_PACKAGE
import tools.aqua.Z3_PACKAGE_PATH
import tools.aqua.isStable
import tools.aqua.isUnstable

plugins {
  `java-library`
  `maven-publish`
  signing

  id("com.diffplug.spotless") version "6.5.1"
  id("com.dorongold.task-tree") version "2.1.0"
  id("com.github.ben-manes.versions") version "0.42.0"
  id("de.undercouch.download") version "5.0.5"
  id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

group = "tools.aqua"

val z3Version = "4.8.17"
val turnkeyVersion = ""

version = "$z3Version$turnkeyVersion"

val z3Distributions =
    listOf(
        SelfBuiltZ3Distribution("MacOSAArch64", "osx", "osx", "aarch64", "dylib"),
        OfficialZ3Distribution("MacOSAmd64", "x64-osx-10.16", "osx", "amd64", "dylib"),
        OfficialZ3Distribution("LinuxAmd64", "x64-glibc-2.31", "linux", "amd64", "so"),
        OfficialZ3Distribution("WinAmd64", "x64-win", "windows", "amd64", "dll"),
        OfficialZ3Distribution("WinX86", "x86-win", "windows", "x86", "dll"),
    )

val downloadZ3Source by
    tasks.registering(Download::class) {
      description = "Download the Z3 source archive."

      src("https://github.com/Z3Prover/z3/archive/z3-$z3Version/z3-$z3Version.zip")
      dest(buildDir.resolve("source-archive/z3-$z3Version.zip"))
      overwrite(false)
      quiet(true)
    }

val extractZ3Source by
    tasks.registering(Copy::class) {
      description = "Extract the Z3 source archive."

      from(zipTree(downloadZ3Source.map { it.dest }))
      into(buildDir.resolve("unpacked-source"))
    }

val copyNonGeneratedSources by
    tasks.registering(Copy::class) {
      description = "Copy the non-generated Z3 Java sources to the correct directory structure."

      from(extractZ3Source.map { it.destinationDir.resolve("z3-z3-$z3Version/src/api/java") })
      include("**/*.java")
      eachFile { path = "$Z3_PACKAGE_PATH/$path" }

      into(buildDir.resolve("non-generated-sources"))
    }

val mkConstsFiles by
    tasks.registering(Z3GeneratorTask::class) {
      description = "Generate the Java source for Z3 enumerations."

      sourceDir.set(
          layout.dir(extractZ3Source.map { it.destinationDir.resolve("z3-z3-$z3Version") }))
      scriptName.set("mk_consts_files")
      realOutputPackage.set("$Z3_PACKAGE.enumerations")
      outputDir.set(buildDir.resolve("generated-enumerations"))
    }

val updateAPI by
    tasks.registering(Z3GeneratorTask::class) {
      description = "Generate the Java source for the Z3 native binding."

      sourceDir.set(
          layout.dir(extractZ3Source.map { it.destinationDir.resolve("z3-z3-$z3Version") }))
      scriptName.set("update_api")
      realOutputPackage.set(Z3_PACKAGE)
      outputDir.set(buildDir.resolve("generated-native"))
    }

val rewriteNativeJava by
    tasks.registering(Copy::class) {
      description = "Rewrite the Z3 native binding to use the new unpack-and-link code."

      from(updateAPI.flatMap { it.outputDir })
      include("$Z3_PACKAGE_PATH/Native.java")
      filter(NativeRewriter::class)
      into(buildDir.resolve("rewritten-native"))
    }

val copyNativeLibs =
    z3Distributions.map { z3 ->
      val download =
          tasks.register("downloadZ3Binary${z3.nameInTasks}", Download::class) {
            description = "Download the Z3 binary distribution for ${z3.nameInTasks}."

            src(z3.downloadURL(z3Version))
            dest(buildDir.resolve("binary-archives/z3${z3.nameInTasks}.zip"))
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

            into(buildDir.resolve("unpacked-binaries/z3${z3.nameInTasks}"))
          }

      val java =
          if (z3.needsInstallNameTool) {
            tasks
                .register("fixZ3JavaSearchPath${z3.nameInTasks}", InstallNameToolTask::class) {
                  description =
                      "Fix the search path for the Z3Java native library for ${z3.nameInTasks}."

                  sourceFile.set(
                      layout.file(
                          extract.map {
                            it.destinationDir.resolve("libz3java.${z3.libraryExtension}")
                          }))
                  installNameToolName.set(
                      provider { project.properties["install_name_tool"]?.toString() })
                  libraryChanges.put(
                      "libz3.${z3.libraryExtension}", "@loader_path/libz3.${z3.libraryExtension}")
                  outputDirectory.set(buildDir.resolve("fixed-binaries/z3${z3.nameInTasks}"))
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
        into(buildDir.resolve("native-libs/z3${z3.nameInTasks}"))
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

dependencies {
  testImplementation(platform("org.junit:junit-bom:5.8.2"))
  testImplementation("org.junit.jupiter", "junit-jupiter")
}

java {
  sourceCompatibility = VERSION_1_8
  targetCompatibility = VERSION_1_8
  withJavadocJar()
  withSourcesJar()
}

tasks {
  named("dependencyUpdates", DependencyUpdatesTask::class.java) {
    rejectVersionIf { candidate.version.isUnstable && currentVersion.isStable }
  }

  test {
    useJUnitPlatform()
    systemProperty("expectedZ3Version", z3Version)
    setForkEvery(1) // for hook tests
    testLogging { events(FAILED, STANDARD_ERROR, SKIPPED, PASSED) }
  }

  javadoc {
    // disable doclint -- the Z3 JavaDoc contains invalid HTML5.
    (options as? StandardJavadocDocletOptions)?.addBooleanOption("Xdoclint:none", true)
  }
}

spotless {
  java {
    target("src/main/java", "src/test/java") // do not reformat Z3!
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
  format("githubWorkflows", FormatExtension::class.java) {
    target(".github/workflows/*.yml")
    licenseHeaderFile(file("contrib/license-header.yml"), "name:").also {
      it.updateYearWithLatest(true)
    }
  }
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
      pom {
        name.set("Z3-TurnKey")
        description.set(
            "A self-unpacking, standalone Z3 distribution that ships all required native support " +
                "code and automatically unpacks it at runtime.")
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
}

signing {
  setRequired { gradle.taskGraph.allTasks.any { it.group == PUBLISH_TASK_GROUP } }
  useGpgCmd()
  sign(publishing.publications["maven"])
}

nexusPublishing { repositories { sonatype() } }
