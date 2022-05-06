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

package com.microsoft.z3;

import static java.lang.System.getProperty;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.newOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Handles automatic unpacking and loading for the Z3 native libraries. The Z3 code is rewritten to
 * invoke this class automatically. It should not be called manually.
 */
final class Z3Loader {

  /** This class should not be constructed. */
  private Z3Loader() {
    throw new AssertionError();
  }

  /**
   * Execute the unpack-and-load algorithm. In essence, this does
   *
   * <ol>
   *   <li>Identify the current OS and CPU architecture the JVM runs on.
   *   <li>Check if matching native libraries are present. If not, abort.
   *   <li>Unpack the native libraries to a temporary directory and schedule the resulting files for
   *       deletion on JVM exit.
   *   <li>Load the native libraries.
   * </ol>
   *
   * Loading is performed during the first Z3 operation that uses native code. A loading error will
   * be thrown during the same operation; otherwise, Z3 should be in a usable state.
   *
   * @throws UnsatisfiedLinkError if the current platform is not supported.
   * @throws UnsatisfiedLinkError if library unpacking or linking fails.
   * @throws IllegalStateException if the Z3 library distribution is incomplete, indicating a
   *     packaging error.
   */
  static void loadZ3() {
    final OperatingSystem os = OperatingSystem.identify();
    final CPUArchitecture cpu = CPUArchitecture.identify();
    final Class<Z3Loader> clazz = Z3Loader.class;

    final InputStream libZ3 = clazz.getResourceAsStream(getLibraryPath(os, cpu, "z3"));
    final InputStream libZ3Java = clazz.getResourceAsStream(getLibraryPath(os, cpu, "z3java"));
    assertLibrariesFound(libZ3, libZ3Java, os, cpu);

    final Path libZ3Out;
    final Path libZ3JavaOut;
    try {
      final Path libraryDir = createTempDirectory("z3-turnkey");
      libraryDir.toFile().deleteOnExit(); // deletion is LIFO, so add the directory first

      libZ3Out = libraryDir.resolve(getLibraryName(os, "z3"));
      libZ3Out.toFile().deleteOnExit();

      libZ3JavaOut = libraryDir.resolve(getLibraryName(os, "z3java"));
      libZ3JavaOut.toFile().deleteOnExit();

      unpackLibrary(libZ3, libZ3Out);
      unpackLibrary(libZ3Java, libZ3JavaOut);
    } catch (IOException e) {
      throw new LinkageError("Could not unpack native libraries", e);
    }

    System.load(libZ3Out.toAbsolutePath().toString());
    System.load(libZ3JavaOut.toAbsolutePath().toString());
  }

  /** Supported operating systems. */
  private enum OperatingSystem {
    /** Mac OS. */
    OS_X("osx", "dylib"),
    /** Linux. */
    LINUX("linux", "so"),
    /** Microsoft Windows. */
    WINDOWS("windows", "dll");

    /** The directory name used for the OS's libraries. */
    final String name;
    /** The file name extension for dynamic libraries defined by the OS. */
    final String libraryExtension;

    /**
     * Construct a new enum entry.
     *
     * @param name the {@link #name}.
     * @param libraryExtension the {@link #libraryExtension}.
     */
    OperatingSystem(final String name, final String libraryExtension) {
      this.name = name;
      this.libraryExtension = libraryExtension;
    }

    /**
     * Identify the current operating system. This uses the {@code os.name} system property.
     *
     * @return the current operating system.
     * @throws UnsatisfiedLinkError if the current operating system is not known, i.e., unsupported.
     */
    static OperatingSystem identify() {
      final String osName = getProperty("os.name");
      if (osName.startsWith("Darwin") || osName.startsWith("Mac")) return OS_X;
      else if (osName.startsWith("Linux")) return LINUX;
      else if (osName.startsWith("Windows")) return WINDOWS;
      else throw new UnsatisfiedLinkError("Unsupported operating system: " + osName);
    }
  }

