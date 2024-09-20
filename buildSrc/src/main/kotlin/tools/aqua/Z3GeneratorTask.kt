/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2024 The TurnKey Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools.aqua

import kotlin.io.path.createDirectories
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import ru.vyarus.gradle.plugin.python.cmd.Python
import ru.vyarus.gradle.plugin.python.cmd.env.SimpleEnvironment

/** Run the Z3 code generator scripts. */
abstract class Z3GeneratorTask : DefaultTask() {

  /** The Z3 source checkout root. */
  @get:InputDirectory abstract val sourceDir: DirectoryProperty

  /** The name of the script to run, without the `.py` suffix. */
  @get:Input abstract val scriptName: Property<String>

  /** The name of the Java package to target for generation. Defaults to `com.microsoft.z3`. */
  @get:Input abstract val targetOutputPackage: Property<String>

  init {
    @Suppress("LeakingThis") targetOutputPackage.convention("com.microsoft.z3")
  }

  /** Controls if the script requires the path to the Java API sources. Defaults to `false`. */
  @get:Input abstract val requiresJavaInput: Property<Boolean>

  init {
    @Suppress("LeakingThis") requiresJavaInput.convention(false)
  }

  /**
   * The generated files' actual package. That can not be derived from the output files. Defaults to
   * [targetOutputPackage].
   */
  @get:Input abstract val realOutputPackage: Property<String>

  init {
    @Suppress("LeakingThis") realOutputPackage.convention(targetOutputPackage)
  }

  /** The output base directory (i.e., the relative resource root). */
  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  /** Transform a Java package name into the corresponding file path. */
  private fun String.packagePath(): String = replace(".", "/")

  /** Run the generator script according to the configuration. */
  @TaskAction
  fun runGenerator() {
    val script = scriptName.flatMap { sourceDir.file("scripts/$it.py") }
    val packageOutputDir = outputDir.dir(targetOutputPackage.get().packagePath())

    val baseOptions =
        listOf(
            "--java-package-name",
            targetOutputPackage.get(),
            "--java-output-dir",
            packageOutputDir.get().toString())

    val javaInputOptions =
        if (requiresJavaInput.get())
            listOf("--java-input-dir", sourceDir.dir("src/api/java").get().toString())
        else emptyList()

    val headers =
        sourceDir
            .dir("src/api")
            .get()
            .asFileTree
            .matching {
              include("z3*.h")
              exclude("*v1*")
            }
            .map { it.toString() }

    val generatorOptions = baseOptions + javaInputOptions + headers

    outputDir
        .dir(realOutputPackage.map { it.packagePath() })
        .get()
        .asFile
        .toPath()
        .createDirectories()

    Python(SimpleEnvironment(project))
        .exec((listOf("-B", script.get()) + generatorOptions).toTypedArray())
  }
}
