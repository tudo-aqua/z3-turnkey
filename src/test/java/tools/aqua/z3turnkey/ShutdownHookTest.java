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
package tools.aqua.z3turnkey;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.z3.Native;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/** Test that the loader library schedules the unpacked Z3 native libraries for deletion. */
public class ShutdownHookTest {

  /**
   * Reflectively obtain the list of deletion-schedules files and verify that files containing
   * {@code z3} and {@code z3java} or {@code libz3} and {@code libz3java} are scheduled for deletion
   * on JVM exit after Z3 has been invoked.
   *
   * @throws ClassNotFoundException on reflection error.
   * @throws NoSuchFieldException on reflection error.
   * @throws IllegalAccessException on reflection error.
   */
  @Test
  public void testShutdownHookGeneration()
      throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    final Class<?> deleteOnExitHook = Class.forName("java.io.DeleteOnExitHook");
    final Field files = deleteOnExitHook.getDeclaredField("files");
    try {
      files.setAccessible(true);
    } catch (Exception e) {
      throw new TestAbortedException("Could not reflectively access hook", e);
    }

    @SuppressWarnings("unchecked")
    final Set<String> before = new HashSet<>((Collection<String>) files.get(null));

    Native.getFullVersion();

    @SuppressWarnings("unchecked")
    final Set<String> after = new HashSet<>((Collection<String>) files.get(null));
    HashSet<String> newFiles = new HashSet<>(after);
    newFiles.removeAll(before);

    assertTrue(
        newFiles.stream()
            .anyMatch(
                file -> {
                  String name = new File(file).getName();
                  return name.startsWith("z3.") || name.startsWith("libz3.");
                }),
        "Z3 library not scheduled for deletion "
            + "(before = "
            + before
            + ", after = "
            + after
            + ", new = "
            + newFiles
            + ")");

    assertTrue(
        newFiles.stream()
            .anyMatch(
                file -> {
                  String name = new File(file).getName();
                  return name.startsWith("z3java.") || name.startsWith("libz3java.");
                }),
        "Z3 java support library not scheduled for deletion "
            + "(before = "
            + before
            + ", after = "
            + after
            + ", new = "
            + newFiles
            + ")");
  }
}