  /** Supported CPU architectures. */
  private enum CPUArchitecture {
    /** Intel/AMD 32 bit. */
    X86("x86"),
    /** Intel/AMD 64 bit. */
    AMD64("amd64"),
    /** ARMv8 64 bit. */
    AARCH64("aarch64");

    /** The directory name used for the OS's libraries. */
    final String name;

    /**
     * Construct a new enum entry.
     *
     * @param name the {@link #name}.
     */
    CPUArchitecture(final String name) {
      this.name = name;
    }

    /**
     * Identify the CPU architecture used by the JVM. For a 32-bit JVM running on a 64-bit CPU, this
     * will identify a 32-bit CPU. Since the JVM needs lo link against same-architecture libraries,
     * this is the desired behavior. This uses the {@code os.arch} system property.
     *
     * @return the current CPU architecture.
     * @throws UnsatisfiedLinkError if the current architecture is not known, i.e., unsupported.
     */
    static CPUArchitecture identify() {
      final String osArch = getProperty("os.arch");
      switch (osArch) {
        case "i386":
        case "i686":
          return X86;
        case "amd64":
        case "x86_64":
          return AMD64;
        case "aarch64":
          return AARCH64;
        default:
          throw new UnsatisfiedLinkError("Unsupported CPU architecture: " + osArch);
      }
    }
  }

  /**
   * Get the expected library file's location for the given OS, CPU architecture and library name.
   *
   * @param os the operating system.
   * @param cpu the CPU architecture.
   * @param library the library name.
   * @return the expected location of the library for the OS-architecture-combination.
   */
  private static String getLibraryPath(
      final OperatingSystem os, final CPUArchitecture cpu, final String library) {
    return "/native/" + os.name + "-" + cpu.name + "/" + getLibraryName(os, library);
  }

  /**
   * Get the expected library file's location for the given OS and library name.
   *
   * @param os the operating system.
   * @return the expected name of the library for the OS.
   */
  private static String getLibraryName(final OperatingSystem os, final String library) {
    return "lib" + library + "." + os.libraryExtension;
  }

  /**
   * Ensure that all required Z3 libraries are present.
   *
   * @param libZ3 the input stream for {@code libz3} or {@code null}, if not found.
   * @param libZ3Java the input stream for {@code libz3java} or {@code null}, if not found.
   * @param os the current operating system, used in exception messages.
   * @param cpu the current CPU architecture, used in exception messages.
   * @throws UnsatisfiedLinkError if no library could be found.
   * @throws IllegalStateException if only one library is missing.
   */
  private static void assertLibrariesFound(
      final InputStream libZ3,
      final InputStream libZ3Java,
      final OperatingSystem os,
      final CPUArchitecture cpu) {
    if (libZ3 == null && libZ3Java == null) {
      throw new UnsatisfiedLinkError("No native libraries present for " + os + " on " + cpu);
    }
    if (libZ3 == null) {
      throw new IllegalStateException(
          "Z3 native library is missing from the distribution. This is a packaging error.");
    }
    if (libZ3Java == null) {
      throw new IllegalStateException(
          "Z3 native java support library is missing from the distribution. This is a packaging error.");
    }
  }

  /**
   * Copy a library to a destination path and closes the input stream.
   *
   * @param lib the input stream for the library, must not be {@code null}.
   * @param destination the destination path for the library.
   * @throws IOException if the copy operation fails.
   */
  private static void unpackLibrary(InputStream lib, Path destination) throws IOException {
    try (OutputStream out = newOutputStream(destination)) {
      copy(lib, out);
    }
    lib.close();
  }

  /**
   * Copy an input stream to an output stream. This functionality is provided by the standard
   * library on Java 9+; unfortunately, we target Java 8.
   *
   * @param in the input stream.
   * @param out the output stream.
   * @throws IOException if the read or write operation fails. The stream may be partially written.
   */
  private static void copy(final InputStream in, final OutputStream out) throws IOException {
    final byte[] buffer = new byte[1 << 13];
    int read;
    while ((read = in.read(buffer, 0, buffer.length)) >= 0) {
      out.write(buffer, 0, read);
    }
  }
}
