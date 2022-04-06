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

package tools.aqua

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import ru.vyarus.gradle.plugin.python.cmd.Python

/** Run the Z3 code generator scripts. */
abstract class Z3GeneratorTask : DefaultTask() {

  /** The Z3 source checkout root. */
  @get:InputDirectory abstract val sourceDir: DirectoryProperty

  /** The name of the script to run, without the `.py` suffix. */
  @get:Input abstract val scriptName: Property<String>

  /**
   * The generated files' package. The scripts do not generate a correct hierarchy by themselves.
   */
  @get:Input abstract val realOutputPackage: Property<String>

  /** The output base directory (i.e., the relative resource root). */
  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  /** Run the generator script according to the configuration. */
  @TaskAction
  fun runGenerator() {
    val script = scriptName.flatMap { sourceDir.file("scripts/$it.py") }
    val packageOutputDir = outputDir.dir(Z3_PACKAGE_PATH)

    val headers =
        sourceDir
            .dir("src/api")
            .get()
            .asFileTree
            .matching {
              it.include("z3*.h")
              it.exclude("*v1*")
            }
            .map { it.toString() }

    val generatorOptions =
        listOf(
            "--java-package-name",
            Z3_PACKAGE,
            "--java-output-dir",
            packageOutputDir.get().toString()) + headers

    outputDir.dir(realOutputPackage.map { it.packagePath() }).get().asFile.mkdirs()

    Python(project).exec((listOf("-B", script.get()) + generatorOptions).toTypedArray())
  }
}
