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

import java.io.StringWriter
import java.nio.charset.Charset.defaultCharset
import javax.inject.Inject
import org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM
import org.apache.commons.io.output.WriterOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecException

/**
 * Runs `install_name_tool`, the library linkage update tool for MacOS.
 * @param exec interface for starting processes.
 * @param fs interface for file system operations.
 */
abstract class InstallNameToolTask
@Inject
constructor(private val exec: ExecOperations, private val fs: FileOperations) : DefaultTask() {

  /** The file to modify. */
  @get:InputFile abstract val sourceFile: RegularFileProperty

  /**
   * The `install_name_tool` to use. Can be used to override the binary name or provide a full path.
   * If absent, a heuristic is used.
   */
  @get:Input @get:Optional abstract val installNameToolName: Property<String>

  /** Update the library's internal ID. Optional. */
  @get:Input @get:Optional abstract val idChange: Property<String>

  /** Update the library's linkage by repacing keys with values. Optional. */
  @get:Input @get:Optional abstract val libraryChanges: MapProperty<String, String>

  /** Update the library's RPATH by repacing keys with values. Optional. */
  @get:Input @get:Optional abstract val rpathChanges: MapProperty<String, String>

  /** Extend the library's RPATH with the given values. Optional. */
  @get:Input @get:Optional abstract val rpathAdditions: ListProperty<String>

  /** Remove the given values from the library's RPATH. Optional. */
  @get:Input @get:Optional abstract val rpathRemovals: ListProperty<String>

  /** The directory the transformed library is written to. */
  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  /**
   * Run the `install_name_tool` with the given configuration and store the result in the defined
   * output directory.
   */
  @TaskAction
  fun runInstallNameTool() {
    val installNameTool = installNameToolName.orNull ?: findInstallNameTool()

    val idChangeCmd = idChange.orNull?.let { listOf("-id", it) } ?: emptyList()
    val libraryChangeCmd =
        libraryChanges.orNull?.let { it.flatMap { (old, new) -> listOf("-change", old, new) } }
            ?: emptyList()
    val rpathChangeCmd =
        rpathChanges.orNull?.let { it.flatMap { (old, new) -> listOf("-rpath", old, new) } }
            ?: emptyList()
    val rpathAddCmd =
        rpathAdditions.orNull?.let { it.flatMap { new -> listOf("-add_rpath", new) } }
            ?: emptyList()
    val rpathDelCmd =
        rpathRemovals.orNull?.let { it.flatMap { old -> listOf("-delete_rpath", old) } }
            ?: emptyList()

    fs.copy {
      it.from(sourceFile)
      it.into(outputDirectory)
    }

    val copiedFile = outputDirectory.file(sourceFile.get().asFile.name).get().asFile.absolutePath

    val stdOut = StringWriter()
    val stdErr = StringWriter()
    exec.exec {
      it.commandLine =
          listOf(installNameTool) +
              idChangeCmd +
              libraryChangeCmd +
              rpathChangeCmd +
              rpathAddCmd +
              rpathDelCmd +
              copiedFile
      it.standardOutput = WriterOutputStream(stdOut, defaultCharset())
      it.errorOutput = WriterOutputStream(stdErr, defaultCharset())
    }
    if (stdOut.buffer.isNotBlank()) logger.info("install_name_tool output: {}", stdOut)
    if (stdErr.buffer.isNotBlank()) logger.error("install_name_tool error: {}", stdErr)
  }

  /**
   * Try to find an installed `install_name_tool` on the system.
   * @return the name of the found tool.
   * @throws GradleException if no tool was found.
   */
  private fun findInstallNameTool(): String {
    listOf("install_name_tool", "x86_64-apple-darwin-install_name_tool").forEach { candidate ->
      try {
        exec.exec {
          it.commandLine = listOf(candidate)
          it.isIgnoreExitValue = true
          it.standardOutput = NULL_OUTPUT_STREAM
          it.errorOutput = NULL_OUTPUT_STREAM
        }
        return candidate
      } catch (_: ExecException) {
        // try the next one
      }
    }

    throw GradleException("No install_name_tool defined or found on the search path")
  }
}
